package one.nexus.p5.connect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HttpWebhookTransport implements WebhookTransport {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @Override public void post(String endpoint,String body,Map<String,String> headers)throws Exception{
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        HttpResponse<Void> response=client.send(builder.build(),HttpResponse.BodyHandlers.discarding());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("webhook HTTP "+response.statusCode());
    }
}
