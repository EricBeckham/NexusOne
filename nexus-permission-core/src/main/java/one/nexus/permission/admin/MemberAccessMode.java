package one.nexus.permission.admin;

import java.util.Locale;

/**
 * PLA5 compatibility switch ({@code nexus.permission.member-access}): how ordinary members get their baseline access.
 * <ul>
 *   <li>{@link #LEGACY} (default) — today's behaviour: every person who enters a tenant is assigned {@code tenant_member}
 *       personally, and the portal shows every installed entry unless it is explicitly denied.</li>
 *   <li>{@link #POSITION} — the re-plan model: {@code tenant_member} and the process-designer role reach people only
 *       through position bindings (PLA1), and portal entries follow {@code fn.*:read} grants strictly.</li>
 * </ul>
 * Legacy data is not migrated (PL0.2): flip to POSITION once the re-initialised test data binds the member roles to
 * positions.
 */
public enum MemberAccessMode {
    LEGACY,
    POSITION;

    public static MemberAccessMode parse(String value) {
        if (value == null || value.isBlank()) {
            return LEGACY;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
