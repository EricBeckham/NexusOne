package one.nexus.p2.idp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local/test IdP. Token format: stub.&lt;base64url(json claims)&gt;
 * Not a product authentication path; used when Keycloak is not running.
 */
public final class StubIdentityProvider implements IdentityProviderPort {
    private final java.util.Map<String, IdentityClaims> users = new ConcurrentHashMap<>();
    private final List<EmailAction> sent = new ArrayList<>();

    @Override
    public AuthorizeUrl authorizationUrl(String state, String redirectUri) {
        return new AuthorizeUrl(redirectUri + "?code=stub-code&state=" + state, state);
    }

    @Override
    public TokenSet exchangeAuthorizationCode(String code, String redirectUri) {
        IdentityClaims claims = users.values().stream().findFirst()
                .orElse(new IdentityClaims("stub-user", "stub@example.com", "stub", "Stub User", null));
        String token = encode(claims);
        return new TokenSet(token, "stub-refresh", token, "Bearer", 3600);
    }

    @Override
    public IdentityClaims validate(String accessToken) {
        if (accessToken == null || !accessToken.startsWith("stub.")) {
            throw new IllegalArgumentException("not a stub token");
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(accessToken.substring(5)), StandardCharsets.UTF_8);
            IdentityClaims claims = new IdentityClaims(
                    field(json, "sub"),
                    field(json, "email"),
                    field(json, "preferred_username"),
                    field(json, "name"),
                    field(json, "phone"));
            users.put(claims.subject(), claims);
            return claims;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid stub token", ex);
        }
    }

    @Override
    public Optional<IdentityClaims> userInfo(String accessToken) {
        return Optional.of(validate(accessToken));
    }

    @Override
    public void logout(String refreshToken, String idTokenHint) {
        // no-op: stub has no Keycloak session
    }

    @Override
    public ProvisionedUser ensureUser(String email, String username, String displayName) {
        String subject = users.values().stream()
                .filter(item -> email.equalsIgnoreCase(item.email()))
                .map(IdentityClaims::subject)
                .findFirst()
                .orElse("kc-" + UUID.randomUUID());
        IdentityClaims claims = new IdentityClaims(subject, email, username, displayName, null);
        users.put(subject, claims);
        return new ProvisionedUser(subject, email, username);
    }

    @Override
    public EmailAction sendExecuteActionsEmail(String subject, List<String> actions, String kind) {
        EmailAction action = new EmailAction(subject, List.copyOf(actions), kind);
        synchronized (sent) {
            sent.add(action);
        }
        return action;
    }

    @Override
    public List<DirectoryUser> listUsers() {
        return users.values().stream()
                .map(StubIdentityProvider::toDirectory)
                .toList();
    }

    @Override
    public Optional<DirectoryUser> findUser(String subject) {
        return Optional.ofNullable(users.get(subject)).map(StubIdentityProvider::toDirectory);
    }

    public void putUser(IdentityClaims claims) {
        users.put(claims.subject(), claims);
    }

    public void removeUser(String subject) {
        users.remove(subject);
    }

    public List<EmailAction> sentEmails() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    public static String token(String subject, String email, String username, String name) {
        String json = "{\"sub\":\"" + subject + "\",\"email\":\"" + email
                + "\",\"preferred_username\":\"" + username + "\",\"name\":\"" + name + "\"}";
        return "stub." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String field(String json, String name) {
        String needle = "\"" + name + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return end < 0 ? null : json.substring(from, end);
    }

    private String encode(IdentityClaims claims) {
        return token(claims.subject(), claims.email(), claims.username(), claims.displayName());
    }

    private static DirectoryUser toDirectory(IdentityClaims claims) {
        return new DirectoryUser(
                claims.subject(), claims.email(), claims.username(), claims.displayName(), claims.phone(), true);
    }
}
