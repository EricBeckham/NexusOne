package one.nexus.p2.smtp;

import one.nexus.p2.idp.IdentityProviderPort;

import java.util.List;

/**
 * Minimal invite / password-reset hook. M0 does not send business mail.
 * Delivery is delegated to Keycloak execute-actions-email when SMTP is enabled there.
 */
public interface SmtpHook {
    IdentityProviderPort.EmailAction invite(String keycloakUserId);

    IdentityProviderPort.EmailAction resetPassword(String keycloakUserId);
}
