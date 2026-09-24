import { describe, expect, it } from "vitest";
import { catalogEntry, catalogRows } from "./catalogDisplay";

describe("self-use API catalog display", () => {
  it("reads snake_case metadata and treats a missing owner as shared", () => {
    const row = catalogEntry({
      id: "api-1",
      name: "列出租户",
      http_method: "get",
      path: "/api/v1/p1/tenants",
      version: "v1",
      scope: "tenant:read",
      status: "ACTIVE",
      updated_at: "2026-09-24T08:00:00Z",
    });
    expect(row.httpMethod).toBe("GET");
    expect(row.shared).toBe(true);
    expect(row.path).toBe("/api/v1/p1/tenants");
    expect(catalogEntry({ name: "no-id" })).toBeNull();
  });

  it("marks an owned row and rejects a non-list payload", () => {
    const rows = catalogRows([
      { id: "own", tenant_id: "tenant-a", name: "本租户", http_method: "POST", path: "/api/v1/p5/clients" },
    ]);
    expect(rows[0].shared).toBe(false);
    expect(catalogRows({ items: [] })).toBeNull();
  });
});
