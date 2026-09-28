/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  portalMenu: vi.fn(),
  portalModules: vi.fn(),
  readableEntries: vi.fn(),
  currentInstances: vi.fn(),
}));

vi.mock("../../api/portal", () => ({
  portalMenu: mocks.portalMenu,
  portalModules: mocks.portalModules,
  readableEntries: mocks.readableEntries,
  currentInstances: mocks.currentInstances,
}));

import MemberPortalView from "./MemberPortalView.vue";

async function mountPortal() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/portal", component: MemberPortalView },
      { path: "/:pathMatch(.*)*", component: { template: "<div />" } },
    ],
  });
  await router.push("/portal");
  const wrapper = mount(MemberPortalView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("PLC5 member portal", () => {
  beforeEach(() => {
    mocks.portalMenu.mockReset();
    mocks.portalModules.mockReset();
    mocks.readableEntries.mockReset();
    mocks.currentInstances.mockReset();
    mocks.currentInstances.mockReturnValue([]);
  });

  it("shows a readable menu entry after load and no instance entries", async () => {
    mocks.portalMenu.mockResolvedValue({
      items: [
        { id: "group", label: "办公", sort: 1, hidden: false },
        { id: "apply", parent_id: "group", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 2, hidden: false },
        { id: "expense", label: "报销", entry_key: "fn:expense:expense_apply", sort: 3, hidden: false },
      ],
    });
    mocks.portalModules.mockResolvedValue({ modules: [{ code: "todos", title: "待办", sort: 1, hidden: false }] });
    mocks.readableEntries.mockResolvedValue(["fn:leave:leave_apply"]);

    const wrapper = await mountPortal();

    expect(wrapper.find("[data-testid='portal-module']").text()).toContain("待办");
    expect(wrapper.text()).toContain("请假申请");
    expect(wrapper.text()).not.toContain("报销");
    expect(wrapper.text()).toContain("还没有应用实例");
    expect(wrapper.text()).not.toContain("实例查询尚未接通");
    expect(mocks.readableEntries).toHaveBeenCalledWith(["fn:leave:leave_apply", "fn:expense:expense_apply"]);
  });

  it("lists an installed application and hides an uninstalled one", async () => {
    mocks.portalMenu.mockResolvedValue({
      items: [{ id: "apply", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 1, hidden: false }],
    });
    mocks.portalModules.mockResolvedValue({ modules: [] });
    mocks.readableEntries.mockResolvedValue(["fn:leave:leave_apply"]);
    mocks.currentInstances.mockResolvedValue([
      { instance_id: "leave-1", status: "INSTALLED", app_code: "leave", version: "1.0.0", entries: ["fn:leave:leave_apply"] },
      { instance_id: "gone", status: "UNINSTALLED", app_code: "expense", version: "1.0.0", entries: [] },
    ]);

    const wrapper = await mountPortal();

    expect(wrapper.get("[data-testid='portal-apps']").text()).toContain("leave");
    expect(wrapper.get("[data-testid='portal-apps']").text()).toContain("1.0.0");
    expect(wrapper.get("[data-testid='portal-apps']").text()).not.toContain("expense");
    expect(wrapper.text()).toContain("请假申请");
  });

  it("keeps the menu when the instance call fails", async () => {
    mocks.portalMenu.mockResolvedValue({
      items: [{ id: "apply", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 1, hidden: false }],
    });
    mocks.portalModules.mockResolvedValue({ modules: [] });
    mocks.readableEntries.mockResolvedValue(["fn:leave:leave_apply"]);
    mocks.currentInstances.mockRejectedValue(new Error("down"));

    const wrapper = await mountPortal();

    expect(wrapper.text()).toContain("请假申请");
    expect(wrapper.text()).toContain("还没有应用实例");
    expect(wrapper.text()).not.toContain("门户暂时打不开");
  });

  it("does not list every menu entry when the readable-entry call fails", async () => {
    mocks.portalMenu.mockResolvedValue({
      items: [{ id: "apply", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 1, hidden: false }],
    });
    mocks.portalModules.mockResolvedValue({ modules: [] });
    mocks.readableEntries.mockRejectedValue(Object.assign(new Error("Not Found"), { status: 404 }));

    const wrapper = await mountPortal();

    expect(wrapper.text()).toContain("门户暂时打不开");
    expect(wrapper.text()).not.toContain("请假申请");
  });
});
