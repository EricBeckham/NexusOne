/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ employments: vi.fn(), positions: vi.fn(), switchTenant: vi.fn() }));
vi.mock("./client", async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    p2: { ...original.p2, employments: mocks.employments, positions: mocks.positions, switchTenant: mocks.switchTenant },
  };
});

import { clearIdentity, loadPrimaryPosition, session, switchTenant } from "./session";

const TENANT = "01900000-0000-7000-8000-000000000101";
const POSITIONS = [
  { id: "pos-manager", code: "engineering_manager", name: "研发经理" },
  { id: "pos-admin", code: "TENANT_ADMIN", name: "租户管理员" },
];

function signedIn() {
  Object.assign(session, {
    accessToken: "token",
    contextId: "ctx-1",
    tenantId: TENANT,
    personId: "person-lin",
    positionName: "",
  });
}

describe("AUTH4 primary position", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    localStorage.clear();
    signedIn();
    mocks.positions.mockResolvedValue(POSITIONS);
  });

  it("uses the PRIMARY active employment of the current tenant, not a secondary admin post", async () => {
    mocks.employments.mockResolvedValue([
      { id: "e2", tenant_id: TENANT, person_id: "person-lin", position_id: "pos-admin", kind: "SECONDARY", status: "ACTIVE" },
      { id: "e1", tenant_id: TENANT, person_id: "person-lin", position_id: "pos-manager", kind: "PRIMARY", status: "ACTIVE" },
    ]);
    expect(await loadPrimaryPosition()).toBe("研发经理");
    expect(mocks.employments).toHaveBeenCalledWith("person-lin");
    expect(session.positionName).toBe("研发经理");
  });

  it("stays empty (暂无) without a primary post, an ended one, another tenant's, or when the lookup fails", async () => {
    mocks.employments.mockResolvedValue([{ tenant_id: TENANT, position_id: "pos-admin", kind: "SECONDARY", status: "ACTIVE" }]);
    expect(await loadPrimaryPosition()).toBe("");
    mocks.employments.mockResolvedValue([{ tenant_id: TENANT, position_id: "pos-manager", kind: "PRIMARY", status: "ENDED" }]);
    expect(await loadPrimaryPosition()).toBe("");
    mocks.employments.mockResolvedValue([{ tenant_id: "other", position_id: "pos-manager", kind: "PRIMARY", status: "ACTIVE" }]);
    expect(await loadPrimaryPosition()).toBe("");
    mocks.employments.mockRejectedValue(Object.assign(new Error("forbidden"), { status: 403 }));
    expect(await loadPrimaryPosition()).toBe("");
    expect(session.positionName).toBe("");
  });

  it("does not ask the server without a tenant context", async () => {
    session.contextId = "";
    expect(await loadPrimaryPosition()).toBe("");
    expect(mocks.employments).not.toHaveBeenCalled();
  });

  it("resets on tenant switch and on logout", async () => {
    session.positionName = "研发经理";
    mocks.switchTenant.mockResolvedValue({ context_id: "ctx-2", tenant_id: "tenant-b", person_id: "person-b", memberships: [] });
    mocks.employments.mockResolvedValue([]);
    await switchTenant("tenant-b");
    expect(session.positionName).toBe("");

    session.positionName = "研发经理";
    clearIdentity();
    expect(session.positionName).toBe("");
  });
});
