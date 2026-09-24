package one.nexus.bootstrap.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import one.nexus.cache.CachePorts;
import one.nexus.cache.Redis32CacheAdapter;
import one.nexus.file.FileProperties;
import one.nexus.file.ObjectStoragePort;
import one.nexus.p2.IdentityConfiguration.IamProperties;
import org.flowable.engine.ProcessEngine;
import org.flywaydb.core.Flyway;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Read-only, best-effort diagnostics. Each probe fails independently and never returns exception details. */
@Service
public class PlatformServiceStatusService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public enum State { UP, DOWN, UNKNOWN }

    public record Component(String code, String name, State status, String message, Instant checkedAt) {}

    public record Snapshot(List<Component> services, Instant checkedAt) {
        public Snapshot { services = List.copyOf(services); }
    }

    private final DataSource dataSource;
    private final CachePorts cache;
    private final ObjectStoragePort storage;
    private final FileProperties files;
    private final IamProperties iam;
    private final ObjectProvider<Scheduler> schedulers;
    private final ObjectProvider<ProcessEngine> processEngines;
    private final ObjectProvider<Flyway> flyways;
    private final Clock clock;

    public PlatformServiceStatusService(
            DataSource dataSource,
            CachePorts cache,
            ObjectStoragePort storage,
            FileProperties files,
            IamProperties iam,
            ObjectProvider<Scheduler> schedulers,
            ObjectProvider<ProcessEngine> processEngines,
            ObjectProvider<Flyway> flyways) {
        this(dataSource, cache, storage, files, iam, schedulers, processEngines, flyways, Clock.systemUTC());
    }

    PlatformServiceStatusService(
            DataSource dataSource,
            CachePorts cache,
            ObjectStoragePort storage,
            FileProperties files,
            IamProperties iam,
            ObjectProvider<Scheduler> schedulers,
            ObjectProvider<ProcessEngine> processEngines,
            ObjectProvider<Flyway> flyways,
            Clock clock) {
        this.dataSource = dataSource;
        this.cache = cache;
        this.storage = storage;
        this.files = files;
        this.iam = iam;
        this.schedulers = schedulers;
        this.processEngines = processEngines;
        this.flyways = flyways;
        this.clock = clock;
    }

    public Snapshot snapshot() {
        List<Component> components = new ArrayList<>();
        components.add(probe("database", "数据库", this::database));
        components.add(probe("redis", "Redis", this::redis));
        components.add(probe("object-storage", "对象存储", this::objectStorage));
        components.add(probe("keycloak", "Keycloak", this::keycloak));
        components.add(probe("flowable", "Flowable", this::flowable));
        components.add(probe("quartz", "Quartz", this::quartz));
        components.add(probe("flyway", "Flyway", this::flyway));
        return new Snapshot(components, clock.instant());
    }

    private Probe database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2)
                    ? new Probe(State.UP, "数据库连接正常")
                    : new Probe(State.DOWN, "数据库连接无效");
        }
    }

    private Probe redis() {
        if (!(cache instanceof Redis32CacheAdapter redis)) {
            return new Probe(State.UNKNOWN, "当前缓存适配器未提供 Redis 探测");
        }
        return "PONG".equalsIgnoreCase(redis.ping())
                ? new Probe(State.UP, "Redis 响应正常")
                : new Probe(State.DOWN, "Redis 未返回 PONG");
    }

    private Probe objectStorage() {
        if (!"s3".equalsIgnoreCase(files.getStorage())
                || files.getS3().getEndpoint() == null || files.getS3().getEndpoint().isBlank()) {
            return new Probe(State.UNKNOWN, "当前未配置远程对象存储");
        }
        storage.listKeys(files.getS3().getBucket(), ".nexus-health/");
        return new Probe(State.UP, "对象存储只读探测正常");
    }

    private Probe keycloak() throws Exception {
        if (!"keycloak".equalsIgnoreCase(iam.idp()) || iam.keycloak() == null
                || iam.keycloak().issuer() == null || iam.keycloak().issuer().isBlank()) {
            return new Probe(State.UNKNOWN, "当前未配置 Keycloak 身份提供方");
        }
        URI discovery = URI.create(trimSlash(iam.keycloak().issuer()) + "/.well-known/openid-configuration");
        HttpRequest request = HttpRequest.newBuilder(discovery)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() >= 200 && response.statusCode() < 300
                ? new Probe(State.UP, "Keycloak OIDC discovery 可访问")
                : new Probe(State.DOWN, "Keycloak OIDC discovery 返回非成功状态");
    }

    private Probe flowable() {
        ProcessEngine engine = processEngines.getIfAvailable();
        return engine == null
                ? new Probe(State.UNKNOWN, "Flowable 引擎未配置")
                : engine.getName() != null
                        ? new Probe(State.UP, "Flowable 引擎已启动")
                        : new Probe(State.DOWN, "Flowable 引擎未就绪");
    }

    private Probe quartz() throws Exception {
        Scheduler scheduler = schedulers.getIfAvailable();
        if (scheduler == null) {
            return new Probe(State.UNKNOWN, "Quartz 调度器未配置");
        }
        if (scheduler.isShutdown()) {
            return new Probe(State.DOWN, "Quartz 调度器已关闭");
        }
        return scheduler.isStarted() && !scheduler.isInStandbyMode()
                ? new Probe(State.UP, "Quartz 调度器运行中")
                : new Probe(State.UNKNOWN, "Quartz 调度器未运行任务");
    }

    private Probe flyway() {
        List<Flyway> configured = flyways.orderedStream().toList();
        if (configured.isEmpty()) {
            return new Probe(State.UNKNOWN, "Flyway 未配置");
        }
        int pending = 0;
        for (Flyway flyway : configured) {
            pending += flyway.info().pending().length;
        }
        return pending == 0
                ? new Probe(State.UP, "Flyway 迁移状态正常")
                : new Probe(State.DOWN, "存在待执行迁移：" + pending);
    }

    private Component probe(String code, String name, SupplierWithException check) {
        Instant checkedAt = clock.instant();
        try {
            Probe result = check.get();
            return new Component(code, name, result.state(), result.message(), checkedAt);
        } catch (Exception | LinkageError ignored) {
            // Diagnostics are best-effort; never leak endpoint, credentials, or vendor exception text.
            return new Component(code, name, State.UNKNOWN, "探测暂不可用", checkedAt);
        }
    }

    private static String trimSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    private record Probe(State state, String message) {}

    @FunctionalInterface
    private interface SupplierWithException {
        Probe get() throws Exception;
    }
}
