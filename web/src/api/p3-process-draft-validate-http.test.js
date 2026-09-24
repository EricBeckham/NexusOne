/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { validateProcessDraft } from "./p3-live";

describe("M9 process draft validation HTTP contract", () => {
  beforeEach(() => {
    session.accessToken = "stub.m9";
    session.contextId = "m9-context";
    persist();
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ valid: false, errors: [{ code: "START_REQUIRED", message: "missing start" }] }),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("posts to the tenant-context draft validator and returns server validation errors", async () => {
    const result = await validateProcessDraft("draft one");
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/process/drafts/draft%20one/validate");
    expect(init.method).toBe("POST");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("m9-context");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
    expect(result).toMatchObject({ valid: false, errors: [{ code: "START_REQUIRED" }] });
  });
});
