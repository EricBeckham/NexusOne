/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProcessViewerView from "./ProcessViewerView.vue";
import { p3 } from "../../api/p3";

vi.mock("../../api/p3", () => ({
  p3: {
    listProcessDefinitions: vi.fn(),
    getProcessDefinition: vi.fn(),
    listProcessDrafts: vi.fn(),
    getProcessDraft: vi.fn(),
    saveProcessDraft: vi.fn(),
    validateProcessDraft: vi.fn(),
  },
}));

const definition = {
  key: "nexusSequentialApproval",
  name: "Nexus sequential approval",
  source: "classpath",
  resource: "processes/nexus-sequential-approval.bpmn20.xml",
  nodes: [
    { id: "start", name: "Start", type: "startEvent" },
    { id: "approveTask", name: "Approve", type: "userTask" },
  ],
  flows: [
    { id: "toApprove", source_ref: "start", target_ref: "approveTask", condition: null },
  ],
};

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/workflow", component: ProcessViewerView },
      { path: "/p3/workflow/:key", component: ProcessViewerView },
    ],
  });
  await router.push(path);
  const wrapper = mount(ProcessViewerView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("process viewer", () => {
  beforeEach(() => {
    p3.listProcessDefinitions.mockReset();
    p3.getProcessDefinition.mockReset();
    p3.listProcessDrafts.mockReset().mockResolvedValue([]);
    p3.getProcessDraft.mockReset();
    p3.saveProcessDraft.mockReset();
    p3.validateProcessDraft.mockReset();
  });

  it("edits a published definition only as a draft and saves it for reopening", async () => {
    let persisted = null;
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.listProcessDrafts.mockImplementation(async () => persisted ? [{ key: persisted.key, name: persisted.name }] : []);
    p3.getProcessDefinition.mockResolvedValue(definition);
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    expect(wrapper.get("[data-testid='process-canvas']").text()).toContain("Approve");
    expect(wrapper.get("[data-testid='process-properties']").text()).toContain("start");
    await wrapper.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(wrapper.get("[data-testid='process-properties']").text()).toContain("approveTask");
    expect(wrapper.find("[data-testid='process-save']").exists()).toBe(false);
    await wrapper.get("[data-testid='process-edit-copy']").trigger("click");
    await wrapper.get("[data-testid='node-name']").setValue("审批单据");
    await wrapper.get("[data-testid='node-assignee']").setValue("${manager}");
    await wrapper.get("[data-testid='node-actions'] button:nth-of-type(2)").trigger("click");
    expect(wrapper.get("[data-testid='process-canvas']").text()).toContain("新用户任务");
    p3.saveProcessDraft.mockImplementation(async (key, body) => {
      persisted = { key, name: body.name, bpmn_xml: body.bpmn_xml };
      return { key, name: body.name };
    });
    p3.getProcessDraft.mockImplementation(async () => persisted);
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(p3.saveProcessDraft).toHaveBeenCalledWith("nexusSequentialApproval", expect.objectContaining({
      name: "Nexus sequential approval",
      bpmn_xml: expect.stringContaining('name="审批单据"'),
    }));
    expect(p3.saveProcessDraft.mock.calls[0][1].bpmn_xml).toContain('flowable:assignee="${manager}"');
    expect(wrapper.text()).not.toContain("发布成功率");
    expect(p3.getProcessDefinition).toHaveBeenCalledWith("nexusSequentialApproval");
    p3.validateProcessDraft.mockResolvedValue({
      valid: false,
      errors: [{ code: "USER_TASK_ASSIGNEE_REQUIRED", message: "用户任务必须填写审批人引用", node_id: "approveTask" }],
    });
    expect(wrapper.get("[data-testid='process-validate']").attributes("disabled")).toBeUndefined();
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(p3.validateProcessDraft).toHaveBeenCalledWith("nexusSequentialApproval");
    expect(wrapper.get("[data-testid='process-validation']").text()).toContain("校验未通过");
    expect(wrapper.get("[data-testid='process-validation']").text()).toContain("USER_TASK_ASSIGNEE_REQUIRED");
    await wrapper.get("[data-testid='node-name']").setValue("已修改但尚未保存");
    expect(wrapper.find("[data-testid='process-validation']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='process-validate']").attributes("disabled")).toBeDefined();
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    p3.validateProcessDraft.mockResolvedValue({ valid: true, errors: [] });
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='process-validation']").text()).toContain("校验通过");

    const reopened = await mountAt("/p3/workflow/nexusSequentialApproval");
    expect(p3.getProcessDraft).toHaveBeenCalledWith("nexusSequentialApproval");
    expect(reopened.get("[data-testid='process-canvas']").text()).toContain("已修改但尚未保存");
    await reopened.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(reopened.get("[data-testid='node-assignee']").element.value).toBe("${manager}");
  });

  it("creates a new basic draft without inventing server-side records before save", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    expect(wrapper.findAll("[data-testid='process-node']")).toHaveLength(3);
    expect(p3.saveProcessDraft).not.toHaveBeenCalled();
    expect(wrapper.get("[data-testid='draft-name']").element.value).toBe("新流程草稿");
    expect(wrapper.find("[data-testid='process-validate']").exists()).toBe(false);
  });

  it("shows an empty state when the catalog has no definitions", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    expect(wrapper.text()).toContain("暂无流程定义");
    expect(p3.getProcessDefinition).not.toHaveBeenCalled();
  });

  it("shows the request error without a diagram", async () => {
    const error = new Error("需要租户上下文");
    error.traceId = "trace-n13";
    p3.listProcessDefinitions.mockRejectedValue(error);
    const wrapper = await mountAt("/p3/workflow");
    expect(wrapper.get("[data-testid='nx-error-state']").text()).toContain("需要租户上下文");
    expect(wrapper.get("[data-testid='nx-error-state']").text()).toContain("trace-n13");
    expect(wrapper.find("[data-testid='process-canvas']").exists()).toBe(false);
  });
});
