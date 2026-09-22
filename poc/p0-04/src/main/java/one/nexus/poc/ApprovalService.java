package one.nexus.poc;

import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {
    private final JdbcTemplate jdbc;
    private final RuntimeService runtime;

    public ApprovalService(JdbcTemplate jdbc, RuntimeService runtime) {
        this.jdbc = jdbc;
        this.runtime = runtime;
    }

    @Transactional
    public String saveAndStart(String businessId, boolean fail) {
        jdbc.update("insert into public.business_marker(id) values (?)", businessId);
        String instanceId = runtime.startProcessInstanceByKey("approval", businessId).getId();
        if (fail) {
            throw new IllegalStateException("force rollback");
        }
        return instanceId;
    }
}
