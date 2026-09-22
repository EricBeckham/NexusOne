package one.nexus.p3.flow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "nexus.flow.engine")
class RollbackProbe {
    private final SequentialApprovalService approvals;

    RollbackProbe(SequentialApprovalService approvals) {
        this.approvals = approvals;
    }

    @Transactional
    void startThenFail(ProcessRecords.StartCommand command) {
        approvals.start(command);
        throw new IllegalStateException("force rollback");
    }
}
