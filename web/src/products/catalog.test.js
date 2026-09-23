import { describe, expect, it } from "vitest";
import { productById, products } from "./catalog";

describe("T1-L3 product catalog", () => {
  it("exposes one stable shell entry for each phase-one product", () => {
    expect(products.map(({ id, name, path }) => ({ id, name, path }))).toEqual([
      { id: "p1", name: "星域", path: "/p1" },
      { id: "p2", name: "星权", path: "/p2" },
      { id: "p3", name: "星构", path: "/p3" },
    ]);
  });

  it("resolves route metadata to the matching product", () => {
    expect(productById("p2").name).toBe("星权");
    expect(productById("p3").path).toBe("/p3");
  });
});
