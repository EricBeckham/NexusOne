/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { getForm } from "./p3-live";

describe("T3-F running form contract", () => {
  beforeEach(() => {
    session.accessToken = "stub.t3f";
    session.contextId = "t3f-context";
    persist();
  });

  afterEach(() => vi.unstubAllGlobals());

  it("does not expose DRAFT bindings as fillable running fields", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({
        form_id: "form-1",
        draft_status: "DRAFT",
        running_version: null,
        bindings: [{ widget_code: "title", ledger_code: "customers", field_code: "title", widget_type: "TEXT" }],
      }),
    })));

    const form = await getForm("form-1");
    expect(form.draft_fields).toHaveLength(1);
    expect(form.running_fields).toEqual([]);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("loads list/detail fields from the published Running snapshot", async () => {
    vi.stubGlobal("fetch", vi.fn(async (path) => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(String(path).endsWith("/running")
        ? { snapshot: { bindings: [{ widget_code: "title", ledger_code: "customers", field_code: "title", widget_type: "TEXT", required: true }] } }
        : { form_id: "form-1", draft_status: "DRAFT", running_version: 1, bindings: [] }),
    })));

    const form = await getForm("form-1");
    expect(form.running_fields).toEqual([expect.objectContaining({ code: "title", required: true })]);
    expect(fetch.mock.calls.map((call) => call[0])).toEqual([
      "/api/v1/p3/forms/form-1",
      "/api/v1/p3/forms/form-1/running",
    ]);
  });
});
