/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p4 } from "./client";

describe("tenant subscribe HTTP", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      status: 201,
      text: async () => JSON.stringify({ id: "install-1", status: "INSTALLED" }),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("posts self-subscribe without a client tenant header", async () => {
    await p4.subscribe("tenant 1", { sku_code: "basic", version_id: "ver-1" });
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/p4/tenants/tenant%201/subscriptions");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ sku_code: "basic", version_id: "ver-1" });
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
  });
});
