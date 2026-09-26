/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { audit } from "../../api/client";
import PlatformAuditView from "./PlatformAuditView.vue";

vi.mock("../../api/client", () => ({ audit: { platformEvents: vi.fn(), verifyChain: vi.fn() } }));

const routes = [{ path: "/p1/audit", name: "p1-audit", component: PlatformAuditView }];

async function open(path = "/p1/audit") {
  const router = createRouter({ history: createMemoryHistory(), routes });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(PlatformAuditView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("P1 platform audit center", () => {
  beforeEach(() => {
    audit.platformEvents.mockReset();
    audit.verifyChain.mockReset();
  });

  it("restores URL filters and opens only a safe read-only summary drawer", async () => {
    audit.platformEvents.mockResolvedValue({
      items: [{
        eventId: "event-1", occurredAt: "2026-09-01T10:00:00Z", tenantId: "tenant-1", actorId: "actor-1",
        action: "p1.tenant.disabled", resourceType: "tenant", resourceId: "acme", result: "REJECTED",
        traceId: "trace-1", platformContext: true, details: { secret: "must-not-render" },
      }],
      total: 41, page: 2, size: 20,
    });
    const { wrapper } = await open("/p1/audit?tenantId=tenant-1&action=p1.tenant.disabled&page=2&size=20");

    expect(wrapper.get('input[aria-label="租户编号"]').element.value).toBe("tenant-1");
    expect(wrapper.text()).toContain("p1.tenant.disabled");
    expect(wrapper.text()).toContain("拒绝");
    expect(wrapper.get("[data-testid='audit-table']").classes()).toContain("nx-table--compact");
    expect(wrapper.get("[data-testid='audit-table'] [data-testid='nx-status-tag']").text()).toBe("拒绝");
    expect(audit.platformEvents).toHaveBeenCalledWith(expect.objectContaining({
      tenantId: "tenant-1", action: "p1.tenant.disabled", page: "2", size: "20",
    }));
    await wrapper.get(".text-button").trigger("click");
    expect(wrapper.get('[role="dialog"]').text()).toContain("tenant-1");
    expect(wrapper.get('[role="dialog"]').text()).toContain("trace-1");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("must-not-render");
    expect(wrapper.get('[role="dialog"]').text()).not.toContain("secret");
    expect(wrapper.get('[role="dialog"]').text()).toContain("追踪编号");
    expect(wrapper.text()).not.toContain("Trace ID");
    expect(wrapper.text()).not.toContain("Actor ID");
  });

  it("writes filters to the URL and restores them when navigating back to the page", async () => {
    audit.platformEvents.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    const { wrapper, router } = await open("/p1/audit?tenantId=tenant-a&action=old.event");
    await wrapper.get('input[aria-label="操作者编号"]').setValue("actor-b");
    await wrapper.get('input[aria-label="事件"]').setValue("new.event");
    await wrapper.get("form").trigger("submit");
    await flushPromises();

    expect(router.currentRoute.value.query.tenantId).toBe("tenant-a");
    expect(router.currentRoute.value.query.actor).toBe("actor-b");
    expect(router.currentRoute.value.query.action).toBe("new.event");
    expect(router.currentRoute.value.query.page).toBe("1");
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.get("[data-testid='nx-empty-next'] button").text()).toBe("清空筛选");
  });

  it("shows API errors and supports retry without exposing payload fields", async () => {
    audit.platformEvents.mockRejectedValueOnce(Object.assign(new Error("forbidden"), { traceId: "trace-n7" }))
      .mockResolvedValueOnce({ items: [], total: 0, page: 1, size: 20 });
    const { wrapper } = await open();
    expect(wrapper.text()).toContain("forbidden");
    expect(wrapper.text()).toContain("trace-n7");
    await wrapper.get('[data-testid="nx-error-state"] button').trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");
    expect(audit.platformEvents).toHaveBeenCalledTimes(2);
  });

  it("verifies the platform hash chain without rendering an event payload", async () => {
    audit.platformEvents.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    audit.verifyChain.mockResolvedValue({
      intact: false, checked: 3, failure: "event hash mismatch at 1", details: { secret: "must-not-render" },
    });
    const { wrapper } = await open();
    await wrapper.get("[data-testid=verify-chain]").trigger("click");
    await flushPromises();

    expect(audit.verifyChain).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("链已破坏");
    expect(wrapper.text()).toContain("3");
    expect(wrapper.text()).toContain("event hash mismatch at 1");
    expect(wrapper.text()).not.toContain("must-not-render");
    expect(wrapper.text()).not.toContain("星构");
  });
});
