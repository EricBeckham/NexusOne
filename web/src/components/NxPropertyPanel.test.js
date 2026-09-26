/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import NxPropertyPanel from "./NxPropertyPanel.vue";

const TABS = [
  { id: "basic", label: "基本" },
  { id: "validate", label: "校验" },
  { id: "look", label: "外观" },
];

describe("NxPropertyPanel", () => {
  it("renders tab slots and switches active panel", async () => {
    const wrapper = mount(NxPropertyPanel, {
      props: { tabs: TABS, modelValue: "basic", "onUpdate:modelValue": (v) => wrapper.setProps({ modelValue: v }) },
      slots: {
        basic: "<p data-testid='slot-basic'>基本内容</p>",
        validate: "<p data-testid='slot-validate'>校验内容</p>",
        look: "<p data-testid='slot-look'>外观内容</p>",
      },
    });

    expect(wrapper.get("[data-testid='nx-property-panel']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='nx-property-tab-basic']").attributes("aria-selected")).toBe("true");
    expect(wrapper.get("[data-testid='nx-property-tab-basic']").attributes("aria-selected")).toBe("true");
    expect(wrapper.get("[data-testid='nx-property-tab-validate']").attributes("aria-selected")).toBe("false");

    await wrapper.get("[data-testid='nx-property-tab-validate']").trigger("click");
    await nextTick();
    expect(wrapper.props("modelValue")).toBe("validate");
    expect(wrapper.get("[data-testid='nx-property-tab-validate']").attributes("aria-selected")).toBe("true");
    expect(wrapper.get("[data-testid='slot-validate']").exists()).toBe(true);
  });

  it("falls back to first tab when modelValue is unknown", () => {
    const wrapper = mount(NxPropertyPanel, {
      props: { tabs: TABS, modelValue: "missing" },
      slots: { basic: "<span>ok</span>" },
    });
    expect(wrapper.get("[data-testid='nx-property-tab-basic']").attributes("aria-selected")).toBe("true");
  });

  it("moves focus with arrow keys across tabs", async () => {
    const wrapper = mount(NxPropertyPanel, {
      props: { tabs: TABS, modelValue: "basic", "onUpdate:modelValue": (v) => wrapper.setProps({ modelValue: v }) },
      attachTo: document.body,
    });
    const first = wrapper.get("[data-testid='nx-property-tab-basic']");
    await first.trigger("keydown", { key: "ArrowRight" });
    await nextTick();
    expect(wrapper.props("modelValue")).toBe("validate");
    wrapper.unmount();
  });
});