package one.nexus.p2.idp;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OIDC + Admin API adapter for Keycloak 26.5.2. Does not use Keycloak Organizations,
 * SAML, LDAP, or browser PKCE as an accepted product path.
 */
public final class KeycloakIdentityProvider implements IdentityProviderPort {
    private final RestClient http;
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public KeycloakIdentityProvider(String issuer, String clientId, String clientSecret) {
        this.issuer = trimSlash(issuer);
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.http = RestClient.builder().build();
        try {
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(
                    URI.create(this.issuer + "/protocol/openid-connect/certs").toURL());
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
            this.jwtProcessor = processor;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize Keycloak JWKS from " + this.issuer, ex);
        }
    }

    @Override
    public AuthorizeUrl authorizationUrl(String state, String redirectUri) {
        String url = issuer + "/protocol/openid-connect/auth"
                + "?client_id=" + enc(clientId)
                + "&response_type=code"
                + "&scope=openid%20profile%20email"
                + "&redirect_uri=" + enc(redirectUri)
                + "&state=" + enc(state);
        return new AuthorizeUrl(url, state);
    }

    @Override
    public TokenSet exchangeAuthorizationCode(String code, String redirectUri) {
        Map<String, Object> body = postForm(issuer + "/protocol/openid-connect/token",
                "grant_type=authorization_code&code=" + enc(code)
                        + "&redirect_uri=" + enc(redirectUri)
                        + "&client_id=" + enc(clientId)
                        + "&client_secret=" + enc(clientSecret));
        if (body.get("access_token") == null) {
            throw new IllegalStateException("Keycloak token endpoint returned no access_token");
        }
        Object tokenType = body.get("token_type");
        return new TokenSet(
                String.valueOf(body.get("access_token")),
                stringOrNull(body.get("refresh_token")),
                stringOrNull(body.get("id_token")),
                tokenType == null ? "Bearer" : String.valueOf(tokenType),
                asLong(body.get("expires_in")));
    }

    @Override
    public IdentityClaims validate(String accessToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(accessToken, null);
            if (!issuer.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("issuer mismatch");
            }
            return new IdentityClaims(
                    claims.getSubject(),
                    claims.getStringClaim("email"),
                    firstNonBlank(claims.getStringClaim("preferred_username"), claims.getStringClaim("email"), claims.getSubject()),
                    firstNonBlank(claims.getStringClaim("name"), claims.getStringClaim("preferred_username"), claims.getSubject()),
                    claims.getStringClaim("phone_number"));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid Keycloak access token", ex);
        }
    }

    @Override
    public Optional<IdentityClaims> userInfo(String accessToken) {
        Map<String, Object> body = getJson(issuer + "/protocol/openid-connect/userinfo", accessToken);
        if (body == null || body.get("sub") == null) {
            return Optional.empty();
        }
        return Optional.of(new IdentityClaims(
                String.valueOf(body.get("sub")),
                stringOrNull(body.get("email")),
                firstNonBlank(stringOrNull(body.get("preferred_username")), stringOrNull(body.get("email"))),
                firstNonBlank(stringOrNull(body.get("name")), stringOrNull(body.get("preferred_username"))),
                stringOrNull(body.get("phone_number"))));
    }

    @Override
    public void logout(String refreshToken, String idTokenHint) {
        StringBuilder form = new StringBuilder("client_id=").append(enc(clientId))
                .append("&client_secret=").append(enc(clientSecret));
        if (refreshToken != null) {
            form.append("&refresh_token=").append(enc(refreshToken));
        }
        if (idTokenHint != null) {
            form.append("&id_token_hint=").append(enc(idTokenHint));
        }
        http.post()
                .uri(issuer + "/protocol/openid-connect/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form.toString())
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public ProvisionedUser ensureUser(String email, String username, String displayName) {
        String adminToken = clientCredentialsToken();
        String adminUsers = issuer.replace("/realms/", "/admin/realms/") + "/users";
        ProvisionedUser existing = findUser(adminUsers, adminToken, username, email);
        if (existing != null) {
            return existing;
        }
        var created = http.post()
                .uri(adminUsers)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "username", username,
                        "email", email,
                        "enabled", true,
                        "emailVerified", true,
                        "firstName", displayName == null ? username : displayName))
                .retrieve()
                .toBodilessEntity();
        String id = lastPathSegment(created.getHeaders().getLocation());
        if (id != null) {
            return new ProvisionedUser(id, email, username);
        }
        ProvisionedUser afterCreate = findUser(adminUsers, adminToken, username, email);
        if (afterCreate == null) {
            throw new IllegalStateException("Keycloak user create did not return the user");
        }
        return afterCreate;
    }

    @SuppressWarnings("unchecked")
    private ProvisionedUser findUser(String adminUsers, String adminToken, String username, String email) {
        for (String query : List.of("username=" + enc(username) + "&exact=true", "email=" + enc(email) + "&exact=true")) {
            List<?> found = http.get()
                    .uri(adminUsers + "?" + query)
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(List.class);
            if (found != null && !found.isEmpty() && found.getFirst() instanceof Map<?, ?> user && user.get("id") != null) {
                return new ProvisionedUser(String.valueOf(user.get("id")), email, username);
            }
        }
        return null;
    }

    private static String lastPathSegment(java.net.URI location) {
        if (location == null || location.getPath() == null || location.getPath().isBlank()) {
            return null;
        }
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    @Override
    public EmailAction sendExecuteActionsEmail(String subject, List<String> actions, String kind) {
        String adminToken = clientCredentialsToken();
        http.put()
                .uri(issuer.replace("/realms/", "/admin/realms/") + "/users/" + enc(subject) + "/execute-actions-email")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(actions)
                .retrieve()
                .toBodilessEntity();
        return new EmailAction(subject, actions, kind);
    }

    private String clientCredentialsToken() {
        Map<String, Object> body = postForm(issuer + "/protocol/openid-connect/token",
                "grant_type=client_credentials&client_id=" + enc(clientId)
                        + "&client_secret=" + enc(clientSecret));
        if (body.get("access_token") == null) {
            throw new IllegalStateException("Keycloak client-credentials grant failed");
        }
        return String.valueOf(body.get("access_token"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String uri, String form) {
        Map<String, Object> body = http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return body == null ? Map.of() : body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String uri, String accessToken) {
        Map<String, Object> body = http.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
        return body == null ? Map.of() : body;
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
