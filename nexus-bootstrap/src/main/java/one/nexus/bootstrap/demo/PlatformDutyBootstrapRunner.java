package one.nexus.bootstrap.demo;

import one.nexus.p1.config.P1Properties;
import one.nexus.permission.PermissionModule;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Startup task for DEMO-U2. Closed by default; refuses an open demo-context switch. */
@Component
public class PlatformDutyBootstrapRunner implements ApplicationRunner {
    private final Environment env;
    private final PermissionModule permissions;
    private final P1Properties properties;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public PlatformDutyBootstrapRunner(Environment env, PermissionModule permissions, P1Properties properties,
            JdbcClient jdbc, TransactionTemplate transaction) {
        this.env = env;
        this.permissions = permissions;
        this.properties = properties;
        this.jdbc = jdbc;
        this.transaction = transaction;
        PlatformDutyBootstrap.refuseDemoContext(env);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!PlatformDutyBootstrap.enabled(env)) {
            return;
        }
        PlatformDutyBootstrap.refuseDemoContext(env);
        PlatformDutyBootstrap.install(permissions, properties.getPlatformTenant().getId());
        transaction.executeWithoutResult(status -> P2DemoSeedConfiguration.alignIdentities(jdbc));
    }
}
