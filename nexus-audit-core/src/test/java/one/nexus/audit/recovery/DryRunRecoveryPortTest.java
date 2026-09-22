package one.nexus.audit.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DryRunRecoveryPortTest {
    @Test
    void documentsPlansWithoutClaimingProfile() {
        RecoveryPort port = new DryRunRecoveryPort();
        assertFalse(port.postgresWholeDatabasePlan().profileAccepted());
        assertFalse(port.objectStorePlan().profileAccepted());
        assertEquals(DryRunRecoveryPort.POSTGRES_RUNBOOK, port.postgresWholeDatabasePlan().runbookPath());
        RecoveryDryRunResult pg = port.dryRunPostgres();
        assertFalse(pg.executed());
        assertFalse(pg.profileAccepted());
        assertTrue("DRY_RUN".equals(pg.status()) || "TOOL_MISSING".equals(pg.status()));
        RecoveryDryRunResult objects = port.dryRunObjectStore();
        assertEquals("DRY_RUN", objects.status());
        assertFalse(objects.executed());
    }
}
