/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it } from "vitest";
import ProductConfigWizardView from "./ProductConfigWizardView.vue";

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/product-config-wizard", name: "p4-product-config-wizard", component: ProductConfigWizardView },
      { path: "/p4/product-definitions", component: { template: "<div />" } },
      { path: "/p1/form-templates", component: { template: "<div />" } },
      { path: "/p3/workflow", component: { template: "<div />" } },
      { path: "/p3/publish-center", component: { template: "<div />" } },
    ],
  });
  await router.push("/p4/product-config-wizard");
  const wrapper = mount(ProductConfigWizardView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("product config wizard shell (PC1)", () => {
  it("renders four pending steps without fake completion checkmarks", async () => {
    const wrapper = await mountPage();
    expect(wrapper.get("[data-testid='product-config-wizard']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='wizard-steps']").text()).toContain("配表单");
    expect(wrapper.get("[data-testid='wizard-steps']").text()).toContain("配流程");
    expect(wrapper.get("[data-testid='wizard-steps']").text()).toContain("绑 SKU");
    expect(wrapper.get("[data-testid='wizard-steps']").text()).toContain("发布");
    const steps = wrapper.findAll("[data-testid='wizard-steps'] [data-testid^='wizard-step-']");
    expect(steps).toHaveLength(4);
    expect(steps.every((node) => node.attributes("data-status") === "PENDING")).toBe(true);
    expect(wrapper.get("[data-testid='wizard-step-status']").text()).toContain("待开始");
    expect(wrapper.text()).not.toMatch(/已完成|✓/);
    expect(wrapper.get("[data-testid='wizard-cta-form']").attributes("href")).toBe("/p1/form-templates");
  });
});
