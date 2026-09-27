import { describe, expect, it } from "vitest";
import { UNNAMED_TENANT, currentTenantLabel, tenantLabel } from "./tenantLabel";

const ACME = "01900000-0000-7000-8000-000000000101";

describe("DEMO-U6 tenant labels", () => {
  it("prefers the name, then the code", () => {
    expect(tenantLabel({ tenant_id: ACME, tenant_code: "demo_acme", tenant_name: "演示租户 · 星河科技" })).toBe("演示租户 · 星河科技");
    expect(tenantLabel({ tenant_id: ACME, tenant_code: "demo_acme" })).toBe("demo_acme");
    expect(tenantLabel({ tenantId: ACME, tenantName: " 星河 " })).toBe("星河");
  });

  it("never falls back to the UUID", () => {
    expect(tenantLabel({ tenant_id: ACME })).toBe(UNNAMED_TENANT);
    expect(tenantLabel({ tenant_id: ACME, tenant_name: "  " })).not.toContain(ACME);
  });

  it("labels the current tenant from the memberships", () => {
    const session = { tenantId: ACME, memberships: [{ tenant_id: "other", tenant_name: "平台" }, { tenant_id: ACME, tenant_code: "demo_acme" }] };
    expect(currentTenantLabel(session)).toBe("demo_acme");
    expect(currentTenantLabel({ tenantId: "", memberships: [] })).toBe("暂无");
    expect(currentTenantLabel({ tenantId: ACME, memberships: [] })).toBe(UNNAMED_TENANT);
  });
});
