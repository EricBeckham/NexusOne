package one.nexus.p3.studio.form;

/** Maps stored form/publish tokens onto §27.8 labels. Unknown tokens stay unchanged. */
public final class PublishCenterStatus {
    public static final String DRAFT = "Draft";
    public static final String VALIDATING = "Validating";
    public static final String READY = "Ready";
    public static final String PUBLISHING = "Publishing";
    public static final String RUNNING = "Running";
    public static final String FAILED = "Failed";
    public static final String ROLLED_BACK = "RolledBack";

    private PublishCenterStatus() {}

    public static String map(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        return switch (raw.trim()) {
            case "DRAFT" -> DRAFT;
            case "VALIDATING" -> VALIDATING;
            case "VALIDATED" -> READY;
            case "PUBLISHING" -> PUBLISHING;
            case "RUNNING" -> RUNNING;
            case "FAILED" -> FAILED;
            case "ROLLED_BACK", "ROLLED BACK", "Rolled Back" -> ROLLED_BACK;
            default -> raw;
        };
    }

    public static String of(FormRecord form, PublishLog latest) {
        if (latest != null && "ROLLED_BACK".equals(latest.outcome())) {
            return ROLLED_BACK;
        }
        if (form.runningVersion() != null) {
            return RUNNING;
        }
        if (latest != null && latest.toStatus() != null && !latest.toStatus().isBlank()) {
            return map(latest.toStatus());
        }
        if (form.draftStatus() != null) {
            return map(form.draftStatus().name());
        }
        return latest == null ? null : latest.toStatus();
    }
}
