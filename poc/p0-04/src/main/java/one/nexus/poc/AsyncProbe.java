package one.nexus.poc;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("asyncProbe")
public class AsyncProbe implements JavaDelegate {
    private final JdbcTemplate jdbc;

    public AsyncProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void execute(DelegateExecution execution) {
        jdbc.update("insert into public.business_marker(id) values (?)", execution.getProcessInstanceBusinessKey());
    }
}
