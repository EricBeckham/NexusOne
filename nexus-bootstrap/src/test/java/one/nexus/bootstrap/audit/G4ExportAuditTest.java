package one.nexus.bootstrap.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import one.nexus.audit.AuditCaptures;
import one.nexus.audit.ChainedAuditEvent;
import one.nexus.audit.InMemoryHashChainedAuditPort;
import one.nexus.bootstrap.isolation.AuditPortIsolationSink;
import one.nexus.common.audit.AuditCategory;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.boundary.IsolatedExport;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * G4 follow-up: accepted IsolatedExport actions land on the existing hash chain
 * via {@code AuditPort.append} and are queryable by trace.
 */
class G4ExportAuditTest {
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String TRACE = "0199a1c0-0000-7000-8000-0000000000e4";

    private final CollectingAuditSink collecting = new CollectingAuditSink();
    private final InMemoryHashChainedAuditPort port =
            new InMemoryHashChainedAuditPort(JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL));
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(),
            Set.of("audit.archive"),
            IsolationAuditSink.tee(collecting, new AuditPortIsolationSink(port, event -> {})));

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void acceptedExportSubmitAndFileLeaveExportEventsQueryableByTrace() {
        AccessContextHolder.set(kernel.tenants().issue(
                new AuthenticatedMembership(Actor.person(TENANT), TENANT, "mem-g4", Set.of(TENANT)),
                TRACE));
        IsolatedExport.submit(TENANT, "{\"ledger\":\"leave\"}");
        AtomicBoolean wrote = new AtomicBoolean(false);
        IsolatedExport.writeFile(TENANT, "export/g4.csv", new byte[] {1, 2}, key -> wrote.set(true));
        IsolatedExport.dispatch(IsolatedExport.submit(TENANT, "{}"), TENANT, item -> {});

        assertTrue(wrote.get());
        var rows = port.findByTraceId(TRACE);
        assertTrue(rows.size() >= 3, () -> "expected export events, got " + rows.size());
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.EXPORT
                        && AuditCaptures.EXPORT_SUBMITTED.equals(event.event().eventType())));
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.EXPORT
                        && AuditCaptures.EXPORT_FILE_WRITTEN.equals(event.event().eventType())));
        assertTrue(rows.stream().anyMatch(event ->
                event.event().category() == AuditCategory.EXPORT
                        && AuditCaptures.EXPORT_DISPATCHED.equals(event.event().eventType())));
        assertTrue(rows.stream().allMatch(event -> TRACE.equals(event.event().traceId())));
        assertTrue(port.verifyChain().intact());
        assertEquals(0, collecting.rejectCount());
        assertEquals(
                rows.stream().map(ChainedAuditEvent::chainIndex).distinct().count(),
                rows.size());
    }
}
