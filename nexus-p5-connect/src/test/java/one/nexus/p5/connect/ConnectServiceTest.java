package one.nexus.p5.connect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.audit.AuditPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.JcaCryptoPort;
import one.nexus.isolation.api.Actor;
import one.nexus.isolation.api.AuthenticatedMembership;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.p5.connect.ConnectModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class ConnectServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");
    private static final UUID TENANT_ID = UUID.fromString("01900000-0000-7000-8000-000000000011");
    private ConnectRepository repository;
    private AuditPort audit;
    private WebhookTransport transport;
    private ConnectService service;
    private ConnectIsolation isolation;

    @BeforeEach
    void setUp() {
        isolation = ConnectIsolationConfiguration.createIsolation(new CollectingAuditSink());
        var context = isolation.kernel().tenants().issue(
                new AuthenticatedMembership(
                        Actor.person("test-person"), TENANT_ID.toString(), "test-membership", java.util.Set.of(TENANT_ID.toString())),
                "trace-test");
        AccessContextHolder.set(context);
        repository = mock(ConnectRepository.class);
        audit = mock(AuditPort.class);
        transport = mock(WebhookTransport.class);
        service = new ConnectService(
                repository,
                JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL),
                audit,
                transport,
                new ObjectMapper(),
                isolation,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearContext() {
        AccessContextHolder.clear();
    }

    @Test
    void p5aIssuesOneTimeSecretAndEnforcesScopeRateAndIdempotency() {
        IssuedClient issued = service.issueClient("scheduler", List.of("integration:call"), 2);
        ArgumentCaptor<ApiClient> clientCaptor = ArgumentCaptor.forClass(ApiClient.class);
        verify(repository).saveClient(clientCaptor.capture());
        ApiClient stored = clientCaptor.getValue();
        assertEquals(issued.client().id(), stored.id());
        assertTrue(issued.secret().startsWith("nx_"));
        assertFalse(new String(stored.secretDigest()).contains(issued.secret()));

        when(repository.client(stored.id())).thenReturn(Optional.of(stored));
        when(repository.idempotentResponse(stored.id(), "job-42"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Map.of("accepted", true)));
        when(repository.consumeRate(stored.id(), NOW, 2)).thenReturn(true);

        InternalCall first = service.invoke(
                stored.id(), issued.secret(), "integration:call", "job-42", Map.of("job", 42));
        InternalCall replay = service.invoke(
                stored.id(), issued.secret(), "integration:call", "job-42", Map.of("job", 42));
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        verify(repository, times(1)).consumeRate(stored.id(), NOW, 2);
        assertThrows(SecurityException.class, () -> service.invoke(
                stored.id(), issued.secret(), "admin:all", "job-43", Map.of()));
        verify(audit, atLeast(2)).append(any());
        verify(audit).appendNewTransaction(any());
    }

    @Test
    void p5bSignsAndDeliversDueWebhook() throws Exception {
        WebhookSubscription subscription = service.subscribe(
                "record sink", "https://hooks.example.test/nexus", List.of("record.changed"));
        when(repository.subscriptionsFor("record.changed", TENANT_ID)).thenReturn(List.of(subscription));
        assertEquals(1, service.publish("evt-1", "record.changed", Map.of("recordId", "r1")));

        ArgumentCaptor<WebhookDelivery> queued = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(repository).saveDelivery(queued.capture());
        when(repository.dueDeliveries(NOW, 20)).thenReturn(List.of(queued.getValue()));
        when(repository.subscription(subscription.id())).thenReturn(Optional.of(subscription));

        service.dispatchDue(20);

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(transport).post(eq(subscription.endpoint()), contains("record.changed"), headers.capture());
        assertEquals("evt-1", headers.getValue().get("X-Nexus-Event-Id"));
        assertFalse(headers.getValue().get("X-Nexus-Signature").isBlank());
        ArgumentCaptor<WebhookDelivery> saved = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(repository, times(2)).saveDelivery(saved.capture());
        assertEquals(DeliveryStatus.DELIVERED, saved.getAllValues().get(1).status());
    }

    @Test
    void p5bMovesFailuresToRetryPool() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        WebhookSubscription subscription = new WebhookSubscription(
                subscriptionId, TENANT_ID, "sink", "https://hooks.example.test/nexus",
                List.of("job.done"), true, NOW);
        WebhookDelivery due = new WebhookDelivery(
                UUID.randomUUID(), TENANT_ID, subscriptionId, "evt-2", "job.done", Map.of(), DeliveryStatus.PENDING,
                0, NOW, null, NOW, null);
        when(repository.dueDeliveries(NOW, 1)).thenReturn(List.of(due));
        when(repository.subscription(subscriptionId)).thenReturn(Optional.of(subscription));
        doThrow(new IllegalStateException("offline")).when(transport).post(anyString(), anyString(), anyMap());

        service.dispatchDue(1);

        ArgumentCaptor<WebhookDelivery> saved = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(repository).saveDelivery(saved.capture());
        assertEquals(DeliveryStatus.RETRY, saved.getValue().status());
        assertEquals(1, saved.getValue().attempts());
        assertTrue(saved.getValue().nextAttemptAt().isAfter(NOW));
    }

    @Test
    void p5cRunsTheSingleSelfUseConnector() {
        assertEquals(List.of(ConnectService.SAMPLE_CONNECTOR),
                service.connectors().stream().map(ConnectorDefinition::code).toList());
        ConnectorRun run = service.runConnector(ConnectService.SAMPLE_CONNECTOR);
        assertEquals("SUCCEEDED", run.status());
        assertEquals("UP", run.output().get("status"));
        verify(repository).saveConnectorRun(run);
        assertThrows(IllegalArgumentException.class, () -> service.runConnector("third-party"));
    }

    @Test
    void tenantReadModelsRejectRowsThatEscapeRepositoryScope() {
        ApiClient client = new ApiClient(
                UUID.randomUUID(), TENANT_ID, "scheduler", "nx_prefix",
                new byte[] {1, 2, 3}, List.of("integration:call"), 60, true, NOW);
        when(repository.clients()).thenReturn(List.of(client));
        assertEquals(client.id(), service.clients().getFirst().id());

        UUID foreignTenant = UUID.fromString("01900000-0000-7000-8000-000000000012");
        ApiCallLog foreignLog = new ApiCallLog(
                UUID.randomUUID(), foreignTenant, client.id(), "integration:call",
                "/internal/call", "POST", "SUCCEEDED", "trace", NOW);
        when(repository.callLogs()).thenReturn(List.of(foreignLog));
        assertEquals(
                HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, service::callLogs).getStatusCode());

        when(repository.clientForCurrentTenant(client.id())).thenReturn(Optional.empty());
        assertEquals(
                HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.client(client.id())).getStatusCode());
    }

    @Test
    void tenantReadModelsRequireAnIssuedContext() {
        AccessContextHolder.clear();

        assertEquals(
                HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, service::clients).getStatusCode());
    }
}
