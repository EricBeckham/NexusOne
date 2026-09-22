package one.nexus.p2;

import com.sun.net.httpserver.HttpServer;
import one.nexus.p2.idp.AuthorizationCodeDriver;
import one.nexus.p2.idp.Totp;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void detectsOtpAndTotpSetupForms() {
        assertEquals(
                AuthorizationCodeDriver.FormKind.OTP,
                AuthorizationCodeDriver.detectKind(
                        "<form id=\"kc-otp-login-form\" action=\"/otp\" method=\"post\">"
                                + "<input id=\"otp\" name=\"otp\"/></form>"));
        assertEquals(
                AuthorizationCodeDriver.FormKind.TOTP_SETUP,
                AuthorizationCodeDriver.detectKind(
                        "<form id=\"kc-totp-settings-form\" action=\"/setup\" method=\"post\">"
                                + "<input type=\"hidden\" name=\"totpSecret\" value=\"MFRGGZDF\"/>"
                                + "<input name=\"totp\"/></form>"));
        assertEquals("MFRGGZDF", AuthorizationCodeDriver.extractTotpSecret(
                "<input type=\"hidden\" id=\"totpSecret\" name=\"totpSecret\" value=\"MFRG GZDF\"/>"));
    }

    @Test
    void passwordOnlyStopsAtOtpChallenge() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String redirect = "http://127.0.0.1:" + port + "/callback";
        server.createContext("/auth", exchange -> {
            byte[] body = ("<form id=\"kc-form-login\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/authenticate\" method=\"post\">"
                    + "<input type=\"hidden\" name=\"session_code\" value=\"s1\"/>"
                    + "<button name=\"login\" type=\"submit\">Sign In</button></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/login-actions/authenticate", exchange -> {
            byte[] body = ("<form id=\"kc-otp-login-form\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/authenticate-otp\" method=\"post\">"
                    + "<input id=\"otp\" name=\"otp\"/>"
                    + "<button name=\"login\" type=\"submit\">Sign In</button></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AuthorizationCodeDriver driver = new AuthorizationCodeDriver();
            AuthorizationCodeDriver.ChallengePage otp = driver.submitPassword(
                    driver.open("http://127.0.0.1:" + port + "/auth", redirect),
                    "tenant-admin", "secret");
            assertEquals(AuthorizationCodeDriver.FormKind.OTP, otp.kind());
            assertThrows(IllegalStateException.class, () -> driver.completeLogin(
                    "http://127.0.0.1:" + port + "/auth", "tenant-admin", "secret", redirect));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void enrollsTotpThenCompletesOtpChallenge() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String redirect = "http://127.0.0.1:" + port + "/callback";
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        server.createContext("/auth", exchange -> {
            byte[] body = ("<form id=\"kc-form-login\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/authenticate\" method=\"post\">"
                    + "<button name=\"login\" type=\"submit\">Sign In</button></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/login-actions/authenticate", exchange -> {
            String posted = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (posted.contains("username=")) {
                byte[] body = ("<form id=\"kc-totp-settings-form\" action=\"http://127.0.0.1:" + port
                        + "/login-actions/setup\" method=\"post\">"
                        + "<input type=\"hidden\" name=\"totpSecret\" value=\"" + secret + "\"/>"
                        + "<input name=\"totp\"/></form>")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
            exchange.close();
        });
        server.createContext("/login-actions/setup", exchange -> {
            String posted = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (posted.contains("totp=") && posted.contains("totpSecret=" + secret)) {
                exchange.getResponseHeaders().add("Location", redirect + "?code=enroll-code&state=s");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
            exchange.close();
        });
        server.createContext("/otp-auth", exchange -> {
            byte[] body = ("<form id=\"kc-form-login\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/password-then-otp\" method=\"post\">"
                    + "<button name=\"login\" type=\"submit\">Sign In</button></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/login-actions/password-then-otp", exchange -> {
            byte[] body = ("<form id=\"kc-otp-login-form\" action=\"http://127.0.0.1:" + port
                    + "/login-actions/otp\" method=\"post\">"
                    + "<input id=\"otp\" name=\"otp\"/></form>")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/login-actions/otp", exchange -> {
            String posted = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (posted.contains("otp=" + Totp.generate(secret))) {
                exchange.getResponseHeaders().add("Location", redirect + "?code=otp-code&state=s");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            AuthorizationCodeDriver driver = new AuthorizationCodeDriver();
            AuthorizationCodeDriver.TotpEnrollment enrolled = driver.enrollTotp(
                    "http://127.0.0.1:" + port + "/auth", "tenant-admin", "secret", redirect);
            assertEquals(secret, enrolled.secret());
            assertEquals("enroll-code", enrolled.callback().code());

            AuthorizationCodeDriver.CodeAndState otp = driver.completeLoginWithOtp(
                    "http://127.0.0.1:" + port + "/otp-auth",
                    "tenant-admin",
                    "secret",
                    Totp.generate(secret),
                    redirect);
            assertEquals("otp-code", otp.code());
        } finally {
            server.stop(0);
        }
    }
}
