/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxPanelCard from "./NxPanelCard.vue";

describe("NxPanelCard", () => {
  it("provides an accessible heading with description, action, body, and footer slots", () => {
    const wrapper = mount(NxPanelCard, {
      props: { title: "安装趋势", description: "按月查看" },
      slots: { actions: "<button>筛选</button>", default: "<p>暂无图表数据</p>", footer: "<span>来源：API</span>" },
    });
    const heading = wrapper.get("h2");
    expect(wrapper.attributes("aria-labelledby")).toBe(heading.attributes("id"));
    expect(wrapper.text()).toContain("安装趋势");
    expect(wrapper.text()).toContain("按月查看");
    expect(wrapper.text()).toContain("筛选");
    expect(wrapper.text()).toContain("暂无图表数据");
    expect(wrapper.text()).toContain("来源：API");
  });
});
