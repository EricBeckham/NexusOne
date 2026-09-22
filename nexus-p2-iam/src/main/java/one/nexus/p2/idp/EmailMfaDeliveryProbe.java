package one.nexus.p2.idp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Honest leftover probe for email MFA / recovery-code <em>delivery</em>.
 * Product SMTP stays invite / password-reset only
 * ({@code nexus.iam.smtp.enabled}, Keycloak {@code execute-actions-email}).
 * This is not an email OTP provider and does not send mail.
 */
public final class EmailMfaDeliveryProbe {
    public static final String SMTP_DISABLED = "P2.IAM.SMTP_DISABLED";
    public static final String EMAIL_OTP_NOT_DELIVERED = "P2.IAM.EMAIL_OTP_NOT_DELIVERED";
    public static final String RECOVERY_NOT_DELIVERED = "P2.IAM.RECOVERY_CODES_NOT_DELIVERED";

    private EmailMfaDeliveryProbe() {}

    public record Result(
            String emailDelivery,
            String recoveryDelivery,
            String skipReason,
            boolean smtpEnabled,
            boolean keycloakReachable,
            boolean smtpHostConfigured,
            String issuer
    ) {}

    public static Result probe(boolean smtpEnabled) {
        return probe(smtpEnabled, firstReachableIssuer(), smtpHostFromEnv());
    }

    public static Result probe(boolean smtpEnabled, String issuer, String smtpHost) {
        boolean hostConfigured = smtpHost != null && !smtpHost.isBlank();
        boolean reachable = issuer != null && !issuer.isBlank() && discoveryOk(issuer);
        if (!smtpEnabled) {
            return new Result(
                    "SKIP",
                    "SKIP",
                    SMTP_DISABLED + ": nexus.iam.smtp.enabled=false; Keycloak execute-actions-email and email OTP are not delivered on this leftover",
                    false,
                    reachable,
                    hostConfigured,
                    issuer == null ? "" : issuer);
        }
        if (!hostConfigured) {
            return new Result(
                    "SKIP",
                    "SKIP",
                    EMAIL_OTP_NOT_DELIVERED + ": SMTP enabled in config but no SMTP host is configured",
                    true,
                    reachable,
                    false,
                    issuer == null ? "" : issuer);
        }
        if (!reachable) {
            return new Result(
                    "SKIP",
                    "SKIP",
                    EMAIL_OTP_NOT_DELIVERED + ": SMTP host is set but Keycloak OIDC discovery is not reachable",
                    true,
                    false,
                    true,
                    issuer == null ? "" : issuer);
        }
        return new Result(
                "SKIP",
                "SKIP",
                RECOVERY_NOT_DELIVERED + ": leftover does not send email OTP or issue Keycloak recovery-authn-codes; live delivery stays unproven",
                true,
                true,
                true,
                issuer);
    }

    static String firstReachableIssuer() {
        String fromEnv = System.getenv("NEXUS_KEYCLOAK_ISSUER");
        if (fromEnv != null && !fromEnv.isBlank() && discoveryOk(fromEnv)) {
            return fromEnv;
        }
        for (String candidate : new String[] {
                "http://127.0.0.1:8080/realms/nexus",
                "http://127.0.0.1:8180/realms/nexus"
        }) {
            if (discoveryOk(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    static String smtpHostFromEnv() {
        for (String name : new String[] {
                "NEXUS_IAM_SMTP_HOST",
                "NEXUS_KEYCLOAK_SMTP_HOST",
                "SMTP_HOST"
        }) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    static boolean discoveryOk(String issuer) {
        Objects.requireNonNull(issuer, "issuer");
        if (issuer.isBlank()) {
            return false;
        }
        String url = issuer + "/.well-known/openid-configuration";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && response.body() != null
                    && response.body().toLowerCase(Locale.ROOT).contains("\"issuer\"");
        } catch (Exception ignored) {
            return false;
        }
    }
}
