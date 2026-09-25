/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxListFooter from "./NxListFooter.vue";

describe("NxListFooter", () => {
  it("shows totals and disables edges (Q2)", async () => {
    const wrapper = mount(NxListFooter, {
      props: { page: 1, pageCount: 3, total: 55, loading: false },
    });
    expect(wrapper.get("[data-testid='nx-list-footer-summary']").text()).toContain("共 55 条");
    expect(wrapper.get("[data-testid='nx-list-footer-summary']").text()).toContain("第 1 / 3 页");
    expect(wrapper.get("[data-testid='nx-list-footer-prev']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='nx-list-footer-next']").attributes("disabled")).toBeUndefined();
    await wrapper.get("[data-testid='nx-list-footer-next']").trigger("click");
    expect(wrapper.emitted("next")).toHaveLength(1);
  });

  it("disables both controls while loading", () => {
    const wrapper = mount(NxListFooter, {
      props: { page: 2, pageCount: 4, total: 80, loading: true },
    });
    expect(wrapper.get("[data-testid='nx-list-footer-prev']").attributes("disabled")).toBeDefined();
    expect(wrapper.get("[data-testid='nx-list-footer-next']").attributes("disabled")).toBeDefined();
  });
});
