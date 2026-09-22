/**
 * @vitest-environment happy-dom
 *
 * G7 leftover: Vue p3-live.withdrawTodo reaches the same HTTP withdraw path
 * as G7LeaveWithdrawIT / G7ExpenseTravelWithdrawIT
 * (POST /api/v1/p3/path/instances/{id}/withdraw).
 *
 * Issued X-Nexus-Context-Id only. Never X-Tenant-Id. No live JAR / IdP.
 * Not a G7 three-type product close. Does not reopen G18.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { saveSettings } from "./client";
import { withdrawTodo } from "./p3-live";

const CONTEXT = "01a0c98a-1f3c-7f72-b724-6ac34746e981";
const TOKEN = "stub.g7-withdraw-reachability";
const HTTP_WITHDRAW = "/api/v1/p3/path/instances/";

const TYPES = [
  { type: "leave", instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7371" },
  { type: "expense", instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7372" },
  { type: "travel", instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7373" },
];

function withdrawResponse(instanceId) {
  return {
    approval_status: "WITHDRAWN",
    process: { id: instanceId, status: "WITHDRAWN" },
    todo: {
      id: `todo-${instanceId}`,
      instance_id: instanceId,
      status: "DONE",
      outcome: "WITHDRAWN",
      task_name: "撤回",
      business_key: instanceId,
    },
  };
}

describe("p3-live withdraw HTTP reachability", () => {
  beforeEach(() => {
    saveSettings({
      accessToken: TOKEN,
      contextId: CONTEXT,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path) => ({
        ok: true,
        status: 200,
        text: async () => JSON.stringify(withdrawResponse(String(path).split("/")[6])),
      })),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it.each(TYPES)("POSTs $type instance to the HTTP withdraw evidence path", async ({ type, instanceId }) => {
    const result = await withdrawTodo(`todo-${type}`, "", instanceId);
    expect(fetch).toHaveBeenCalledTimes(1);
    const [path, init] = fetch.mock.calls[0];
    expect(path).toBe(`${HTTP_WITHDRAW}${instanceId}/withdraw`);
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe(`Bearer ${TOKEN}`);
    expect(init.headers["X-Nexus-Context-Id"]).toBe(CONTEXT);
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
    expect(init.headers["X-Nexus-Demo-Tenant-Id"]).toBeUndefined();
    expect(init.headers["X-Trace-Id"]).toBeTruthy();
    expect(result.approval_status).toBe("WITHDRAWN");
    expect(result.instance_id).toBe(instanceId);
  });

  it("refuses withdraw without instance_id so the HTTP path is not guessed", async () => {
    await expect(withdrawTodo("todo-missing", "", "")).rejects.toMatchObject({
      code: "P3.FLOW.NO_INSTANCE",
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
  });
});
