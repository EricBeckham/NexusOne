import { describe, expect, it } from "vitest";
import { generate } from "./totp.js";
import {
  ENROLL_CODES,
  ENROLL_TTL_MS,
  MFA_REQUIRED_COPY,
  confirmEnrollment,
  formatEnrollmentError,
  isMfaRequiredError,
  startEnrollment,
} from "./totpEnrollment.js";

const SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
const NOW = 59_000;

describe("TOTP enrollment copy and error paths", () => {
  it("starts a pending session with grouped secret and otpauth", () => {
    const started = startEnrollment({
      account: "admin-a@example.com",
      secret: SECRET,
      now: NOW,
    });
    expect(started.ok).toBe(true);
    expect(started.session.status).toBe("pending");
    expect(started.session.secretGrouped).toContain("GEZD");
    expect(started.session.otpauth).toContain("otpauth://totp/");
  });

  it("rejects blank, malformed, wrong, stale, and already-enrolled codes", () => {
    const pending = startEnrollment({ secret: SECRET, now: NOW }).session;
    expect(confirmEnrollment(null, "287082").code).toBe(ENROLL_CODES.TOTP_NO_PENDING);
    expect(confirmEnrollment(pending, "").code).toBe(ENROLL_CODES.TOTP_CODE_BLANK);
    expect(confirmEnrollment(pending, "12ab").code).toBe(ENROLL_CODES.TOTP_CODE_FORMAT);
    expect(confirmEnrollment(pending, "000000", { now: NOW }).code).toBe(
      ENROLL_CODES.TOTP_VERIFY_FAILED,
    );
    expect(confirmEnrollment(pending, "287082", { now: NOW + ENROLL_TTL_MS + 1 }).code).toBe(
      ENROLL_CODES.TOTP_STALE,
    );
    const enrolled = confirmEnrollment(pending, generate(SECRET, NOW), { now: NOW });
    expect(enrolled.ok).toBe(true);
    expect(confirmEnrollment(enrolled.session, generate(SECRET, NOW), { now: NOW }).code).toBe(
      ENROLL_CODES.TOTP_ALREADY_ENROLLED,
    );
  });

  it("keeps MFA_REQUIRED copy honest and maps codes to Chinese paths", () => {
    expect(isMfaRequiredError({ code: "P2.IAM.MFA_REQUIRED" })).toBe(true);
    expect(MFA_REQUIRED_COPY.body).toContain("P2.IAM.MFA_REQUIRED");
    expect(MFA_REQUIRED_COPY.body).toContain("不重开 G18");
    expect(formatEnrollmentError(ENROLL_CODES.TOTP_VERIFY_FAILED)).toContain("过期");
    expect(formatEnrollmentError(ENROLL_CODES.TOTP_ALREADY_ENROLLED)).toContain("Keycloak");
  });
});
