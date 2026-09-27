/** @vitest-environment happy-dom */
import { afterEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const actuatorHealth = vi.fn();

vi.mock("../../api/client", () => ({
  p1: { actuatorHealth: (...args) => actuatorHealth(...args) },
}));

import DemoDeployStatusView from "./DemoDeployStatusView.vue";

const SECRET_PATTERN = /CLIENT_SECRET|REPLACE_FROM_SECRET|REPLACE_|password\s*[:=]|secret\s*[:=]/i;

afterEach(() => {
  actuatorHealth.mockReset();
  vi.unstubAllEnvs();
});

async function mountPage() {
  const wrapper = mount(DemoDeployStatusView);
  await flushPromises();
  return wrapper;
}

describe("DemoDeployStatusView", () => {
  it("shows health UP and presence booleans without secret text", async () => {
    vi.stubEnv("VITE_NEXUS_KEYCLOAK_ISSUER", "");
    actuatorHealth.mockResolvedValue({
      status: "UP",
      components: { db: { password: "must-not-render", secret: "also-hidden" } },
    });
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid=demo-deploy-health]").text()).toContain("UP");
    expect(wrapper.get("[data-testid=issuer-presence]").text()).toBe("未配置");
    expect(wrapper.get("[data-testid=server-prod-presence]").text()).toBe("未知");
    expect(wrapper.get("[data-testid=demo-deploy-checklist]").text()).toContain("待外部");
    expect(wrapper.get("[data-testid=demo-deploy-checklist]").text()).toContain("可本地");
    expect(wrapper.text()).not.toMatch(SECRET_PATTERN);
    expect(wrapper.text()).not.toContain("must-not-render");
    expect(wrapper.find("input").exists()).toBe(false);
    expect(wrapper.find("textarea").exists()).toBe(false);
  });

  it("uses the error shell when health returns 503", async () => {
    const error = new Error("HTTP 503");
    error.status = 503;
    actuatorHealth.mockRejectedValue(error);
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid=nx-error-state]").text()).toContain("健康探活失败");
    expect(wrapper.get("[data-testid=nx-error-state]").text()).toContain("HTTP 503");
    expect(wrapper.find("[data-testid=demo-deploy-health]").exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(SECRET_PATTERN);
  });
});
