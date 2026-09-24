package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import one.nexus.p4.market.MarketModels.*;

@RestController
@RequestMapping("/api/v1/p4")
public class MarketController {
    private final MarketService service;
    public MarketController(MarketService service) { this.service = service; }

    @PostMapping("/apps") @ResponseStatus(HttpStatus.CREATED)
    AppPackage createApp(@RequestBody CreateApp request) { return service.createApp(request.code(),request.name(),request.summary()); }
    @PostMapping("/apps/{appId}/versions") @ResponseStatus(HttpStatus.CREATED)
    AppVersion createVersion(@PathVariable UUID appId,@RequestBody CreateVersion request){return service.createVersion(appId,request.version(),request.releaseNotes());}
    @PostMapping("/versions/{id}/review") AppVersion review(@PathVariable UUID id){return service.transition(id,VersionStatus.IN_REVIEW);}
    @PostMapping("/versions/{id}/approve") AppVersion approve(@PathVariable UUID id){return service.transition(id,VersionStatus.APPROVED);}
    @PostMapping("/versions/{id}/publish") AppVersion publish(@PathVariable UUID id){return service.transition(id,VersionStatus.PUBLISHED);}
    @PostMapping("/versions/{id}/unpublish") AppVersion unpublish(@PathVariable UUID id){return service.transition(id,VersionStatus.UNPUBLISHED);}
    @PostMapping("/skus") @ResponseStatus(HttpStatus.CREATED)
    Sku sku(@RequestBody CreateSku request){return service.defineSku(request.code(),request.appId(),request.name(),request.entitlements());}
    @GetMapping("/apps") List<AppPackage> apps(){return service.apps();}
    @GetMapping("/apps/{appId}") AppPackage app(@PathVariable UUID appId){return service.getApp(appId);}
    @GetMapping("/apps/{appId}/versions") List<AppVersion> versions(@PathVariable UUID appId){return service.versions(appId);}
    @GetMapping("/versions/{id}") AppVersion version(@PathVariable UUID id){return service.getVersion(id);}
    @GetMapping("/skus") List<Sku> skus(){return service.skus();}
    @GetMapping("/skus/{code}") Sku sku(@PathVariable String code){return service.getSku(code);}
    @GetMapping("/catalog") List<CatalogItem> catalog(){return service.catalog();}
    @PostMapping("/tenants/{tenantId}/installations") @ResponseStatus(HttpStatus.CREATED)
    Installation install(@PathVariable UUID tenantId,@RequestBody Install request){return service.subscribeAndInstall(tenantId,request.skuCode(),request.versionId());}
    @PostMapping("/tenants/{tenantId}/subscriptions") @ResponseStatus(HttpStatus.CREATED)
    Installation selfSubscribe(@PathVariable UUID tenantId,@RequestBody Install request){
        return service.subscribeProduct(tenantId,request.skuCode(),request.versionId());
    }
    @GetMapping("/subscriptions") List<Subscription> subscriptions(){return service.subscriptions();}
    @GetMapping("/subscriptions/{id}") Subscription subscription(@PathVariable UUID id){return service.getSubscription(id);}
    @GetMapping("/installations") List<Installation> installations(){return service.installations();}
    @GetMapping("/installations/{id}") Installation installationRead(@PathVariable UUID id){return service.getInstallation(id);}
    @GetMapping("/installations/{id}/product-pin") InstallationProductPin installationProductPin(@PathVariable UUID id){
        return service.installationProductPin(id);
    }
    @PostMapping("/installations/{id}/upgrade") Installation upgrade(@PathVariable UUID id,@RequestBody Upgrade request){return service.upgrade(id,request.versionId());}
    @PostMapping("/installations/{id}/rollback") Installation rollback(@PathVariable UUID id,@RequestBody Upgrade request){return service.upgrade(id,request.versionId());}
    @PostMapping("/installations/{id}/uninstall") Installation uninstall(@PathVariable UUID id){return service.uninstall(id);}

    record CreateApp(String code,String name,String summary){}
    record CreateVersion(String version,String releaseNotes){}
    record CreateSku(String code,UUID appId,String name,Map<String,EntitlementSpec> entitlements){}
    record Install(String skuCode,UUID versionId){}
    record Upgrade(UUID versionId){}
}
