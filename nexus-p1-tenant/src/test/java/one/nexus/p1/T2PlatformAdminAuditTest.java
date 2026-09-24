package one.nexus.p1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import one.nexus.p1.application.DefaultEntitlementPackProjector;
import one.nexus.p1.application.TenantLifecycleService;
import one.nexus.p1.application.TenantLifecycleService.CreateTenantCommand;
import one.nexus.p1.application.TenantLifecycleService.CreatedTenant;
import one.nexus.p1.config.P1Properties;
import one.nexus.p1.domain.Actor;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.domain.DomainException;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.ErrorCode;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.domain.ProjectionSource;
import one.nexus.p1.domain.Quota;
import one.nexus.p1.domain.QuotaMetric;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.domain.TenantConfig;
import one.nexus.p1.domain.TenantKind;
import one.nexus.p1.domain.TenantStatus;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.ClockPort;
import one.nexus.p1.port.IdGenerator;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p1.port.PlatformContext;
import one.nexus.p1.port.QuotaRepository;
import one.nexus.p1.port.TenantAdminInitializer;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitCommand;
import one.nexus.p1.port.TenantAdminInitializer.AdminInitResult;
import one.nexus.p1.port.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T2-D acceptance: platform admin (PlatformDuty) can open/enable/disable tenants and
 * those critical writes leave a queryable durable audit trail (action, tenant, actor/context,
 * outcome). Ops dashboard / content ops UI, T2-B/C, billing, and backup are out of scope.
 * T2-A default pack projection on create must remain intact.
 */
class T2PlatformAdminAuditTest {

    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("01900000-0000-7000-8000-00000000d001");
    private static final UUID PLATFORM_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ACTOR_HOME = PLATFORM_ID;

    private MutableClock clock;
    private SequentialIds ids;
    private RecordingAuditPort audit;
    private InMemoryTenantRepository tenants;
    private InMemoryQuotaRepository quotas;
    private StubIsolationGuard isolation;
    private TenantLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        ids = new SequentialIds();
        audit = new RecordingAuditPort();
        tenants = new InMemoryTenantRepository();
        quotas = new InMemoryQuotaRepository();
        isolation = new StubIsolationGuard();

        P1Properties properties = new P1Properties();
        properties.getDefaultPack().setCode("nexus.default.m0");
        properties.getDefaultPack().setVersion("0.1.0");
        properties.getQuota().setAppLimit(5);
        properties.getQuota().setCustomLedgerLimit(10);
        properties.getQuota().setWarnPercent(80);

        seedPlatformTenant();

        lifecycle = new TenantLifecycleService(
                tenants,
                quotas,
                new DefaultEntitlementPackProjector(properties),
                command -> new AdminInitResult(ids.next(), AdminInitResult.Status.ACCEPTED, "t2-d stub"),
                isolation,
                audit,
                ids,
                clock);
    }

    @Test
    void platformAdminCreateEnableDisableLeaveQueryableAuditTrail() {
        isolation.activate(PlatformDuty.SYSTEM_ADMIN, "t2-d-open-tenant", "trace-create");

        CreatedTenant created = lifecycle.create(new CreateTenantCommand(
                "Acme T2D",
                "acme_t2d",
                "Acme T2D Ltd",
                "Acme Admin",
                "admin@acme-t2d.example",
                null));

        Tenant tenant = created.tenant();
        assertThat(tenant.status()).isEqualTo(TenantStatus.ENABLED);
        assertThat(created.projection().source()).isEqualTo(ProjectionSource.DEFAULT_PACK);
        assertThat(created.projection().packCode()).isEqualTo("nexus.default.m0");
        assertThat(quotas.listByTenant(tenant.id())).hasSize(2);

        AuditEvent createdEvent = requireLatest(tenant.id(), AuditEvent.TENANT_CREATED);
        assertCriticalAccepted(createdEvent, tenant.id(), "trace-create");
        assertThat(createdEvent.details()).containsEntry("code", "acme_t2d");
        assertThat(createdEvent.details()).containsKey("admin_init_status");

        AuditEvent adminInit = requireLatest(tenant.id(), AuditEvent.ADMIN_INIT_ACCEPTED);
        assertCriticalAccepted(adminInit, tenant.id(), "trace-create");

        isolation.activate(PlatformDuty.SYSTEM_ADMIN, "t2-d-disable", "trace-disable");
        clock.advanceSeconds(1);
        Tenant disabled = lifecycle.disable(tenant.id(), true, "maintenance window");
        assertThat(disabled.status()).isEqualTo(TenantStatus.DISABLED);

        AuditEvent disabledEvent = requireLatest(tenant.id(), AuditEvent.TENANT_DISABLED);
        assertCriticalAccepted(disabledEvent, tenant.id(), "trace-disable");
        assertThat(disabledEvent.details()).containsEntry("reason", "maintenance window");

        isolation.activate(PlatformDuty.SYSTEM_ADMIN, "t2-d-enable", "trace-enable");
        clock.advanceSeconds(1);
        Tenant enabled = lifecycle.enable(tenant.id(), true);
        assertThat(enabled.status()).isEqualTo(TenantStatus.ENABLED);

        AuditEvent enabledEvent = requireLatest(tenant.id(), AuditEvent.TENANT_ENABLED);
        assertCriticalAccepted(enabledEvent, tenant.id(), "trace-enable");

        List<AuditEvent> trail = audit.listByTenant(tenant.id(), 20);
        assertThat(trail)
                .extracting(AuditEvent::eventType)
                .contains(
                        AuditEvent.TENANT_CREATED,
                        AuditEvent.ADMIN_INIT_ACCEPTED,
                        AuditEvent.TENANT_DISABLED,
                        AuditEvent.TENANT_ENABLED);
        assertThat(trail).allMatch(AuditEvent::platformContext);
        assertThat(trail).allMatch(e -> ACTOR_ID.equals(e.actorSubjectId()));
        assertThat(trail).allMatch(e -> ACTOR_HOME.equals(e.actorTenantId()));
    }

    @Test
    void withoutPlatformDutyCreateIsDeniedAndLeavesNoAudit() {
        isolation.activate(PlatformDuty.AUDIT_ADMIN, "wrong-duty", "trace-denied");

        assertThatThrownBy(() -> lifecycle.create(new CreateTenantCommand(
                        "Denied Co",
                        "denied_co",
                        "Denied Co Ltd",
                        "Admin",
                        "admin@denied.example",
                        null)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).errorCode()).isEqualTo(ErrorCode.DUTY_DENIED));

        assertThat(tenants.findByCode("denied_co")).isEmpty();
        assertThat(audit.all()).isEmpty();
    }

    @Test
    void inactivePlatformContextCannotOpenTenant() {
        isolation.deactivate();

        assertThatThrownBy(() -> lifecycle.create(new CreateTenantCommand(
                        "NoCtx",
                        "no_ctx",
                        "NoCtx Ltd",
                        "Admin",
                        "admin@noct.example",
                        null)))
                .isInstanceOf(DomainException.class)
                .satisfies(ex ->
                        assertThat(((DomainException) ex).errorCode()).isEqualTo(ErrorCode.CONTEXT_PLATFORM_REQUIRED));

        assertThat(audit.all()).isEmpty();
    }

    @Test
    void platformTenantDisableIsRejectedWithDurableAudit() {
        isolation.activate(PlatformDuty.SYSTEM_ADMIN, "t2-d-protect-platform", "trace-reject");

        assertThatThrownBy(() -> lifecycle.disable(PLATFORM_ID, true, "should fail"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex ->
                        assertThat(((DomainException) ex).errorCode()).isEqualTo(ErrorCode.TENANT_PLATFORM_PROTECTED));

        assertThat(tenants.findById(PLATFORM_ID)).get().extracting(Tenant::status).isEqualTo(TenantStatus.ENABLED);

        AuditEvent rejected = requireLatest(PLATFORM_ID, AuditEvent.TENANT_DISABLE_REJECTED);
        assertThat(rejected.outcome()).isEqualTo(AuditEvent.OUTCOME_REJECTED);
        assertThat(rejected.platformContext()).isTrue();
        assertThat(rejected.actorSubjectId()).isEqualTo(ACTOR_ID);
        assertThat(rejected.actorTenantId()).isEqualTo(ACTOR_HOME);
        assertThat(rejected.traceId()).isEqualTo("trace-reject");
        assertThat(rejected.resourceType()).isEqualTo("tenant");
        assertThat(rejected.resourceId()).isEqualTo(PLATFORM_ID.toString());
        assertThat(rejected.details()).containsEntry("reason_code", ErrorCode.TENANT_PLATFORM_PROTECTED.code());
        assertThat(audit.newTransactionCount()).isEqualTo(1);
    }

    private void assertCriticalAccepted(AuditEvent event, UUID tenantId, String traceId) {
        assertThat(event.outcome()).isEqualTo(AuditEvent.OUTCOME_ACCEPTED);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.actorSubjectId()).isEqualTo(ACTOR_ID);
        assertThat(event.actorTenantId()).isEqualTo(ACTOR_HOME);
        assertThat(event.platformContext()).isTrue();
        assertThat(event.traceId()).isEqualTo(traceId);
        assertThat(event.resourceType()).isEqualTo("tenant");
        assertThat(event.resourceId()).isEqualTo(tenantId.toString());
        assertThat(event.eventVersion()).isEqualTo(AuditEvent.VERSION);
        assertThat(event.reason()).isNotBlank();
    }

    private AuditEvent requireLatest(UUID tenantId, String eventType) {
        List<AuditEvent> matches = audit.listByType(tenantId, eventType, 5);
        assertThat(matches).as("expected audit %s for tenant %s", eventType, tenantId).isNotEmpty();
        return matches.get(0);
    }

    private void seedPlatformTenant() {
        Instant now = clock.now();
        Tenant platform = new Tenant(
                PLATFORM_ID,
                "platform",
                "NexusOne Platform",
                TenantKind.PLATFORM,
                TenantStatus.ENABLED,
                "NexusOne Platform",
                now,
                ACTOR_ID,
                now,
                ACTOR_ID,
                null,
                1);
        tenants.insert(
                platform,
                TenantConfig.defaults(PLATFORM_ID, platform.name(), now, ACTOR_ID),
                EntitlementProjection.fromDefaultPack(
                        PLATFORM_ID,
                        "nexus.default.m0",
                        "0.1.0",
                        one.nexus.p1.domain.EntitlementPayload.defaultPack(5, 10),
                        now));
    }

    private static final class MutableClock implements ClockPort {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant now() {
            return now;
        }
    }

    private static final class SequentialIds implements IdGenerator {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public UUID next() {
            int n = seq.getAndIncrement();
            return UUID.fromString(String.format("01900000-0000-7000-8000-%012x", n));
        }
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditEvent> events = new ArrayList<>();
        private final AtomicInteger newTx = new AtomicInteger();

        @Override
        public void append(AuditEvent event) {
            events.add(event);
        }

        @Override
        public void appendNewTransaction(AuditEvent event) {
            newTx.incrementAndGet();
            events.add(event);
        }

        List<AuditEvent> all() {
            return List.copyOf(events);
        }

        int newTransactionCount() {
            return newTx.get();
        }

        List<AuditEvent> listByTenant(UUID tenantId, int limit) {
            return events.stream()
                    .filter(e -> tenantId.equals(e.tenantId()))
                    .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed())
                    .limit(Math.max(limit, 0))
                    .toList();
        }

        List<AuditEvent> listByType(UUID tenantId, String eventType, int limit) {
            return events.stream()
                    .filter(e -> tenantId.equals(e.tenantId()) && eventType.equals(e.eventType()))
                    .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed())
                    .limit(Math.max(limit, 0))
                    .toList();
        }
    }

    private static final class StubIsolationGuard implements IsolationGuard {
        private final AtomicReference<PlatformContext> platform = new AtomicReference<>(PlatformContext.inactive());
        private final AtomicReference<Actor> actor = new AtomicReference<>();

        void activate(PlatformDuty duty, String reason, String traceId) {
            Actor a = new Actor(ACTOR_ID, ACTOR_HOME, "t2-d-platform-admin", Set.of(duty));
            actor.set(a);
            platform.set(new PlatformContext(true, reason, a, traceId, ACTOR_HOME));
        }

        void deactivate() {
            actor.set(null);
            platform.set(PlatformContext.inactive());
        }

        @Override
        public PlatformContext requirePlatformContext(String operation, PlatformDuty duty) {
            PlatformContext ctx = platform.get();
            if (ctx == null || !ctx.active()) {
                throw new DomainException(
                        ErrorCode.CONTEXT_PLATFORM_REQUIRED,
                        "platform context required for " + operation,
                        Map.of("operation", operation));
            }
            Actor a = actor.get();
            if (a == null || !a.has(duty)) {
                throw new DomainException(
                        ErrorCode.DUTY_DENIED,
                        "platform duty " + duty + " required for " + operation,
                        Map.of("operation", operation, "duty", duty.name()));
            }
            return ctx;
        }

        @Override
        public void requireTenantOrPlatform(UUID tenantId, String operation) {
            requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN);
        }

        @Override
        public IsolationSnapshot current() {
            return new IsolationSnapshot(actor.get(), platform.get(), null, platform.get().traceId());
        }
    }

    private static final class InMemoryTenantRepository implements TenantRepository {
        private final Map<UUID, Tenant> byId = new LinkedHashMap<>();
        private final Map<String, UUID> codeIndex = new LinkedHashMap<>();
        private final Map<UUID, TenantConfig> configs = new LinkedHashMap<>();
        private final Map<UUID, EntitlementProjection> projections = new LinkedHashMap<>();

        @Override
        public void insert(Tenant tenant, TenantConfig config, EntitlementProjection projection) {
            byId.put(tenant.id(), tenant);
            codeIndex.put(tenant.code(), tenant.id());
            configs.put(tenant.id(), config);
            projections.put(tenant.id(), projection);
        }

        @Override
        public void update(Tenant tenant) {
            byId.put(tenant.id(), tenant);
        }

        @Override
        public void updateConfig(TenantConfig config) {
            configs.put(config.tenantId(), config);
        }

        @Override
        public Optional<Tenant> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Tenant> findByCode(String code) {
            UUID id = codeIndex.get(code);
            return id == null ? Optional.empty() : findById(id);
        }

        @Override
        public boolean codeExists(String code) {
            return codeIndex.containsKey(code);
        }

        @Override
        public List<Tenant> listAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<TenantConfig> findConfig(UUID tenantId) {
            return Optional.ofNullable(configs.get(tenantId));
        }

        @Override
        public Optional<EntitlementProjection> findProjection(UUID tenantId) {
            return Optional.ofNullable(projections.get(tenantId));
        }

        @Override
        public void updateProjection(EntitlementProjection projection) {
            EntitlementProjection current = projections.get(projection.tenantId());
            if (current == null || current.version() != projection.version() - 1) {
                throw new IllegalStateException(
                        "optimistic lock failed for entitlement projection " + projection.tenantId());
            }
            projections.put(projection.tenantId(), projection);
        }

        @Override
        public void insertAdminInit(
                UUID requestId,
                UUID tenantId,
                String displayName,
                String email,
                AdminInitResult.Status status,
                String message,
                UUID actorId,
                String traceId) {
            // durable admin-init row is out of T2-D assertion scope; lifecycle still calls this.
        }
    }

    private static final class InMemoryQuotaRepository implements QuotaRepository {
        private final List<Quota> rows = new ArrayList<>();

        @Override
        public void insert(Quota quota) {
            rows.add(quota);
        }

        @Override
        public List<Quota> listByTenant(UUID tenantId) {
            return rows.stream().filter(q -> tenantId.equals(q.tenantId())).toList();
        }

        @Override
        public Optional<Quota> find(UUID tenantId, QuotaMetric metric) {
            return rows.stream()
                    .filter(q -> tenantId.equals(q.tenantId()) && metric == q.metric())
                    .findFirst();
        }

        @Override
        public Quota lock(UUID tenantId, QuotaMetric metric) {
            return find(tenantId, metric).orElseThrow();
        }

        @Override
        public void update(Quota quota) {
            rows.removeIf(q -> q.tenantId().equals(quota.tenantId()) && q.metric() == quota.metric());
            rows.add(quota);
        }

        @Override
        public Optional<IdempotencyRecord> findIdempotency(UUID tenantId, QuotaMetric metric, String key) {
            return Optional.empty();
        }

        @Override
        public void insertIdempotency(IdempotencyRecord record) {
            // unused in T2-D
        }
    }
}
