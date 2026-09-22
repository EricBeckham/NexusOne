import { describe, expect, it } from "vitest";
import { COVERED_PAGES, G2_CLOSE, OPEN_PAGES, coveredPaths, openPaths } from "./h5PageInventory.js";

describe("H5 every-page SurfaceGuard honesty inventory", () => {
  it("does not claim product G2 closed", () => {
    expect(G2_CLOSE).toBe("NOT_CLAIMED");
    expect(openPaths().length).toBeGreaterThan(0);
    expect(coveredPaths().length).toBeGreaterThan(0);
  });

  it("keeps #116 list/detail/form/todo/query covered", () => {
    expect(coveredPaths()).toEqual(expect.arrayContaining([
      "/m/p3/lists",
      "/m/p3/lists/:id",
      "/m/p3/lists/:id/records/:recordId",
      "/m/p3/forms",
      "/m/p3/forms/:id",
      "/m/p3/todos",
      "/m/p3/todos/:id",
      "/m/p3/query",
    ]));
    expect(COVERED_PAGES.every((page) => page.cell)).toBe(true);
  });

  it("keeps home/files/notify/login/host/harness open", () => {
    expect(openPaths()).toEqual(expect.arrayContaining([
      "/m",
      "/m/login",
      "/m/callback",
      "/m/host",
      "/m/p3/home",
      "/m/p3/files",
      "/m/p3/files/:id",
      "/m/p3/notifications",
    ]));
    expect(openPaths()).not.toContain("/m/p3/lists");
    expect(OPEN_PAGES.every((page) => !page.cell)).toBe(true);
  });
});
