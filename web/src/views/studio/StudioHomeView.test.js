/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ health: vi.fn(), listApplications: vi.fn() }));

vi.mock("../../api/client", () => ({ p2: { health: mocks.health } }));
vi.mock("../../api/p3", () => ({ p3: { listApplications: mocks.listApplications } }));
vi.mock("../../api/session", () => ({
  session: {
    tenantId: "tenant-w3",
    email: "builder@nexus.test",
    platformMode: false,
  },
}));

import StudioHomeView from "./StudioHomeView.vue";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p3", component: StudioHomeView },
      { path: "/p3/:pathMatch(.*)*", component: { template: "<div />" } },
    ],
  });
  await router.push("/p3");
  await router.isReady();
  const wrapper = mount(StudioHomeView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("W3 Studio home", () => {
  beforeEach(() => {
    mocks.health.mockReset();
    mocks.listApplications.mockReset();
    mocks.listApplications.mockResolvedValue({ items: [] });
  });

  it("groups the P3 entries into cards and shows the session without developer notes (R11)", async () => {
    mocks.health.mockResolvedValue({ status: "UP" });
    const wrapper = await mountHome();
    expect(wrapper.get("h1").text()).toBe("星构工作台");
    expect(wrapper.findAll("[data-testid='studio-entry-group'] h2").map((node) => node.text())).toEqual(["工作台", "流程配置", "数据与审计"]);
    expect(wrapper.findAll(".quick-actions a").map((link) => link.attributes("href"))).toEqual([
      "/p3/applications",
      "/p3/todos",
      "/p3/forms",
      "/p3/ledgers",
      "/p3/notifications",
      "/p3/portal",
      "/p3/workflow",
      "/p3/publish-center",
      "/p3/reports",
      "/p3/exports/wizard",
      "/p3/dumps",
      "/p3/audit",
    ]);
    expect(wrapper.findAll(".nx-page-header .primary, .quick-actions .primary")).toHaveLength(1);
    const sessionCard = wrapper.get("[data-testid='studio-session']");
    expect(sessionCard.text()).toContain("tenant-w3");
    expect(sessionCard.text()).toContain("builder@nexus.test");
    expect(wrapper.get("[data-testid='studio-health']").text()).toBe("运行正常");
    for (const noise of ["IAM health", "TenantContext", "没有趋势聚合接口", "近期活动", "发布成功率", "待办总数"]) {
      expect(wrapper.text()).not.toContain(noise);
    }
    expect(wrapper.text()).toContain("暂无应用");
  });

  it("lists applications returned by the API with the count in the panel title", async () => {
    mocks.health.mockResolvedValue({ status: "UP" });
    mocks.listApplications.mockResolvedValue({
      items: [
        { id: "app-1", app_code: "crm", display_name: "客户", kind: "CUSTOM", status: "ACTIVE" },
        { id: "app-2", app_code: "hr", display_name: "人事", kind: "CUSTOM", status: "ACTIVE" },
      ],
    });
    const wrapper = await mountHome();
    const panel = wrapper.get("[data-testid='studio-apps']");
    expect(panel.text()).toContain("应用 · 2 个");
    expect(panel.text()).toContain("crm");
    expect(panel.text()).toContain("客户");
    expect(panel.text()).toContain("CUSTOM");
    expect(panel.findAll("[data-testid='nx-status-tag']").map((tag) => tag.text())).toEqual(["启用", "启用"]);
    const rowLinks = wrapper.findAll(".row-links a").map((link) => link.attributes("href"));
    expect(rowLinks).toEqual(["/p3/ledgers", "/p3/forms", "/p3/ledgers", "/p3/forms"]);
  });

  it("shows a skeleton while applications load", async () => {
    mocks.health.mockResolvedValue({ status: "UP" });
    mocks.listApplications.mockReturnValue(new Promise(() => {}));
    const wrapper = await mountHome();
    expect(wrapper.get("[data-testid='studio-apps-loading']").attributes("data-variant")).toBe("list-row");
  });

  it("offers a retry when the health probe fails", async () => {

    mocks.health.mockRejectedValueOnce(new Error("health unavailable"));
    mocks.health.mockResolvedValueOnce({ status: "UP" });
    const wrapper = await mountHome();

    expect(wrapper.text()).toContain("health unavailable");
    await wrapper.get("[data-testid='nx-error-state'] button").trigger("click");
    await flushPromises();
    expect(mocks.health).toHaveBeenCalledTimes(2);
    expect(wrapper.get("[data-testid='studio-health']").text()).toBe("运行正常");
  });
});
