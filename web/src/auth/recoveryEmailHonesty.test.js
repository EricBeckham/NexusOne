import { describe, expect, it } from "vitest";
import { requestEmailOtp, EMAIL_MFA_CODES } from "./emailMfa.js";
import { issueRecoveryCodes, consumeRecoveryCode, RECOVERY_CODES } from "./recoveryCodes.js";
import {
  HONESTY_REASONS,
  HONESTY_RESULTS,
  describeRecoveryEmailHonesty,
  probeEmailDeliveryHonesty,
  probeRecoveryAuthnHonesty,
  probeRecoveryEmailHonesty,
  formatHonestyStatus,
} from "./recoveryEmailHonesty.js";

describe("G17 recovery / email delivery honesty beyond #154", () => {
  it("describes a leftover that does not close G17 or redo siblings", () => {
    const bridge = describeRecoveryEmailHonesty();
    expect(bridge.leftover).toBe("g17-mfa-recovery-email-honesty");
    expect(bridge.siblingContract).toBe("g17-admin-mfa-recovery-email");
    expect(bridge.vueWritesKeycloakOtp).toBe(false);
    expect(bridge.vueWritesRecoveryAuthnCodes).toBe(false);
    expect(bridge.smtpSendsMail).toBe(false);
    expect(bridge.mailboxObserved).toBe(false);
    expect(bridge.recoveryAuthnCodesEnrolled).toBe(false);
    expect(bridge.dengbaoClaimed).toBe(false);
    expect(bridge.mipinClaimed).toBe(false);
    expect(bridge.g17Close).toBe("NOT_CLAIMED");
    expect(bridge.g18).toBe("closed_unchanged");
    expect(bridge.sessionGate).toBe("#96");
    expect(bridge.httpDriverWalk).toBe("#118");
    expect(bridge.enrollmentUx).toBe("#133");
    expect(bridge.recoveryEmailContract).toBe("#154");
    expect(bridge.vueOtpWriteback).toBe("#158");
    expect(bridge.gmtlsHandshake).toBe("#160");
  });

  it("keeps the #154 local recovery-code contract (issue / consume / reuse)", async () => {
    const issued = issueRecoveryCodes({ random: () => 9 });
    expect(issued.ok).toBe(true);
    expect(issued.session.plaintext).toHaveLength(8);
    const first = await consumeRecoveryCode(issued.session, issued.session.plaintext[0]);
    expect(first.ok).toBe(true);
    const reuse = await consumeRecoveryCode(first.session, issued.session.plaintext[0]);
    expect(reuse.code).toBe(RECOVERY_CODES.RECOVERY_ALREADY_USED);
  });

  it("keeps the #154 email request SKIP when SMTP is off", () => {
    const skip = requestEmailOtp("admin-a@example.com");
    expect(skip.skip).toBe(true);
    expect(skip.code).toBe(EMAIL_MFA_CODES.SMTP_DISABLED);
  });

  it("SKIPs email delivery when SMTP is disabled", async () => {
    const skipped = await probeEmailDeliveryHonesty({
      smtpEnabled: false,
      smtpHost: "",
      issuers: ["http://127.0.0.1:65533/realms/nexus"],
      timeoutMs: 50,
      fetchImpl: async () => {
        throw new Error("ECONNREFUSED");
      },
    });
    expect(skipped.result).toBe(HONESTY_RESULTS.SKIP);
    expect(skipped.reason).toBe(HONESTY_REASONS.SMTP_DISABLED);
    expect(skipped.mailboxObserved).toBe(false);
  });

  it("SKIPs email delivery when SMTP is on but no mailbox was observed", async () => {
    const skipped = await probeEmailDeliveryHonesty({
      smtpEnabled: true,
      smtpHost: "127.0.0.1",
      mailboxObserved: false,
      issuers: ["http://127.0.0.1:8080/realms/nexus"],
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({ issuer: "http://127.0.0.1:8080/realms/nexus" }),
      }),
    });
    expect(skipped.result).toBe(HONESTY_RESULTS.SKIP);
    expect(skipped.reason).toBe(HONESTY_REASONS.MAILBOX_NOT_OBSERVED);
    expect(skipped.keycloakIssuerReachable).toBe(true);
    expect(skipped.mailboxObserved).toBe(false);
  });

  it("SKIPs live recovery-authn-codes when Keycloak is unreachable", async () => {
    const skipped = await probeRecoveryAuthnHonesty({
      issuers: ["http://127.0.0.1:65533/realms/nexus"],
      timeoutMs: 50,
      fetchImpl: async () => {
        throw new Error("ECONNREFUSED");
      },
    });
    expect(skipped.result).toBe(HONESTY_RESULTS.SKIP);
    expect(skipped.reason).toBe(HONESTY_REASONS.KEYCLOAK_ISSUER_UNREACHABLE);
    expect(skipped.recoveryAuthnCodesEnrolled).toBe(false);
    expect(skipped.recoveryCodeConsumedLive).toBe(false);
  });

  it("SKIPs when Keycloak answers but recovery-authn-codes were not enrolled", async () => {
    const skipped = await probeRecoveryAuthnHonesty({
      issuers: ["http://127.0.0.1:8080/realms/nexus"],
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({ issuer: "http://127.0.0.1:8080/realms/nexus" }),
      }),
    });
    expect(skipped.result).toBe(HONESTY_RESULTS.SKIP);
    expect(skipped.reason).toBe(HONESTY_REASONS.RECOVERY_AUTHN_CODES_NOT_ENROLLED);
    expect(skipped.keycloakIssuerReachable).toBe(true);
  });

  it("combined probe stays SKIP and does not claim G17", async () => {
    const probe = await probeRecoveryEmailHonesty({
      smtpEnabled: false,
      issuers: ["http://127.0.0.1:65533/realms/nexus"],
      timeoutMs: 50,
      fetchImpl: async () => {
        throw new Error("ECONNREFUSED");
      },
    });
    expect(probe.emailDelivery).toBe(HONESTY_RESULTS.SKIP);
    expect(probe.recoveryLive).toBe(HONESTY_RESULTS.SKIP);
    expect(probe.mailboxObserved).toBe(false);
    expect(probe.vueWritesKeycloakOtp).toBe(false);
    expect(probe.g17Close).toBe("NOT_CLAIMED");
    expect(probe.g18).toBe("closed_unchanged");
    expect(formatHonestyStatus(probe)).toContain("email_delivery=SKIP");
    expect(formatHonestyStatus(probe)).toContain("Not product G17");
  });
});
