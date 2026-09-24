package one.nexus.p5.connect;

import java.util.Map;

public interface WebhookTransport {
    void post(String endpoint, String body, Map<String,String> headers) throws Exception;
}
