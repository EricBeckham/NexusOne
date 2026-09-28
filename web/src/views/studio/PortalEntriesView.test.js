/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ api: vi.fn() }));
vi.mock("../../api/client", async (importOriginal) => ({ ...(await importOriginal()), api: mocks.api }));

import PortalEntriesView from "./PortalEntriesView.vue";

describe("portal entries", () => {
  beforeEach(() => mocks.api.mockReset());

  it("groups visible entries and does not treat the list as an action", async () => {
    mocks.api.mockResolvedValue({
      entries: [
        { key: "fn:leave:leave_approve", name: "请假审批", kind: "VIEW", default_group: "审批", sort: 20 },
        { key: "fn:leave:leave_apply", name: "请假申请", kind: "FILL", default_group: "填报", sort: 10 },
      ],
    });
    const wrapper = mount(PortalEntriesView);
    await flushPromises();
    expect(mocks.api).toHaveBeenCalledWith("/api/v1/p3/portal/entries");
    expect(wrapper.findAll("[data-testid='portal-group'] h2").map((node) => node.text())).toEqual(["审批", "填报"]);
    expect(wrapper.findAll("[data-testid='portal-entry'] strong").map((node) => node.text())).toEqual(["请假审批", "请假申请"]);
    expect(wrapper.text()).toContain("填报");
    expect(wrapper.text()).toContain("查看");
    expect(wrapper.find("a,button").exists()).toBe(false);
  });

  it("shows an empty state when nothing is installed", async () => {
    mocks.api.mockResolvedValue({ entries: [] });
    const wrapper = mount(PortalEntriesView);
    await flushPromises();
    expect(wrapper.text()).toContain("还没有可进入的功能入口");
  });

  it("keeps a fill entry visible and shows that its position is not mapped", async () => {
    mocks.api.mockResolvedValue({
      entries: [
        {
          key: "fn:leave:leave_apply",
          name: "请假申请",
          kind: "FILL",
          default_group: "办公",
          sort: 10,
          submittable: false,
          blocked_reason: "管理员尚未配置审批岗位",
        },
      ],
    });
    const wrapper = mount(PortalEntriesView);
    await flushPromises();
    expect(wrapper.get("[data-testid='portal-entry'] strong").text()).toBe("请假申请");
    expect(wrapper.get("[data-testid='portal-blocked']").text()).toBe("管理员尚未配置审批岗位");
    expect(wrapper.find("button").exists()).toBe(false);
  });
});
