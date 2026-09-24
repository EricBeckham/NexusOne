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

  it("keeps P1 and P2 on their own menus", () => {
    const p1 = navFor("p1").map((item) => item.label);
    expect(p1).toEqual(["首页", "租户列表", "新建租户", "租户详情", "租户关系", "配额", "平台服务态", "平台审计"]);
    expect(navFor("p1").filter((item) => item.later).map((item) => item.label)).toEqual([
      "租户关系",
      "配额",
      "平台服务态",
      "平台审计",
    ]);
    const p2Labels = navFor("p2").map((item) => item.label);
    expect(p2Labels).toEqual(["首页", "用户", "组织架构", "岗位任职", "权限", "身份安全"]);
    const p2Links = collectLinks(navFor("p2"));
    expect(p2Links).toEqual(expect.arrayContaining([
      "/p2/persons",
      "/p2/org",
      "/p2/positions",
      "/p2/permissions",
    ]));
    expect(collectLinks(navFor("p1")).some((link) => link.startsWith("/p2"))).toBe(false);
    expect(p2Links.some((link) => link.startsWith("/p1"))).toBe(false);
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
