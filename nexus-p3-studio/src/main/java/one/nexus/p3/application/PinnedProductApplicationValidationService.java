package one.nexus.p3.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.file.FileObjectRepository;
import one.nexus.file.FileStatus;
import one.nexus.isolation.api.AccessContext;
import one.nexus.isolation.api.TenantContext;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p3.port.ProductApplicationStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Loads the tenant-owned K12a pin and invokes the pure pinned-snapshot validator. */
@Service
@ConditionalOnBean({IdentityRepository.class, FileObjectRepository.class})
public class PinnedProductApplicationValidationService {
    private final ProductApplicationStore store;
    private final ObjectMapper mapper;
    private final IdentityRepository identities;
    private final FileObjectRepository files;

    public PinnedProductApplicationValidationService(ProductApplicationStore store, ObjectMapper mapper,
            IdentityRepository identities, FileObjectRepository files) {
        this.store = store;
        this.mapper = mapper;
        this.identities = identities;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(UUID installationId, JsonNode values) {
        UUID tenantId = currentTenantId();
        ProductApplicationStore.InstalledPin pin = store.findInstalledPin(tenantId, installationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "installed product not found"));
        String snapshot = store.formTemplateSnapshot(pin.formTemplateVersionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "pinned form template snapshot is missing"));
        List<ProductApplicationValuesValidator.Field> fields = parseFields(snapshot);
        List<ProductApplicationValuesValidator.FieldError> errors = ProductApplicationValuesValidator.validate(
                tenantId, fields, values, new ProductApplicationValuesValidator.TenantValueOwnership() {
                    @Override public boolean personBelongsToTenant(UUID tenant, UUID personId) {
                        return identities.findPerson(tenant,personId)
                                .filter(person -> person.status()==Model.PersonStatus.ACTIVE).isPresent();
                    }
                    @Override public boolean fileBelongsToTenant(UUID tenant, UUID fileId) {
                        return files.find(fileId).filter(file -> tenant.equals(file.tenantId())
                                && file.status()==FileStatus.UPLOADED).isPresent();
                    }
                });
        return new ValidationResult(installationId, pin.productDefinitionId(), pin.formTemplateVersionId(), errors.isEmpty(), errors);
    }

    private List<ProductApplicationValuesValidator.Field> parseFields(String snapshot) {
        try {
            JsonNode array = mapper.readTree(snapshot);
            if (array == null || !array.isArray()) throw new IllegalStateException("pinned template snapshot is not an array");
            List<ProductApplicationValuesValidator.Field> fields = new ArrayList<>();
            for (JsonNode node : array) {
                JsonNode keyNode=node.get("key"), typeNode=node.get("type");
                if (keyNode==null || typeNode==null || !keyNode.isTextual() || !typeNode.isTextual())
                    throw new IllegalStateException("pinned template snapshot contains an invalid field");
                Set<String> options=new HashSet<>();
                JsonNode choices=node.get("options");
                if (choices!=null && choices.isArray()) for (JsonNode choice:choices) {
                    if (choice.isTextual()) options.add(choice.asText());
                    else if (choice.isObject() && choice.get("value")!=null && choice.get("value").isTextual())
                        options.add(choice.get("value").asText());
                }
                fields.add(new ProductApplicationValuesValidator.Field(keyNode.asText(),typeNode.asText(),
                        node.path("required").asBoolean(false),options));
            }
            return List.copyOf(fields);
        } catch (JacksonException ex) {
            throw new IllegalStateException("pinned template snapshot is invalid JSON",ex);
        }
    }

    private static UUID currentTenantId() {
        AccessContext context=AccessContextHolder.currentOrNull();
        if (!(context instanceof TenantContext tenant)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"TenantContext is required");
        try { return UUID.fromString(tenant.tenantId()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"invalid tenant context"); }
    }

    public record ValidationResult(UUID installationId, UUID productDefinitionId, UUID formTemplateVersionId,
            boolean valid, List<ProductApplicationValuesValidator.FieldError> errors) { }
}
