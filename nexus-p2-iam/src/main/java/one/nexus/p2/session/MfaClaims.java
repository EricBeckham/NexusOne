package one.nexus.p2.session;

/**
 * Interprets OIDC {@code acr} / {@code amr} (and stub {@code mfa}) as
 * "this session already completed a second factor". Password-only
 * Keycloak tokens typically carry {@code acr=1} and do not satisfy.
 */
public final class MfaClaims {
    private MfaClaims() {}

    public static boolean satisfied(String acr, String amr) {
        if (acrSatisfied(acr)) {
            return true;
        }
        return amrSatisfied(amr);
    }

    static boolean acrSatisfied(String acr) {
        if (acr == null || acr.isBlank()) {
            return false;
        }
        String value = acr.trim().toLowerCase();
        return "mfa".equals(value)
                || "2".equals(value)
                || "aal2".equals(value)
                || "gold".equals(value)
                || "otp".equals(value)
                || value.contains("multi-factor");
    }

    static boolean amrSatisfied(String amr) {
        if (amr == null || amr.isBlank()) {
            return false;
        }
        for (String part : amr.toLowerCase().split("[,\\s\\[\\]]+")) {
            part = part.replace("\"", "").trim();
            if (part.isBlank()) {
                continue;
            }
            if ("otp".equals(part)
                    || "mfa".equals(part)
                    || "totp".equals(part)
                    || "hotp".equals(part)
                    || "hwk".equals(part)
                    || "sms".equals(part)
                    || "email".equals(part)
                    || "mca".equals(part)
                    || "recovery".equals(part)
                    || "recovery-authn-code".equals(part)
                    || "recovery-authn-codes".equals(part)) {
                return true;
            }
        }
        return false;
    }
}
