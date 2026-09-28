package one.nexus.p3.instance;

import one.nexus.p3.portal.EmptyInstalledEntrySource;
import one.nexus.p3.portal.EntryReadFilter;
import one.nexus.p3.portal.InstalledEntrySource;
import one.nexus.p3.portal.OpenEntryReadFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Stand-ins for ports another line implements later (B's {@link AppInstanceQuery} / {@link PlatformObjectSource} /
 * {@link ProcessPositionSource} / {@link InstalledEntrySource}, A's {@link EntryReadFilter}).
 * <p>
 * They must be registered here, not as {@code @Component + @ConditionalOnMissingBean}: on a component-scanned class the
 * condition sees the class's own bean definition and drops it, which left {@code AppInstanceEventConsumer} without a
 * query and stopped the application from starting. Auto-configuration runs after every scanned bean is registered, so
 * a real implementation always wins and the stand-in only fills a gap.
 */
@AutoConfiguration
public class P3PlaceholderAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AppInstanceQuery.class)
    AppInstanceQuery emptyAppInstanceQuery() {
        return new EmptyAppInstanceQuery();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformObjectSource.class)
    PlatformObjectSource emptyPlatformObjectSource() {
        return new EmptyPlatformObjectSource();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessPositionSource.class)
    ProcessPositionSource emptyProcessPositionSource() {
        return new EmptyProcessPositionSource();
    }

    @Bean
    @ConditionalOnMissingBean(InstalledEntrySource.class)
    InstalledEntrySource emptyInstalledEntrySource() {
        return new EmptyInstalledEntrySource();
    }

    @Bean
    @ConditionalOnMissingBean(EntryReadFilter.class)
    EntryReadFilter openEntryReadFilter() {
        return new OpenEntryReadFilter();
    }
}
