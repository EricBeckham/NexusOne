/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProcessViewerView from "./ProcessViewerView.vue";
import { p3 } from "../../api/p3";
import { clearToasts, useNxToastState } from "../../feedback/nxMessage";

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
    clearToasts();
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
    // Z10: "where: what" without the raw code (the code is a dev note).
    expect(wrapper.get("[data-testid='validation-item']").text()).toBe("审批单据：用户任务必须填写审批人引用");
    expect(wrapper.get("[data-testid='process-validation']").text()).not.toContain("USER_TASK_ASSIGNEE_REQUIRED");
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
    expect(wrapper.get("[data-testid='process-next-bind']").attributes("href")).toBe("/p4/product-definitions");

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

  it("shows an empty state with a next step when the tenant has no processes (N4)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    const empty = wrapper.get("[data-testid='process-empty']");
    expect(empty.text()).toContain("暂无流程");
    expect(empty.text()).toContain("新建一个草稿");
    expect(p3.getProcessDefinition).not.toHaveBeenCalled();
    await wrapper.get("[data-testid='process-empty-new']").trigger("click");
    expect(wrapper.findAll("[data-testid='process-node']")).toHaveLength(3);
    expect(wrapper.get("[data-testid='draft-state']").attributes("data-tone")).toBe("warning");
    expect(wrapper.get("[data-testid='draft-state']").text()).toContain("保存后才能校验和发布");
  });

  it("says where a draft is between save, validate and publish (N4)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "nexusSequentialApproval", name: "Approval" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "nexusSequentialApproval",
      name: "Approval",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="nexusSequentialApproval"><startEvent id="start"/><userTask id="task" name="Review" flowable:assignee="${assignee}"/><endEvent id="end"/><sequenceFlow id="one" sourceRef="start" targetRef="task"/><sequenceFlow id="two" sourceRef="task" targetRef="end"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    p3.validateProcessDraft.mockResolvedValue({ valid: true, errors: [] });
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    const state = () => wrapper.get("[data-testid='draft-state']");
    expect(state().attributes("data-tone")).toBe("info");
    expect(state().text()).toContain("下一步「校验草稿」");
    expect(wrapper.get(".step .step-kind").text()).toBe("第 1 步 · 审批");
    await wrapper.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(wrapper.get("[data-testid='node-type']").text()).toBe("审批节点（userTask）");

    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(state().attributes("data-tone")).toBe("success");
    expect(state().text()).toContain("可以发布");

    await wrapper.get("[data-testid='node-name']").setValue("复核");
    expect(state().attributes("data-tone")).toBe("warning");
    expect(state().text()).toContain("有未保存的修改");
    expect(wrapper.get("[data-testid='process-validate']").attributes("disabled")).toBeDefined();
  });

  it("explains a read-only process and one without approval steps (N4)", async () => {
    const direct = {
      key: "direct",
      name: "Direct",
      source: "classpath",
      nodes: [
        { id: "start", name: "Start", type: "startEvent" },
        { id: "end", name: "End", type: "endEvent" },
      ],
      flows: [{ id: "f", source_ref: "start", target_ref: "end", condition: null }],
    };
    p3.listProcessDefinitions.mockResolvedValue([direct]);
    p3.getProcessDefinition.mockResolvedValue(direct);
    const wrapper = await mountAt("/p3/workflow/direct");
    expect(wrapper.get("[data-testid='draft-state']").text()).toContain("已发布只读");
    expect(wrapper.get("[data-testid='flow-steps-empty']").text()).toContain("直接结束");
    await wrapper.get("[data-testid='process-edit-copy']").trigger("click");
    expect(wrapper.get("[data-testid='flow-steps-empty']").text()).toContain("添加审批节点");
    await wrapper.get("[data-testid='add-step']").trigger("click");
    expect(wrapper.find("[data-testid='flow-steps-empty']").exists()).toBe(false);
    expect(wrapper.findAll(".step")).toHaveLength(1);
  });

  it("asks for a tenant sign-in instead of a retry when there is no session (401)", async () => {
    // Real M2 response without a session: 401 P2.IAM.UNAUTHORIZED "missing bearer token".
    p3.listProcessDefinitions.mockRejectedValue(Object.assign(new Error("missing bearer token"), {
      status: 401, code: "P2.IAM.UNAUTHORIZED", traceId: "01a0d95b-23b4-7c7d-9bc0-50bf62477270",
    }));
    const wrapper = await mountAt("/p3/workflow");
    const state = wrapper.get("[data-testid='process-signin-required']");
    expect(state.text()).toContain("需要登录租户账号");
    expect(state.text()).toContain("01a0d95b-23b4-7c7d-9bc0-50bf62477270");
    expect(state.get("[data-testid='process-signin-link']").attributes("href")).toBe("/login");
    expect(wrapper.text()).not.toContain("missing bearer token");
    expect(wrapper.text()).not.toContain("重试");
    expect(wrapper.find("[data-testid='process-canvas']").exists()).toBe(false);
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

  it("adds and removes an exclusive gateway on an editable draft and saves it (Z6)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");

    // The draft is no longer sequential: the graph view takes over, with the new gateway selected.
    expect(wrapper.find("[data-testid='flow-steps']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='process-nonsequential']").text()).toContain("此草稿包含条件分支或并行分支，以流程图编辑");
    expect(wrapper.findAll("[data-testid='process-node']")).toHaveLength(5);
    expect(wrapper.get("[data-testid='node-type']").text()).toBe("条件分支（exclusiveGateway）");
    expect(wrapper.get("svg.graph").text()).toContain("条件分支");
    expect(wrapper.get("[data-testid='node-name']").element.value).toBe("条件分支");

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    const xml = p3.saveProcessDraft.mock.calls[0][1].bpmn_xml;
    expect(xml).toContain('<exclusiveGateway id="gateway" name="条件分支"/>');
    expect((xml.match(/<sequenceFlow /g) || []).length).toBe(5);

    await wrapper.get("[data-testid='remove-gateway']").trigger("click");
    expect(wrapper.find("[data-testid='flow-steps']").exists()).toBe(true);
    expect(wrapper.findAll(".step")).toHaveLength(1);
  });

  it("offers no branch actions on a read-only published definition (Z6)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.getProcessDefinition.mockResolvedValue(definition);
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    await wrapper.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(wrapper.find("[data-testid='add-gateway']").exists()).toBe(false);
  });

  it("selects a branch flow and edits its condition, which is saved as conditionExpression (Z7)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");

    // From the gateway's related flows, pick the branch to 分支审批.
    const related = wrapper.findAll("[data-testid='related-flow']");
    const toBranch = related.find((item) => item.text().includes("分支审批"));
    expect(toBranch.text()).toContain("无条件");
    await toBranch.trigger("click");
    expect(wrapper.get("[data-testid='process-properties'] h2").text()).toBe("连线属性");
    expect(wrapper.get("[data-testid='flow-properties']").text()).toContain("条件分支");
    // ZA7: the builder is the default; 原文 still takes any expression.
    await wrapper.get("[data-testid='condition-mode-raw']").trigger("click");
    await wrapper.get("[data-testid='flow-condition']").setValue("${amount > 1000}");
    expect(wrapper.get("[data-testid='process-flow'].current").classes()).toContain("conditional");
    // ZA9: the edge label reads in words; the raw expression is the hover title.
    expect(wrapper.get(".flow-condition title").text()).toBe("${amount > 1000}");
    expect(wrapper.get(".flow-condition").text()).toContain("amount 大于 1000");

    // A flow that does not leave a gateway explains why it has no condition.
    await wrapper.findAll("[data-testid='process-flow']")[0].trigger("click");
    expect(wrapper.find("[data-testid='flow-condition']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='flow-condition-text']").text()).toContain("只有从条件分支出发的连线可以设置条件");

    // Clicking a node switches back to node properties.
    await wrapper.findAll("[data-testid='process-node']")[0].trigger("click");
    expect(wrapper.find("[data-testid='flow-properties']").exists()).toBe(false);

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    const xml = p3.saveProcessDraft.mock.calls[0][1].bpmn_xml;
    expect(xml).toMatch(/<sequenceFlow id="flow_gateway_branch_task" sourceRef="gateway" targetRef="branch_task">\s*<conditionExpression xsi:type="bpmn:tFormalExpression">\$\{amount &gt; 1000\}<\/conditionExpression>/);
    expect((xml.match(/<conditionExpression/g) || []).length).toBe(1);
  });

  it("shows conditions read-only on a published definition (Z7)", async () => {
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
    await wrapper.get("[data-testid='process-flow'][data-flow-id='f2']").trigger("click");
    expect(wrapper.find("[data-testid='flow-condition']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='flow-condition-text']").text()).toBe("${ok}");
  });

  it("adds a parallel fork/join block, labels it by role, saves it and removes it from the join (Z9)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-parallel']").trigger("click");

    expect(wrapper.findAll("[data-testid='process-node']")).toHaveLength(7);
    expect(wrapper.get("[data-testid='node-type']").text()).toBe("并行开始（parallelGateway）");
    const kinds = wrapper.findAll("svg.graph g.node .type").map((node) => node.text());
    expect(kinds).toContain("并行开始");
    expect(kinds).toContain("并行汇合");
    expect(kinds.filter((kind) => kind === "审批节点")).toHaveLength(3);

    // Flows inside the block explain that they take no condition.
    await wrapper.get("[data-testid='process-flow'][data-flow-id='flow_fork_parallel_task']").trigger("click");
    expect(wrapper.find("[data-testid='flow-condition']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='flow-condition-text']").text()).toContain("并行分支的连线不设条件");

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    const xml = p3.saveProcessDraft.mock.calls[0][1].bpmn_xml;
    expect(xml).toContain('<parallelGateway id="fork" name="并行开始"/>');
    expect(xml).toContain('<parallelGateway id="join" name="并行汇合"/>');

    const join = wrapper.findAll("[data-testid='process-node']").find((node) => node.text().includes("并行汇合"));
    await join.trigger("click");
    expect(wrapper.get("[data-testid='node-type']").text()).toBe("并行汇合（parallelGateway）");
    await wrapper.get("[data-testid='remove-parallel']").trigger("click");
    expect(wrapper.find("[data-testid='flow-steps']").exists()).toBe(true);
    expect(wrapper.findAll(".step")).toHaveLength(1);
  });

  it("marks server validation errors on graph nodes and flows and locates them from the summary (Z10)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "branchy", name: "Branchy" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "branchy",
      name: "Branchy",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="branchy"><startEvent id="start"/><exclusiveGateway id="gate" name="金额判断"/><userTask id="big" name="大额审批"/><userTask id="small" name="小额审批"/><endEvent id="end"/>'
        + '<sequenceFlow id="f0" sourceRef="start" targetRef="gate"/><sequenceFlow id="f1" sourceRef="gate" targetRef="big"/><sequenceFlow id="f2" sourceRef="gate" targetRef="small"/>'
        + '<sequenceFlow id="f3" sourceRef="big" targetRef="end"/><sequenceFlow id="f4" sourceRef="small" targetRef="end"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    const wrapper = await mountAt("/p3/workflow/branchy");

    // Before validating: a gateway whose branches have no conditions gets a non-blocking warning.
    await wrapper.findAll("[data-testid='process-node']").find((node) => node.text().includes("金额判断")).trigger("click");
    expect(wrapper.get("[data-testid='gateway-warning']").text()).toContain("始终走第一条分支");

    p3.validateProcessDraft.mockResolvedValue({
      valid: false,
      errors: [
        { code: "USER_TASK_ASSIGNEE_REQUIRED", message: "用户任务必须填写审批人引用", node_id: "big" },
        { code: "FLOW_CONDITION_EMPTY", message: "连线条件不能为空；无条件连线请移除条件", flow_id: "f2" },
        { code: "END_REQUIRED", message: "流程至少需要一个结束节点" },
      ],
    });
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    const summary = wrapper.get("[data-testid='process-validation']");
    expect(summary.text()).toContain("校验未通过 · 3 处需要修改");
    expect(wrapper.findAll("[data-testid='validation-item']").map((item) => item.text())).toEqual([
      "大额审批：用户任务必须填写审批人引用",
      "连线 金额判断 → 小额审批：连线条件不能为空；无条件连线请移除条件",
      "流程至少需要一个结束节点",
    ]);
    expect(wrapper.findAll("svg.graph g.node.invalid")).toHaveLength(1);
    expect(wrapper.get("[data-testid='process-flow'].invalid").attributes("data-flow-id")).toBe("f2");

    // Clicking a flow item selects the flow and shows its error in the flow panel.
    await wrapper.findAll("[data-testid='validation-item']")[1].trigger("click");
    expect(wrapper.get("[data-testid='flow-error']").text()).toContain("连线条件不能为空");
    await wrapper.findAll("[data-testid='validation-item']")[0].trigger("click");
    expect(wrapper.get("[data-testid='node-error']").text()).toContain("审批人引用");
    expect(useNxToastState().items).toHaveLength(0);
  });

  it("reports save, validate and publish results as toasts and keeps the editor when saving fails (Z10)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    p3.saveProcessDraft.mockRejectedValueOnce(Object.assign(new Error("draft storage unavailable"), { status: 500, code: "CORE.API.0099", traceId: "trace-z10" }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "error", message: "保存没有完成：CORE.API.0099 draft storage unavailable", traceId: "trace-z10" });
    expect(wrapper.find("[data-testid='nx-error-state']").exists()).toBe(false);
    expect(wrapper.findAll("[data-testid='process-node']")).toHaveLength(3);

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "success", message: "草稿已保存，下一步校验草稿" });

    p3.validateProcessDraft.mockResolvedValue({ valid: true, errors: [] });
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "success", message: "校验通过，可以发布" });

    p3.publishProcessDraft.mockResolvedValue({ version: 2 });
    await wrapper.get("[data-testid='process-publish']").trigger("click");
    await wrapper.get("[data-testid='process-publish-confirm-submit']").trigger("click");
    await flushPromises();
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "success", message: "流程已发布为 v2" });
  });

  it("builds a branch condition from variable, operator and value and reads it back (ZA7)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");
    await wrapper.get("[data-testid='process-flow'][data-flow-id='flow_gateway_branch_task']").trigger("click");

    const generated = () => wrapper.get("[data-testid='condition-generated']").text();
    expect(generated()).toBe("无条件");
    await wrapper.get("[data-testid='condition-variable']").setValue("outcome");
    // Catalog enums only offer 等于 / 不等于 and a value list.
    expect(wrapper.findAll("[data-testid='condition-operator'] option").map((option) => option.text())).toEqual(["等于", "不等于"]);
    expect(generated()).toBe("无条件"); // incomplete: nothing written yet
    await wrapper.get("[data-testid='condition-value']").setValue("REJECT");
    expect(generated()).toBe("${outcome == 'REJECT'}");
    await wrapper.get("[data-testid='condition-operator']").setValue("!=");
    expect(generated()).toBe("${outcome != 'REJECT'}");

    // A free variable with a number value.
    await wrapper.get("[data-testid='condition-variable']").setValue("__custom");
    await wrapper.get("[data-testid='condition-custom']").setValue("amount");
    await wrapper.get("[data-testid='condition-operator']").setValue(">");
    await wrapper.get("[data-testid='condition-value']").setValue("1000");
    expect(generated()).toBe("${amount > 1000}");
    expect(wrapper.text()).toContain("申请表单的字段目前不会传入流程");

    // Re-selecting the flow reads the string back into the builder.
    await wrapper.findAll("[data-testid='process-node']")[0].trigger("click");
    await wrapper.get("[data-testid='process-flow'][data-flow-id='flow_gateway_branch_task']").trigger("click");
    expect(wrapper.get("[data-testid='condition-variable']").element.value).toBe("__custom");
    expect(wrapper.get("[data-testid='condition-custom']").element.value).toBe("amount");
    expect(wrapper.get("[data-testid='condition-value']").element.value).toBe("1000");

    await wrapper.get("[data-testid='condition-clear']").trigger("click");
    expect(generated()).toBe("无条件");

    await wrapper.get("[data-testid='condition-variable']").setValue("outcome");
    await wrapper.get("[data-testid='condition-value']").setValue("APPROVE");
    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(p3.saveProcessDraft.mock.calls[0][1].bpmn_xml).toContain("<conditionExpression xsi:type=\"bpmn:tFormalExpression\">${outcome == &apos;APPROVE&apos;}</conditionExpression>");
  });

  it("opens an advanced expression in 原文 and never rewrites it (ZA7)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "adv", name: "Adv" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "adv",
      name: "Adv",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="adv"><startEvent id="start"/><exclusiveGateway id="gate" name="判断"/><userTask id="a" name="A"/><userTask id="b" name="B"/><endEvent id="end"/>'
        + '<sequenceFlow id="f0" sourceRef="start" targetRef="gate"/><sequenceFlow id="f1" sourceRef="gate" targetRef="a"><conditionExpression>${x &gt; 1 &amp;&amp; y &lt; 2}</conditionExpression></sequenceFlow>'
        + '<sequenceFlow id="f2" sourceRef="gate" targetRef="b"/><sequenceFlow id="f3" sourceRef="a" targetRef="end"/><sequenceFlow id="f4" sourceRef="b" targetRef="end"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    const wrapper = await mountAt("/p3/workflow/adv");
    await wrapper.get("[data-testid='process-flow'][data-flow-id='f1']").trigger("click");
    expect(wrapper.get("[data-testid='flow-condition']").element.value).toBe("${x > 1 && y < 2}");
    await wrapper.get("[data-testid='condition-mode-builder']").trigger("click");
    expect(wrapper.get("[data-testid='condition-mode-note']").text()).toContain("高级表达式");
    expect(wrapper.find("[data-testid='condition-variable']").exists()).toBe(false);

    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='draft-name']").setValue("Adv 2");
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(p3.saveProcessDraft.mock.calls[0][1].bpmn_xml).toContain("${x &gt; 1 &amp;&amp; y &lt; 2}");
  });

  it("explains an incomplete builder draft next to its fields and keeps the last complete condition (ZA8)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");
    await wrapper.get("[data-testid='process-flow'][data-flow-id='flow_gateway_branch_task']").trigger("click");

    expect(wrapper.find("[data-testid='condition-incomplete']").exists()).toBe(false); // empty = no condition, no nagging
    await wrapper.get("[data-testid='condition-variable']").setValue("outcome");
    expect(wrapper.get("[data-testid='condition-error-value']").text()).toBe("请选择一个值");
    expect(wrapper.get("[data-testid='condition-value']").attributes("aria-invalid")).toBe("true");
    expect(wrapper.get("[data-testid='condition-incomplete']").text()).toContain("保存时仍使用：无条件");
    await wrapper.get("[data-testid='condition-value']").setValue("APPROVE");
    expect(wrapper.find("[data-testid='condition-incomplete']").exists()).toBe(false);

    // A numeric operator with text keeps the last complete condition and says why.
    await wrapper.get("[data-testid='condition-variable']").setValue("__custom");
    await wrapper.get("[data-testid='condition-value']").setValue("很多");
    expect(wrapper.get("[data-testid='condition-error-variable']").text()).toBe("请填写变量名");
    await wrapper.get("[data-testid='condition-custom']").setValue("amount");
    await wrapper.get("[data-testid='condition-operator']").setValue(">");
    expect(wrapper.get("[data-testid='condition-error-value']").text()).toBe("「大于」需要填写数字");
    // The last complete condition (amount == 很多) stays; the mistyped > 很多 is never written.
    expect(wrapper.get("[data-testid='condition-incomplete']").text()).toContain("${amount == '很多'}");
    expect(wrapper.get("[data-testid='condition-generated']").text()).toBe("${amount == '很多'}");
    await wrapper.get("[data-testid='condition-value']").setValue("1000");
    expect(wrapper.get("[data-testid='condition-generated']").text()).toBe("${amount > 1000}");
  });

  it("blocks saving a malformed raw condition, marks the edge and lists it (ZA8)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");
    await wrapper.get("[data-testid='process-flow'][data-flow-id='flow_gateway_branch_task']").trigger("click");
    await wrapper.get("[data-testid='condition-mode-raw']").trigger("click");
    await wrapper.get("[data-testid='flow-condition']").setValue("${note == 'x}");

    expect(wrapper.get("[data-testid='condition-error-raw']").text()).toBe("引号没有成对");
    expect(wrapper.get("[data-testid='process-flow'][data-flow-id='flow_gateway_branch_task']").classes()).toContain("invalid");
    expect(wrapper.get("[data-testid='condition-problems']").text()).toContain("条件需要修改 · 1 处");
    expect(wrapper.get("[data-testid='condition-problem']").text()).toBe("连线 条件分支 → 分支审批：引号没有成对");

    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(p3.saveProcessDraft).not.toHaveBeenCalled();
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "error", message: "有 1 条连线的条件格式不对，已在图上标出，修改后再保存" });

    await wrapper.get("[data-testid='flow-condition']").setValue("${note == 'x'}");
    expect(wrapper.find("[data-testid='condition-problems']").exists()).toBe(false);
    p3.saveProcessDraft.mockImplementation(async (key, body) => ({ key, name: body.name }));
    await wrapper.get("[data-testid='process-save']").trigger("click");
    await flushPromises();
    expect(p3.saveProcessDraft).toHaveBeenCalledTimes(1);
  });

  it("summarizes branch conditions in words on the gateway, the edges and related flows (ZA9)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/workflow");
    await wrapper.get("[data-testid='process-new']").trigger("click");
    await wrapper.get(".step").trigger("click");
    await wrapper.get("[data-testid='add-gateway']").trigger("click");

    // The gateway panel lists its branches; picking one opens its condition editor at full width.
    const items = () => wrapper.findAll("[data-testid='branch-summary-item']");
    const parts = (item) => [".branch-index", ".branch-target", ".branch-condition"].map((selector) => item.get(selector).text());
    expect(items().map(parts)).toEqual([["分支 1", "→ 结束", "无条件"], ["分支 2", "→ 分支审批", "无条件"]]);
    await items()[1].trigger("click");
    expect(wrapper.get("[data-testid='condition-editor']").classes()).toContain("wide");
    await wrapper.get("[data-testid='condition-variable']").setValue("outcome");
    await wrapper.get("[data-testid='condition-value']").setValue("REJECT");

    expect(wrapper.get(".flow-condition").text()).toContain("审批结果 等于 驳回");
    expect(wrapper.get(".flow-condition title").text()).toBe("${outcome == 'REJECT'}");
    await wrapper.findAll("[data-testid='process-node']").find((node) => node.text().includes("条件分支")).trigger("click");
    const related = wrapper.findAll("[data-testid='related-flow']").map((item) => item.text());
    expect(related.some((text) => text.includes("审批结果 等于 驳回"))).toBe(true);
    expect(parts(items()[1])).toEqual(["分支 2", "→ 分支审批", "审批结果 等于 驳回"]);
    expect(items()[1].attributes("title")).toBe("${outcome == 'REJECT'}");
  });

  it("explains a parallel block read-only: shaded scope on the graph and 并行范围 in the panel (ZA13)", async () => {
    const parallel = {
      key: "parallel",
      name: "Parallel",
      source: "classpath",
      nodes: [
        { id: "start", name: "开始", type: "startEvent" },
        { id: "fork", name: "并行开始", type: "parallelGateway" },
        { id: "finance", name: "财务审批", type: "userTask" },
        { id: "legal", name: "法务审批", type: "userTask" },
        { id: "legal2", name: "法务复核", type: "userTask" },
        { id: "join", name: "并行汇合", type: "parallelGateway" },
        { id: "end", name: "结束", type: "endEvent" },
      ],
      flows: [
        { id: "f0", source_ref: "start", target_ref: "fork" },
        { id: "f1", source_ref: "fork", target_ref: "finance" },
        { id: "f2", source_ref: "fork", target_ref: "legal" },
        { id: "f3", source_ref: "legal", target_ref: "legal2" },
        { id: "f4", source_ref: "finance", target_ref: "join" },
        { id: "f5", source_ref: "legal2", target_ref: "join" },
        { id: "f6", source_ref: "join", target_ref: "end" },
      ],
    };
    p3.listProcessDefinitions.mockResolvedValue([parallel]);
    p3.getProcessDefinition.mockResolvedValue(parallel);
    const wrapper = await mountAt("/p3/workflow/parallel");

    const box = wrapper.get("[data-testid='parallel-scope-box']");
    expect(box.attributes("data-fork")).toBe("fork");
    expect(box.text()).toBe("并行");

    await wrapper.findAll("[data-testid='process-node']").find((node) => node.text().includes("法务复核")).trigger("click");
    const scope = wrapper.get("[data-testid='parallel-scope']");
    expect(scope.findAll("[data-testid='parallel-scope-branch']").map((item) => item.text())).toEqual(["分支 1财务审批", "分支 2法务审批 → 法务复核"]);
    expect(scope.text()).toContain("2 条分支同时进行，全部完成后在「并行汇合」继续");
    // Read-only: no editing controls appear for the published definition.
    expect(wrapper.find("[data-testid='remove-parallel']").exists()).toBe(false);

    await wrapper.findAll("[data-testid='process-node']")[0].trigger("click");
    expect(wrapper.find("[data-testid='parallel-scope']").exists()).toBe(false);
  });

  it("names the exit flow for a pairing finding that carries both locators (ZA14)", async () => {
    p3.listProcessDefinitions.mockResolvedValue([]);
    p3.listProcessDrafts.mockResolvedValue([{ key: "unjoined", name: "Unjoined" }]);
    p3.getProcessDraft.mockResolvedValue({
      key: "unjoined",
      name: "Unjoined",
      bpmn_xml: '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="unjoined"><startEvent id="start" name="开始"/><parallelGateway id="fork" name="并行开始"/><userTask id="a" name="财务"/><userTask id="b" name="法务"/><endEvent id="e1" name="结束一"/><endEvent id="e2" name="结束二"/>'
        + '<sequenceFlow id="f0" sourceRef="start" targetRef="fork"/><sequenceFlow id="fa" sourceRef="fork" targetRef="a"/><sequenceFlow id="fb" sourceRef="fork" targetRef="b"/>'
        + '<sequenceFlow id="f1" sourceRef="a" targetRef="e1"/><sequenceFlow id="f2" sourceRef="b" targetRef="e2"/></process></definitions>',
    });
    p3.getProcessPublication.mockRejectedValue(Object.assign(new Error("not published"), { status: 404 }));
    // Real wire shape (ProcessDraftValidateWireNamingTest): snake_case locators.
    p3.validateProcessDraft.mockResolvedValue({
      valid: false,
      errors: [
        { code: "PARALLEL_BRANCH_NOT_JOINED", message: "并行分支没有汇合就结束了；每条并行分支都要连到同一个并行汇合", node_id: "fork", flow_id: "fa" },
        { code: "PARALLEL_BRANCH_NOT_JOINED", message: "并行分支没有汇合就结束了；每条并行分支都要连到同一个并行汇合", node_id: "fork", flow_id: "fb" },
      ],
    });
    const wrapper = await mountAt("/p3/workflow/unjoined");
    expect(wrapper.find("[data-testid='parallel-scope-box']").exists()).toBe(false);
    await wrapper.get("[data-testid='process-validate']").trigger("click");
    await flushPromises();
    expect(wrapper.findAll("[data-testid='validation-item']").map((item) => item.get(".validation-where").text())).toEqual([
      "连线 并行开始 → 财务：",
      "连线 并行开始 → 法务：",
    ]);
    expect(wrapper.findAll("[data-testid='process-flow'].invalid").map((edge) => edge.attributes("data-flow-id"))).toEqual(["fa", "fb"]);
  });

  it("uses design tokens instead of hard-coded success/white hex (UI3)", async () => {
    const { readFileSync } = await import("node:fs");
    const { fileURLToPath } = await import("node:url");
    const { dirname, join } = await import("node:path");
    const source = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "ProcessViewerView.vue"), "utf8");
    expect(source).not.toMatch(/#238636/i);
    expect(source).not.toMatch(/color:\s*#fff\b/i);
    expect(source).toContain("border-color: var(--nx-success)");
    expect(source).toContain("color: var(--nx-surface)");
  });
});
