package one.nexus.bootstrap.demo;

import one.nexus.permission.PermissionModule;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Installs the demo_acme role split when the local demo switch is on, or when the
 * DEMO-U2 duty window is open. A closed window and a closed demo switch do nothing.
 */
@Component
public class DemoAcmeRoleSplitRunner implements ApplicationRunner {
    private final Environment env;
    private final PermissionModule permissions;

    public DemoAcmeRoleSplitRunner(Environment env, PermissionModule permissions) {
        this.env = env;
        this.permissions = permissions;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean demo = DemoProfileGate.enabled(env.getProperty(DemoProfileGate.ENABLED));
        boolean window = PlatformDutyBootstrap.enabled(env);
        if (!demo && !window) {
            return;
        }
        if (window) {
            PlatformDutyBootstrap.refuseDemoContext(env);
        }
        if (demo) {
            DemoProfileGate.assertAllowed(
                    true,
                    env.getProperty(DemoProfileGate.ENVIRONMENT, "local"),
                    env.getProperty("spring.datasource.url", ""));
        }
        DemoAcmeRoleSplit.install(permissions);
    }
}
