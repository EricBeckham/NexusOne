/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxEmpty from "./NxEmpty.vue";

describe("NxEmpty", () => {
  it("renders an accessible default empty state and an optional action", () => {
    const wrapper = mount(NxEmpty, { slots: { action: "<button>立即创建</button>" } });

    expect(wrapper.attributes("role")).toBe("status");
    expect(wrapper.text()).toContain("暂无数据");
    expect(wrapper.get("button").text()).toBe("立即创建");
  });

  it("supports compact custom copy", () => {
    const wrapper = mount(NxEmpty, {
      props: { title: "暂无租户", description: "请先创建租户", compact: true },
    });

    expect(wrapper.classes()).toContain("compact");
    expect(wrapper.text()).toContain("请先创建租户");
  });
});
