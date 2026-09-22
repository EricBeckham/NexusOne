package one.nexus.p2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.LoopbackHttps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthorizationCodeHttpsDriverTest {
    @TempDir
    Path temp;

    @Test
    void httpsCookieManagerSendsSecureCookiesAndHttpDoesNot() throws Exception {
        LoopbackHttps.Material material = LoopbackHttps.generatePkcs12(temp.resolve("certs"));
        HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(material.serverContext()));
        int httpsPort = https.getAddress().getPort();
        String httpsRedirect = "https://127.0.0.1:" + httpsPort + "/callback";
        mountLogin(https, httpsPort, "https", httpsRedirect);
        https.start();

        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int httpPort = http.getAddress().getPort();
        String httpRedirect = "http://127.0.0.1:" + httpPort + "/callback";
        mountLogin(http, httpPort, "http", httpRedirect);
        http.start();
        try {
            AuthorizationCodeDriver httpsDriver = AuthorizationCodeDriver.httpsHonoringSecureCookies(
                    material.clientContext());
            AuthorizationCodeDriver.CodeAndState result = httpsDriver.completeLogin(
                    "https://127.0.0.1:" + httpsPort + "/auth", "carol", "secret", httpsRedirect);
            assertEquals("live-code", result.code());
            assertEquals("s", result.state());
            assertTrue(result.redirectUri().startsWith(httpsRedirect));

            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            AuthorizationCodeDriver httpHonoringSecure = new AuthorizationCodeDriver(
                    HttpClient.newBuilder()
                            .cookieHandler(cookies)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build());
            IllegalStateException blocked = assertThrows(IllegalStateException.class, () ->
                    httpHonoringSecure.completeLogin(
                            "http://127.0.0.1:" + httpPort + "/auth", "carol", "secret", httpRedirect));
            assertTrue(blocked.getMessage().contains("Restart login cookie not found")
                            || blocked.getMessage().contains("expected redirect")
                            || blocked.getMessage().contains("login form"),
                    blocked.getMessage());
            assertTrue(Files.exists(material.certificatePem()));
        } finally {
            https.stop(0);
            http.stop(0);
        }
    }

    private static void mountLogin(HttpServer server, int port, String scheme, String redirect) {
        server.createContext("/auth", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie",
                    "KC_RESTART=restart-token; Path=/; Secure; HttpOnly; SameSite=None");
            exchange.getResponseHeaders().add("Set-Cookie",
                    "AUTH_SESSION_ID=sess; Path=/; Secure; HttpOnly; SameSite=None");
            byte[] body = ("<form id=\"kc-form-login\" action=\"" + scheme + "://127.0.0.1:" + port
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
    }
}
