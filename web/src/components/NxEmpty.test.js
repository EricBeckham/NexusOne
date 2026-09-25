/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxEmpty from "./NxEmpty.vue";

describe("NxEmpty", () => {
  it("renders icon, title, description, and a next-step slot (O5)", () => {
    const wrapper = mount(NxEmpty, {
      props: { title: "暂无租户", description: "创建第一个租户后，可开始配置组织和业务应用。" },
      slots: { next: "<button type='button'>新建租户</button>" },
    });

    expect(wrapper.attributes("role")).toBe("status");
    expect(wrapper.get("[data-testid='nx-empty-icon']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("暂无租户");
    expect(wrapper.get("[data-testid='nx-empty-description']").text()).toContain("创建第一个租户");
    expect(wrapper.get("[data-testid='nx-empty-next'] button").text()).toBe("新建租户");
  });

  it("keeps #action as a compatibility alias for the next-step slot", () => {
    const wrapper = mount(NxEmpty, { slots: { action: "<button>立即创建</button>" } });

    expect(wrapper.text()).toContain("暂无数据");
    expect(wrapper.get("[data-testid='nx-empty-next'] button").text()).toBe("立即创建");
  });

  it("supports compact custom copy without a next step", () => {
    const wrapper = mount(NxEmpty, {
      props: { title: "暂无租户", description: "请先创建租户", compact: true },
    });

    expect(wrapper.classes()).toContain("compact");
    expect(wrapper.text()).toContain("请先创建租户");
    expect(wrapper.find("[data-testid='nx-empty-next']").exists()).toBe(false);
  });
});
