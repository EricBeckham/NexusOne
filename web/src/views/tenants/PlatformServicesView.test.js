/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { p1 } from "../../api/client";
import PlatformServicesView from "./PlatformServicesView.vue";

vi.mock("../../api/client", () => ({ p1: { serviceStatus: vi.fn() } }));

describe("P1 platform service status page", () => {
  beforeEach(() => p1.serviceStatus.mockReset());

  it("renders the real component statuses including honest UNKNOWN", async () => {
    p1.serviceStatus.mockResolvedValue({
      checkedAt: "2026-09-24T08:00:00Z",
      services: [
        { code: "database", name: "数据库", status: "UP", message: "数据库连接正常", checkedAt: "2026-09-24T08:00:00Z" },
        { code: "redis", name: "Redis", status: "UNKNOWN", message: "当前缓存适配器未提供 Redis 探测", checkedAt: "2026-09-24T08:00:00Z" },
        { code: "keycloak", name: "Keycloak", status: "DOWN", message: "端点返回异常", checkedAt: "2026-09-24T08:00:00Z" },
      ],
    });
    const wrapper = mount(PlatformServicesView);
    await flushPromises();

    expect(wrapper.text()).toContain("数据库");
    expect(wrapper.get('[data-status="UP"]').text()).toContain("正常");
    expect(wrapper.get('[data-status="UNKNOWN"]').text()).toContain("未知");
    expect(wrapper.get('[data-status="UNKNOWN"]').text()).toContain("未提供 Redis 探测");
    expect(wrapper.get('[data-status="DOWN"]').text()).toContain("异常");
    expect(wrapper.findAll("[data-testid='nx-status-tag']").length).toBe(3);
    expect(p1.serviceStatus).toHaveBeenCalledOnce();
  });

  it("shows list-row skeletons while probing", async () => {
    let resolveStatus;
    p1.serviceStatus.mockReturnValueOnce(new Promise((resolve) => { resolveStatus = resolve; }));
    const wrapper = mount(PlatformServicesView);
    await wrapper.vm.$nextTick();
    expect(wrapper.get("[data-testid='services-loading']").findAll("[data-testid='nx-skeleton']").length).toBeGreaterThan(0);
    resolveStatus({ services: [] });
    await flushPromises();
    expect(wrapper.find("[data-testid='services-loading']").exists()).toBe(false);
    expect(wrapper.text()).toContain("暂无组件数据");
  });

  it("shows retryable API errors without fabricating green states", async () => {
    p1.serviceStatus.mockRejectedValueOnce(Object.assign(new Error("forbidden"), { traceId: "trace-n5" }))
      .mockResolvedValueOnce({ services: [] });
    const wrapper = mount(PlatformServicesView);
    await flushPromises();

    expect(wrapper.text()).toContain("forbidden");
    expect(wrapper.text()).toContain("trace-n5");
    expect(wrapper.find('[data-status="UP"]').exists()).toBe(false);
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");
    expect(p1.serviceStatus).toHaveBeenCalledTimes(2);
  });
});
