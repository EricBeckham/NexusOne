/**
 * @vitest-environment happy-dom
 */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { p1 } from "../../api/client";
import TenantHomeView from "./TenantHomeView.vue";

vi.mock("../../api/client", () => ({
  p1: {
    listTenants: vi.fn(),
    platformTenant: vi.fn(),
  },
}));

describe("P1 home", () => {
  beforeEach(() => {
    p1.listTenants.mockReset();
    p1.platformTenant.mockReset();
  });

  it("shows counts from the tenant APIs", async () => {
    p1.listTenants.mockResolvedValue([{ id: "a" }, { id: "b" }]);
    p1.platformTenant.mockResolvedValue({ name: "平台租户" });
    const wrapper = mount(TenantHomeView);
    await flushPromises();
    expect(wrapper.text()).toContain("星域");
    expect(wrapper.text()).toContain("2");
    expect(wrapper.text()).toContain("平台租户");
    expect(wrapper.text()).not.toContain("12");
  });

  it("shows 暂无 when the APIs fail", async () => {
    p1.listTenants.mockRejectedValue(new Error("down"));
    p1.platformTenant.mockResolvedValue({});
    const wrapper = mount(TenantHomeView);
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("接口不可用");
    expect(wrapper.text()).not.toMatch(/\d+/);
  });
});
