package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import one.nexus.cache.CachePorts;
import one.nexus.file.FileProperties;
import one.nexus.file.ObjectStoragePort;
import one.nexus.p2.IdentityConfiguration.IamProperties;
import org.flowable.engine.ProcessEngine;
import org.flywaydb.core.Flyway;
import org.quartz.Scheduler;

class PlatformServiceStatusServiceTest {
    @Test
    void returnsAllComponentsAndAnUnavailableProbeDoesNotBreakTheSnapshot() throws Exception {
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.isValid(2)).thenReturn(true);
        var cache = org.mockito.Mockito.mock(CachePorts.class);
        org.mockito.Mockito.when(cache.backend()).thenReturn("memory");
        var storage = org.mockito.Mockito.mock(ObjectStoragePort.class);
        var files = new FileProperties();
        var schedulers = provider((org.quartz.Scheduler) null);
        var engines = provider((org.flowable.engine.ProcessEngine) null);
        var flyways = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(flyways.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        var service = new PlatformServiceStatusService(dataSource, cache, storage, files,
                new IamProperties(null, null, null, null, null, null, null, null),
                schedulers, engines, flyways,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC));

        var snapshot = service.snapshot();

        assertEquals(7, snapshot.services().size());
        assertEquals(PlatformServiceStatusService.State.UP, snapshot.services().get(0).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(1).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(2).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(3).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(4).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(5).status());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(6).status());
        assertNotNull(snapshot.checkedAt());
        assertTrue(snapshot.services().stream().allMatch(item -> item.checkedAt().equals(snapshot.checkedAt())));
    }

    @Test
    void oneProbeFailureIsReportedAsUnknownWithoutLeakingItsException() throws Exception {
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("password=private"));
        var service = new PlatformServiceStatusService(dataSource,
                org.mockito.Mockito.mock(CachePorts.class), org.mockito.Mockito.mock(ObjectStoragePort.class),
                new FileProperties(), new IamProperties(null, null, null, null, null, null, null, null),
                provider((org.quartz.Scheduler) null), provider((org.flowable.engine.ProcessEngine) null), emptyFlyways());

        var snapshot = service.snapshot();

        assertEquals(PlatformServiceStatusService.State.UNKNOWN, snapshot.services().get(0).status());
        assertEquals("探测暂不可用", snapshot.services().get(0).message());
        assertEquals(7, snapshot.services().size());
    }

    @Test
    void aSlowObjectStorageProbeTimesOutWhileTheOthersStayNormal() throws Exception {
        var dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var connection = org.mockito.Mockito.mock(Connection.class);
        org.mockito.Mockito.when(dataSource.getConnection()).thenReturn(connection);
        org.mockito.Mockito.when(connection.isValid(2)).thenReturn(true);
        var storage = org.mockito.Mockito.mock(one.nexus.file.ObjectStoragePort.class);
        org.mockito.Mockito.when(storage.listKeys(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(5_000);
                    return java.util.List.of();
                });
        var files = new FileProperties();
        files.setStorage("s3");
        files.getS3().setEndpoint("http://127.0.0.1:9");
        files.getS3().setBucket("nexus-files");
        var service = new PlatformServiceStatusService(dataSource,
                org.mockito.Mockito.mock(CachePorts.class), storage, files,
                new IamProperties(null, null, null, null, null, null, null, null),
                provider((org.quartz.Scheduler) null), provider((org.flowable.engine.ProcessEngine) null), emptyFlyways());
        service.setProbeTimeout(Duration.ofMillis(200));

        long started = System.nanoTime();
        var snapshot = service.snapshot();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(elapsedMs < 1_000, "snapshot took " + elapsedMs + "ms");
        assertEquals(PlatformServiceStatusService.State.UP, component(snapshot, "database").status());
        assertEquals("数据库连接正常", component(snapshot, "database").message());
        assertEquals(PlatformServiceStatusService.State.UNKNOWN, component(snapshot, "object-storage").status());
        assertEquals("探测超时", component(snapshot, "object-storage").message());
        assertEquals("当前缓存适配器未提供 Redis 探测", component(snapshot, "redis").message());
        assertEquals("Flyway 未配置", component(snapshot, "flyway").message());
        assertTrue(snapshot.services().stream().noneMatch(item -> item.message().contains("Exception")));
    }

    private static PlatformServiceStatusService.Component component(
            PlatformServiceStatusService.Snapshot snapshot, String code) {
        return snapshot.services().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static ObjectProvider<Flyway> emptyFlyways() {
        @SuppressWarnings("unchecked")
        ObjectProvider<Flyway> flyways = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(flyways.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        return flyways;
    }
}
