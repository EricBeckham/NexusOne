package one.nexus.p2.idp;

import java.net.CookieHandler;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loopback HTTP helper for Keycloak's login form. Not a browser. Used by M0-04
 * confidential-client smoke and the G4 Authorization Code + PKCE walk.
 *
 * <p>Keycloak 26 sets {@code AUTH_SESSION_ID} / {@code KC_RESTART} as {@code Secure; SameSite=None}
 * even on loopback HTTP. The JDK {@code CookieManager} will not send those cookies on {@code http://},
 * which produces "Restart login cookie not found". This driver stores and returns cookies
 * regardless of the Secure flag so the live HTTP walk can complete. Product browsers on HTTPS
 * still honor Secure cookies normally.
 */
public final class AuthorizationCodeDriver {
    private static final Pattern FORM_ACTION = Pattern.compile(
            "(?i)<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"|(?i)<form[^>]*action=\"([^\"]+)\"[^>]*method=\"post\"");
    private static final Pattern HIDDEN = Pattern.compile(
            "(?i)<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"");

    private final HttpClient http;

    public AuthorizationCodeDriver() {
        this.http = HttpClient.newBuilder()
                .cookieHandler(new LoopbackSecureCookieHandler())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record CodeAndState(String code, String state, String redirectUri) {}

    public CodeAndState completeLogin(String authorizationUrl, String username, String password, String expectedRedirectPrefix)
            throws Exception {
        HttpResponse<String> first = send(GET(authorizationUrl));
        HttpResponse<String> loginPage = followUntilHtml(first, expectedRedirectPrefix);
        String location = header(loginPage, "location");
        if (location != null && absolute(loginPage, location).startsWith(expectedRedirectPrefix)) {
            return parseRedirect(absolute(loginPage, location));
        }
        String html = loginPage.body() == null ? "" : loginPage.body();
        if (html.isBlank() && loginPage.statusCode() >= 300 && loginPage.statusCode() < 400) {
            throw new IllegalStateException("login redirected unexpectedly to " + location);
        }
        if (html.contains("kc-passwd-update-form") || html.toLowerCase(Locale.ROOT).contains("update password")) {
            throw new IllegalStateException("Keycloak demanded a password update; smoke user must be non-temporary");
        }
        String action = formAction(html);
        if (action == null) {
            throw new IllegalStateException("Keycloak login form not found. status="
                    + loginPage.statusCode() + " body=" + html.substring(0, Math.min(html.length(), 800)));
        }
        String form = "username=" + enc(username)
                + "&password=" + enc(password)
                + "&credentialId="
                + "&login=" + enc("Sign In")
                + hiddenFields(html);
        HttpResponse<String> submitted = send(HttpRequest.newBuilder(URI.create(action))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", originOf(action))
                .header("Referer", loginPage.uri().toString())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        HttpResponse<String> after = followUntilHtml(submitted, expectedRedirectPrefix);
        String callback = header(after, "location");
        if (callback == null || !absolute(after, callback).startsWith(expectedRedirectPrefix)) {
            throw new IllegalStateException("expected redirect to " + expectedRedirectPrefix
                    + " got status=" + after.statusCode() + " location=" + callback
                    + " body=" + snippet(after.body()));
        }
        return parseRedirect(absolute(after, callback));
    }

    private HttpResponse<String> followUntilHtml(HttpResponse<String> response, String expectedRedirectPrefix)
            throws Exception {
        for (int i = 0; i < 8; i++) {
            int status = response.statusCode();
            String location = header(response, "location");
            if (status >= 300 && status < 400 && location != null) {
                String absolute = absolute(response, location);
                if (absolute.startsWith(expectedRedirectPrefix)) {
                    return response;
                }
                response = send(GET(absolute));
                continue;
            }
            return response;
        }
        throw new IllegalStateException("too many Keycloak redirects");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest GET(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static String absolute(HttpResponse<?> response, String location) {
        return response.uri().resolve(location).toString();
    }

    private static String originOf(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort();
        String host = uri.getScheme() + "://" + uri.getHost();
        return port > 0 ? host + ":" + port : host;
    }

    private static String formAction(String html) {
        Matcher matcher = FORM_ACTION.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String action = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return unescape(action);
    }

    private static String hiddenFields(String html) {
        StringBuilder extra = new StringBuilder();
        Matcher matcher = HIDDEN.matcher(html);
        while (matcher.find()) {
            extra.append('&').append(enc(unescape(matcher.group(1))))
                    .append('=').append(enc(unescape(matcher.group(2))));
        }
        return extra.toString();
    }

    private static CodeAndState parseRedirect(String location) {
        URI uri = URI.create(location);
        Map<String, String> query = query(uri.getRawQuery());
        String code = query.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("callback missing code: " + location);
        }
        String redirectUri = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
        return new CodeAndState(code, query.get("state"), redirectUri);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(decode(part), "");
            } else {
                map.put(decode(part.substring(0, eq)), decode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.substring(0, Math.min(body.length(), 500));
    }

    /**
     * Host-scoped cookie jar that ignores the Secure attribute. Used only by the live HTTP walk.
     */
    static final class LoopbackSecureCookieHandler extends CookieHandler {
        private final Map<String, String> cookies = new LinkedHashMap<>();

        @Override
        public synchronized Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            if (cookies.isEmpty()) {
                return Map.of();
            }
            List<String> pairs = new ArrayList<>();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                pairs.add(entry.getKey() + "=" + entry.getValue());
            }
            return Map.of("Cookie", List.of(String.join("; ", pairs)));
        }

        @Override
        public synchronized void put(URI uri, Map<String, List<String>> responseHeaders) {
            for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
                if (header.getKey() == null || !header.getKey().equalsIgnoreCase("Set-Cookie")) {
                    continue;
                }
                for (String raw : header.getValue()) {
                    absorb(raw);
                }
            }
        }

        private void absorb(String setCookie) {
            if (setCookie == null || setCookie.isBlank()) {
                return;
            }
            String nv = setCookie.split(";", 2)[0];
            int eq = nv.indexOf('=');
            if (eq <= 0) {
                return;
            }
            String name = nv.substring(0, eq).trim();
            String value = nv.substring(eq + 1);
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
