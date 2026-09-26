/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxResult from "./NxResult.vue";

describe("NxResult (S3)", () => {
  it("renders icon, title, description, and next-step slot", () => {
    const wrapper = mount(NxResult, {
      props: {
        title: "申请已提交",
        description: "可在我的申请中查看进度。",
        tone: "success",
      },
      slots: { next: '<a href="/p3/todos?box=requests">查看我的申请</a>' },
    });
    expect(wrapper.get("[data-testid='nx-result']").attributes("data-tone")).toBe("success");
    expect(wrapper.get("[data-testid='nx-result-title']").text()).toBe("申请已提交");
    expect(wrapper.get("[data-testid='nx-result-description']").text()).toContain("我的申请");
    expect(wrapper.get("[data-testid='nx-result-icon']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='nx-result-next']").text()).toContain("查看我的申请");
  });

  it("omits the next row when no action slot is provided", () => {
    const wrapper = mount(NxResult, { props: { title: "已完成" } });
    expect(wrapper.find("[data-testid='nx-result-next']").exists()).toBe(false);
  });
});
