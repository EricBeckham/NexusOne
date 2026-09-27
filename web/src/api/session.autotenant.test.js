/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ login: vi.fn(), switchTenant: vi.fn(), employments: vi.fn(), positions: vi.fn() }));
vi.mock("./client", async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, p2: { ...original.p2, ...mocks } };
});

import { DEMO_BUSINESS_TENANT_ID, clearIdentity, loginWithAccessToken, pickAutoTenant, session } from "./session";

const OTHER = "01900000-0000-7000-8000-000000000001";
const THIRD = "01900000-0000-7000-8000-000000000301";
const m = (tenant, status = "ACTIVE") => ({ tenant_id: tenant, person_id: `p-${tenant}`, status });

describe("AUTH5 pickAutoTenant", () => {
  it("prefers demo_acme among several ACTIVE memberships", () => {
    expect(pickAutoTenant([m(OTHER), m(DEMO_BUSINESS_TENANT_ID)])).toBe(DEMO_BUSINESS_TENANT_ID);
  });

  it("falls back to the first ACTIVE membership and ignores inactive ones", () => {
    expect(pickAutoTenant([m(DEMO_BUSINESS_TENANT_ID, "SUSPENDED"), m(OTHER), m(THIRD)])).toBe(OTHER);
  });

  it("does nothing for fewer than two ACTIVE memberships", () => {
    expect(pickAutoTenant([m(OTHER)])).toBeNull();
    expect(pickAutoTenant([m(OTHER), m(THIRD, "SUSPENDED")])).toBeNull();
    expect(pickAutoTenant([])).toBeNull();
    expect(pickAutoTenant(undefined)).toBeNull();
  });
});

describe("AUTH5 login without a context", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    localStorage.clear();
    clearIdentity();
    session.error = "";
    mocks.employments.mockResolvedValue([]);
    mocks.positions.mockResolvedValue([]);
  });

  const noContext = (memberships) => ({
    account_id: "acc-lin",
    display_name: "林晓",
    context_id: null,
    tenant_id: null,
    person_id: null,
    memberships,
    access_token: "bearer-token",
  });

  it("switches into demo_acme with the Bearer token and keeps the manual list", async () => {
    const memberships = [m(OTHER), m(DEMO_BUSINESS_TENANT_ID)];
    mocks.login.mockResolvedValue(noContext(memberships));
    mocks.switchTenant.mockResolvedValue({ account_id: "acc-lin", display_name: "林晓", context_id: "ctx-demo",
      tenant_id: DEMO_BUSINESS_TENANT_ID, person_id: "p-demo", memberships });
    await loginWithAccessToken("bearer-token");
    expect(mocks.switchTenant).toHaveBeenCalledWith(DEMO_BUSINESS_TENANT_ID);
    expect(session.accessToken).toBe("bearer-token");
    expect(session.contextId).toBe("ctx-demo");
    expect(session.tenantId).toBe(DEMO_BUSINESS_TENANT_ID);
    expect(session.memberships).toHaveLength(2);
  });

  it("does not switch when the server already issued a context (single membership)", async () => {
    mocks.login.mockResolvedValue({ ...noContext([m(OTHER)]), context_id: "ctx-1", tenant_id: OTHER, person_id: "p" });
    await loginWithAccessToken("bearer-token");
    expect(mocks.switchTenant).not.toHaveBeenCalled();
    expect(session.contextId).toBe("ctx-1");
  });

  it("stays signed in without a context and says why when the switch fails", async () => {
    mocks.login.mockResolvedValue(noContext([m(OTHER), m(THIRD)]));
    mocks.switchTenant.mockRejectedValue(new Error("tenant membership is not active"));
    await loginWithAccessToken("bearer-token");
    expect(mocks.switchTenant).toHaveBeenCalledWith(OTHER);
    expect(session.accessToken).toBe("bearer-token");
    expect(session.contextId).toBe("");
    expect(session.error).toContain("tenant membership is not active");
  });
});
