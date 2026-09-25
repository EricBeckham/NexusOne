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
    getProcessPublication: vi.fn(),
    saveProcessDraft: vi.fn(),
    validateProcessDraft: vi.fn(),
    publishProcessDraft: vi.fn(),
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
  wrapper.router = router;
  return wrapper;
}

describe("process viewer", () => {
  beforeEach(() => {
    p3.listProcessDefinitions.mockReset();
    p3.getProcessDefinition.mockReset();
    p3.listProcessDrafts.mockReset().mockResolvedValue([]);
    p3.getProcessDraft.mockReset();
    p3.getProcessPublication.mockReset();
    p3.saveProcessDraft.mockReset();
    p3.validateProcessDraft.mockReset();
    p3.publishProcessDraft.mockReset();
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
    await wrapper.get("[data-testid='add-step']").trigger("click");
    expect(wrapper.get("[data-testid='process-canvas']").text()).toContain("审批节点 2");
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
    expect(wrapper.find("[data-testid='process-publish']").exists()).toBe(true);
    await wrapper.get("[data-testid='process-publish']").trigger("click");
    expect(wrapper.get("[data-testid='process-publish-confirm']").text()).toContain("确认发布流程");
    await wrapper.get("[data-testid='process-publish-cancel']").trigger("click");
    expect(p3.publishProcessDraft).not.toHaveBeenCalled();
    p3.publishProcessDraft.mockResolvedValue({ key: "nexusSequentialApproval", version: 3 });
    await wrapper.get("[data-testid='process-publish']").trigger("click");
    await wrapper.get("[data-testid='process-publish-confirm-submit']").trigger("click");
    await flushPromises();
    expect(p3.publishProcessDraft).toHaveBeenCalledWith("nexusSequentialApproval");
    expect(wrapper.get("[data-testid='process-publication']").text()).toContain("最近发布版本 v3");

    const reopened = await mountAt("/p3/workflow/nexusSequentialApproval");
    expect(p3.getProcessDraft).toHaveBeenCalledWith("nexusSequentialApproval");
    expect(reopened.get("[data-testid='process-canvas']").text()).toContain("已修改但尚未保存");
    await reopened.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(reopened.get("[data-testid='node-assignee']").element.value).toBe("${manager}");
  });

  it("shows the server error without a false published state and preserves the deep link", async () => {
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "nexusSequentialApproval", name: "Approval" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "nexusSequentialApproval",
      name: "Approval",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="nexusSequentialApproval"><startEvent id="start"/><userTask id="task" name="Review" flowable:assignee="${assignee}"/><endEvent id="end"/><sequenceFlow id="one" sourceRef="start" targetRef="task"/><sequenceFlow id="two" sourceRef="task" targetRef="end"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    p3.validateProcessDraft.mockResolvedValue({ valid: true, errors: [] });
    p3.publishProcessDraft.mockRejectedValue(new Error("Flowable unavailable"));
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    await wrapper.get("[data-testid='process-publish']").trigger("click");
    await wrapper.get("[data-testid='process-publish-confirm-submit']").trigger("click");
    await flushPromises();
    expect(wrapper.get("[data-testid='process-publish-error']").text()).toContain("Flowable unavailable");
    expect(wrapper.find("[data-testid='process-publication']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='process-publish-confirm']").exists()).toBe(true);
    expect(wrapper.router.currentRoute.value.fullPath).toBe("/p3/workflow/nexusSequentialApproval");
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

  it("edits a sequential draft: insert, drag reorder, keyboard moves, delete, and saves a straight chain", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    const stepNames = () => wrapper.findAll(".step .step-body strong").map((node) => node.text());
    expect(wrapper.get("[data-testid='flow-mode']").text()).toContain("1 个审批节点");
    expect(wrapper.get("[data-testid='step-remove']").attributes("disabled")).toBeDefined();

    await wrapper.get("[data-testid='add-step']").trigger("click");
    await wrapper.findAll("[data-testid='insert-step']")[0].trigger("click");
    expect(stepNames()).toEqual(["审批节点 3", "审批", "审批节点 2"]);
    expect(wrapper.get("[data-testid='node-name']").element.value).toBe("审批节点 3");

    const transfer = () => ({ setData: vi.fn(), effectAllowed: "", dropEffect: "" });
    const steps = wrapper.findAll(".step");
    await steps[0].trigger("dragstart", { dataTransfer: transfer() });
    expect(steps[0].classes()).toContain("dragging");
    await steps[2].trigger("dragover", { dataTransfer: transfer() });
    expect(wrapper.findAll("[data-testid='step-drop-indicator']")).toHaveLength(1);
    await steps[2].trigger("drop", { dataTransfer: transfer() });
    expect(stepNames()).toEqual(["审批", "审批节点 2", "审批节点 3"]);
    expect(wrapper.find("[data-testid='step-drop-indicator']").exists()).toBe(false);

    await wrapper.findAll("[data-testid='step-up']")[2].trigger("click");
    expect(stepNames()).toEqual(["审批", "审批节点 3", "审批节点 2"]);
    await wrapper.findAll("[data-testid='step-remove']")[0].trigger("click");
    expect(stepNames()).toEqual(["审批节点 3", "审批节点 2"]);

    await wrapper.findAll(".step")[0].trigger("click");
    await wrapper.get("[data-testid='assignee-preset']").trigger("click");
    expect(wrapper.get("[data-testid='node-assignee']").element.value).toBe("${assignee}");
    expect(wrapper.findAll(".step")[0].text()).toContain("发起时选择的审批人");
    expect(wrapper.findAll(".step")[1].text()).toContain("未设置审批人");

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    const xml = p3.saveProcessDraft.mock.calls[0][1].bpmn_xml;
    const flows = [...xml.matchAll(/sourceRef="([^"]+)" targetRef="([^"]+)"/g)].map((match) => `${match[1]}>${match[2]}`);
    expect(flows).toEqual(["start>approve_3", "approve_3>approve_2", "approve_2>end"]);
  });

  it("marks validation errors on the step they belong to", async () => {
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "nexusSequentialApproval", name: "Approval" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "nexusSequentialApproval",
      name: "Approval",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="nexusSequentialApproval"><startEvent id="start"/><userTask id="task" name="Review"/><endEvent id="end"/><sequenceFlow id="one" sourceRef="start" targetRef="task"/><sequenceFlow id="two" sourceRef="task" targetRef="end"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    p3.validateProcessDraft.mockResolvedValue({
      valid: false,
      errors: [{ code: "USER_TASK_ASSIGNEE_REQUIRED", message: "用户任务必须填写审批人引用", node_id: "task" }],
    });
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    expect(wrapper.get("[data-testid='process-validate']").attributes("disabled")).toBeUndefined();
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.get(".step").classes()).toContain("invalid");
    expect(wrapper.get("[data-testid='step-error']").text()).toBe("用户任务必须填写审批人引用");
  });

  it("keeps branching definitions view-only in the graph", async () => {
    const branched = {
      key: "branchy",
      name: "Branchy",
      source: "classpath",
      nodes: [
        { id: "start", name: "Start", type: "startEvent" },
        { id: "gate", name: "Gate", type: "exclusiveGateway" },
        { id: "a", name: "A", type: "userTask" },
      ],
      flows: [
        { id: "f1", source_ref: "start", target_ref: "gate", condition: null },
        { id: "f2", source_ref: "gate", target_ref: "a", condition: "${ok}" },
      ],
    };
    p3.listProcessDefinitions.mockResolvedValue([branched]);
    p3.getProcessDefinition.mockResolvedValue(branched);
    const wrapper = await mountAt("/p3/workflow/branchy");
    expect(wrapper.get("[data-testid='process-nonsequential']").text()).toContain("仅可查看");
    expect(wrapper.find("[data-testid='flow-steps']").exists()).toBe(false);
    expect(wrapper.find("svg").exists()).toBe(true);
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
