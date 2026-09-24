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
  });

  it("opens a read-only graph and keeps save disabled", async () => {
    p3.listProcessDefinitions.mockResolvedValue([definition]);
    p3.getProcessDefinition.mockResolvedValue(definition);
    const wrapper = await mountAt("/p3/workflow/nexusSequentialApproval");
    expect(wrapper.get("[data-testid='process-canvas']").text()).toContain("Approve");
    expect(wrapper.get("[data-testid='process-properties']").text()).toContain("start");
    expect(wrapper.get("[data-testid='process-properties']").text()).toContain("暂无");
    const save = wrapper.get("[data-testid='process-save']");
    expect(save.attributes("disabled")).toBeDefined();
    expect(wrapper.text()).not.toContain("发布成功率");
    await wrapper.findAll("[data-testid='process-node']")[1].trigger("click");
    expect(wrapper.get("[data-testid='process-properties']").text()).toContain("approveTask");
    expect(p3.getProcessDefinition).toHaveBeenCalledWith("nexusSequentialApproval");
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
