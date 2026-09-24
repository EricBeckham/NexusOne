package one.nexus.p5.connect;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import one.nexus.p5.connect.ConnectModels.*;

@RestController @RequestMapping("/api/v1/p5")
public class ConnectController {
    private final ConnectService service; public ConnectController(ConnectService service){this.service=service;}
    @PostMapping("/clients") @ResponseStatus(HttpStatus.CREATED) IssuedClientResponse client(@RequestBody ClientRequest r){
        IssuedClient issued=service.issueClient(r.name(),r.scopes(),r.rateLimitPerMinute()==null?60:r.rateLimitPerMinute());
        ApiClient client=issued.client();
        return new IssuedClientResponse(client.id(),client.name(),client.keyPrefix(),client.scopes(),client.rateLimitPerMinute(),issued.secret());
    }
    @PostMapping("/internal/call") InternalCall call(@RequestHeader("X-Nexus-Client-Id")UUID id,@RequestHeader("X-Nexus-Client-Secret")String secret,
            @RequestHeader("X-Nexus-Scope")String scope,@RequestHeader("Idempotency-Key")String key,@RequestBody(required=false)Map<String,Object> body){return service.invoke(id,secret,scope,key,body);}
    @PostMapping("/webhooks") @ResponseStatus(HttpStatus.CREATED) WebhookSubscription subscribe(@RequestBody SubscriptionRequest r){return service.subscribe(r.name(),r.endpoint(),r.eventTypes());}
    @PostMapping("/events") Map<String,Integer> publish(@RequestBody EventRequest r){return Map.of("enqueued",service.publish(r.eventId(),r.eventType(),r.payload()));}
    @PostMapping("/webhooks/dispatch") List<WebhookDelivery> dispatch(@RequestParam(defaultValue="20")int limit){return service.dispatchDue(limit);}
    @GetMapping("/connectors") List<ConnectorDefinition> connectors(){return service.connectors();}
    @PostMapping("/connectors/{code}/run") ConnectorRun run(@PathVariable String code){return service.runConnector(code);}
    @GetMapping("/connector-runs") List<ConnectorRun> runs(){return service.runs();}
    record ClientRequest(String name,List<String> scopes,Integer rateLimitPerMinute){}
    record IssuedClientResponse(UUID id,String name,String keyPrefix,List<String> scopes,int rateLimitPerMinute,String secret){}
    record SubscriptionRequest(String name,String endpoint,List<String> eventTypes){}
    record EventRequest(String eventId,String eventType,Map<String,Object> payload){}
}
