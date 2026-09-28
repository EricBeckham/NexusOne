/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  portalMenu: vi.fn(),
  portalModules: vi.fn(),
  replacePortalMenu: vi.fn(),
  replacePortalModules: vi.fn(),
}));

vi.mock("../../api/portal", () => ({
  portalMenu: mocks.portalMenu,
  portalModules: mocks.portalModules,
  replacePortalMenu: mocks.replacePortalMenu,
  replacePortalModules: mocks.replacePortalModules,
  readableEntries: vi.fn(),
  currentInstances: () => [],
}));

import PortalConfigView from "./PortalConfigView.vue";

const menu = {
  items: [
    { id: "g", label: "办公", section: "办事", sort: 0, hidden: false },
    { id: "a", parent_id: "g", label: "请假申请", entry_key: "fn:leave:leave_apply", sort: 0, hidden: false },
  ],
};
const modules = { modules: [{ code: "todos", title: "待办", sort: 0, hidden: false }] };

async function mountConfig() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/portal/config", component: PortalConfigView }],
  });
  await router.push("/p3/portal/config");
  const wrapper = mount(PortalConfigView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("PLC4 portal config page", () => {
  beforeEach(() => {
    mocks.portalMenu.mockReset();
    mocks.portalModules.mockReset();
    mocks.replacePortalMenu.mockReset();
    mocks.replacePortalModules.mockReset();
    mocks.portalMenu.mockResolvedValue(menu);
    mocks.portalModules.mockResolvedValue(modules);
    mocks.replacePortalMenu.mockImplementation(async (body) => body);
    mocks.replacePortalModules.mockImplementation(async (body) => body);
  });

  it("loads the menu and saves a hidden entry through the PLC3 replace calls", async () => {
    const wrapper = await mountConfig();
    expect(wrapper.findAll("[data-testid='menu-label']").map((node) => node.element.value)).toEqual(["办公", "请假申请"]);
    const hidden = wrapper.findAll("[data-testid='menu-hidden']");
    await hidden[1].setValue(true);
    await wrapper.get("[data-testid='save-portal']").trigger("click");
    await flushPromises();

    expect(mocks.replacePortalMenu).toHaveBeenCalledWith({
      items: [
        { id: "g", parent_id: null, label: "办公", entry_key: null, section: "办事", sort: 0, hidden: false },
        { id: "a", parent_id: "g", label: "请假申请", entry_key: "fn:leave:leave_apply", section: null, sort: 0, hidden: true },
      ],
    });
    expect(mocks.replacePortalModules).toHaveBeenCalledWith({
      modules: [{ code: "todos", title: "待办", sort: 0, hidden: false }],
    });
    expect(wrapper.get("[data-testid='save-ok']").text()).toContain("已保存");
  });

  it("does not save when the entry key is not fn:应用:表单", async () => {
    const wrapper = await mountConfig();
    await wrapper.findAll("[data-testid='menu-entry']")[1].setValue("leave");
    await wrapper.get("[data-testid='save-portal']").trigger("click");
    await flushPromises();

    expect(wrapper.get("[data-testid='menu-problems']").text()).toContain("fn:应用:表单");
    expect(mocks.replacePortalMenu).not.toHaveBeenCalled();
  });
});
