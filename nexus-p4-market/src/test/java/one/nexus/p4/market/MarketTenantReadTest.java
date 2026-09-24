package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.api.QueryAst;
import one.nexus.isolation.api.RenderedSql;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p4.market.MarketModels.Installation;
import one.nexus.p4.market.MarketModels.InstallationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MarketTenantReadTest {
    private static final UUID ALPHA = UUID.fromString("01900000-0000-7000-8000-000000000011");
    private static final UUID BETA = UUID.fromString("01900000-0000-7000-8000-000000000012");
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
    }

    @Test
    void compilerAddsTheIssuedTenantToSchemaQualifiedInstallationQueries() {
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry()
                        .registerTenant(
                                "market.installation",
                                "nexus-p4-market",
                                1,
                                "id",
                                "tenant_id",
                                "app_id",
                                "version_id",
                                "status",
                                "installed_at",
                                "updated_at")
                        .freeze(),
                Set.of(),
                new CollectingAuditSink());
        var context = context(kernel, ALPHA);

        RenderedSql query = AccessContextHolder.call(
                context,
                () -> kernel.compiler().compile(QueryAst.select(
                        "market.installation",
                        List.of("id", "tenant_id", "app_id"),
                        Map.of("id", UUID.fromString("01900000-0000-7000-8000-000000000013"))),
                        context));

        assertEquals(
                "SELECT \"id\", \"tenant_id\", \"app_id\" FROM \"market\".\"installation\" "
                        + "WHERE \"id\" = ? AND \"tenant_id\" = ?",
                query.sql());
        assertEquals(ALPHA, query.parameters().getLast());
        assertTrue(query.plan().tenantRequired());
    }

    @Test
    void serviceRequiresContextAndRejectsRowsThatEscapeTenantRepositoryScope() {
        MarketRepository market = mock(MarketRepository.class);
        MarketService service = new MarketService(
                market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(
                HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, service::installations).getStatusCode());

        Installation foreign = new Installation(
                UUID.randomUUID(),
                BETA,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InstallationStatus.INSTALLED,
                NOW,
                NOW);
        when(market.installations()).thenReturn(List.of(foreign));

        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());
        assertEquals(
                HttpStatus.FORBIDDEN,
                AccessContextHolder.call(
                        context(kernel, ALPHA),
                        () -> assertThrows(ResponseStatusException.class, service::installations).getStatusCode()));
    }

    @Test
    void foreignInstallationDetailIsIndistinguishableFromMissing() {
        MarketRepository market = mock(MarketRepository.class);
        UUID installationId = UUID.randomUUID();
        when(market.installationForCurrentTenant(installationId)).thenReturn(Optional.empty());
        MarketService service = new MarketService(
                market, mock(TenantRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
        IsolationKernel kernel = IsolationKernel.create(
                new TableRegistry().freeze(), Set.of(), new CollectingAuditSink());

        assertEquals(
                HttpStatus.NOT_FOUND,
                AccessContextHolder.call(
                        context(kernel, ALPHA),
                        () -> assertThrows(
                                ResponseStatusException.class, () -> service.getInstallation(installationId))
                                .getStatusCode()));
    }

    private static one.nexus.isolation.api.TenantContext context(IsolationKernel kernel, UUID tenantId) {
        return kernel.tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("market-reader"),
                        tenantId.toString(),
                        tenantId.toString(),
                        Set.of(tenantId.toString())),
                "market-test-trace");
    }
}
