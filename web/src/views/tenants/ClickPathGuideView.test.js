/** @vitest-environment happy-dom */
import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { CLICK_PATH_STEPS } from "../../demo/clickPath";
import ClickPathGuideView from "./ClickPathGuideView.vue";

afterEach(() => vi.unstubAllEnvs());

async function mountGuide() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p1/demo-clickpath", component: ClickPathGuideView },
      { path: "/p1/demo-deploy", component: { template: "<div />" } },
      { path: "/login", component: { template: "<div />" } },
      { path: "/p4/market", component: { template: "<div />" } },
      { path: "/p4/activation-applications", component: { template: "<div />" } },
      { path: "/p4/installations", component: { template: "<div />" } },
    ],
  });
  await router.push("/p1/demo-clickpath");
  const wrapper = mount(ClickPathGuideView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("click path guide", () => {
  it("shows the six steps in order and labels them stub when issuer is absent", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "");
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const wrapper = await mountGuide();
    const ids = [...wrapper.get("[data-testid=clickpath-steps]").element.querySelectorAll("li")].map((item) => item.getAttribute("data-testid"));
    expect(ids).toEqual(CLICK_PATH_STEPS.map((step) => `clickpath-step-${step.id}`));
    expect(wrapper.get("[data-testid=clickpath-mode]").text()).toContain("本地 stub");
    expect(wrapper.get("[data-testid=clickpath-mode-login]").text()).toBe("本地 stub");
    expect(wrapper.get("[data-testid=clickpath-docs]").text()).toContain("M7-Keycloak");
    expect(wrapper.find("button").exists()).toBe(false);
    expect(wrapper.find("form").exists()).toBe(false);
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it("labels every step Keycloak when the public issuer is set", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "https://id.example.test/realms/nexus");
    const wrapper = await mountGuide();
    expect(wrapper.get("[data-testid=clickpath-mode]").text()).toContain("Keycloak");
    expect(wrapper.get("[data-testid=clickpath-mode]").text()).toContain("不宣称真机已验收");
    for (const step of CLICK_PATH_STEPS) {
      expect(wrapper.get(`[data-testid=clickpath-mode-${step.id}]`).text()).toBe("Keycloak");
    }
    expect(wrapper.text()).not.toMatch(/CLIENT_SECRET|REPLACE_FROM_SECRET/);
  });
});
