import { describe, expect, it } from "vitest";
import { confirmEnrollment, startEnrollment } from "./totpEnrollment.js";
import { generate } from "./totp.js";
import {
  WRITEBACK_REASONS,
  WRITEBACK_RESULTS,
  attemptVueOtpWriteback,
  describeOtpWritebackBridge,
  formatWritebackStatus,
  probeKeycloakOtpWriteback,
  productWritebackEndpointPresent,
} from "./totpWriteback.js";

const SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
const NOW = 59_000;

function enrolledSession() {
  const pending = startEnrollment({ secret: SECRET, now: NOW }).session;
  return confirmEnrollment(pending, generate(SECRET, NOW), { now: NOW }).session;
}

describe("Vue Keycloak otp write-back honesty bridge", () => {
  it("describes a leftover that does not write Keycloak and does not close G17", () => {
    const bridge = describeOtpWritebackBridge();
    expect(bridge.vueConfirmWritesKeycloak).toBe(false);
    expect(bridge.productWritebackEndpoint).toBe(false);
    expect(productWritebackEndpointPresent()).toBe(false);
    expect(bridge.recoveryCodes).toBe(false);
    expect(bridge.emailMfa).toBe(false);
    expect(bridge.dengbaoClaimed).toBe(false);
    expect(bridge.g17Close).toBe("NOT_CLAIMED");
    expect(bridge.g18).toBe("closed_unchanged");
    expect(bridge.httpDriverWalk).toBe("#118");
    expect(bridge.enrollmentUx).toBe("#133");
    expect(bridge.sessionGate).toBe("#96");
  });

  it("keeps Vue confirm local-only and SKIPs write-back without an enrolled session", async () => {
    const pending = startEnrollment({ secret: SECRET, now: NOW }).session;
    const skipped = await attemptVueOtpWriteback({
      session: pending,
      fetchImpl: async () => {
        throw new Error("should not fetch before enroll");
      },
    });
    expect(skipped.result).toBe(WRITEBACK_RESULTS.SKIP);
    expect(skipped.reason).toBe(WRITEBACK_REASONS.VUE_CONFIRM_LOCAL_ONLY);
    expect(skipped.vueConfirmWritesKeycloak).toBe(false);
    expect(skipped.otpCredentialStored).toBe(false);
    expect(formatWritebackStatus(skipped)).toContain("SKIP");
  });

  it("SKIPs honestly when Keycloak discovery is unreachable", async () => {
    const skipped = await attemptVueOtpWriteback({
      session: enrolledSession(),
      issuers: ["http://127.0.0.1:65533/realms/nexus"],
      timeoutMs: 50,
      fetchImpl: async () => {
        throw new Error("ECONNREFUSED");
      },
    });
    expect(skipped.result).toBe(WRITEBACK_RESULTS.SKIP);
    expect(skipped.reason).toBe(WRITEBACK_REASONS.KEYCLOAK_ISSUER_UNREACHABLE);
    expect(skipped.keycloakIssuerReachable).toBe(false);
    expect(skipped.otpCredentialStored).toBe(false);
  });

  it("SKIPs when live Keycloak answers but the Vue write-back endpoint is absent", async () => {
    const skipped = await attemptVueOtpWriteback({
      session: enrolledSession(),
      issuers: ["http://127.0.0.1:8080/realms/nexus"],
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({
          issuer: "http://127.0.0.1:8080/realms/nexus",
          authorization_endpoint: "http://127.0.0.1:8080/realms/nexus/protocol/openid-connect/auth",
        }),
      }),
    });
    expect(skipped.result).toBe(WRITEBACK_RESULTS.SKIP);
    expect(skipped.reason).toBe(WRITEBACK_REASONS.PRODUCT_ENDPOINT_ABSENT);
    expect(skipped.keycloakIssuerReachable).toBe(true);
    expect(skipped.oidcDiscovery).toBe("PASS");
    expect(skipped.vueConfirmWritesKeycloak).toBe(false);
  });

  it("refuses PASS unless a stored otp credential is proven", async () => {
    const failed = await attemptVueOtpWriteback({
      session: enrolledSession(),
      issuers: ["http://127.0.0.1:8080/realms/nexus"],
      productWritebackEndpoint: true,
      optIn: true,
      otpCredentialStored: false,
      fetchImpl: async () => ({
        ok: true,
        json: async () => ({ issuer: "http://127.0.0.1:8080/realms/nexus" }),
      }),
    });
    expect(failed.result).toBe(WRITEBACK_RESULTS.FAIL);
    expect(failed.reason).toBe(WRITEBACK_REASONS.OTP_CREDENTIAL_NOT_STORED);
    expect(failed.vueConfirmWritesKeycloak).toBe(false);
  });

  it("probes missing fetch as unreachable SKIP", async () => {
    const probe = await probeKeycloakOtpWriteback({
      issuers: ["http://127.0.0.1:8080/realms/nexus"],
      fetchImpl: undefined,
      env: {},
    });
    expect(["SKIP", "FAIL"]).toContain(probe.discovery);
    expect(probe.reachable).toBe(false);
  });
});
