/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { h } from "vue";
import { mount } from "@vue/test-utils";
import NxTodoList from "./NxTodoList.vue";

describe("NxTodoList", () => {
  it("renders an empty state when there are no tasks", () => {
    expect(mount(NxTodoList).text()).toContain("暂无待办");
  });

  it("renders supplied task data and offers item/action slots", () => {
    const items = [{ id: "task-1", title: "审核应用版本", description: "等待审批", dueAt: "2026-09-25" }];
    const wrapper = mount(NxTodoList, {
      props: { items },
      slots: { actions: ({ item }) => h("button", { class: "task-action" }, `打开 ${item.id}`) },
    });
    expect(wrapper.text()).toContain("审核应用版本"); expect(wrapper.text()).toContain("等待审批");
    expect(wrapper.text()).toContain("截止：2026-09-25"); expect(wrapper.text()).toContain("打开 task-1");
    const custom = mount(NxTodoList, { props: { items }, slots: { item: ({ item }) => h("span", { class: "custom-task" }, item.title) } });
    expect(custom.find(".custom-task").text()).toBe("审核应用版本");
  });
});
