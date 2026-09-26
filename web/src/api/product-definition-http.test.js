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

  it("calls IM4/IM5/IM6 product-config export and import endpoints", async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: { get: (name) => (name === "Content-Disposition" ? 'attachment; filename="demo.product-config.json"' : name === "Content-Type" ? "application/json" : null) },
      blob: async () => new Blob(["{}"], { type: "application/json" }),
    });
    const exported = await p4.exportProductDefinition("def-1");
    expect(exported.filename).toBe("demo.product-config.json");
    expect(fetch.mock.calls[0][0]).toBe("/api/v1/p4/product-definitions/def-1/export");

    fetch.mockResolvedValueOnce({
      ok: true,
      status: 202,
      text: async () => JSON.stringify({ import_job_id: "job-1", status: "COMPLETED" }),
    });
    await p4.importProductDefinition({ manifest: {} }, "skip", "idem-1");
    expect(fetch.mock.calls[1][0]).toBe("/api/v1/p4/product-definitions/import");
    expect(fetch.mock.calls[1][1].method).toBe("POST");
    expect(fetch.mock.calls[1][1].headers["Idempotency-Key"]).toBe("idem-1");
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      package_body: { manifest: {} },
      conflict_strategy: "skip",
    });

    fetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ id: "job-1", status: "COMPLETED" }),
    });
    await p4.productDefinitionImportJob("job 1");
    expect(fetch.mock.calls[2][0]).toBe("/api/v1/p4/product-definitions/import-jobs/job%201");
  });
});
