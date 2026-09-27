package one.nexus.p4.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.PaperSize;
import one.nexus.p4.market.PrintMappingModels.PrintMappingContract;
import one.nexus.p4.market.PrintMappingModels.PrintMappingRevision;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMappingPut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** CF-R3: static print-mapping contract; DPO-L3: per-product mappings in the database with revision history. */
@Service
@ConditionalOnBean({IsolationGuard.class, one.nexus.p1.port.ClockPort.class})
public class PrintMappingService {
    private static final String CONTRACT_RESOURCE = "print/cf-r3-print-mapping-contract.v1.json";

    private final IsolationGuard isolation;
    private final MarketRepository market;
    private final PrintMappingStore store;
    private final java.util.function.Supplier<java.time.Instant> clock;
    /**
     * The contract file is camelCase and read once at startup. It must not depend on an injected mapper: the
     * application context has no Jackson 2 {@code ObjectMapper} bean (the product mapper is Jackson 3, SNAKE_CASE),
     * and requiring one stopped the application from starting.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PrintMappingContract contract;

    @org.springframework.beans.factory.annotation.Autowired
    public PrintMappingService(IsolationGuard isolation, MarketRepository market, PrintMappingStore store,
            one.nexus.p1.port.ClockPort clock) {
        this(isolation, market, store, (java.util.function.Supplier<java.time.Instant>) clock::now);
    }

    /** Unit tests: any store, wall-clock time. */
    PrintMappingService(IsolationGuard isolation, MarketRepository market, PrintMappingStore store) {
        this(isolation, market, store, (java.util.function.Supplier<java.time.Instant>) java.time.Instant::now);
    }

    private PrintMappingService(IsolationGuard isolation, MarketRepository market, PrintMappingStore store,
            java.util.function.Supplier<java.time.Instant> clock) {
        this.isolation = isolation;
        this.market = market;
        this.store = store;
        this.clock = clock;
    }

    @PostConstruct
    void loadContract() throws IOException {
        ClassPathResource resource = new ClassPathResource(CONTRACT_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            this.contract = objectMapper.readValue(in, PrintMappingContract.class);
        }
    }

    public PrintMappingContract contract() {
        isolation.requirePlatformContext("print-mapping.contract", PlatformDuty.SYSTEM_ADMIN);
        return contract;
    }

    /** Package-visible for unit tests that skip IsolationGuard. */
    PrintMappingContract loadedContract() {
        return contract;
    }

    public ProductPrintMapping get(UUID productDefinitionId) {
        isolation.requirePlatformContext("print-mapping.read", PlatformDuty.SYSTEM_ADMIN);
        requireProduct(productDefinitionId);
        return store.find(productDefinitionId).orElseGet(() -> defaultMapping(productDefinitionId));
    }

    /** DPO-L3: the most recent accepted saves, newest first ({@code limit} clamped to 1..100). */
    public List<PrintMappingRevision> history(UUID productDefinitionId, int limit) {
        isolation.requirePlatformContext("print-mapping.history", PlatformDuty.SYSTEM_ADMIN);
        requireProduct(productDefinitionId);
        return store.history(productDefinitionId, Math.max(1, Math.min(limit, 100)));
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized ProductPrintMapping put(UUID productDefinitionId, ProductPrintMappingPut body) {
        one.nexus.p1.port.PlatformContext actor =
                isolation.requirePlatformContext("print-mapping.write", PlatformDuty.SYSTEM_ADMIN);
        requireProduct(productDefinitionId);
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String paper = body.paper() == null || body.paper().isBlank()
                ? contract.builtinTemplate().defaultPaper()
                : body.paper().trim();
        String orientation = body.orientation() == null || body.orientation().isBlank()
                ? contract.builtinTemplate().defaultOrientation()
                : body.orientation().trim();
        validatePaper(paper);
        validateOrientation(orientation);
        validateMappings(body.mappings());
        long current = store.find(productDefinitionId).map(ProductPrintMapping::revision).orElse(0L);
        if (body.expectedRevision() != null && body.expectedRevision() != current) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "print mapping changed: expected revision " + body.expectedRevision() + ", current " + current);
        }
        ProductPrintMapping saved = new ProductPrintMapping(productDefinitionId, store.source(),
                paper, orientation, contract.builtinTemplate().code(), body.mappings(), store.note(), current + 1);
        UUID savedBy = actor == null || actor.actor() == null ? null : actor.actor().subjectId();
        if (!store.saveIfRevision(saved, current, savedBy, clock.get())) {
            // Another instance saved between our read and our write; the conditional write changed nothing.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "print mapping changed: expected revision " + current + ", saved concurrently");
        }
        return saved;
    }

    private ProductPrintMapping defaultMapping(UUID productDefinitionId) {
        return new ProductPrintMapping(productDefinitionId, ProductPrintMapping.SOURCE_DEFAULT,
                contract.builtinTemplate().defaultPaper(), contract.builtinTemplate().defaultOrientation(),
                contract.builtinTemplate().code(), contract.defaultFieldMappings(),
                ProductPrintMapping.NOTE_DEFAULT, 0);
    }

    private void requireProduct(UUID productDefinitionId) {
        ProductDefinition definition = market.productDefinition(productDefinitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found"));
        // touch so unused warning stays quiet if record accessors differ
        if (definition.id() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product definition not found");
        }
    }

    private void validatePaper(String paper) {
        boolean ok = contract.paperSizes().stream().map(PaperSize::code).anyMatch(paper::equals);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown paper: " + paper);
        }
    }

    private void validateOrientation(String orientation) {
        boolean ok = contract.orientations().stream().anyMatch(o -> o.code().equals(orientation));
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown orientation: " + orientation);
        }
    }

    private void validateMappings(List<FieldSlotMapping> mappings) {
        Set<String> slotIds = new LinkedHashSet<>();
        contract.builtinTemplate().slots().forEach(s -> slotIds.add(s.id()));
        Set<String> seenFields = new LinkedHashSet<>();
        for (FieldSlotMapping m : mappings) {
            if (m.fieldKey() == null || m.fieldKey().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldKey required");
            }
            if (m.slotId() == null || m.slotId().isBlank() || !slotIds.contains(m.slotId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown slotId: " + m.slotId());
            }
            if (!seenFields.add(m.fieldKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate fieldKey: " + m.fieldKey());
            }
        }
    }
}