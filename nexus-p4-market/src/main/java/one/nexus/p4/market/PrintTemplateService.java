package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p4.market.PrintMappingModels.BuiltinTemplate;
import one.nexus.p4.market.PrintMappingModels.PrintMappingContract;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplateCopy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * DPO-L4: list the built-in print template plus stored copies, and copy the built-in one. Metadata only
 * (name / paper / orientation); the built-in template itself is never written.
 */
@Service
@ConditionalOnBean({IsolationGuard.class, ClockPort.class})
public class PrintTemplateService {
    private final IsolationGuard isolation;
    private final PrintMappingService printMapping;
    private final PrintTemplateStore store;
    private final ClockPort clock;

    public PrintTemplateService(IsolationGuard isolation, PrintMappingService printMapping, PrintTemplateStore store,
            ClockPort clock) {
        this.isolation = isolation;
        this.printMapping = printMapping;
        this.store = store;
        this.clock = clock;
    }

    public List<PrintTemplate> list() {
        isolation.requirePlatformContext("print-template.list", PlatformDuty.SYSTEM_ADMIN);
        List<PrintTemplate> templates = new ArrayList<>();
        templates.add(builtin(contract().builtinTemplate()));
        templates.addAll(store.copies());
        return templates;
    }

    @Transactional
    public PrintTemplate copy(PrintTemplateCopy body) {
        PlatformContext actor = isolation.requirePlatformContext("print-template.copy", PlatformDuty.SYSTEM_ADMIN);
        PrintMappingContract contract = contract();
        BuiltinTemplate source = contract.builtinTemplate();
        String sourceCode = body == null || blank(body.sourceTemplateCode()) ? source.code() : body.sourceTemplateCode().trim();
        if (!source.code().equals(sourceCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only the built-in template can be copied: " + source.code());
        }
        String name = body == null || blank(body.name()) ? "副本 · " + source.name() : body.name().trim();
        if (name.length() > 160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is too long");
        String paper = body == null || blank(body.paper()) ? source.defaultPaper() : body.paper().trim();
        String orientation = body == null || blank(body.orientation()) ? source.defaultOrientation() : body.orientation().trim();
        if (contract.paperSizes().stream().noneMatch(p -> p.code().equals(paper))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown paper: " + paper);
        }
        if (contract.orientations().stream().noneMatch(o -> o.code().equals(orientation))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown orientation: " + orientation);
        }
        UUID id = UUID.randomUUID();
        UUID createdBy = actor == null || actor.actor() == null ? null : actor.actor().subjectId();
        PrintTemplate copy = new PrintTemplate(id, "copy." + id.toString().replace("-", "").substring(0, 12), name,
                PrintTemplateModels.KIND_COPY, source.code(), paper, orientation, true, clock.now(), createdBy, 1,
                PrintTemplateModels.NOTE_COPY);
        store.insert(copy);
        return copy;
    }

    private PrintMappingContract contract() {
        return printMapping.loadedContract();
    }

    static PrintTemplate builtin(BuiltinTemplate source) {
        return new PrintTemplate(null, source.code(), source.name(), PrintTemplateModels.KIND_BUILTIN, null,
                source.defaultPaper(), source.defaultOrientation(), false, null, null, 1, PrintTemplateModels.NOTE_BUILTIN);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
