/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ connectors: vi.fn(), connectorRuns: vi.fn() }));
vi.mock("../../api/client", () => ({ p5: mocks }));

import ConnectHomeView from "./ConnectHomeView.vue";

async function mountHome() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p5", component: ConnectHomeView }, { path: "/p5/connect", component: { template: "<div />" } }],
  });
  await router.push("/p5");
  await router.isReady();
  const wrapper = mount(ConnectHomeView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("W4 Connect home", () => {
  beforeEach(() => {
    mocks.connectors.mockReset();
    mocks.connectorRuns.mockReset();
  });

  it("uses real connector and run data and links to the existing overview", async () => {
    mocks.connectors.mockResolvedValue([
      { code: "health", name: "自检", type: "INTERNAL", active: true },
      { code: "legacy", name: "停用连接器", type: "INTERNAL", active: false },
    ]);
    mocks.connectorRuns.mockResolvedValue([
      { id: "run-1", connectorCode: "health", status: "SUCCESS" },
      { id: "run-2", connectorCode: "health", status: "FAILED" },
    ]);
    const wrapper = await mountHome();

    expect(wrapper.get("h1").text()).toBe("星联工作台");
    expect(wrapper.text()).toContain("可用连接器");
    expect(wrapper.text()).toContain("运行记录");
    expect(wrapper.text()).toContain("SUCCESS");
    expect(wrapper.get("a.primary").attributes("href")).toBe("/p5/connect");
    expect(wrapper.text()).toContain("自检");
    expect(wrapper.text()).toContain("停用连接器");
    expect(wrapper.text()).toContain("停用");
  });

  it("shows empty data honestly when no connector or run exists", async () => {
    mocks.connectors.mockResolvedValue([]);
    mocks.connectorRuns.mockResolvedValue([]);
    const wrapper = await mountHome();
    expect(wrapper.text()).toContain("暂无可用连接器");
    expect(wrapper.text()).toContain("暂无");
  });
});
