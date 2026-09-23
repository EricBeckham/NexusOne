/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { createRecord, deleteRecord, updateRecord } from "./p3-live";

describe("T3-E record CRUD HTTP contract", () => {
  beforeEach(() => {
    session.accessToken = "stub.t3e";
    session.contextId = "t3e-context";
    persist();
    vi.stubGlobal("fetch", vi.fn(async (_path, init) => ({
      ok: true,
      status: init.method === "DELETE" ? 204 : 200,
      text: async () => init.method === "DELETE" ? "" : JSON.stringify({ id: "record-1", version: 2 }),
    })));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("uses POST, versioned PATCH, and DELETE without a tenant identity header", async () => {
    await createRecord("ledger-1", { title: "新记录", amount: 25 });
    await updateRecord("ledger-1", "record-1", { title: "已更新" }, 1);
    await deleteRecord("ledger-1", "record-1");

    expect(fetch.mock.calls.map((call) => [call[0], call[1].method])).toEqual([
      ["/api/v1/p3/ledgers/ledger-1/records", "POST"],
      ["/api/v1/p3/ledgers/ledger-1/records/record-1", "PATCH"],
      ["/api/v1/p3/ledgers/ledger-1/records/record-1", "DELETE"],
    ]);
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ title: "新记录", amount: 25 });
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ values: { title: "已更新" }, version: 1 });
    for (const [, init] of fetch.mock.calls) {
      expect(init.headers["X-Nexus-Context-Id"]).toBe("t3e-context");
      expect(init.headers["X-Tenant-Id"]).toBeUndefined();
    }
  });
});
