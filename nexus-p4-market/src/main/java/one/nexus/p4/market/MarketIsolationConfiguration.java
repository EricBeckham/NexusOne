package one.nexus.p4.market;

import java.util.Set;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.IsolationAuditSink;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.query.TableRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketIsolationConfiguration {
    @Bean
    MarketIsolation marketIsolation(ObjectProvider<IsolationAuditSink> sinks) {
        return new MarketIsolation(createKernel(sinks.getIfAvailable(CollectingAuditSink::new)));
    }

    private static IsolationKernel createKernel(IsolationAuditSink sink) {
        TableRegistry tables = new TableRegistry()
                .registerTenant(
                        "market.subscription",
                        "nexus-p4-market",
                        1,
                        "id",
                        "tenant_id",
                        "sku_code",
                        "status",
                        "started_at",
                        "ended_at")
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
                        "updated_at");
        return IsolationKernel.create(tables.freeze(), Set.of(), sink);
    }
}
