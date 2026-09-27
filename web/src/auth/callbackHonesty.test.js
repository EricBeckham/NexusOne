/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { callbackFailureCopy, sessionHasTenantContext, tenantContextMissingCopy } from "./callbackHonesty";

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

describe("TC3 tenant context missing copy", () => {
  const JARGON = /leftover|PKCE|code_verifier|TenantContext|context/i;

  it("no ACTIVE membership: back to login or ask an admin, no retry", () => {
    for (const memberships of [undefined, [], [{ tenant_id: "t1", status: "SUSPENDED" }]]) {
      const copy = tenantContextMissingCopy(memberships);
      expect(copy.kind).toBe("no_membership");
      expect(copy.title).toBe("还没有可进入的租户");
      expect(copy.description).toContain("联系管理员");
      expect(copy.description).toContain("登录页");
      expect(copy.canRetry).toBe(false);
      expect(copy.canSwitchManually).toBe(false);
      expect(`${copy.title}${copy.description}`).not.toMatch(JARGON);
    }
  });

  it("memberships but entering failed: retry, and manual switch when there is a choice", () => {
    const several = tenantContextMissingCopy([
      { tenant_id: "a", status: "ACTIVE" },
      { tenant_id: "b", status: "active" },
      { tenant_id: "c", status: "SUSPENDED" },
    ]);
    expect(several.kind).toBe("auto_enter_failed");
    expect(several.title).toBe("未能自动进入租户");
    expect(several.description).toContain("2 个可用租户");
    expect(several.description).toContain("右上角");
    expect(several.canRetry).toBe(true);
    expect(several.canSwitchManually).toBe(true);
    expect(`${several.title}${several.description}`).not.toMatch(JARGON);

    const single = tenantContextMissingCopy([{ tenant_id: "a", status: "ACTIVE" }]);
    expect(single.kind).toBe("auto_enter_failed");
    expect(single.canRetry).toBe(true);
    expect(single.canSwitchManually).toBe(false);
    expect(single.description).not.toContain("右上角");
  });
});