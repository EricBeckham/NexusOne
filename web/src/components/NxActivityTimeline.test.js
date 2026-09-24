/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { h } from "vue";
import { mount } from "@vue/test-utils";
import NxActivityTimeline from "./NxActivityTimeline.vue";

describe("NxActivityTimeline", () => {
  it("shows an empty state when there is no activity", () => {
    expect(mount(NxActivityTimeline).text()).toContain("暂无活动");
  });

  it("renders supplied event details and supports an item slot", () => {
    const items = [{ id: "event-1", title: "版本已发布", description: "v1.2.0", datetime: "2026-09-24T02:00:00Z", timeLabel: "刚刚", actor: "发布中心", tone: "success" }];
    const wrapper = mount(NxActivityTimeline, { props: { items } });
    expect(wrapper.text()).toContain("版本已发布"); expect(wrapper.text()).toContain("v1.2.0");
    expect(wrapper.get("time").attributes("datetime")).toBe(items[0].datetime);
    expect(wrapper.text()).toContain("发布中心");
    const custom = mount(NxActivityTimeline, { props: { items }, slots: { item: ({ item }) => h("span", { class: "custom-event" }, item.title) } });
    expect(custom.find(".custom-event").text()).toBe("版本已发布");
  });
});
