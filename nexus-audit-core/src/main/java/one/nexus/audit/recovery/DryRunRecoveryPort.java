package one.nexus.audit.recovery;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dry-run only. Does not invoke {@code pg_restore}, does not copy objects, and
 * never sets {@code profileAccepted=true}.
 */
public final class DryRunRecoveryPort implements RecoveryPort {
    public static final String POSTGRES_RUNBOOK = "docs/engineering/runbooks/postgres-whole-db-restore.md";
    public static final String OBJECT_RUNBOOK = "docs/engineering/runbooks/object-store-restore.md";

    @Override
    public RecoveryPlan postgresWholeDatabasePlan() {
        return new RecoveryPlan(
                RecoveryKind.POSTGRES_WHOLE_DATABASE,
                "PostgreSQL 15 whole-database restore (logical)",
                List.of(
                        "Take pg_dump --format=custom of the business database on a schedule.",
                        "Restore onto a separate PostgreSQL 15 instance (never onto the live cluster).",
                        "Verify Flyway history, row counts, and audit hash chain after restore.",
                        "Record RPO/RTO. This plan is not a target Linux/ARM Profile acceptance."),
                POSTGRES_RUNBOOK,
                false);
    }

    @Override
    public RecoveryPlan objectStorePlan() {
        return new RecoveryPlan(
                RecoveryKind.OBJECT_STORE,
                "Object store restore (MinIO/AIStor or compatible S3)",
                List.of(
                        "Replicate or version buckets out of band.",
                        "Restore into an isolated bucket/prefix.",
                        "Reconcile file_id metadata with object keys; reject cross-tenant prefixes.",
                        "Do not treat this dry-run as G6 acceptance."),
                OBJECT_RUNBOOK,
                false);
    }

    @Override
    public RecoveryDryRunResult dryRunPostgres() {
        String version = probe("pg_dump");
        boolean present = version != null;
        return new RecoveryDryRunResult(
                RecoveryKind.POSTGRES_WHOLE_DATABASE,
                present ? "DRY_RUN" : "TOOL_MISSING",
                version,
                List.of(
                        present
                                ? "pg_dump is present; dump/restore was not executed."
                                : "pg_dump not on PATH; restore was not executed.",
                        "profileAccepted remains false (ARCH-009 / G6 not claimed)."),
                false,
                false);
    }

    @Override
    public RecoveryDryRunResult dryRunObjectStore() {
        return new RecoveryDryRunResult(
                RecoveryKind.OBJECT_STORE,
                "DRY_RUN",
                null,
                List.of(
                        "No object-store client was invoked.",
                        "See " + OBJECT_RUNBOOK + ". G6 / Profile acceptance is not claimed."),
                false,
                false);
    }

    static String probe(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null || line.isBlank() ? command : line.trim();
            }
        } catch (Exception ex) {
            return null;
        }
    }
}
