/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { persist, session } from "./session";
import { publishProcessDraft, validateProcessDraft } from "./p3-live";

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

  it.each(["exclusive", "parallel"])("returns %s draft validation without client-side rejection", async (kind) => {
    fetch.mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ valid: true, errors: [] }) });
    expect(await validateProcessDraft(`${kind} branch`)).toEqual({ valid: true, errors: [] });
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe(`/api/v1/process/drafts/${kind}%20branch/validate`);
    expect(init.method).toBe("POST");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("m9-context");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
  });

  it("preserves gateway and condition error locations from the server", async () => {
    const errors = [
      { code: "FLOW_CONDITION_EMPTY", message: "连线条件不能为空", nodeId: null, flowId: "branch" },
      { code: "PARALLEL_FLOW_CONDITION_UNSUPPORTED", message: "并行网关的出线不能带条件", nodeId: "fork", flowId: "a" },
    ];
    fetch.mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ valid: false, errors }) });
    expect(await validateProcessDraft("gateway")).toEqual({ valid: false, errors });
  });

  it("posts to the tenant-context draft validator and returns server validation errors", async () => {
    const result = await validateProcessDraft("draft one");
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/process/drafts/draft%20one/validate");
    expect(init.method).toBe("POST");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("m9-context");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
    expect(result).toMatchObject({ valid: false, errors: [{ code: "START_REQUIRED" }] });
  });

  it("posts publication to the server and returns its real deployment version", async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ key: "approval", version: 4, deploymentId: "dep-4" }),
    });
    const result = await publishProcessDraft("approval one");
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe("/api/v1/process/drafts/approval%20one/publish");
    expect(init.method).toBe("POST");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("m9-context");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
    expect(result).toMatchObject({ key: "approval", version: 4, deploymentId: "dep-4" });
  });

  it("passes the real snake_case pairing locators through unchanged (ZA14)", async () => {
    // Wire shape pinned by ProcessDraftValidateWireNamingTest (SNAKE_CASE, non_null): no nodeId/flowId, absent locators omitted.
    const errors = [
      { code: "PARALLEL_BRANCH_NOT_JOINED", message: "并行分支没有汇合就结束了", node_id: "fork", flow_id: "fa" },
      { code: "PARALLEL_JOIN_UNPAIRED", message: "并行汇合没有对应的并行开始", node_id: "join" },
      { code: "END_REQUIRED", message: "流程至少需要一个结束节点" },
    ];
    fetch.mockResolvedValueOnce({ ok: true, status: 200, text: async () => JSON.stringify({ valid: false, errors }) });
    const result = await validateProcessDraft("parallel draft");
    expect(result).toEqual({ valid: false, errors });
    expect(result.errors[1]).not.toHaveProperty("flow_id");
  });
});
