/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { h } from "vue";
import { mount } from "@vue/test-utils";
import NxHealthList from "./NxHealthList.vue";

describe("NxHealthList", () => {
  it("renders a truthful empty state and supports an empty slot", () => {
    const empty = mount(NxHealthList);
    expect(empty.text()).toContain("暂无健康状态");
    const customEmpty = mount(NxHealthList, { slots: { empty: "<p>健康数据不可用</p>" } });
    expect(customEmpty.text()).toContain("健康数据不可用");
  });

  it("renders provided health rows and allows row customization", () => {
    const items = [{ id: "api", label: "API", detail: "响应检查", status: "ACTIVE", statusLabel: "正常", tone: "success" }];
    const wrapper = mount(NxHealthList, { props: { items } });
    expect(wrapper.text()).toContain("API"); expect(wrapper.text()).toContain("响应检查"); expect(wrapper.text()).toContain("正常");
    const customized = mount(NxHealthList, { props: { items }, slots: { item: ({ item }) => h("strong", { class: "custom-row" }, item.label) } });
    expect(customized.find(".custom-row").text()).toBe("API");
    expect(customized.find(".health-indicator").exists()).toBe(false);
  });
});
