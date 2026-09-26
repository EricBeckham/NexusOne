/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p4 } from "./client";

// XB14: the subscription table's read of the projection summary. Wire shape pinned by
// CommercialEntitlementWireNamingTest: snake_case, and unknown `projected` is an absent key.
describe("commercial entitlement summary HTTP", () => {
  const rows = [
    { subscription_id: "sub-1", sku_code: "TEAM", subscription_status: "ACTIVE", lines: [], summary: { enabled_count: 1, total_count: 1, projected: true, projection_version: 2, projected_at: "2026-09-26T00:00:00Z" } },
    { subscription_id: "sub-2", sku_code: "TEAM", subscription_status: "ACTIVE", lines: [], summary: { enabled_count: 0, total_count: 2 } },
  ];

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: true, status: 200, text: async () => JSON.stringify(rows) })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("lists with GET on the M20 path without a client tenant header and passes the summary through", async () => {
    expect(await p4.commercialEntitlements()).toEqual(rows);
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/p4/commercial-entitlements");
    expect(init.method).toBe("GET");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
  });

  it("keeps an absent projected as unknown and carries no money fields", async () => {
    const [, unknown] = await p4.commercialEntitlements();
    expect(unknown.summary).not.toHaveProperty("projected");
    expect(unknown.summary.projected ?? null).toBeNull();
    const keys = JSON.stringify(rows).toLowerCase();
    for (const money of ["price", "amount", "invoice", "coupon", "billing", "payment"]) expect(keys).not.toContain(money);
  });
});