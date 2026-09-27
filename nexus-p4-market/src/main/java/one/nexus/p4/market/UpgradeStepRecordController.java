package one.nexus.p4.market;

import java.util.UUID;
import one.nexus.p4.market.MarketModels.UpgradeStepRecord;
import one.nexus.p4.market.UpgradeStepRecordService.StepRecordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** DPO-L6: platform operator records one upgrade step (the only way a step leaves UNKNOWN). */
@RestController
@RequestMapping("/api/v1/p4/installations/{id}/upgrade-steps")
public class UpgradeStepRecordController {
    private final UpgradeStepRecordService records;

    public UpgradeStepRecordController(UpgradeStepRecordService records) {
        this.records = records;
    }

    @PostMapping("/{stepKey}/records")
    @ResponseStatus(HttpStatus.CREATED)
    public UpgradeStepRecord record(@PathVariable UUID id, @PathVariable String stepKey, @RequestBody StepRecordRequest body) {
        return records.record(id, stepKey, body);
    }
}
