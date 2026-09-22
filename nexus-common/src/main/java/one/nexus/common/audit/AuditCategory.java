package one.nexus.common.audit;

/**
 * M0-11 capture categories. Approval is modeled for later M0-08 wiring;
 * this package does not emit business events by itself.
 */
public enum AuditCategory {
    LOGIN,
    PERMISSION,
    ADMIN,
    DATA,
    APPROVAL,
    GUARD_REJECT,
    EXPORT
}
