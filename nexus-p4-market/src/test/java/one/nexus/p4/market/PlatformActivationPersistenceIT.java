package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p1.domain.Tenant;
import one.nexus.p1.port.AuditPort;
import one.nexus.p1.port.TenantRepository;
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
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V600__p4_market.sql"),
                new ClassPathResource("db/migration/V601__market_product_definition.sql"),
                new ClassPathResource("db/migration/V705__market_installation_product_pin.sql"),
                new ClassPathResource("db/migration/V706__tenant_self_reg_and_activation_application.sql"))
                .execute(source);
        market=new JdbcMarketRepository(jdbc,new ObjectMapper(),mock(MarketIsolation.class));
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
        when(tenants.findProjection(tenant)).thenReturn(Optional.empty());
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
        assertEquals(ActivationApplicationStatus.PENDING,market.activationApplication(application).orElseThrow().status());
        assertEquals(0,jdbc.sql("SELECT count(*) FROM market.installation_product_pin WHERE product_definition_id=:id")
                .param("id",product).query(Integer.class).single());
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
