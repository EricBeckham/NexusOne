import { describe, expect, it } from "vitest";
import { PRODUCTS } from "./products";
import { NAV_BY_PRODUCT, collectLinks, menuDepth, navFor } from "./index";

describe("product shell menus", () => {
  it("lists five products with homes", () => {
    expect(PRODUCTS.map((item) => item.id)).toEqual(["p1", "p2", "p3", "p4", "p5"]);
    for (const product of PRODUCTS) {
      expect(product.name).toBeTruthy();
      expect(product.en).toBeTruthy();
      expect(product.home).toBe(`/${product.id}`);
    }
  });

  it("keeps each sidebar within two levels and eleven top items", () => {
    for (const [id, items] of Object.entries(NAV_BY_PRODUCT)) {
      expect(items.length, id).toBeLessThanOrEqual(11);
      expect(menuDepth(items), id).toBeLessThanOrEqual(2);
      expect(navFor(id)).toBe(items);
    }
  });

  it("keeps existing notification, audit, and MFA links", () => {
    const links = collectLinks([...navFor("p2"), ...navFor("p3")]);
    expect(links).toEqual(expect.arrayContaining([
      "/p3/notifications",
      "/p3/audit",
      "/p2/mfa/enroll",
      "/p2/mfa/recovery",
    ]));
  });

  it("keeps the P3 sidebar scoped to Studio and marks deferred surfaces", () => {
    const items = navFor("p3");
    const links = collectLinks(items);

    expect(links).toEqual([
      "/p3",
      "/p3/ledgers",
      "/p3/forms",
      "/p3/todos",
      "/p3/reports",
      "/p3/dumps",
      "/p3/exports/wizard",
      "/p3/notifications",
      "/p3/audit",
    ]);
    expect(links.every((link) => link === "/p3" || link.startsWith("/p3/"))).toBe(true);
    expect(items.filter((item) => !item.to).map((item) => item.label)).toEqual([
      "流程设计器（后续）",
      "发布中心（后续）",
    ]);
  });
});
