package one.nexus.p2.idp;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional Chromium / Chrome DevTools walk for the G4 HTTPS leftover.
 * Not required for LOGIN evidence — the HTTP/2 {@link AuthorizationCodeDriver}
 * is the always-available cookie-honoring path.
 */
public final class ChromeDevToolsDriver {
    private ChromeDevToolsDriver() {}

    public static Optional<Path> chromeBinary() {
        String configured = System.getenv("NEXUS_G4_CHROME");
        List<String> candidates = new java.util.ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        candidates.addAll(List.of(
                "/usr/local/bin/google-chrome",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser"));
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    public static boolean available() {
        return chromeBinary().isPresent();
    }

    public static AuthorizationCodeDriver.CodeAndState completeLogin(
            String authorizationUrl, String username, String password, String expectedRedirectPrefix)
            throws Exception {
        Path chrome = chromeBinary().orElseThrow(() -> new IllegalStateException("Chrome/Chromium is not on PATH"));
        Path profile = Files.createTempDirectory("g4-chrome-");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Process process = new ProcessBuilder(
                chrome.toString(),
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--allow-insecure-localhost",
                "--ignore-certificate-errors",
                "--remote-debugging-address=127.0.0.1",
                "--remote-debugging-port=" + port,
                "--user-data-dir=" + profile.toAbsolutePath(),
                "about:blank")
                .redirectErrorStream(true)
                .start();
        try {
            waitForDevtools(port);
            String wsUrl = openTab(port, authorizationUrl);
            CdpSession cdp = CdpSession.connect(wsUrl);
            cdp.send("Page.enable", null);
            cdp.send("Runtime.enable", null);
            waitUntil(cdp, "document.querySelector('#kc-form-login, form') ? 'form' : ''", "form", 20);
            String filled = cdp.evaluate("""
                    (function(){
                      var u = document.getElementById('username') || document.querySelector('input[name=username]');
                      var p = document.getElementById('password') || document.querySelector('input[name=password]');
                      var f = document.getElementById('kc-form-login') || document.querySelector('form');
                      if (!u || !p || !f) return 'no-form';
                      u.value = %s;
                      p.value = %s;
                      f.submit();
                      return 'submitted';
                    })()
                    """.formatted(jsString(username), jsString(password)));
            if (!filled.contains("submitted")) {
                throw new IllegalStateException("Chrome did not find the Keycloak login form: " + filled);
            }
            String location = waitForRedirect(cdp, expectedRedirectPrefix, 20);
            return parseRedirect(location);
        } finally {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void waitForDevtools(int port) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        Exception last = null;
        for (int i = 0; i < 40; i++) {
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/version"))
                                .timeout(Duration.ofSeconds(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("webSocketDebuggerUrl")) {
                    return;
                }
            } catch (Exception ex) {
                last = ex;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Chrome DevTools did not open on " + port, last);
    }

    private static String openTab(int port, String url) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/new?" + enc(url)))
                        .timeout(Duration.ofSeconds(5)).PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            response = http.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/new?" + enc(url)))
                            .timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        String ws = jsonField(response.body(), "webSocketDebuggerUrl");
        if (ws == null || ws.isBlank()) {
            throw new IllegalStateException("Chrome /json/new missing webSocketDebuggerUrl: " + response.body());
        }
        return ws;
    }

    private static void waitUntil(CdpSession cdp, String expression, String expected, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            last = cdp.evaluate(expression);
            if (last.contains(expected)) {
                return;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("timed out waiting for " + expected + " last=" + last);
    }

    private static String waitForRedirect(CdpSession cdp, String prefix, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            last = stripJsonString(cdp.evaluate("location.href"));
            if (last.startsWith(prefix)) {
                return last;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Chrome did not reach " + prefix + " last=" + last);
    }

    private static AuthorizationCodeDriver.CodeAndState parseRedirect(String location) {
        URI uri = URI.create(location);
        String query = uri.getRawQuery();
        String code = null;
        String state = null;
        if (query != null) {
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                String name = eq < 0 ? part : part.substring(0, eq);
                String value = eq < 0 ? "" : java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                if ("code".equals(name)) {
                    code = value;
                } else if ("state".equals(name)) {
                    state = value;
                }
            }
        }
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("callback missing code: " + location);
        }
        String redirectUri = uri.getScheme() + "://" + uri.getRawAuthority() + uri.getRawPath();
        return new AuthorizationCodeDriver.CodeAndState(code, state, redirectUri);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String jsonField(String json, String name) {
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end).replace("\\/", "/");
    }

    private static String stripJsonString(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static final class CdpSession implements WebSocket.Listener {
        private final CompletableFuture<WebSocket> socket = new CompletableFuture<>();
        private final ConcurrentHashMap<Long, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
        private final StringBuilder buffer = new StringBuilder();
        private final AtomicLong nextId = new AtomicLong(1);

        static CdpSession connect(String url) {
            CdpSession session = new CdpSession();
            HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create(url), session)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
            session.socket.orTimeout(10, TimeUnit.SECONDS).join();
            return session;
        }

        String send(String method, String paramsJson) throws Exception {
            long id = nextId.getAndIncrement();
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(id, future);
            String message = paramsJson == null
                    ? "{\"id\":" + id + ",\"method\":\"" + method + "\"}"
                    : "{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
            socket.join().sendText(message, true).join();
            return future.get(20, TimeUnit.SECONDS);
        }

        String evaluate(String expression) throws Exception {
            String params = "{\"expression\":" + jsonQuote(expression) + ",\"returnByValue\":true}";
            String raw = send("Runtime.evaluate", params);
            String value = jsonField(raw, "value");
            if (value != null) {
                return value;
            }
            int result = raw.indexOf("\"result\"");
            return result < 0 ? raw : raw.substring(result);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket.complete(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String json = buffer.toString();
                buffer.setLength(0);
                Long id = jsonLong(json, "id");
                if (id != null) {
                    CompletableFuture<String> future = pending.remove(id);
                    if (future != null) {
                        future.complete(json);
                    }
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket.completeExceptionally(error);
            pending.values().forEach(future -> future.completeExceptionally(error));
        }

        private static Long jsonLong(String json, String name) {
            String key = "\"" + name + "\":";
            int start = json.indexOf(key);
            if (start < 0) {
                return null;
            }
            start += key.length();
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            if (end == start) {
                return null;
            }
            return Long.parseLong(json.substring(start, end));
        }

        private static String jsonQuote(String value) {
            String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            return "\"" + escaped + "\"";
        }
    }
}
