/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p4 } from "./client";

describe("product definition HTTP", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ id: "def-1", code: "hr-basic" }),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("writes a product definition with the platform actor and without a client tenant", async () => {
    await p4.productDefinitions();
    await p4.createProductDefinition({
      code: "hr-basic",
      name: "HR 基础版",
      description: null,
      formTemplateVersionId: "ver-9",
      processDefinitionKey: "leave_flow",
      processDefinitionVersion: 3,
      defaultPermissionPack: { summary: "租户成员可发起" },
    });
    await p4.updateProductDefinition("def 1", { code: "hr-basic", name: "HR 基础版" });

    const calls = fetch.mock.calls.map(([path, init]) => ({ path, method: init.method || "GET", headers: init.headers }));
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      "GET /api/v1/p4/product-definitions",
      "POST /api/v1/p4/product-definitions",
      "PUT /api/v1/p4/product-definitions/def%201",
    ]);
    for (const call of calls) {
      expect(call.headers["X-Nexus-Demo-Actor-Id"]).toBeTruthy();
      expect(call.headers["X-Tenant-Id"]).toBeUndefined();
    }
  });
});
