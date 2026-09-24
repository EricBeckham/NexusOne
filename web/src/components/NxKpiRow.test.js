/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxKpiRow from "./NxKpiRow.vue";

describe("NxKpiRow", () => {
  it("renders actual KPI inputs through the shared stat card and preserves empty values", () => {
    const wrapper = mount(NxKpiRow, {
      props: { ariaLabel: "租户指标", items: [
        { id: "tenants", label: "租户总数", value: 6, unit: "个" },
        { id: "active", label: "活跃租户", value: null },
      ] },
    });
    expect(wrapper.attributes("aria-label")).toBe("租户指标");
    expect(wrapper.findAll("[data-testid='nx-stat-card']")).toHaveLength(2);
    expect(wrapper.text()).toContain("6");
    expect(wrapper.text()).toContain("暂无");
  });

  it("shows a truthful empty state when no KPI data is provided", () => {
    const wrapper = mount(NxKpiRow);
    expect(wrapper.text()).toContain("暂无统计");
    expect(wrapper.findAll("[data-testid='nx-stat-card']")).toHaveLength(0);
  });
});
