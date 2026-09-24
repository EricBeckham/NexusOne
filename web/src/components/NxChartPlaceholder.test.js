/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxChartPlaceholder from "./NxChartPlaceholder.vue";

describe("NxChartPlaceholder", () => {
  it("renders an empty chart canvas and accessible empty label without a fabricated trend", () => {
    const wrapper = mount(NxChartPlaceholder, { props: { title: "收入趋势", description: "数据接入后显示" } });
    expect(wrapper.attributes("role")).toBe("group");
    expect(wrapper.attributes("aria-label")).toBe("收入趋势：暂无图表数据");
    expect(wrapper.text()).toContain("暂无图表数据");
    expect(wrapper.text()).toContain("数据接入后显示");
    expect(wrapper.find(".chart-grid path").exists()).toBe(true);
    expect(wrapper.find("polyline").exists()).toBe(false);
    expect(wrapper.find("canvas").exists()).toBe(false);
  });
});
