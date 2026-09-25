/** @vitest-environment happy-dom */
import { afterEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
  localStorage.removeItem("nexus.devNotes");
});

async function mountBanner() {
  const { default: NxSourceBanner } = await import("./NxSourceBanner.vue");
  return mount(NxSourceBanner, { props: { body: "GET /api/v1/p3/forms" } });
}

describe("NxSourceBanner (O9)", () => {
  it("renders nothing in the demo by default", async () => {
    const wrapper = await mountBanner();
    expect(wrapper.find("[data-testid='p3-source-banner']").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("/api/v1");
  });

  it("shows the developer note when dev notes are switched on", async () => {
    vi.stubEnv("VITE_NEXUS_DEV_NOTES", "true");
    const wrapper = await mountBanner();
    expect(wrapper.get("[data-testid='p3-source-banner']").text()).toContain("GET /api/v1/p3/forms");
  });
});