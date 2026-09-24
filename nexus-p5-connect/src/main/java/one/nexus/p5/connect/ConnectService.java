package one.nexus.p5.connect;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import one.nexus.audit.AuditPort;
import one.nexus.common.audit.AuditCategory;
import one.nexus.common.audit.AuditEvent;
import one.nexus.crypto.CryptoPort;
import one.nexus.p5.connect.ConnectModels.*;

@Service
public class ConnectService {
    public static final String SAMPLE_CONNECTOR="nexus-self-health";
    private final ConnectRepository repository; private final CryptoPort crypto; private final AuditPort audit;
    private final WebhookTransport transport; private final ObjectMapper json; private final Clock clock;
    @Autowired public ConnectService(ConnectRepository repository,CryptoPort crypto,AuditPort audit,WebhookTransport transport,ObjectMapper json){
        this(repository,crypto,audit,transport,json,Clock.systemUTC());
    }
    ConnectService(ConnectRepository repository,CryptoPort crypto,AuditPort audit,WebhookTransport transport,ObjectMapper json,Clock clock){
        this.repository=repository;this.crypto=crypto;this.audit=audit;this.transport=transport;this.json=json;this.clock=clock;
    }
    @Transactional public IssuedClient issueClient(String name,List<String> scopes,int rateLimit){
        if(name==null||name.isBlank()||scopes==null||scopes.isEmpty()||rateLimit<1)throw new IllegalArgumentException("name, scopes and positive rate limit required");
        UUID id=UUID.randomUUID(); String secret="nx_"+UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        ApiClient client=new ApiClient(id,name,secret.substring(0,10),crypto.digest(secret.getBytes(StandardCharsets.UTF_8)),scopes,rateLimit,true,clock.instant());
        repository.saveClient(client); append("p5.client.issued",id,"api_client",id.toString(),"issued");
        return new IssuedClient(client,secret);
    }
    @Transactional public InternalCall invoke(UUID clientId,String secret,String requiredScope,String idempotencyKey,Map<String,Object> request){
        ApiClient client=repository.client(clientId).orElse(null);
        if(client==null){reject(clientId,"client not found");throw new SecurityException("client not found");}
        if(!client.active()||!constantTime(client.secretDigest(),crypto.digest(text(secret)))){
            reject(clientId,"invalid client credential");throw new SecurityException("invalid client credential");
        }
        if(requiredScope==null||!client.scopes().contains(requiredScope)){
            reject(clientId,"scope denied");throw new SecurityException("scope denied");
        }
        if(idempotencyKey==null||idempotencyKey.isBlank())throw new IllegalArgumentException("Idempotency-Key required");
        var replay=repository.idempotentResponse(clientId,idempotencyKey);
        if(replay.isPresent())return new InternalCall(clientId,requiredScope,idempotencyKey,replay.get(),true);
        Instant minute=clock.instant().truncatedTo(ChronoUnit.MINUTES);
        if(!repository.consumeRate(clientId,minute,client.rateLimitPerMinute())){
            reject(clientId,"rate limit exceeded");throw new RateLimitException("rate limit exceeded");
        }
        Map<String,Object> response=Map.of("accepted",true,"clientId",clientId.toString(),"scope",requiredScope,"request",request==null?Map.of():request);
        repository.saveIdempotentResponse(clientId,idempotencyKey,response,clock.instant());
        append("p5.api.called",clientId,"api_client",clientId.toString(),requiredScope);
        return new InternalCall(clientId,requiredScope,idempotencyKey,response,false);
    }
    @Transactional public WebhookSubscription subscribe(String name,String endpoint,List<String> events){
        if(name==null||name.isBlank())throw new IllegalArgumentException("webhook name required");
        URI target;
        try{target=URI.create(endpoint);}catch(Exception e){throw new IllegalArgumentException("valid webhook endpoint required");}
        if(!"https".equalsIgnoreCase(target.getScheme())||target.getHost()==null||target.getUserInfo()!=null)throw new IllegalArgumentException("webhook endpoint must be an https URL without user info");
        if(events==null||events.isEmpty())throw new IllegalArgumentException("event types required");
        WebhookSubscription s=new WebhookSubscription(UUID.randomUUID(),name,endpoint,events,true,clock.instant());repository.saveSubscription(s);
        append("p5.webhook.subscribed",s.id(),"webhook_subscription",s.id().toString(),endpoint);return s;
    }
    @Transactional public int publish(String eventId,String eventType,Map<String,Object> payload){
        if(eventId==null||eventId.isBlank()||eventType==null||eventType.isBlank())throw new IllegalArgumentException("event id and type required");
        Map<String,Object> safePayload=payload==null?Map.of():Map.copyOf(payload);
        int count=0;for(WebhookSubscription s:repository.subscriptionsFor(eventType)){
            WebhookDelivery d=new WebhookDelivery(UUID.randomUUID(),s.id(),eventId,eventType,safePayload,DeliveryStatus.PENDING,0,clock.instant(),null,clock.instant(),null);
            repository.saveDelivery(d);count++;}return count;
    }
    @Transactional public List<WebhookDelivery> dispatchDue(int limit){
        List<WebhookDelivery> due=repository.dueDeliveries(clock.instant(),Math.max(1,Math.min(limit,100)));
        for(WebhookDelivery d:due)dispatch(d);return due;
    }
    private void dispatch(WebhookDelivery d){
        Instant now=clock.instant(); int attempts=d.attempts()+1;
        try{
            WebhookSubscription s=repository.subscription(d.subscriptionId()).orElseThrow();
            String body=json.writeValueAsString(Map.of("id",d.eventId(),"type",d.eventType(),"payload",d.payload()));
            String timestamp=Long.toString(now.getEpochSecond());
            String signature=Base64.getEncoder().encodeToString(crypto.mac((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
            transport.post(s.endpoint(),body,Map.of("X-Nexus-Event-Id",d.eventId(),"X-Nexus-Delivery-Attempt",Integer.toString(attempts),"X-Nexus-Signature-Timestamp",timestamp,"X-Nexus-Signature",signature));
            repository.saveDelivery(new WebhookDelivery(d.id(),d.subscriptionId(),d.eventId(),d.eventType(),d.payload(),DeliveryStatus.DELIVERED,attempts,now,null,d.createdAt(),now));
        }catch(Exception e){
            DeliveryStatus status=attempts>=5?DeliveryStatus.FAILED:DeliveryStatus.RETRY;
            Instant next=now.plusSeconds(Math.min(300,1L<<Math.min(attempts,8)));
            repository.saveDelivery(new WebhookDelivery(d.id(),d.subscriptionId(),d.eventId(),d.eventType(),d.payload(),status,attempts,next,safe(e.getMessage()),d.createdAt(),null));
        }
    }
    public List<ConnectorDefinition> connectors(){return List.of(new ConnectorDefinition(SAMPLE_CONNECTOR,"NexusOne 自检","INTERNAL",true));}
    @Transactional public ConnectorRun runConnector(String code){
        if(!SAMPLE_CONNECTOR.equals(code))throw new IllegalArgumentException("connector not found");
        Instant started=clock.instant();Map<String,Object> output=Map.of("status","UP","service","nexusone","checkedAt",started.toString());
        ConnectorRun run=new ConnectorRun(UUID.randomUUID(),code,"SUCCEEDED",output,null,started,clock.instant());repository.saveConnectorRun(run);
        append("p5.connector.ran",run.id(),"connector_run",run.id().toString(),code);return run;
    }
    public List<ConnectorRun> runs(){return repository.connectorRuns();}
    private void append(String type,UUID actor,String resource,String id,String action){String trace=UUID.randomUUID().toString();audit.append(new AuditEvent(UUID.randomUUID(),1,clock.instant(),AuditCategory.DATA,type,null,actor,null,true,"p5-internal",trace,trace,null,AuditEvent.OUTCOME_ACCEPTED,resource,id,Map.of("action",action)));}
    private void reject(UUID actor,String reason){String trace=UUID.randomUUID().toString();audit.appendNewTransaction(new AuditEvent(UUID.randomUUID(),1,clock.instant(),AuditCategory.GUARD_REJECT,"p5.api.rejected",null,actor,null,true,"p5-internal",trace,trace,null,AuditEvent.OUTCOME_REJECTED,"api_client",actor.toString(),Map.of("reason",reason)));}
    private static byte[] text(String v){return (v==null?"":v).getBytes(StandardCharsets.UTF_8);}
    private static boolean constantTime(byte[] a,byte[] b){if(a.length!=b.length)return false;int d=0;for(int i=0;i<a.length;i++)d|=a[i]^b[i];return d==0;}
    private static String safe(String v){if(v==null)return "delivery failed";return v.substring(0,Math.min(v.length(),500));}
}
