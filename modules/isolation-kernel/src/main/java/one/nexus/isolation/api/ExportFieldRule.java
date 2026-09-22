package one.nexus.isolation.api;

/**
 * Kernel-side FieldPolicy fact for the export surface. M0-05 still owns merge /
 * mask compilation; this record is only the visibility + second-auth gate that
 * {@code IsolatedExport} checks before compile / file write.
 */
public record ExportFieldRule(boolean visible, boolean secondAuthorization) {
    public static ExportFieldRule allow() {
        return new ExportFieldRule(true, false);
    }

    public static ExportFieldRule hidden() {
        return new ExportFieldRule(false, false);
    }

    public static ExportFieldRule requireSecondAuthorization() {
        return new ExportFieldRule(true, true);
    }
}
