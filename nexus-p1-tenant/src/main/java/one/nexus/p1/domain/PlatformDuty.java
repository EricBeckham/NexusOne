package one.nexus.p1.domain;

/**
 * P1-M05-G01-F003 three-power split. P2 owns the actual platform-tenant roles;
 * P1 only names the duties it requires.
 */
public enum PlatformDuty {
    SYSTEM_ADMIN,
    SECURITY_ADMIN,
    AUDIT_ADMIN,
    /** Test/demo only — never assigned by M0-04. */
    ALL_DEMO
}
