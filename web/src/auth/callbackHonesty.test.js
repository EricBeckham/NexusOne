/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { callbackFailureCopy, sessionHasTenantContext } from "./callbackHonesty";

describe("callback honesty copy", () => {
  it("classifies callback failures without echoing token fragments", () => {
    const denied = callbackFailureCopy({
      queryError: "access_denied",
      message: "error_description=Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig",
    });
    expect(denied.description).toContain("授权被拒绝");
    expect(denied.description).toContain("/callback");
    expect(JSON.stringify(denied)).not.toMatch(/eyJ|Bearer/);

    const mismatch = callbackFailureCopy({ message: "state mismatch access_token=secret" });
    expect(mismatch.description).toContain("state 不一致");
    expect(mismatch.description).not.toContain("secret");
  });

  it("treats a missing context id as absent", () => {
    expect(sessionHasTenantContext({ display_name: "A" })).toBe(false);
    expect(sessionHasTenantContext({ context_id: "ctx-1" })).toBe(true);
  });
});
