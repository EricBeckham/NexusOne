package one.nexus.p3.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import one.nexus.p3.api.ApiModels.PageView;
import one.nexus.p3.api.ApiModels.UpdateRecordRequest;
import one.nexus.p3.application.LedgerRecordService;
import one.nexus.p3.infra.jdbc.GuardedLedgerStore.Page;

@RestController
@RequestMapping("/api/v1/p3/ledgers/{ledgerId}/records")
public class LedgerRecordController {
    private final LedgerRecordService records;

    public LedgerRecordController(LedgerRecordService records) {
        this.records = records;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID ledgerId, @RequestBody Map<String, Object> body) {
        return records.create(ledgerId, body);
    }

    @GetMapping
    public PageView list(
            @PathVariable UUID ledgerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam Map<String, String> query) {
        query.remove("page");
        query.remove("size");
        Page result = records.list(ledgerId, query, page, size);
        return new PageView(result.items(), result.total(), result.page(), result.size());
    }

    @GetMapping("/{recordId}")
    public Map<String, Object> get(@PathVariable UUID ledgerId, @PathVariable UUID recordId) {
        return records.get(ledgerId, recordId);
    }

    @PatchMapping("/{recordId}")
    public Map<String, Object> update(
            @PathVariable UUID ledgerId,
            @PathVariable UUID recordId,
            @RequestBody UpdateRecordRequest request) {
        Map<String, Object> values = request == null || request.values() == null ? Map.of() : request.values();
        Long version = request == null ? null : request.version();
        return records.update(ledgerId, recordId, values, version);
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ledgerId, @PathVariable UUID recordId) {
        records.delete(ledgerId, recordId);
    }
}
