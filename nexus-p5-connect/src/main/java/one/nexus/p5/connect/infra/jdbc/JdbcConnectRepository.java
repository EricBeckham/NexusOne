package one.nexus.p5.connect.infra.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import one.nexus.p5.connect.ConnectRepository;
import one.nexus.p5.connect.ConnectModels.*;

@Repository @DependsOn("flyway")
public class JdbcConnectRepository implements ConnectRepository {
    private final JdbcClient jdbc; private final ObjectMapper json;
    public JdbcConnectRepository(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @Override public void saveClient(ApiClient c){jdbc.sql("""
            INSERT INTO integration.api_client(id,name,key_prefix,secret_digest,scopes,rate_limit_per_minute,active,created_at)
            VALUES(:id,:name,:prefix,:digest,CAST(:scopes AS jsonb),:rate,:active,:created)
            ON CONFLICT(id) DO UPDATE SET name=:name,scopes=CAST(:scopes AS jsonb),rate_limit_per_minute=:rate,active=:active
            """).param("id",c.id()).param("name",c.name()).param("prefix",c.keyPrefix()).param("digest",c.secretDigest())
            .param("scopes",write(c.scopes())).param("rate",c.rateLimitPerMinute()).param("active",c.active()).param("created",timestamp(c.createdAt())).update();}
    @Override public Optional<ApiClient> client(UUID id){return jdbc.sql("SELECT * FROM integration.api_client WHERE id=:id").param("id",id).query(this::mapClient).optional();}
    @Override public boolean consumeRate(UUID id,Instant minute,int limit){return jdbc.sql("""
            INSERT INTO integration.api_rate_window(client_id,window_start,request_count) VALUES(:id,:minute,1)
            ON CONFLICT(client_id,window_start) DO UPDATE SET request_count=integration.api_rate_window.request_count+1
            WHERE integration.api_rate_window.request_count < :limit RETURNING request_count
            """).param("id",id).param("minute",timestamp(minute)).param("limit",limit).query(Integer.class).optional().isPresent();}
    @Override public Optional<Map<String,Object>> idempotentResponse(UUID id,String key){return jdbc.sql("SELECT response FROM integration.api_idempotency WHERE client_id=:id AND idempotency_key=:key")
            .param("id",id).param("key",key).query(String.class).optional().map(v->read(v,new TypeReference<Map<String,Object>>(){}));}
    @Override public void saveIdempotentResponse(UUID id,String key,Map<String,Object> response,Instant at){jdbc.sql("""
            INSERT INTO integration.api_idempotency(client_id,idempotency_key,response,created_at)
            VALUES(:id,:key,CAST(:response AS jsonb),:created) ON CONFLICT(client_id,idempotency_key) DO NOTHING
            """).param("id",id).param("key",key).param("response",write(response)).param("created",timestamp(at)).update();}
    @Override public void saveSubscription(WebhookSubscription s){jdbc.sql("""
            INSERT INTO integration.webhook_subscription(id,name,endpoint,event_types,active,created_at)
            VALUES(:id,:name,:endpoint,CAST(:events AS jsonb),:active,:created)
            ON CONFLICT(id) DO UPDATE SET name=:name,endpoint=:endpoint,event_types=CAST(:events AS jsonb),active=:active
            """).param("id",s.id()).param("name",s.name()).param("endpoint",s.endpoint()).param("events",write(s.eventTypes()))
            .param("active",s.active()).param("created",timestamp(s.createdAt())).update();}
    @Override public Optional<WebhookSubscription> subscription(UUID id){return jdbc.sql("SELECT * FROM integration.webhook_subscription WHERE id=:id").param("id",id).query(this::mapSubscription).optional();}
    @Override public List<WebhookSubscription> subscriptionsFor(String type){return jdbc.sql("""
            SELECT * FROM integration.webhook_subscription
            WHERE active AND event_types @> jsonb_build_array(:type)
            ORDER BY created_at
            """).param("type",type).query(this::mapSubscription).list();}
    @Override public void saveDelivery(WebhookDelivery d){jdbc.sql("""
            INSERT INTO integration.webhook_delivery(id,subscription_id,event_id,event_type,payload,status,attempts,next_attempt_at,last_error,created_at,delivered_at)
            VALUES(:id,:subscription,:event,:type,CAST(:payload AS jsonb),:status,:attempts,:next,:error,:created,:delivered)
            ON CONFLICT(id) DO UPDATE SET status=:status,attempts=:attempts,next_attempt_at=:next,last_error=:error,delivered_at=:delivered
            """).param("id",d.id()).param("subscription",d.subscriptionId()).param("event",d.eventId()).param("type",d.eventType())
            .param("payload",write(d.payload())).param("status",d.status().name()).param("attempts",d.attempts()).param("next",timestamp(d.nextAttemptAt()))
            .param("error",d.lastError()).param("created",timestamp(d.createdAt())).param("delivered",timestamp(d.deliveredAt())).update();}
    @Override public List<WebhookDelivery> dueDeliveries(Instant now,int limit){return jdbc.sql("""
            SELECT * FROM integration.webhook_delivery WHERE status IN ('PENDING','RETRY') AND next_attempt_at<=:now
            ORDER BY next_attempt_at LIMIT :limit
            """).param("now",timestamp(now)).param("limit",limit).query(this::mapDelivery).list();}
    @Override public void saveConnectorRun(ConnectorRun r){jdbc.sql("""
            INSERT INTO integration.connector_run(id,connector_code,status,output,error,started_at,finished_at)
            VALUES(:id,:code,:status,CAST(:output AS jsonb),:error,:started,:finished)
            """).param("id",r.id()).param("code",r.connectorCode()).param("status",r.status()).param("output",write(r.output()))
            .param("error",r.error()).param("started",timestamp(r.startedAt())).param("finished",timestamp(r.finishedAt())).update();}
    @Override public List<ConnectorRun> connectorRuns(){return jdbc.sql("SELECT * FROM integration.connector_run ORDER BY started_at DESC LIMIT 100").query(this::mapRun).list();}
    private ApiClient mapClient(ResultSet r,int n)throws SQLException{return new ApiClient(uuid(r,"id"),r.getString("name"),r.getString("key_prefix"),r.getBytes("secret_digest"),read(r.getString("scopes"),new TypeReference<List<String>>(){}),r.getInt("rate_limit_per_minute"),r.getBoolean("active"),instant(r,"created_at"));}
    private WebhookSubscription mapSubscription(ResultSet r,int n)throws SQLException{return new WebhookSubscription(uuid(r,"id"),r.getString("name"),r.getString("endpoint"),read(r.getString("event_types"),new TypeReference<List<String>>(){}),r.getBoolean("active"),instant(r,"created_at"));}
    private WebhookDelivery mapDelivery(ResultSet r,int n)throws SQLException{return new WebhookDelivery(uuid(r,"id"),uuid(r,"subscription_id"),r.getString("event_id"),r.getString("event_type"),read(r.getString("payload"),new TypeReference<Map<String,Object>>(){}),DeliveryStatus.valueOf(r.getString("status")),r.getInt("attempts"),instant(r,"next_attempt_at"),r.getString("last_error"),instant(r,"created_at"),instant(r,"delivered_at"));}
    private ConnectorRun mapRun(ResultSet r,int n)throws SQLException{return new ConnectorRun(uuid(r,"id"),r.getString("connector_code"),r.getString("status"),read(r.getString("output"),new TypeReference<Map<String,Object>>(){}),r.getString("error"),instant(r,"started_at"),instant(r,"finished_at"));}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private <T>T read(String v,TypeReference<T> type){try{return json.readValue(v,type);}catch(Exception e){throw new IllegalStateException(e);}}
    private static UUID uuid(ResultSet r,String c)throws SQLException{Object v=r.getObject(c);return v instanceof UUID u?u:UUID.fromString(v.toString());}
    private static Instant instant(ResultSet r,String c)throws SQLException{var t=r.getTimestamp(c);return t==null?null:t.toInstant();}
    private static Timestamp timestamp(Instant value){return value==null?null:Timestamp.from(value);}
}
