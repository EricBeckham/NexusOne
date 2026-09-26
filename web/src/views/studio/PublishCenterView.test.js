/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PublishCenterView from "./PublishCenterView.vue";
import { p3 } from "../../api/p3";

vi.mock("../../api/p3", () => ({
  p3: {
    listPublishCenter: vi.fn(),
    getPublishCenter: vi.fn(),
  },
}));

const row = {
  id: "form-1",
  form_id: "form-1",
  form_code: "center_form",
  name: "中心表单",
  app_code: "studio",
  status: "Running",
  source_status: "RUNNING",
  running_version: null,
  latest_log: null,
};

const detail = {
  ...row,
  versions: [{ version_no: 1, status: "Draft", source_status: "DRAFT", notes: null, created_at: "2026-09-24T00:00:00Z" }],
  logs: [],
  schema_steps: ["Expand", "Migrate", "Switch", "Contract"],
  schema_execution: false,
  schema_notice: "回滚不会回滚共享物理 Schema，也不会删除已有业务数据。",
};

async function mountAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3/publish-center", component: PublishCenterView },
      { path: "/p3/publish-center/:id", component: PublishCenterView },
      { path: "/p3/forms", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  const wrapper = mount(PublishCenterView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("publish center", () => {
  beforeEach(() => {
    p3.listPublishCenter.mockReset();
    p3.getPublishCenter.mockReset();
  });

  it("renders the list and the schema notice from the detail payload", async () => {
    p3.listPublishCenter.mockResolvedValue([row]);
    p3.getPublishCenter.mockResolvedValue(detail);
    const wrapper = await mountAt("/p3/publish-center/form-1");
    expect(wrapper.get("[data-testid='publish-table']").text()).toContain("中心表单");
    expect(wrapper.get("[data-testid='publish-table']").text()).toContain("暂无");
    expect(wrapper.get("[data-testid='schema-notice']").text()).toBe("回滚不会回滚共享物理 Schema，也不会删除已有业务数据。");
    expect(wrapper.get("[data-testid='schema-steps']").text()).toContain("Expand");
    expect(wrapper.get("[data-testid='schema-steps']").find("button").exists()).toBe(false);
    expect(wrapper.text()).toContain("仅供查看，本页不会执行结构变更。");
    expect(wrapper.text()).toContain("结构变更步骤");
    expect(wrapper.text()).not.toContain("发布成功率");
    expect(wrapper.text()).not.toMatch(/\d+%/);
    expect(p3.getPublishCenter).toHaveBeenCalledWith("form-1");
  });

  it("shows an empty state when the tenant has no publish rows", async () => {
    p3.listPublishCenter.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/publish-center");
    expect(wrapper.text()).toContain("暂无发布记录");
    expect(wrapper.get("[data-testid='nx-empty-next']").text()).toContain("前往表单列表");
    expect(p3.getPublishCenter).not.toHaveBeenCalled();
  });

  it("shows list-row skeletons while the publish list loads", async () => {
    let resolveList;
    p3.listPublishCenter.mockReturnValueOnce(new Promise((resolve) => { resolveList = resolve; }));
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/p3/publish-center", component: PublishCenterView },
        { path: "/p3/forms", component: { template: "<div />" } },
      ],
    });
    await router.push("/p3/publish-center");
    const wrapper = mount(PublishCenterView, { global: { plugins: [router] } });
    await wrapper.vm.$nextTick();
    expect(wrapper.get("[data-testid='publish-loading']").findAll("[data-testid='nx-skeleton']").length).toBeGreaterThan(0);
    resolveList([]);
    await flushPromises();
    expect(wrapper.find("[data-testid='publish-loading']").exists()).toBe(false);
  });

  it("shows the request error without a table", async () => {
    const error = new Error("需要租户上下文");
    error.traceId = "trace-n15";
    p3.listPublishCenter.mockRejectedValue(error);
    const wrapper = await mountAt("/p3/publish-center");
    expect(wrapper.get("[data-testid='nx-error-state']").text()).toContain("需要租户上下文");
    expect(wrapper.get("[data-testid='nx-error-state']").text()).toContain("trace-n15");
    expect(wrapper.find("[data-testid='publish-table']").exists()).toBe(false);
  });
});
