package one.nexus.common.permission;

/**
 * Allow / deny outcome. Merge semantics (explicit deny first) are M0-05.
 */
public enum PermissionDecision {
    ALLOW,
    DENY
}
