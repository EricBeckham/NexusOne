/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ health: vi.fn() }));

vi.mock("../../api/client", () => ({ p2: { health: mocks.health } }));
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
  beforeEach(() => mocks.health.mockReset());

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
