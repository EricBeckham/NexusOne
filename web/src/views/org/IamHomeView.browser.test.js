/**
 * @vitest-environment happy-dom
 */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p2 } from "../../api/client";
import { session } from "../../api/session";
import IamHomeView from "./IamHomeView.vue";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p2", component: IamHomeView },
      { path: "/p2/persons", component: { template: "<div />" } },
    ],
  });
  await router.push("/p2");
  await router.isReady();
  return mount(IamHomeView, { global: { plugins: [router] } });
}

vi.mock("../../api/client", async () => {
  const actual = await vi.importActual("../../api/client");
  return {
    ...actual,
    p2: {
      ...actual.p2,
      persons: vi.fn(),
      departments: vi.fn(),
      positions: vi.fn(),
    },
  };
});

describe("P2 home", () => {
  beforeEach(() => {
    session.contextId = "";
    p2.persons.mockReset();
    p2.departments.mockReset();
    p2.positions.mockReset();
  });

  it("stays empty without a tenant context", async () => {
    const wrapper = await mountHome();
    await flushPromises();
    expect(wrapper.find("[data-testid='dashboard-layers']").exists()).toBe(true);
    expect(wrapper.text()).toContain("星权");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("需要租户上下文");
    expect(p2.persons).not.toHaveBeenCalled();
  });

  it("shows counts returned by the IAM APIs", async () => {
    session.contextId = "ctx-1";
    p2.persons.mockResolvedValue([{ id: "p" }]);
    p2.departments.mockResolvedValue([{ id: "d", children: [{ id: "c" }] }]);
    p2.positions.mockResolvedValue([]);
    const wrapper = await mountHome();
    await flushPromises();
    expect(wrapper.findAll("[data-testid='nx-stat-card'] .value").map((node) => node.text())).toEqual(["1", "2", "0"]);
    expect(wrapper.text()).toContain("暂无图表数据");
  });
});
