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

  it("moves the P3 shortcuts and live session context into the product home", async () => {
    mocks.health.mockResolvedValue({ status: "UP" });
    const wrapper = await mountHome();
    const links = wrapper.findAll(".quick-actions a").map((link) => link.attributes("href"));

    expect(wrapper.get("h1").text()).toBe("星构工作台");
    expect(links).toEqual([
      "/p3/ledgers",
      "/p3/forms",
      "/p3/todos",
      "/p3/notifications",
      "/p3/audit",
      "/p3/reports",
      "/p3/dumps",
      "/p3/exports/wizard",
    ]);
    expect(wrapper.text()).toContain("tenant-w3");
    expect(wrapper.text()).toContain("builder@nexus.test");
    expect(wrapper.text()).toContain("IAM health: UP");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).not.toContain("发布成功率");
    expect(wrapper.text()).not.toContain("待办总数");
  });

  it("lists applications returned by the API and counts only that list", async () => {
    mocks.health.mockResolvedValue({ status: "UP" });
    mocks.listApplications.mockResolvedValue({
      items: [
        { id: "app-1", app_code: "crm", display_name: "客户", kind: "CUSTOM", status: "ACTIVE" },
        { id: "app-2", app_code: "hr", display_name: "人事", kind: "CUSTOM", status: "ACTIVE" },
      ],
    });
    const wrapper = await mountHome();
    expect(wrapper.text()).toContain("crm");
    expect(wrapper.text()).toContain("客户");
    expect(wrapper.text()).toContain("CUSTOM");
    expect(wrapper.get("[data-testid='nx-stat-card'] .value").text()).toBe("2");
    const rowLinks = wrapper.findAll(".row-links a").map((link) => link.attributes("href"));
    expect(rowLinks).toEqual(["/p3/ledgers", "/p3/forms", "/p3/ledgers", "/p3/forms"]);
  });

  it("offers a retry when the health probe fails", async () => {
    mocks.health.mockRejectedValueOnce(new Error("health unavailable"));
    mocks.health.mockResolvedValueOnce({ status: "UP" });
    const wrapper = await mountHome();

    expect(wrapper.text()).toContain("health unavailable");
    await wrapper.get("[data-testid='nx-error-state'] button").trigger("click");
    await flushPromises();
    expect(mocks.health).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("IAM health: UP");
  });
});
