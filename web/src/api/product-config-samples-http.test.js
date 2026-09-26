/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p4 } from "./client";

// PC14: wizard readiness (PC6/PC7) and the three product samples (PC8–PC13). Wire shape is pinned by
// ProductConfigSamplesWireNamingTest; here: paths, GET, platform demo headers, no tenant header.
describe("product config readiness and samples HTTP", () => {
  let body;
  beforeEach(() => {
    body = {};
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: true, status: 200, text: async () => JSON.stringify(body) })));
  });
  afterEach(() => vi.unstubAllGlobals());

  const cases = [
    ["productConfigReadiness", "config-readiness"],
    ["productLedgerSample", "samples/ledger"],
    ["productPrintSample", "samples/print"],
    ["productReportSample", "samples/report"],
  ];

  for (const [method, suffix] of cases) {
    it(`${method} GETs /product-definitions/{id}/${suffix} as a platform read`, async () => {
      body = { product_definition_id: "a b" };
      expect(await p4[method]("a b")).toEqual(body);
      const [path, init] = fetch.mock.calls[0];
      expect(path).toBe(`/api/v1/p4/product-definitions/a%20b/${suffix}`);
      expect(init.method).toBe("GET");
      expect(init.headers["X-Tenant-Id"]).toBeUndefined();
      expect(init.headers["X-Nexus-Demo-Actor-Id"]).toBeTruthy();
    });
  }

  it("passes an uncountable metric through without a value so the page can say 暂无", async () => {
    body = { metrics: [{ key: "installed_tenants", label: "已开通租户", note: "暂时无法统计。" }], trend_available: false };
    const report = await p4.productReportSample("d1");
    expect(report.metrics[0]).not.toHaveProperty("value");
    expect(report.trend_available).toBe(false);
    expect(report).not.toHaveProperty("series");
  });
});