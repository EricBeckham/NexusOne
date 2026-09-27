/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));
vi.mock("../../api/client", async (importOriginal) => ({ ...(await importOriginal()), api: mocks.api }));

import CompletionRunsPanel from "./CompletionRunsPanel.vue";
import { mapRun } from "../../api/completionActions";

const done = {
  id: "run-done", action_id: "act-1", source_ledger_code: "expense", source_record_id: "11111111-aaaa",
  outcome: "APPROVED", status: "DONE", target_record_id: "22222222-bbbb", attempts: 1, error_code: null,
  updated_at: "2026-09-27T08:00:00Z",
};
const failed = {
  id: "run-failed", action_id: "act-1", source_ledger_code: "expense", source_record_id: "33333333-cccc",
  outcome: "REJECTED", status: "FAILED", target_record_id: null, attempts: 3, error_code: "P3.LEDGER.0003",
  updated_at: "2026-09-27T09:00:00Z",
};

describe("FA6 post-completion run records", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.api.mockImplementation(async (path) => (path.includes("/runs?") ? [failed, done] : {}));
  });

  it("lists one action's runs with status, generated row, attempts and error category", async () => {
    const wrapper = mount(CompletionRunsPanel, { props: { actionId: "act-1" } });
    await flushPromises();
    expect(mocks.api.mock.calls[0][0]).toBe("/api/v1/p3/completion-actions/runs?action_id=act-1&limit=50");
    const failedRow = wrapper.get("[data-testid='run-row-run-failed']").text();
    expect(failedRow).toContain("失败");
    expect(failedRow).toContain("驳回");
    expect(failedRow).toContain("P3.LEDGER.0003");
    expect(failedRow).toContain("3");
    const doneRow = wrapper.get("[data-testid='run-row-run-done']").text();
    expect(doneRow).toContain("成功");
    expect(doneRow).toContain("22222222");
    expect(wrapper.find("[data-testid='run-retry-run-done']").exists()).toBe(false);
  });

  it("lists the tenant's latest runs when no action is given", async () => {
    mount(CompletionRunsPanel);
    await flushPromises();
    expect(mocks.api.mock.calls[0][0]).toBe("/api/v1/p3/completion-actions/runs?limit=50");
  });

  it("retries a failed run and says it was resubmitted", async () => {
    const wrapper = mount(CompletionRunsPanel, { props: { actionId: "act-1" } });
    await flushPromises();
    mocks.api.mockResolvedValueOnce({ ...failed });
    await wrapper.get("[data-testid='run-retry-run-failed']").trigger("click");
    await flushPromises();
    const [path, init] = mocks.api.mock.calls.at(-1);
    expect(path).toBe("/api/v1/p3/completion-actions/runs/run-failed/retry");
    expect(init.method).toBe("POST");
    expect(wrapper.get("[data-testid='runs-notice']").text()).toContain("已重新提交");
  });

  it("explains a 409 retry and an empty history", async () => {
    const wrapper = mount(CompletionRunsPanel, { props: { actionId: "act-1" } });
    await flushPromises();
    mocks.api.mockRejectedValueOnce(Object.assign(new Error("run already done"), { status: 409, code: "P3.ACTION.0003" }));
    await wrapper.get("[data-testid='run-retry-run-failed']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='runs-error']").text()).toContain("已停用");

    mocks.api.mockResolvedValueOnce([]);
    const empty = mount(CompletionRunsPanel, { props: { actionId: "act-2" } });
    await flushPromises();
    expect(empty.get("[data-testid='runs-empty']").text()).toContain("还没有执行记录");
  });

  it("maps camelCase answers too", () => {
    expect(mapRun({ id: "x", targetRecordId: "t", status: "DONE" }).targetRecordId).toBe("t");
  });
});
