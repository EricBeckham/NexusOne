import { describe, expect, it } from "vitest";
import { H5_SURFACES, denyCode } from "./surfaceCatalog";
import { decide, formatDeny, requireAllowed } from "./surfaceGuard";

describe("H5 SurfaceGuard", () => {
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
    const decision = await decide(check, H5_SURFACES.listRead.resource, H5_SURFACES.listRead.action);
    expect(decision.allowed).toBe(true);
    expect(decision.engine).toBe("PermissionEngine");
    expect(decision.resource).toBe("ledger");
  });

  it("H5 cells match desktop SurfaceGuard pairs", () => {
    expect(H5_SURFACES.listRead).toEqual({ resource: "ledger", action: "read" });
    expect(H5_SURFACES.formRead).toEqual({ resource: "form", action: "read" });
    expect(H5_SURFACES.formWrite).toEqual({ resource: "form", action: "write" });
    expect(H5_SURFACES.todoRead).toEqual({ resource: "process.task", action: "read" });
    expect(H5_SURFACES.todoApprove).toEqual({ resource: "process.task", action: "approve" });
    expect(H5_SURFACES.todoReject).toEqual({ resource: "process.task", action: "reject" });
    expect(H5_SURFACES.todoWithdraw).toEqual({ resource: "process.instance", action: "withdraw" });
  });

  it("formatDeny prints code plus reason", () => {
    expect(formatDeny({ code: "P2.PERM.0003", message: "no_grant" })).toBe("P2.PERM.0003 no_grant");
  });
});
