package one.nexus.p2.smtp;

import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.support.DomainException;

import java.util.List;

public final class KeycloakSmtpHook implements SmtpHook {
    private final IdentityProviderPort idp;
    private final boolean enabled;

    public KeycloakSmtpHook(IdentityProviderPort idp, boolean enabled) {
        this.idp = idp;
        this.enabled = enabled;
    }

    @Override
    public IdentityProviderPort.EmailAction invite(String keycloakUserId) {
        requireEnabled();
        return idp.sendExecuteActionsEmail(keycloakUserId, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"), "invite");
    }

    @Override
    public IdentityProviderPort.EmailAction resetPassword(String keycloakUserId) {
        requireEnabled();
        return idp.sendExecuteActionsEmail(keycloakUserId, List.of("UPDATE_PASSWORD"), "password-reset");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw DomainException.badRequest("P2.IAM.SMTP_DISABLED", "SMTP hook is disabled; enable Keycloak SMTP and nexus.iam.smtp.enabled");
        }
    }
}
