/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxStatusTag from "./NxStatusTag.vue";

describe("NxStatusTag", () => {
  it("maps common backend statuses to consistent labels and tones", () => {
    const wrapper = mount(NxStatusTag, { props: { status: "FAILED" } });
    expect(wrapper.text()).toBe("失败");
    expect(wrapper.get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("danger");
  });

  it("allows domain-specific copy and tone overrides", () => {
    const wrapper = mount(NxStatusTag, {
      props: { status: "CUSTOM", label: "待审核", tone: "warning" },
    });
    expect(wrapper.text()).toBe("待审核");
    expect(wrapper.get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("warning");
  });
});
