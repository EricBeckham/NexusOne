package one.nexus.bootstrap.demo;

/**
 * Frozen DEMO-U4 login labels. Passwords stay in the IdP and are not stored here.
 * When the realm uses email as username, Keycloak's Username field is the email;
 * these labels remain the checklist names and the IAM {@code username} column.
 */
public final class DemoIdentityConvention {
    public static final String PLATFORM_EMAIL = "platform.admin@demo.invalid";
    public static final String PLATFORM_USERNAME = "nexus-demo-platform";
    public static final String TENANT_ADMIN_EMAIL = "lin.xiao@demo.invalid";
    public static final String TENANT_ADMIN_USERNAME = "nexus-demo-tenant-admin";
    public static final String MEMBER_EMAIL = "zhou.ran@demo.invalid";
    public static final String MEMBER_USERNAME = "nexus-demo-member";

    private DemoIdentityConvention() {}
}
