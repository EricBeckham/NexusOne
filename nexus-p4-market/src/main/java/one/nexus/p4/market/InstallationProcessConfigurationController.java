package one.nexus.p4.market;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnBean(InstallationProcessConfigurationService.class)
@RequestMapping("/api/v1/p4/installations/{id}/process-configuration")
public class InstallationProcessConfigurationController {
    private final InstallationProcessConfigurationService service;
    public InstallationProcessConfigurationController(InstallationProcessConfigurationService service) { this.service=service; }
    record Write(String key,int version) {}
    @GetMapping public InstallationProcessConfigurationService.Configuration get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping public InstallationProcessConfigurationService.Configuration save(@PathVariable UUID id,@RequestBody Write body) { return service.save(id,body.key(),body.version()); }
    @DeleteMapping public InstallationProcessConfigurationService.Configuration reset(@PathVariable UUID id) { return service.reset(id); }
}
