/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxSkeleton from "./NxSkeleton.vue";

describe("NxSkeleton", () => {
  it("renders a list-row variant for table/catalog loading (O6)", () => {
    const wrapper = mount(NxSkeleton, { props: { variant: "list-row" } });
    expect(wrapper.attributes("role")).toBe("status");
    expect(wrapper.attributes("data-variant")).toBe("list-row");
    expect(wrapper.classes()).toContain("nx-skeleton--list-row");
    expect(wrapper.text()).toContain("加载中");
  });

  it("renders a form-block variant for create/edit loading", () => {
    const wrapper = mount(NxSkeleton, { props: { variant: "form-block", label: "正在加载表单" } });
    expect(wrapper.attributes("data-variant")).toBe("form-block");
    expect(wrapper.classes()).toContain("nx-skeleton--form-block");
    expect(wrapper.text()).toContain("正在加载表单");
  });
});
