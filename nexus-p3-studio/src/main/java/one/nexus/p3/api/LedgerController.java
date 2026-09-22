package one.nexus.p3.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import one.nexus.p3.api.ApiModels.CreateLedgerRequest;
import one.nexus.p3.api.ApiModels.FieldRequest;
import one.nexus.p3.api.ApiModels.LedgerView;
import one.nexus.p3.application.LedgerDefinitionService;
import one.nexus.p3.application.LedgerDefinitionService.FieldDraft;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.LedgerScope;

@RestController
@RequestMapping("/api/v1/p3")
public class LedgerController {
    private final LedgerDefinitionService ledgers;

    public LedgerController(LedgerDefinitionService ledgers) {
        this.ledgers = ledgers;
    }

    @PostMapping("/applications/{applicationId}/ledgers")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerView create(
            @PathVariable UUID applicationId, @Valid @RequestBody CreateLedgerRequest request) {
        return LedgerView.of(ledgers.create(
                applicationId,
                request.ledgerCode(),
                request.displayName(),
                LedgerScope.fromApi(request.scope()),
                drafts(request.fields())));
    }

    @GetMapping("/ledgers")
    public List<LedgerView> list() {
        return ledgers.list().stream().map(LedgerView::of).toList();
    }

    @GetMapping("/ledgers/{id}")
    public LedgerView get(@PathVariable UUID id) {
        return LedgerView.of(ledgers.get(id));
    }

    @PostMapping("/ledgers/{id}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerView addField(@PathVariable UUID id, @Valid @RequestBody FieldRequest request) {
        return LedgerView.of(ledgers.addField(id, draft(request)));
    }

    private static List<FieldDraft> drafts(List<FieldRequest> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream().map(LedgerController::draft).toList();
    }

    private static FieldDraft draft(FieldRequest request) {
        return new FieldDraft(
                request.fieldCode(),
                request.displayName(),
                FieldType.fromApi(request.fieldType() == null ? "TEXT" : request.fieldType()),
                request.required(),
                request.indexed());
    }
}
