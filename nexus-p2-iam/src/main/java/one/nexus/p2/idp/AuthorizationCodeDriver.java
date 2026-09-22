package one.nexus.p2.idp;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
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
import javax.net.ssl.SSLContext;

/**
 * Loopback helper for Keycloak's login form. Not a product Web/H5 UI.
 *
 * <p>The default constructor is the M0-04 / #108 HTTP walk: Keycloak 26 sets
 * {@code AUTH_SESSION_ID} / {@code KC_RESTART} as {@code Secure; SameSite=None}
 * even on loopback HTTP, so this driver stores and returns those cookies despite
 * the Secure flag.
 *
 * <p>{@link #httpsHonoringSecureCookies(SSLContext)} is the G4 HTTPS leftover:
 * JDK {@code CookieManager} (honors Secure; does not ignore it) over HTTP/2
 * against local TLS. A real browser on HTTPS behaves the same way.
 */
public final class AuthorizationCodeDriver {
    private static final Pattern FORM_ACTION = Pattern.compile(
            "(?i)<form[^>]*(?:id=\"kc-form-login\"|id=\"kc-otp-login-form\"|id=\"kc-totp-settings-form\"|id=\"kc-update-profile-form\"|id=\"kc-recovery-code-login-form\"|id=\"kc-recovery-codes-setup-form\"|id=\"kc-email-code-login-form\")[^>]*action=\"([^\"]+)\""
                    + "|(?i)<form[^>]*action=\"([^\"]+)\"[^>]*method=\"post\""
                    + "|(?i)<form[^>]*method=\"post\"[^>]*action=\"([^\"]+)\"");
    private static final Pattern HIDDEN = Pattern.compile(
            "(?i)<input[^>]*type=\"hidden\"[^>]*name=\"([^\"]+)\"[^>]*value=\"([^\"]*)\"");

    private final HttpClient http;
    private volatile HttpClient.Version lastVersion = HttpClient.Version.HTTP_1_1;

    public AuthorizationCodeDriver() {
        this(httpClient(new LoopbackSecureCookieHandler(), null, HttpClient.Version.HTTP_1_1));
    }

    public AuthorizationCodeDriver(HttpClient http) {
        this.http = http;
    }

    /**
     * Cookie-preserving HTTPS / HTTP/2 driver. Uses the JDK cookie jar, which
     * sends {@code Secure} cookies only on {@code https://}.
     */
    public static AuthorizationCodeDriver httpsHonoringSecureCookies(SSLContext ssl) {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        return new AuthorizationCodeDriver(httpClient(cookies, ssl, HttpClient.Version.HTTP_2));
    }

    public static HttpClient httpsClient(SSLContext ssl) {
        return httpClient(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER), ssl, HttpClient.Version.HTTP_2);
    }

    public HttpClient.Version lastHttpVersion() {
        return lastVersion;
    }

    public HttpClient http() {
        return http;
    }

    private static HttpClient httpClient(CookieHandler cookies, SSLContext ssl, HttpClient.Version version) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .version(version)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10));
        if (ssl != null) {
            builder.sslContext(ssl);
        }
        return builder.build();
    }

    public record CodeAndState(String code, String state, String redirectUri) {}

    public enum FormKind {
        PASSWORD,
        OTP,
        TOTP_SETUP,
        RECOVERY_CODE,
        EMAIL_OTP,
        UPDATE_PROFILE,
        REDIRECT,
        UNKNOWN
    }

    /**
     * One Keycloak HTML form (or the product callback redirect) in the
     * authorization-code walk. Used by admin TOTP enrollment / OTP challenge.
     */
    public static final class ChallengePage {
        private final HttpResponse<String> response;
        private final String expectedRedirectPrefix;
        private final FormKind kind;
        private final String html;
        private final String action;
        private final String totpSecret;
        private final CodeAndState redirect;

        ChallengePage(
                HttpResponse<String> response,
                String expectedRedirectPrefix,
                FormKind kind,
                String html,
                String action,
                String totpSecret,
                CodeAndState redirect) {
            this.response = response;
            this.expectedRedirectPrefix = expectedRedirectPrefix;
            this.kind = kind;
            this.html = html == null ? "" : html;
            this.action = action;
            this.totpSecret = totpSecret;
            this.redirect = redirect;
        }

        public FormKind kind() {
            return kind;
        }

        public String html() {
            return html;
        }

        public String action() {
            return action;
        }

        public String totpSecret() {
            return totpSecret;
        }

        public CodeAndState redirect() {
            return redirect;
        }

        public boolean isRedirect() {
            return kind == FormKind.REDIRECT && redirect != null;
        }

        int statusCode() {
            return response.statusCode();
        }

        String location() {
            return header(response, "location");
        }

        URI uri() {
            return response.uri();
        }

        String redirectPrefix() {
            return expectedRedirectPrefix;
        }
    }

    public record TotpEnrollment(String secret, CodeAndState callback) {}

    public CodeAndState completeLogin(String authorizationUrl, String username, String password, String expectedRedirectPrefix)
            throws Exception {
        ChallengePage page = open(authorizationUrl, expectedRedirectPrefix);
        if (page.isRedirect()) {
            return page.redirect();
        }
        ChallengePage after = submitPassword(page, username, password);
        after = completeRequiredActions(after, username);
        if (after.isRedirect()) {
            return after.redirect();
        }
        throw new IllegalStateException("expected redirect to " + expectedRedirectPrefix
                + " got kind=" + after.kind()
                + " status=" + after.statusCode()
                + " location=" + after.location()
                + " body=" + snippet(after.html()));
    }

    /**
     * Password then OTP. Fails if Keycloak never presents the OTP form
     * (user has no OTP credential / Conditional OTP skipped).
     */
    public CodeAndState completeLoginWithOtp(
            String authorizationUrl,
            String username,
            String password,
            String otp,
            String expectedRedirectPrefix) throws Exception {
        ChallengePage page = open(authorizationUrl, expectedRedirectPrefix);
        if (page.kind() == FormKind.PASSWORD) {
            page = submitPassword(page, username, password);
        }
        page = completeRequiredActions(page, username);
        if (page.kind() != FormKind.OTP) {
            throw new IllegalStateException("expected OTP form after password, got " + page.kind()
                    + " body=" + snippet(page.html()));
        }
        ChallengePage after = submitOtp(page, otp);
        if (!after.isRedirect()) {
            throw new IllegalStateException("expected redirect after OTP, got " + after.kind()
                    + " body=" + snippet(after.html()));
        }
        return after.redirect();
    }

    /**
     * Password then recovery-code form. Leftover only: does not enroll
     * Keycloak {@code recovery-authn-codes}. Live delivery is a harness SKIP
     * unless a realm actually presents this form.
     */
    public CodeAndState completeLoginWithRecoveryCode(
            String authorizationUrl,
            String username,
            String password,
            String recoveryCode,
            String expectedRedirectPrefix) throws Exception {
        ChallengePage page = open(authorizationUrl, expectedRedirectPrefix);
        if (page.kind() == FormKind.PASSWORD) {
            page = submitPassword(page, username, password);
        }
        page = completeRequiredActions(page, username);
        if (page.kind() != FormKind.RECOVERY_CODE) {
            throw new IllegalStateException("expected recovery-code form after password, got " + page.kind()
                    + " body=" + snippet(page.html()));
        }
        ChallengePage after = submitRecoveryCode(page, recoveryCode);
        if (!after.isRedirect()) {
            throw new IllegalStateException("expected redirect after recovery code, got " + after.kind()
                    + " body=" + snippet(after.html()));
        }
        return after.redirect();
    }

    /**
     * Password then email-OTP form. Leftover only: does not send mail.
     */
    public CodeAndState completeLoginWithEmailOtp(
            String authorizationUrl,
            String username,
            String password,
            String emailOtp,
            String expectedRedirectPrefix) throws Exception {
        ChallengePage page = open(authorizationUrl, expectedRedirectPrefix);
        if (page.kind() == FormKind.PASSWORD) {
            page = submitPassword(page, username, password);
        }
        page = completeRequiredActions(page, username);
        if (page.kind() != FormKind.EMAIL_OTP) {
            throw new IllegalStateException("expected email-OTP form after password, got " + page.kind()
                    + " body=" + snippet(page.html()));
        }
        ChallengePage after = submitEmailOtp(page, emailOtp);
        if (!after.isRedirect()) {
            throw new IllegalStateException("expected redirect after email OTP, got " + after.kind()
                    + " body=" + snippet(after.html()));
        }
        return after.redirect();
    }

    /**
     * Password then CONFIGURE_TOTP setup form. Parses the Keycloak secret,
     * generates a TOTP, and submits enrollment. The resulting code may still
     * be password-only for AMR (required actions run after authenticators).
     */
    public TotpEnrollment enrollTotp(
            String authorizationUrl,
            String username,
            String password,
            String expectedRedirectPrefix) throws Exception {
        ChallengePage page = open(authorizationUrl, expectedRedirectPrefix);
        if (page.kind() == FormKind.PASSWORD) {
            page = submitPassword(page, username, password);
        }
        page = completeRequiredActions(page, username);
        if (page.kind() != FormKind.TOTP_SETUP) {
            throw new IllegalStateException("expected TOTP setup form, got " + page.kind()
                    + " body=" + snippet(page.html()));
        }
        String secret = page.totpSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("TOTP setup form had no totpSecret: " + snippet(page.html()));
        }
        ChallengePage after = submitTotpSetup(page, Totp.generate(secret), "admin-totp");
        if (!after.isRedirect()) {
            throw new IllegalStateException("expected redirect after TOTP enrollment, got " + after.kind()
                    + " body=" + snippet(after.html()));
        }
        return new TotpEnrollment(secret, after.redirect());
    }

    public ChallengePage open(String authorizationUrl, String expectedRedirectPrefix) throws Exception {
        return classify(followUntilHtml(send(GET(authorizationUrl)), expectedRedirectPrefix), expectedRedirectPrefix);
    }

    public ChallengePage submitPassword(ChallengePage page, String username, String password) throws Exception {
        if (page.kind() != FormKind.PASSWORD) {
            throw new IllegalStateException("not a password form: " + page.kind());
        }
        String form = "username=" + enc(username)
                + "&password=" + enc(password)
                + "&credentialId="
                + "&login=" + enc("Sign In")
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    public ChallengePage submitOtp(ChallengePage page, String otp) throws Exception {
        if (page.kind() != FormKind.OTP) {
            throw new IllegalStateException("not an OTP form: " + page.kind());
        }
        String form = "otp=" + enc(otp)
                + "&login=" + enc("Sign In")
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    public ChallengePage submitRecoveryCode(ChallengePage page, String recoveryCode) throws Exception {
        if (page.kind() != FormKind.RECOVERY_CODE) {
            throw new IllegalStateException("not a recovery-code form: " + page.kind());
        }
        String form = "recoveryCodeInput=" + enc(recoveryCode)
                + "&login=" + enc("Sign In")
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    public ChallengePage submitEmailOtp(ChallengePage page, String emailOtp) throws Exception {
        if (page.kind() != FormKind.EMAIL_OTP) {
            throw new IllegalStateException("not an email-OTP form: " + page.kind());
        }
        String field = namedValue(page.html(), "emailCode") != null || page.html().contains("name=\"emailCode\"")
                ? "emailCode"
                : "otp";
        String form = field + "=" + enc(emailOtp)
                + "&login=" + enc("Sign In")
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    public ChallengePage submitUpdateProfile(ChallengePage page, String username) throws Exception {
        if (page.kind() != FormKind.UPDATE_PROFILE) {
            throw new IllegalStateException("not an update-profile form: " + page.kind());
        }
        String email = firstNonBlank(namedValue(page.html(), "email"), username);
        String first = firstNonBlank(namedValue(page.html(), "firstName"), username);
        String last = firstNonBlank(namedValue(page.html(), "lastName"), "Admin");
        String form = "email=" + enc(email)
                + "&firstName=" + enc(first)
                + "&lastName=" + enc(last)
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    private ChallengePage completeRequiredActions(ChallengePage page, String username) throws Exception {
        for (int i = 0; i < 4 && page.kind() == FormKind.UPDATE_PROFILE; i++) {
            page = submitUpdateProfile(page, username);
        }
        return page;
    }

    public ChallengePage submitTotpSetup(ChallengePage page, String totpCode, String label) throws Exception {
        if (page.kind() != FormKind.TOTP_SETUP) {
            throw new IllegalStateException("not a TOTP setup form: " + page.kind());
        }
        String secret = page.totpSecret() == null ? "" : page.totpSecret();
        String form = "totp=" + enc(totpCode)
                + "&totpSecret=" + enc(secret)
                + "&userLabel=" + enc(label == null || label.isBlank() ? "admin-totp" : label)
                + hiddenFields(page.html());
        return postForm(page, form);
    }

    private ChallengePage postForm(ChallengePage page, String form) throws Exception {
        String action = page.action();
        if (action == null || action.isBlank()) {
            throw new IllegalStateException("form action missing for " + page.kind());
        }
        HttpResponse<String> submitted = send(HttpRequest.newBuilder(URI.create(action))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", originOf(action))
                .header("Referer", page.uri().toString())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());
        return classify(followUntilHtml(submitted, page.redirectPrefix()), page.redirectPrefix());
    }

    private ChallengePage classify(HttpResponse<String> response, String expectedRedirectPrefix) {
        String location = header(response, "location");
        if (location != null && absolute(response, location).startsWith(expectedRedirectPrefix)) {
            return new ChallengePage(
                    response, expectedRedirectPrefix, FormKind.REDIRECT, "", null, null,
                    parseRedirect(absolute(response, location)));
        }
        String html = response.body() == null ? "" : response.body();
        if (html.contains("kc-passwd-update-form") || html.toLowerCase(Locale.ROOT).contains("update password")) {
            throw new IllegalStateException("Keycloak demanded a password update; smoke user must be non-temporary");
        }
        FormKind kind = detectKind(html);
        String action = formAction(html);
        if (kind == FormKind.PASSWORD && action == null) {
            throw new IllegalStateException("Keycloak login form not found. status="
                    + response.statusCode() + " body=" + html.substring(0, Math.min(html.length(), 800)));
        }
        return new ChallengePage(
                response, expectedRedirectPrefix, kind, html, action, extractTotpSecret(html), null);
    }

    public static FormKind detectKind(String html) {
        if (html == null || html.isBlank()) {
            return FormKind.UNKNOWN;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (html.contains("kc-update-profile-form")
                || html.contains("login-actions/required-action") && html.contains("VERIFY_PROFILE")
                || (html.contains("name=\"lastName\"") && html.contains("name=\"firstName\""))) {
            return FormKind.UPDATE_PROFILE;
        }
        if (html.contains("kc-totp-settings-form")
                || html.contains("name=\"totpSecret\"")
                || html.contains("id=\"totpSecret\"")
                || html.contains("kc-totp-secret-key")
                || lower.contains("logintotptitle")
                || lower.contains("mobile authenticator")) {
            return FormKind.TOTP_SETUP;
        }
        if (html.contains("kc-recovery-code-login-form")
                || html.contains("kc-recovery-codes-setup-form")
                || html.contains("kc-recovery-codes-list")
                || html.contains("recoveryCodeInput")
                || html.contains("CONFIGURE_RECOVERY_AUTHN_CODES")
                || lower.contains("recovery-authn-code")) {
            return FormKind.RECOVERY_CODE;
        }
        if (html.contains("kc-email-code-login-form")
                || html.contains("kc-email-otp")
                || html.contains("name=\"emailCode\"")
                || html.contains("id=\"emailCode\"")
                || lower.contains("email otp")
                || lower.contains("email-otp")) {
            return FormKind.EMAIL_OTP;
        }
        if (html.contains("kc-otp-login-form")
                || html.contains("id=\"otp\"")
                || (html.contains("name=\"otp\"") && !html.contains("totpSecret"))) {
            return FormKind.OTP;
        }
        if (html.contains("kc-form-login") || html.contains("name=\"password\"")) {
            return FormKind.PASSWORD;
        }
        return FormKind.UNKNOWN;
    }

    public static String extractTotpSecret(String html) {
        if (html == null) {
            return null;
        }
        String hidden = namedValue(html, "totpSecret");
        if (hidden != null && !hidden.isBlank()) {
            return hidden.replace(" ", "");
        }
        Matcher encoded = Pattern.compile(
                "(?is)id=\"kc-totp-secret-key\"[^>]*>([^<]+)<").matcher(html);
        if (encoded.find()) {
            return encoded.group(1).replace(" ", "").trim();
        }
        return null;
    }

    public static List<String> extractRecoveryCodes(String html) {
        List<String> codes = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return codes;
        }
        Matcher list = Pattern.compile(
                "(?is)id=\"kc-recovery-codes-list\"[^>]*>(.*?)</(?:ol|ul|div)>").matcher(html);
        String body = list.find() ? list.group(1) : html;
        Matcher item = Pattern.compile("(?i)([A-Z2-9]{4}-[A-Z2-9]{4})").matcher(body);
        while (item.find()) {
            String code = item.group(1).toUpperCase(Locale.ROOT);
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    static String namedValue(String html, String name) {
        Pattern[] patterns = new Pattern[] {
                Pattern.compile("(?is)<input[^>]*name=\"" + Pattern.quote(name) + "\"[^>]*value=\"([^\"]*)\""),
                Pattern.compile("(?is)<input[^>]*value=\"([^\"]*)\"[^>]*name=\"" + Pattern.quote(name) + "\"")
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return unescape(matcher.group(1));
            }
        }
        return null;
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
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        lastVersion = response.version();
        return response;
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
        String action = matcher.group(1) != null
                ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
