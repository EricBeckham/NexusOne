/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ api: vi.fn(), listLedgers: vi.fn(), listProcessDefinitions: vi.fn() }));
vi.mock("../../api/client", async (importOriginal) => ({ ...(await importOriginal()), api: mocks.api }));
vi.mock("../../api/p3", () => ({
  p3: { listLedgers: mocks.listLedgers, listProcessDefinitions: mocks.listProcessDefinitions },
}));

import CompletionActionsView from "./CompletionActionsView.vue";
import { actionProblems, mapAction } from "../../api/completionActions";

const expense = {
  code: "expense", name: "费用报销", status: "READY",
  fields: [
    { code: "title", name: "事由", type: "text", required: true },
    { code: "amount", name: "金额", type: "numeric", required: true },
    { code: "approval_status", name: "审批状态", type: "text", required: false },
  ],
};
const payment = {
  code: "payment", name: "付款台账", status: "READY",
  fields: [
    { code: "payee", name: "收款人", type: "text", required: true },
    { code: "amount", name: "金额", type: "numeric", required: true },
    { code: "pay_status", name: "付款状态", type: "text", required: true },
    { code: "created_by", name: "创建人", type: "text", required: false },
  ],
};
const draftLedger = { code: "draft_only", name: "草稿台账", status: "DRAFT", fields: [] };
const savedAction = {
  id: "act-1", name: "报销通过→付款", source_ledger_code: "expense", process_key: "expenseApproval",
  trigger: "APPROVED", target_ledger_code: "payment", enabled: true, version: 1,
  field_mapping: [
    { target: "payee", kind: "SYSTEM", value: "INITIATOR" },
    { target: "amount", kind: "FIELD", value: "amount" },
    { target: "pay_status", kind: "CONST", value: "待付款" },
  ],
};

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/p3/completion-actions", component: CompletionActionsView }] });
  await router.push("/p3/completion-actions");
  const wrapper = mount(CompletionActionsView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("FA5 post-completion actions page", () => {
  beforeEach(() => {
    mocks.api.mockReset();
    mocks.listLedgers.mockResolvedValue({ items: [expense, payment, draftLedger] });
    mocks.listProcessDefinitions.mockResolvedValue([{ key: "expenseApproval" }]);
    mocks.api.mockImplementation(async (path) => (path === "/api/v1/p3/completion-actions" ? [savedAction] : {}));
  });

  it("lists actions with ledger names and trigger labels", async () => {
    const wrapper = await mountPage();
    const row = wrapper.get("[data-testid='action-row-act-1']").text();
    expect(row).toContain("费用报销");
    expect(row).toContain("付款台账");
    expect(row).toContain("审批通过");
    expect(row).toContain("启用");
  });

  it("offers only READY ledgers, never the source as target, and no system columns to map", async () => {
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='action-new']").trigger("click");
    const sources = wrapper.findAll("[data-testid='action-source'] option").map((o) => o.attributes("value"));
    expect(sources).toEqual(["", "expense", "payment"]);
    await wrapper.get("[data-testid='action-source']").setValue("expense");
    const targets = wrapper.findAll("[data-testid='action-target'] option").map((o) => o.attributes("value"));
    expect(targets).toEqual(["", "payment"]);
    await wrapper.get("[data-testid='action-target']").setValue("payment");
    const mapped = wrapper.findAll("[data-testid^='mapping-kind-']").map((el) => el.attributes("data-testid"));
    expect(mapped).toEqual(["mapping-kind-payee", "mapping-kind-amount", "mapping-kind-pay_status"]);
    expect(wrapper.get("[data-testid='mapping-payee']").text()).toContain("*");
  });

  it("sends a snake_case body with only the filled mappings", async () => {
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='action-new']").trigger("click");
    await wrapper.get("[data-testid='action-name']").setValue("报销通过→付款");
    await wrapper.get("[data-testid='action-source']").setValue("expense");
    await wrapper.get("[data-testid='action-process']").setValue("expenseApproval");
    await wrapper.get("[data-testid='action-target']").setValue("payment");
    await wrapper.get("[data-testid='mapping-kind-payee']").setValue("SYSTEM");
    await wrapper.get("[data-testid='mapping-value-payee']").setValue("INITIATOR");
    await wrapper.get("[data-testid='mapping-kind-pay_status']").setValue("CONST");
    await wrapper.get("[data-testid='mapping-value-pay_status']").setValue("待付款");
    mocks.api.mockImplementation(async (path, init) => (init?.method === "POST" ? { ...savedAction, id: "act-2" } : [savedAction]));
    await wrapper.get("[data-testid='action-save']").trigger("click");
    await flushPromises();
    const post = mocks.api.mock.calls.find(([, init]) => init?.method === "POST");
    expect(post[0]).toBe("/api/v1/p3/completion-actions");
    expect(post[1].body).toMatchObject({
      name: "报销通过→付款", source_ledger_code: "expense", process_key: "expenseApproval", trigger: "APPROVED",
      target_ledger_code: "payment",
      field_mapping: [
        { target: "payee", kind: "SYSTEM", value: "INITIATOR" },
        { target: "pay_status", kind: "CONST", value: "待付款" },
      ],
    });
  });

  it("shows the server's validation problems next to the editor", async () => {
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='action-row-act-1'] button").trigger("click");
    mocks.api.mockRejectedValueOnce(Object.assign(new Error("field mapping is invalid"), {
      status: 400, code: "P3.ACTION.0001", body: { details: { problems: ["amount: TEXT cannot fill NUMERIC"] } },
    }));
    await wrapper.get("[data-testid='action-save']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='action-problems']").text()).toContain("amount: TEXT cannot fill NUMERIC");
  });

  it("previews a row for an existing action without saving", async () => {
    const wrapper = await mountPage();
    await wrapper.get("[data-testid='action-row-act-1'] button").trigger("click");
    mocks.api.mockResolvedValueOnce({ row: { payee: "zhou", amount: "128.50", pay_status: "待付款" } });
    await wrapper.get("[data-testid='action-preview-record']").setValue("rec-1");
    await wrapper.get("[data-testid='action-preview-run']").trigger("click");
    await flushPromises();
    const [path, init] = mocks.api.mock.calls.at(-1);
    expect(path).toBe("/api/v1/p3/completion-actions/act-1/preview");
    expect(init.body.source_record_id).toBe("rec-1");
    expect(wrapper.get("[data-testid='action-preview-row']").text()).toContain("待付款");
  });

  it("says plainly when the account may not configure actions", async () => {
    mocks.api.mockRejectedValueOnce(Object.assign(new Error("denied"), { status: 403, code: "P2.PERM.0003" }));
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='actions-error']").text()).toContain("租户管理员");
  });

  it("maps camelCase and snake_case answers the same way", () => {
    expect(mapAction({ ...savedAction }).targetLedgerCode).toBe("payment");
    expect(mapAction({ id: "x", targetLedgerCode: "t", fieldMapping: [] }).targetLedgerCode).toBe("t");
    expect(actionProblems({})).toEqual([]);
  });
});
