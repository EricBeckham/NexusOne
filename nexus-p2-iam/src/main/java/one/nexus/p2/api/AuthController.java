package one.nexus.p2.api;

import one.nexus.p2.IdentityConfiguration;
import one.nexus.p2.IdentityModule;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.Pkce;
import one.nexus.p2.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityModule module;
    private final IdentityConfiguration.IamProperties properties;

    public AuthController(IdentityModule module, IdentityConfiguration.IamProperties properties) {
        this.module = module;
        this.properties = properties;
    }

    @GetMapping("/authorize-url")
    public Map<String, String> authorizeUrl(
            @RequestParam(required = false) String acr,
            @RequestParam(required = false) String pkce,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge) {
        String state = UUID.randomUUID().toString();
        Pkce.Challenge challenge = pkceChallenge(pkce, codeChallenge);
        var url = module.sessions().authorizationUrl(state, properties.keycloak().redirectUri(), acr, challenge);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("url", url.url());
        body.put("state", url.state());
        if (challenge != null) {
            body.put("code_challenge", challenge.challenge());
            body.put("code_challenge_method", challenge.method());
            if (challenge.verifier() != null && !challenge.verifier().isBlank()) {
                body.put("code_verifier", challenge.verifier());
            }
        }
        return body;
    }

    @PostMapping("/login")
    public SessionService.SessionView login(@RequestBody LoginRequest request) {
        if (request.accessToken() != null && !request.accessToken().isBlank()) {
            return module.sessions().loginWithAccessToken(request.accessToken());
        }
        if (request.code() == null || request.code().isBlank()) {
            throw one.nexus.p2.support.DomainException.badRequest("P2.IAM.LOGIN", "code or accessToken is required");
        }
        String redirect = request.redirectUri() == null ? properties.keycloak().redirectUri() : request.redirectUri();
        return module.sessions().loginWithCode(request.code(), redirect, request.codeVerifier());
    }

    @GetMapping("/callback")
    public SessionService.SessionView callback(String code, String state) {
        return module.sessions().loginWithCode(code, properties.keycloak().redirectUri());
    }

    @PostMapping("/tenant-switch")
    public SessionService.SessionView switchTenant(HttpServletRequest http, @RequestBody TenantSwitchRequest request) {
        return module.sessions().switchTenant(identity(http), request.tenantId());
    }

    @GetMapping("/session")
    public SessionService.SessionView session(HttpServletRequest http) {
        return module.sessions().current(identity(http));
    }

    /**
     * LO3: always clears the app session; {@code end_session_url} is the Keycloak RP-logout URL for the browser, or
     * an empty string when the server holds no IdP hint (the browser may then still have an SSO session).
     */
    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest http, @RequestBody(required = false) LogoutRequest request) {
        LogoutRequest body = request == null ? new LogoutRequest(null, null) : request;
        var result = module.sessions().logout(identity(http), body.refreshToken(), body.idToken(),
                postLogoutRedirectUri(properties.keycloak().redirectUri()));
        Map<String, String> response = new LinkedHashMap<>();
        response.put("end_session_url", result.endSessionUrl());
        return response;
    }

    /**
     * Only the configured front-end origin (taken from the configured login redirect URI) plus {@code /login};
     * never a value supplied by the request. Null when the configured URI has no usable http(s) origin.
     */
    static String postLogoutRedirectUri(String configuredRedirectUri) {
        if (configuredRedirectUri == null || configuredRedirectUri.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(configuredRedirectUri.trim());
            String scheme = uri.getScheme();
            if (uri.getRawAuthority() == null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return null;
            }
            return scheme.toLowerCase(java.util.Locale.ROOT) + "://" + uri.getRawAuthority() + "/login";
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    @PostMapping("/password-reset")
    public Map<String, String> passwordReset(@RequestBody PasswordResetRequest request) {
        Model.GlobalAccount account = module.repository().findAccountByEmail(request.email())
                .orElseThrow(() -> one.nexus.p2.support.DomainException.notFound("account not found"));
        module.smtp().resetPassword(account.keycloakUserId());
        return Map.of("status", "submitted");
    }

    private static Pkce.Challenge pkceChallenge(String pkce, String codeChallenge) {
        boolean hasChallenge = codeChallenge != null && !codeChallenge.isBlank();
        if (pkce == null || pkce.isBlank()) {
            if (hasChallenge) {
                throw one.nexus.p2.support.DomainException.badRequest(
                        "P2.IAM.PKCE", "pkce must be s256 when code_challenge is set");
            }
            return null;
        }
        if (!"s256".equalsIgnoreCase(pkce)) {
            throw one.nexus.p2.support.DomainException.badRequest(
                    "P2.IAM.PKCE", "pkce must be s256 when set");
        }
        if (hasChallenge) {
            try {
                return Pkce.clientHeld(codeChallenge);
            } catch (IllegalArgumentException ex) {
                throw one.nexus.p2.support.DomainException.badRequest("P2.IAM.PKCE", ex.getMessage());
            }
        }
        return Pkce.s256();
    }

    private static SessionService.AuthenticatedIdentity identity(HttpServletRequest http) {
        Object value = http.getAttribute(IdentityAuthFilter.ATTR_IDENTITY);
        if (value instanceof SessionService.AuthenticatedIdentity identity) {
            return identity;
        }
        throw one.nexus.p2.support.DomainException.unauthorized("unauthenticated");
    }

    public record LoginRequest(String code, String redirectUri, String accessToken, String codeVerifier) {}
    public record TenantSwitchRequest(UUID tenantId) {}
    public record LogoutRequest(String refreshToken, String idToken) {}
    public record PasswordResetRequest(String email) {}
}
