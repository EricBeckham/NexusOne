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
    expect(wrapper.text()).toContain("这些步骤只是标签，当前不会执行。");
    expect(wrapper.text()).not.toContain("发布成功率");
    expect(wrapper.text()).not.toMatch(/\d+%/);
    expect(p3.getPublishCenter).toHaveBeenCalledWith("form-1");
  });

  it("shows an empty state when the tenant has no publish rows", async () => {
    p3.listPublishCenter.mockResolvedValue([]);
    const wrapper = await mountAt("/p3/publish-center");
    expect(wrapper.text()).toContain("暂无发布记录");
    expect(p3.getPublishCenter).not.toHaveBeenCalled();
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
