package one.nexus.p2.idp;

import java.util.List;
import java.util.Optional;

/** Port over Keycloak. Product path is OIDC against Keycloak 26.5.2. */
public interface IdentityProviderPort {
    record IdentityClaims(
            String subject,
            String email,
            String username,
            String displayName,
            String phone,
            String acr,
            String amr
    ) {
        public IdentityClaims(String subject, String email, String username, String displayName, String phone) {
            this(subject, email, username, displayName, phone, null, null);
        }

        public boolean mfaSatisfied() {
            return one.nexus.p2.session.MfaClaims.satisfied(acr, amr);
        }
    }

    record TokenSet(String accessToken, String refreshToken, String idToken, String tokenType, long expiresIn) {}

    record AuthorizeUrl(String url, String state) {}

    record ProvisionedUser(String subject, String email, String username) {}

    record EmailAction(String subject, List<String> actions, String kind) {}

    /** Admin-directory view of an IdP user. Used by G10 person/Keycloak reconcile. */
    record DirectoryUser(String subject, String email, String username, String displayName, String phone, boolean enabled) {
        public IdentityClaims toClaims() {
            return new IdentityClaims(subject, email, username, displayName, phone);
        }
    }

    AuthorizeUrl authorizationUrl(String state, String redirectUri);

    /** Optional ACR step-up. Default ignores {@code acrValues}. */
    default AuthorizeUrl authorizationUrl(String state, String redirectUri, String acrValues) {
        return authorizationUrl(state, redirectUri);
    }

    TokenSet exchangeAuthorizationCode(String code, String redirectUri);

    IdentityClaims validate(String accessToken);

    Optional<IdentityClaims> userInfo(String accessToken);

    void logout(String refreshToken, String idTokenHint);

    ProvisionedUser ensureUser(String email, String username, String displayName);

    EmailAction sendExecuteActionsEmail(String subject, List<String> actions, String kind);

    List<DirectoryUser> listUsers();

    Optional<DirectoryUser> findUser(String subject);
}
