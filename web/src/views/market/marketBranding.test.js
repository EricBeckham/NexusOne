import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// M3: one market brand on the demo path — eyebrow "P4 · 星市"; tab/breadcrumb titles never repeat the product name.
const here = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(here, path), "utf8");

describe("星市 branding", () => {
  it("uses the P4 · 星市 eyebrow on every tenant-facing market page", () => {
    for (const view of ["MarketHomeView.vue", "MarketView.vue", "P4ManagementView.vue", "ProductStartView.vue", "CommercialEntitlementsView.vue"]) {
      const source = read(view);
      expect(source, view).toContain('eyebrow="P4 · 星市"');
      expect(source, view).not.toContain("NEXUS MARKET");
    }
    expect(read("ProductDefinitionBindingView.vue")).toContain('eyebrow="P4 · 平台"');
  });

  it("never titles a desktop P4 route with the product name itself (no 星市 · 星市 tab or breadcrumb)", () => {
    const routes = read("../../router.js").split("\n").filter((line) => line.includes('path: "/p4'));
    expect(routes.length).toBeGreaterThan(5);
    for (const line of routes) expect(line, line).not.toContain('title: "星市"');
    expect(routes.find((line) => line.includes('path: "/p4/market"'))).toContain('title: "应用目录"');
  });
});
