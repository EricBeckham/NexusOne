package one.nexus.p3.path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Forces a G18 submit TX to fail after ledger/process/outbox/audit writes so IT can assert they all roll back. */
@Component
@ConditionalOnBean(BusinessPathService.class)
public class G18RollbackProbe {
    private final BusinessPathService path;

    public G18RollbackProbe(BusinessPathService path) {
        this.path = path;
    }

    @Transactional
    public void submitStartThenFail(BusinessPathRecords.SubmitStartCommand command) {
        path.submitAndStart(command);
        throw new IllegalStateException("force rollback");
    }
}
