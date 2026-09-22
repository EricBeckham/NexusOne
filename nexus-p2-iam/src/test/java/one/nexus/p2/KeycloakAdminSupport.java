package one.nexus.p2;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loopback Admin REST for the G17 live TOTP leftover. Secrets stay in env.
 * Configures AMR mapper + authenticator reference values so an OTP walk
 * produces {@code amr} containing {@code otp}.
 */
final class KeycloakAdminSupport {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String issuer;
    private final String clientId;
    private final String clientSecret;

    KeycloakAdminSupport(String issuer, String clientId, String clientSecret) {
        this.issuer = trimSlash(issuer);
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    String adminBase() {
        return issuer.replace("/realms/", "/admin/realms/");
    }

    String clientCredentialsToken() throws Exception {
        String form = "grant_type=client_credentials&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2 || !response.body().contains("access_token")) {
            throw new IllegalStateException("client-credentials failed: " + response.statusCode()
                    + " " + snippet(response.body()));
        }
        return jsonString(response.body(), "access_token");
    }

    String findUserId(String username) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> response = get(token, adminBase() + "/users?username=" + enc(username) + "&exact=true");
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        return matcher.find() ? matcher.group(1) : null;
    }

    void setProfile(String userId, String firstName, String lastName, String email) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> current = get(token, adminBase() + "/users/" + enc(userId));
        if (current.statusCode() / 100 != 2) {
            throw new IllegalStateException("get user failed: " + current.statusCode());
        }
        String payload = current.body();
        payload = replaceJsonString(payload, "firstName", firstName);
        payload = replaceJsonString(payload, "lastName", lastName);
        if (email != null && !email.isBlank()) {
            payload = replaceJsonString(payload, "email", email);
        }
        HttpResponse<String> updated = put(token, adminBase() + "/users/" + enc(userId), payload);
        if (updated.statusCode() / 100 != 2) {
            throw new IllegalStateException("update profile failed: " + updated.statusCode()
                    + " " + snippet(updated.body()));
        }
    }

    static String replaceJsonString(String json, String name, String value) {
        String replacement = "\"" + name + "\":" + jsonQuote(value);
        if (json.contains("\"" + name + "\"")) {
            return json.replaceFirst("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"[^\"]*\"", replacement);
        }
        return json.replaceFirst("\\{", "{" + replacement + ",");
    }

    void setPassword(String userId, String password) throws Exception {
        String token = clientCredentialsToken();
        String body = "{\"type\":\"password\",\"temporary\":false,\"value\":"
                + jsonQuote(password) + "}";
        HttpResponse<String> response = put(
                token,
                adminBase() + "/users/" + enc(userId) + "/reset-password",
                body);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("reset-password failed: " + response.statusCode()
                    + " " + snippet(response.body()));
        }
    }

    void setRequiredActions(String userId, List<String> actions) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> current = get(token, adminBase() + "/users/" + enc(userId));
        if (current.statusCode() / 100 != 2) {
            throw new IllegalStateException("get user failed: " + current.statusCode());
        }
        String payload = replaceRequiredActions(current.body(), actions);
        HttpResponse<String> updated = put(token, adminBase() + "/users/" + enc(userId), payload);
        if (updated.statusCode() / 100 != 2) {
            throw new IllegalStateException("update requiredActions failed: " + updated.statusCode()
                    + " " + snippet(updated.body()));
        }
    }

    void deleteOtpCredentials(String userId) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> response = get(token, adminBase() + "/users/" + enc(userId) + "/credentials");
        if (response.statusCode() / 100 != 2) {
            return;
        }
        Matcher matcher = Pattern.compile(
                "\\{[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^}]*\"type\"\\s*:\\s*\"otp\"|"
                        + "\\{[^}]*\"type\"\\s*:\\s*\"otp\"[^}]*\"id\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(response.body());
        while (matcher.find()) {
            String id = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            delete(token, adminBase() + "/users/" + enc(userId) + "/credentials/" + enc(id));
        }
    }

    boolean userHasOtp(String userId) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> response = get(token, adminBase() + "/users/" + enc(userId) + "/credentials");
        return response.statusCode() / 100 == 2 && response.body().contains("\"type\":\"otp\"");
    }

    void assignRealmRole(String userId, String roleName) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> role = get(token, adminBase() + "/roles/" + enc(roleName));
        if (role.statusCode() / 100 != 2 || !role.body().contains("\"id\"")) {
            return;
        }
        HttpResponse<String> mapped = post(
                token,
                adminBase() + "/users/" + enc(userId) + "/role-mappings/realm",
                "[" + role.body() + "]");
        if (mapped.statusCode() / 100 != 2 && mapped.statusCode() != 409) {
            throw new IllegalStateException("assign role failed: " + mapped.statusCode()
                    + " " + snippet(mapped.body()));
        }
    }

    void ensureAmrMapper(String clientUuid) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> existing = get(
                token, adminBase() + "/clients/" + enc(clientUuid) + "/protocol-mappers/models");
        if (existing.body() != null && existing.body().contains("\"oidc-amr-mapper\"")) {
            return;
        }
        String body = "{\"name\":\"amr\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-amr-mapper\","
                + "\"consentRequired\":false,\"config\":{"
                + "\"id.token.claim\":\"true\",\"access.token.claim\":\"true\","
                + "\"userinfo.token.claim\":\"true\",\"introspection.token.claim\":\"true\"}}";
        HttpResponse<String> created = post(
                token, adminBase() + "/clients/" + enc(clientUuid) + "/protocol-mappers/models", body);
        if (created.statusCode() / 100 != 2 && created.statusCode() != 409) {
            throw new IllegalStateException("create AMR mapper failed: " + created.statusCode()
                    + " " + snippet(created.body()));
        }
    }

    String findClientUuid(String clientIdValue) throws Exception {
        String token = clientCredentialsToken();
        HttpResponse<String> response = get(
                token, adminBase() + "/clients?clientId=" + enc(clientIdValue));
        Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
        return matcher.find() ? matcher.group(1) : null;
    }

    List<String> ensureAuthenticatorReferences() throws Exception {
        List<String> applied = new ArrayList<>();
        String token = clientCredentialsToken();
        HttpResponse<String> executions = get(token, adminBase() + "/authentication/flows/browser/executions");
        if (executions.statusCode() == 403) {
            applied.add("skipped:service-account-lacks-manage-realm");
            return applied;
        }
        if (executions.statusCode() / 100 != 2) {
            throw new IllegalStateException("browser executions failed: " + executions.statusCode()
                    + " " + snippet(executions.body()));
        }
        applyReference(token, executions.body(), "auth-username-password-form", "pwd", applied);
        applyReference(token, executions.body(), "auth-otp-form", "otp", applied);
        return applied;
    }

    private void applyReference(
            String token, String executionsJson, String providerId, String reference, List<String> applied)
            throws Exception {
        for (String executionId : executionIds(executionsJson, providerId)) {
            String configId = executionConfigId(executionsJson, executionId);
            if (configId != null && !configId.isBlank()) {
                HttpResponse<String> current = get(token, adminBase() + "/authentication/config/" + enc(configId));
                String alias = jsonString(current.body(), "alias");
                if (alias == null || alias.isBlank()) {
                    alias = providerId + "-amr";
                }
                String body = "{\"id\":" + jsonQuote(configId)
                        + ",\"alias\":" + jsonQuote(alias)
                        + ",\"config\":{\"default.reference.value\":" + jsonQuote(reference)
                        + ",\"default.reference.maxAge\":\"3600\"}}";
                HttpResponse<String> updated = put(
                        token, adminBase() + "/authentication/config/" + enc(configId), body);
                if (updated.statusCode() / 100 != 2) {
                    throw new IllegalStateException("update authenticator config failed: "
                            + updated.statusCode() + " " + snippet(updated.body()));
                }
            } else {
                String body = "{\"alias\":" + jsonQuote(providerId + "-amr-" + reference)
                        + ",\"config\":{\"default.reference.value\":" + jsonQuote(reference)
                        + ",\"default.reference.maxAge\":\"3600\"}}";
                HttpResponse<String> created = post(
                        token,
                        adminBase() + "/authentication/executions/" + enc(executionId) + "/config",
                        body);
                if (created.statusCode() / 100 != 2 && created.statusCode() != 409) {
                    throw new IllegalStateException("create authenticator config failed: "
                            + created.statusCode() + " " + snippet(created.body()));
                }
            }
            applied.add(providerId + "=" + reference);
        }
    }

    static List<String> executionIds(String json, String providerId) {
        List<String> ids = new ArrayList<>();
        Matcher block = Pattern.compile("\\{[^\\{\\}]*\\}").matcher(json);
        while (block.find()) {
            String object = block.group();
            if (!object.contains("\"providerId\":\"" + providerId + "\"")
                    && !object.contains("\"providerId\": \"" + providerId + "\"")) {
                continue;
            }
            String id = jsonString(object, "id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    static String executionConfigId(String json, String executionId) {
        Matcher block = Pattern.compile("\\{[^\\{\\}]*\\}").matcher(json);
        while (block.find()) {
            String object = block.group();
            if (!object.contains("\"id\":\"" + executionId + "\"")
                    && !object.contains("\"id\": \"" + executionId + "\"")) {
                continue;
            }
            return jsonString(object, "authenticationConfig");
        }
        return null;
    }

    static String replaceRequiredActions(String userJson, List<String> actions) {
        String quoted = actions.stream()
                .map(KeycloakAdminSupport::jsonQuote)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String replacement = "\"requiredActions\":[" + quoted + "]";
        if (userJson.contains("\"requiredActions\"")) {
            return userJson.replaceFirst("\"requiredActions\"\\s*:\\s*\\[[^\\]]*\\]", replacement);
        }
        return userJson.replaceFirst("\\{", "{" + replacement + ",");
    }

    private HttpResponse<String> get(String token, String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String token, String url, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String token, String url, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String token, String url) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + token)
                        .DELETE()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static String jsonString(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String jsonQuote(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.substring(0, Math.min(body.length(), 400));
    }
}
