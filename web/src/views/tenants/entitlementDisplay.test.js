import { describe, expect, it } from "vitest";
import { entitlementItems, entitlementSummary, quotaRows, show, usagePercent } from "./entitlementDisplay";

describe("entitlement display", () => {
  it("reads only fields the entitlement payload actually has", () => {
    const summary = entitlementSummary({
      source: "DEFAULT_PACK",
      pack_code: "default",
      pack_version: "1",
      payload: {
        schema: "p4.runtime-entitlement.v1",
        entitlements: { apps: { enabled: true, quota: 3 } },
      },
    });
    expect(summary.source).toBe("DEFAULT_PACK");
    expect(summary.packCode).toBe("default");
    expect(summary.skuCode).toBeNull();
    expect(entitlementItems({
      payload: { entitlements: { apps: { enabled: true, quota: 3 }, extra: {} } },
    })).toEqual([
      { key: "apps", enabled: true, quota: 3 },
      { key: "extra", enabled: null, quota: null },
    ]);
  });

  it("does not turn a missing list into zero rows of invented quotas", () => {
    expect(quotaRows(null)).toBeNull();
    expect(quotaRows({ items: [] })).toBeNull();
    expect(quotaRows([])).toEqual([]);
    expect(quotaRows([{ metric: "APPS", used: 0, limit: 5, warning: false }])).toEqual([
      { metric: "APPS", used: 0, limit: 5, warnPercent: null, policy: null, warning: false },
    ]);
  });

  it("prints 暂无 for missing values and keeps a real zero", () => {
    expect(show(null)).toBe("暂无");
    expect(show(0)).toBe("0");
  });

  it("derives a progress percent only when both used and limit are real", () => {
    expect(usagePercent(1, 3)).toBe(33);
    expect(usagePercent(0, 5)).toBe(0);
    expect(usagePercent(8, 3)).toBe(100);
    expect(usagePercent(null, 3)).toBeNull();
    expect(usagePercent(1, 0)).toBeNull();
  });
});
