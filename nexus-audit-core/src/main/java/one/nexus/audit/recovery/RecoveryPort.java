package one.nexus.audit.recovery;

/**
 * PostgreSQL whole-database and object-store restore contracts.
 *
 * <p>The product implementation is dry-run only. It may describe runbooks or
 * probe {@code pg_dump --version}. It must not execute dump/restore against a
 * live product database, must not open JDBC, must not target a named
 * product database, must not mark G6 / target Profile as accepted, and
 * must not claim minute-level RPO or product HA.
 */
public interface RecoveryPort {
    RecoveryPlan postgresWholeDatabasePlan();

    RecoveryPlan objectStorePlan();

    RecoveryDryRunResult dryRunPostgres();

    RecoveryDryRunResult dryRunObjectStore();
}
