package one.nexus.audit.recovery;

/**
 * PostgreSQL whole-database and object-store restore contracts. Implementations
 * may document or dry-run; they must not mark G6 / target Profile as accepted.
 */
public interface RecoveryPort {
    RecoveryPlan postgresWholeDatabasePlan();

    RecoveryPlan objectStorePlan();

    RecoveryDryRunResult dryRunPostgres();

    RecoveryDryRunResult dryRunObjectStore();
}
