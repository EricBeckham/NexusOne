/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxFilterBar from "./NxFilterBar.vue";

describe("NxFilterBar (O10, spec §11)", () => {
  it("lays conditions, 重置 and a summary on one flat row without a card", async () => {
    const wrapper = mount(NxFilterBar, {
      props: { label: "目录筛选", testid: "catalog" },
      slots: {
        default: '<label class="nx-filter-field">关键词<input data-testid="kw" /></label>',
        summary: "共 3 个",
      },
    });
    const form = wrapper.get("form");
    expect(form.attributes("role")).toBe("search");
    expect(form.attributes("aria-label")).toBe("目录筛选");
    expect(form.classes()).not.toContain("nx-card");
    expect(wrapper.find("[data-testid='catalog-search']").exists()).toBe(false);
    expect(wrapper.get(".summary").text()).toBe("共 3 个");
    await wrapper.get("[data-testid='catalog-reset']").trigger("click");
    expect(wrapper.emitted("reset")).toHaveLength(1);
    await form.trigger("submit");
    expect(wrapper.emitted("search")).toHaveLength(1);
    expect(wrapper.find("[data-testid='catalog-more']").exists()).toBe(false);
  });

  it("shows 查询 when searchable and folds advanced conditions under 更多筛选", async () => {
    const wrapper = mount(NxFilterBar, {
      props: { searchable: true },
      slots: { default: "<span />", more: '<label class="nx-filter-field">类型<select /></label>' },
    });
    await wrapper.get("[data-testid='filter-search']").trigger("click");
    const toggle = wrapper.get("[data-testid='filter-more']");
    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(wrapper.find("[data-testid='filter-more-panel']").exists()).toBe(false);
    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("[data-testid='filter-more-panel']").text()).toContain("类型");
  });
});