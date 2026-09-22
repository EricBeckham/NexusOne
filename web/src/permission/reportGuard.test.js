import { describe, expect, it } from "vitest";
import { REPORT_SURFACES, denyCode } from "./reportCatalog.js";
import { decide, formatDeny, requireAllowed } from "./reportGuard.js";

describe("Report SurfaceGuard", () => {
  it("maps desktop deny reasons to P2.PERM codes", () => {
    expect(denyCode("no_grant")).toBe("P2.PERM.0003");
    expect(denyCode("explicit_deny")).toBe("P2.PERM.0002");
    expect(denyCode("tenant_boundary")).toBe("P2.PERM.0001");
    expect(denyCode("unknown")).toBe("P2.PERM.0003");
  });

  it("requireAllowed denies !allowed with the same codes as desktop", () => {
    for (const reason of ["no_grant", "explicit_deny", "tenant_boundary"]) {
      try {
        requireAllowed({ allowed: false, reason, engine: "PermissionEngine" });
        throw new Error("expected deny");
      } catch (err) {
        expect(err.status).toBe(403);
        expect(err.code).toBe(denyCode(reason));
        expect(err.message).toBe(reason);
        expect(err.body.engine).toBe("PermissionEngine");
      }
    }
  });

  it("null decision is no_grant", () => {
    try {
      requireAllowed(null);
      throw new Error("expected deny");
    } catch (err) {
      expect(err.code).toBe("P2.PERM.0003");
      expect(err.message).toBe("no_grant");
    }
  });

  it("decide calls PermissionService.check and keeps engine", async () => {
    const check = async ({ resource, action }) => {
      expect(resource).toBe("ledger");
      expect(action).toBe("read");
      return { allowed: true, reason: "allowed", engine: "PermissionEngine", policy_version: 3 };
    };
    const decision = await decide(check, REPORT_SURFACES.query.resource, REPORT_SURFACES.query.action);
    expect(decision.allowed).toBe(true);
    expect(decision.engine).toBe("PermissionEngine");
    expect(decision.resource).toBe("ledger");
  });

  it("report cells match desktop list and AuthorizedExport pairs", () => {
    expect(REPORT_SURFACES.query).toEqual({ resource: "ledger", action: "read" });
    expect(REPORT_SURFACES.export).toEqual({ resource: "ledger.export.csv", action: "submit" });
  });

  it("formatDeny prints code plus reason", () => {
    expect(formatDeny({ code: "P2.PERM.0003", message: "no_grant" })).toBe("P2.PERM.0003 no_grant");
  });
});
