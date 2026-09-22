import { describe, expect, it } from "vitest";
import { EMAIL_MFA_CODES, EMAIL_MFA_COPY, requestEmailOtp } from "./emailMfa";

describe("email MFA leftover", () => {
  it("skips when SMTP is disabled", () => {
    const result = requestEmailOtp("admin-a@example.com");
    expect(result.skip).toBe(true);
    expect(result.code).toBe(EMAIL_MFA_CODES.SMTP_DISABLED);
    expect(result.message).toContain("诚实 SKIP");
    expect(EMAIL_MFA_COPY.hint).toContain("不重开 G18");
    expect(EMAIL_MFA_COPY.body).toContain("诚实 SKIP");
  });

  it("skips when SMTP is on but delivery cannot run", () => {
    const result = requestEmailOtp("admin-a@example.com", {
      smtpEnabled: true,
      smtpHost: "",
      keycloakReachable: false,
    });
    expect(result.skip).toBe(true);
    expect(result.code).toBe(EMAIL_MFA_CODES.EMAIL_OTP_NOT_DELIVERED);
  });

  it("rejects blank and bad email without claiming delivery", () => {
    expect(requestEmailOtp("").code).toBe(EMAIL_MFA_CODES.EMAIL_BLANK);
    expect(requestEmailOtp("not-an-email").code).toBe(EMAIL_MFA_CODES.EMAIL_FORMAT);
    expect(requestEmailOtp("").skip).toBe(false);
  });
});
