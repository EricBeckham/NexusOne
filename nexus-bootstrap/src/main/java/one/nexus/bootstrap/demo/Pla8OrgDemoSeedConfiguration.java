package one.nexus.bootstrap.demo;

import one.nexus.p2.IdentityModule;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.TenantMemberGrantSeeder;

/**
 * PLA8 gate. Boot no longer starts this by itself: {@link Plx2DemoSeedConfiguration} writes the organization
 * after the demo tenant row exists. Tests still call {@link #run} to check the switch and the remote-database refusal.
 */
public final class Pla8OrgDemoSeedConfiguration {
    private Pla8OrgDemoSeedConfiguration() {}

    static void run(String enabled, String environment, String datasourceUrl, IdentityModule identity,
            PermissionModule permissions, TenantMemberGrantSeeder seeder) {
        if (!DemoProfileGate.enabled(enabled)) {
            return;
        }
        DemoProfileGate.assertAllowed(true, environment, datasourceUrl);
        Pla8OrgDemoContent.write(identity, permissions, seeder, P1DemoSeedConfiguration.TENANT_ID);
    }
}
