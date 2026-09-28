package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import one.nexus.p1.domain.EntitlementPayload;
import one.nexus.p1.domain.EntitlementProjection;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.TenantRepository;
import one.nexus.p1.infra.jdbc.JdbcTenantRepository;
import one.nexus.p4.market.MarketModels.*;
import one.nexus.p4.market.infra.jdbc.JdbcMarketRepository;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Opt-in: an explicitly named disposable loopback database, never the application datasource. */
class PlatformActivationPersistenceIT {
    static JdbcClient jdbc;
    static JdbcMarketRepository market;
    static JdbcTenantRepository realTenants;
    static TransactionTemplate transaction;
    UUID tenant, app, version, form, product, application;
    ProductDefaultPermissionProvisioner packs;
    MarketService service;

    @BeforeAll static void schema() {
        String url=System.getProperty("nexus.test.activation.jdbc", "");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:(?!5432/)[0-9]+/test_[a-z0-9_]+"),
                "explicit disposable loopback test_* JDBC URL on a non-business port is required");
        var source=new DriverManagerDataSource(url,"postgres","");
        jdbc=JdbcClient.create(source);
        jdbc.sql("CREATE SCHEMA platform").update();
        jdbc.sql("CREATE TABLE platform.tenant(id uuid PRIMARY KEY)").update();
        jdbc.sql("CREATE TABLE platform.form_template_version(id uuid PRIMARY KEY)").update();
        // V100 entitlement_projection shape, scoped to this explicit disposable test database.
        jdbc.sql("""
                CREATE TABLE platform.entitlement_projection (
                    tenant_id uuid PRIMARY KEY REFERENCES platform.tenant(id),
                    source varchar(32) NOT NULL,
                    pack_code varchar(64) NOT NULL,
                    pack_version varchar(32) NOT NULL,
                    sku_code varchar(64),
                    subscription_id uuid,
                    projected_at timestamptz NOT NULL,
                    payload jsonb NOT NULL,
                    updated_at timestamptz NOT NULL,
                    version bigint NOT NULL DEFAULT 1,
                    CONSTRAINT p1_entitlement_source CHECK (source IN ('DEFAULT_PACK', 'SUBSCRIPTION')),
                    CONSTRAINT p1_entitlement_sub CHECK (
                        (source='DEFAULT_PACK' AND subscription_id IS NULL AND sku_code IS NULL)
                        OR (source='SUBSCRIPTION' AND subscription_id IS NOT NULL))
                )
                """).update();
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V600__p4_market.sql"),
                new ClassPathResource("db/migration/V601__market_product_definition.sql"),
                new ClassPathResource("db/migration/V705__market_installation_product_pin.sql"),
                new ClassPathResource("db/migration/V706__tenant_self_reg_and_activation_application.sql"))
                .execute(source);
        market=new JdbcMarketRepository(jdbc,new ObjectMapper(),mock(MarketIsolation.class));
        realTenants=new JdbcTenantRepository(jdbc,new ObjectMapper());
        transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
    }
    @BeforeEach void fixture() {
        tenant=UUID.randomUUID(); app=UUID.randomUUID(); version=UUID.randomUUID();
        form=UUID.randomUUID(); product=UUID.randomUUID(); application=UUID.randomUUID();
        Instant now=Instant.now();
        jdbc.sql("INSERT INTO platform.tenant VALUES(:id)").param("id",tenant).update();
        jdbc.sql("INSERT INTO platform.form_template_version VALUES(:id)").param("id",form).update();
        String sku="sku_"+application;
        market.saveApp(new AppPackage(app,"app_"+app,"Test","",now));
        market.saveVersion(new AppVersion(version,app,"1",VersionStatus.PUBLISHED,"",now,now));
        market.saveSku(new Sku(sku,app,"Test",Map.of("apps",new EntitlementSpec(true,1,Map.of()))));
        market.saveProductDefinition(new ProductDefinition(product,"product_"+product,"Test","",form,"default",1,Map.of(),"PUBLISHED",now,now,1));
        market.bindSkuToProductDefinition(sku,product);
        market.saveActivationApplication(new ActivationApplication(application,tenant,sku,version,
                ActivationApplicationStatus.PENDING,null,null,null,null,null,now,UUID.randomUUID()));
        var tenants=mock(TenantRepository.class);
        when(tenants.findById(tenant)).thenReturn(Optional.of(mock(Tenant.class)));
        when(tenants.findProjection(tenant)).thenAnswer(ignored -> realTenants.findProjection(tenant));
        doAnswer(invocation -> {realTenants.insertProjection(invocation.getArgument(0)); return null;})
                .when(tenants).insertProjection(any());
        doAnswer(invocation -> {realTenants.updateProjection(invocation.getArgument(0)); return null;})
                .when(tenants).updateProjection(any());
        packs=mock(ProductDefaultPermissionProvisioner.class);
        service=new MarketService(market,tenants,Clock.systemUTC(),null,mock(AuditPort.class),packs);
        PlatformActivationTestContext.bind();
    }
    @AfterEach void clear(){PlatformActivationTestContext.clear();}
    int count(String table) {
        return jdbc.sql("SELECT count(*) FROM market."+table+" WHERE tenant_id=:tenant")
                .param("tenant",tenant).query(Integer.class).single();
    }
    @Test void approvalCommitsTargetInstallationAndPinOnlyOnce() {
        var approved=transaction.execute(status->service.approveActivation(application,"Approved"));
        assertEquals(ActivationApplicationStatus.APPROVED,approved.status());
        assertEquals(one.nexus.p2.kernel.TenantContextHolder.require().personId(),approved.reviewedBy());
        assertEquals(1,count("subscription")); assertEquals(1,count("installation"));
        var projection=realTenants.findProjection(tenant).orElseThrow();
        assertEquals(1,projection.version());
        assertEquals(approved.skuCode(),projection.skuCode());
        assertEquals(product,jdbc.sql("SELECT product_definition_id FROM market.installation_product_pin WHERE installation_id=:id")
                .param("id",approved.resultingInstallationId()).query(UUID.class).single());
        assertThrows(RuntimeException.class,()->transaction.execute(status->service.approveActivation(application,"again")));
        assertEquals(1,count("installation"));
        verify(packs,times(1)).provisionApproved(any(),any());
    }
    @Test void permissionFailureRollsBackSubscriptionInstallationPinAndReview() {
        doThrow(new IllegalStateException("pack failure")).when(packs).provisionApproved(any(),any());
        assertThrows(IllegalStateException.class,()->transaction.execute(status->service.approveActivation(application,"Approved")));
        assertEquals(0,count("subscription")); assertEquals(0,count("installation"));
        assertTrue(realTenants.findProjection(tenant).isEmpty());
        assertEquals(ActivationApplicationStatus.PENDING,market.activationApplication(application).orElseThrow().status());
        assertEquals(0,jdbc.sql("SELECT count(*) FROM market.installation_product_pin WHERE product_definition_id=:id")
                .param("id",product).query(Integer.class).single());
    }
    @Test void existingProjectionUpdatesByVersionAndConcurrentFirstInsertCannotOverwrite() {
        realTenants.insertProjection(EntitlementProjection.fromDefaultPack(tenant,"DEFAULT","1",
                EntitlementPayload.defaultPack(1,1),Instant.now()));
        var before=realTenants.findProjection(tenant).orElseThrow();
        assertThrows(IllegalStateException.class,()->realTenants.insertProjection(
                EntitlementProjection.fromDefaultPack(tenant,"OTHER","1",EntitlementPayload.defaultPack(2,2),Instant.now())));
        assertEquals(before,realTenants.findProjection(tenant).orElseThrow());
        transaction.execute(status->service.approveActivation(application,"Approved"));
        var after=realTenants.findProjection(tenant).orElseThrow();
        assertEquals(2,after.version());
        assertEquals(market.activationApplication(application).orElseThrow().skuCode(),after.skuCode());
        assertThrows(IllegalStateException.class,()->realTenants.updateProjection(
                EntitlementProjection.fromDefaultPack(tenant,"STALE","1",EntitlementPayload.defaultPack(1,1),Instant.now())));
        assertEquals(after,realTenants.findProjection(tenant).orElseThrow());
    }
    @Test void twoConcurrentFirstProjectionWritersCannotOverwriteEachOther() throws Exception {
        var first=EntitlementProjection.fromDefaultPack(tenant,"FIRST","1",EntitlementPayload.defaultPack(1,1),Instant.now());
        var second=EntitlementProjection.fromDefaultPack(tenant,"SECOND","1",EntitlementPayload.defaultPack(2,2),Instant.now());
        var firstInserted=new CountDownLatch(1);
        var releaseFirst=new CountDownLatch(1);
        var secondStarted=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var firstWriter=pool.submit(()->transaction.execute(status->{
                realTenants.insertProjection(first);
                firstInserted.countDown();
                try { if(!releaseFirst.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("first writer release timed out"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return null;
            }));
            assertTrue(firstInserted.await(10,TimeUnit.SECONDS));
            var secondWriter=pool.submit(()->transaction.execute(status->{
                secondStarted.countDown();
                realTenants.insertProjection(second);
                return null;
            }));
            try { assertTrue(secondStarted.await(10,TimeUnit.SECONDS)); }
            finally { releaseFirst.countDown(); }
            firstWriter.get(10,TimeUnit.SECONDS);
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class,()->secondWriter.get(10,TimeUnit.SECONDS)).getCause());
        } finally {
            releaseFirst.countDown();
        }
        var stored=realTenants.findProjection(tenant).orElseThrow();
        assertEquals("FIRST",stored.packCode());
        assertEquals(1,stored.version());
        assertEquals(first.payload(),stored.payload());
    }
    @Test void capabilityCannotWriteToCallerSuppliedTenantAndOrdinaryWriteRemainsGuarded() {
        transaction.execute(status->{
            var pending=market.activationApplicationForReview(application).orElseThrow();
            var definition=market.productDefinitionForSku(pending.skuCode()).orElseThrow();
            var command=PlatformActivationCommand.forPending(pending,definition);
            assertThrows(SecurityException.class,()->market.saveApprovedSubscription(command,
                    new Subscription(UUID.randomUUID(),UUID.randomUUID(),pending.skuCode(),SubscriptionStatus.ACTIVE,Instant.now(),null)));
            assertThrows(RuntimeException.class,()->market.saveSubscription(
                    new Subscription(UUID.randomUUID(),tenant,pending.skuCode(),SubscriptionStatus.ACTIVE,Instant.now(),null)));
            return null;
        });
        assertEquals(0,count("subscription"));
    }
}
