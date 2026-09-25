/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { createProductApplication, productApplicationForm } from "./p3-live";

describe("product start HTTP", () => {
  beforeEach(() => {
    session.accessToken = "stub.k16";
    session.contextId = "k16-context";
    persist();
    vi.stubGlobal("fetch", vi.fn(async (path, init) => ({
      ok: true,
      status: init?.method === "POST" ? 201 : 200,
      text: async () => JSON.stringify(String(path).endsWith("/form")
        ? { installationId: "install 1", fields: [] }
        : { id: "app-1", status: "RUNNING" }),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("loads the pinned form and posts values without a client tenant header", async () => {
    await productApplicationForm("install 1");
    await createProductApplication("install 1", { title: "请假", approver: "person-1" });
    const [formPath, formInit] = fetch.mock.calls[0];
    const [createPath, createInit] = fetch.mock.calls[1];
    expect(formPath).toBe("/api/v1/p3/product-applications/installations/install%201/form");
    expect(formInit.method).toBe("GET");
    expect(createPath).toBe("/api/v1/p3/product-applications/installations/install%201");
    expect(createInit.method).toBe("POST");
    expect(JSON.parse(createInit.body)).toEqual({ values: { title: "请假", approver: "person-1" } });
    for (const [, init] of fetch.mock.calls) {
      expect(init.headers["X-Tenant-Id"]).toBeUndefined();
      expect(init.headers["X-Nexus-Context-Id"]).toBe("k16-context");
    }
  });
});
