package one.nexus.p2;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationCodeDriverTest {
    @Test
    void completesAuthorizationCodeDespiteSecureLoopbackCookies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String redirect = "http://127.0.0.1:" + port + "/callback";
        server.createContext("/auth", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie",
                    "KC_RESTART=restart-token; Path=/; Secure; HttpOnly; SameSite=None");
            exchange.getResponseHeaders().add("Set-Cookie",
                    "AUTH_SESSION_ID=sess; Path=/; Secure; HttpOnly; SameSite=None");
            byte[] body = ("<form id=\"kc-form-login\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/authenticate\" method=\"post\">"
                    + "<input type=\"hidden\" name=\"credentialId\" value=\"\"/>"
                    + "<button name=\"login\" type=\"submit\">Sign In</button></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/login-actions/authenticate", exchange -> {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (cookie != null && cookie.contains("KC_RESTART=restart-token")
                    && body.contains("username=carol")
                    && body.contains("login=")) {
                exchange.getResponseHeaders().add("Location", redirect + "?code=live-code&state=s");
                exchange.sendResponseHeaders(302, -1);
            } else {
                byte[] err = "Restart login cookie not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, err.length);
                exchange.getResponseBody().write(err);
            }
            exchange.close();
        });
        server.start();
        try {
            AuthorizationCodeDriver.CodeAndState result = new AuthorizationCodeDriver().completeLogin(
                    "http://127.0.0.1:" + port + "/auth", "carol", "secret", redirect);
            assertEquals("live-code", result.code());
            assertEquals("s", result.state());
            assertTrue(result.redirectUri().startsWith(redirect));
        } finally {
            server.stop(0);
        }
    }
}
