/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p1 } from "./client";

describe("platform form template HTTP", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ id: "tpl-1", fields: [] }),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("lists and writes drafts with the platform actor and without a client tenant", async () => {
    await p1.formTemplates();
    await p1.formTemplate("tpl 1");
    await p1.createFormTemplate({ code: "leave", name: "请假", description: null, fields: [] });
    await p1.updateFormTemplate("tpl 1", { code: "leave", name: "请假", description: null, fields: [] });

    const calls = fetch.mock.calls.map(([path, init]) => ({ path, method: init.method || "GET", headers: init.headers }));
    expect(calls.map((call) => `${call.method} ${call.path}`)).toEqual([
      "GET /api/v1/p1/platform/form-templates",
      "GET /api/v1/p1/platform/form-templates/tpl%201",
      "POST /api/v1/p1/platform/form-templates",
      "PUT /api/v1/p1/platform/form-templates/tpl%201",
    ]);
    for (const call of calls) {
      expect(call.headers["X-Nexus-Demo-Actor-Id"]).toBeTruthy();
      expect(call.headers["X-Tenant-Id"]).toBeUndefined();
    }
  });

  it("posts validation without a client tenant", async () => {
    await p1.validateFormTemplate("tpl 1");
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/p1/platform/form-templates/tpl%201/validate");
    expect(init.method).toBe("POST");
    expect(init.headers["X-Nexus-Demo-Actor-Id"]).toBeTruthy();
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
  });
});
