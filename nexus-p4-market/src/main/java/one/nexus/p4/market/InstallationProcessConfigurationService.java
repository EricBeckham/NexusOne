package one.nexus.p4.market;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.ProcessDefinitionPublishService;
import one.nexus.p3.infra.permission.ProcessDefinitionSurfaceGuard;
import one.nexus.audit.AuditPort;
import one.nexus.audit.AuditCaptures;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@ConditionalOnBean({ProcessDefinitionPublishService.class, ProcessDefinitionSurfaceGuard.class})
public class InstallationProcessConfigurationService {
    private final MarketService market;
    private final InstallationProcessConfigurationRepository repository;
    private final ProcessDefinitionPublishService processes;
    private final ProcessDefinitionSurfaceGuard guard;
    private final ObjectProvider<AuditPort> audit;
    public InstallationProcessConfigurationService(MarketService market, InstallationProcessConfigurationRepository repository,
            ProcessDefinitionPublishService processes, ProcessDefinitionSurfaceGuard guard, ObjectProvider<AuditPort> audit) {
        this.market=market; this.repository=repository; this.processes=processes; this.guard=guard; this.audit=audit;
    }
    public record Configuration(String defaultKey, Integer defaultVersion, String overrideKey, Integer overrideVersion) {}
    @Transactional(readOnly=true)
    public Configuration get(UUID installation) {
        guard.requireRead(); requireInstalled(installation);
        var pin=market.installationProductPin(installation);
        var custom=repository.find(TenantContextHolder.require().tenantId(),installation);
        return new Configuration(pin.processDefinitionKey(),pin.processDefinitionVersion(),
                custom.map(InstallationProcessConfigurationRepository.Override::key).orElse(null),
                custom.map(InstallationProcessConfigurationRepository.Override::version).orElse(null));
    }
    @Transactional
    public Configuration save(UUID installation, String key, int version) {
        guard.requireWrite(); requireInstalled(installation);
        if (key == null || key.isBlank() || version < 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"published process key and positive version are required");
        // This get is explicitly tenant-owned and exact-version; no platform/other-tenant fallback.
        processes.get(key,version);
        var context=TenantContextHolder.require();
        repository.save(context.tenantId(),installation,key,version,context.personId(),java.time.Instant.now());
        append(installation,"configured"); return get(installation);
    }
    @Transactional
    public Configuration reset(UUID installation) {
        guard.requireWrite(); requireInstalled(installation);
        repository.delete(TenantContextHolder.require().tenantId(),installation);
        append(installation,"reset"); return get(installation);
    }
    private void append(UUID installation,String action) {
        var port=audit.getIfAvailable(); var context=TenantContextHolder.require();
        if(port!=null) port.append(AuditCaptures.data("p4.installation_process."+action,true,context.tenantId(),context.personId(),
                "installation",installation.toString(),"tenant process configuration",context.contextId().toString(),java.util.Map.of()));
    }
    private void requireInstalled(UUID installation) {
        if (market.getInstallation(installation).status() != MarketModels.InstallationStatus.INSTALLED)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"installation is not active");
    }
}
