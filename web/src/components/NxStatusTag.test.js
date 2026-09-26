/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxStatusTag from "./NxStatusTag.vue";

describe("NxStatusTag", () => {
  it("maps common backend statuses to consistent labels and tones", () => {
    const wrapper = mount(NxStatusTag, { props: { status: "FAILED" } });
    expect(wrapper.text()).toBe("失败");
    expect(wrapper.get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("danger");
  });

  it("allows domain-specific copy and tone overrides", () => {
    const wrapper = mount(NxStatusTag, {
      props: { status: "CUSTOM", label: "待审核", tone: "warning" },
    });
    expect(wrapper.text()).toBe("待审核");
    expect(wrapper.get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("warning");
  });

  it("maps draft / published / approval codes used on tenant lists", () => {
    expect(mount(NxStatusTag, { props: { status: "DRAFT" } }).text()).toBe("草稿");
    expect(mount(NxStatusTag, { props: { status: "PUBLISHED" } }).get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("success");
    expect(mount(NxStatusTag, { props: { status: "APPROVED" } }).text()).toBe("已通过");
    expect(mount(NxStatusTag, { props: { status: "INSTALLED" } }).get("[data-testid='nx-status-tag']").attributes("data-tone")).toBe("success");
  });

  it("lets the caller override data-testid (MarketView catalog badge)", () => {
    const wrapper = mount(NxStatusTag, {
      props: { status: "ACTIVE", label: "可开通", tone: "info" },
      attrs: { "data-testid": "catalog-card-badge" },
    });
    expect(wrapper.get("[data-testid='catalog-card-badge']").text()).toBe("可开通");
  });
});
