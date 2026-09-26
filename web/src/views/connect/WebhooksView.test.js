/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import WebhooksView from "./WebhooksView.vue";
import { p5 } from "../../api/client";

vi.mock("../../api/client", () => ({ p5: { webhooks: vi.fn(), webhook: vi.fn(), webhookDeliveries: vi.fn(), createWebhook: vi.fn() } }));

async function open(path = "/p5/webhooks") {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: "/p5/webhooks", name: "p5-webhooks", component: WebhooksView },
    { path: "/p5/webhooks/:id", name: "p5-webhook", component: WebhooksView },
  ] });
  await router.push(path);
  const wrapper = mount(WebhooksView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("Webhook visual states", () => {
  beforeEach(() => Object.values(p5).forEach((fn) => fn.mockReset()));

  it("shows compact subscription rows and shared status labels", async () => {
    p5.webhooks.mockResolvedValue([{ id: "w1", name: "订单回调", endpoint: "https://example.test/hook", event_types: ["order.created"], active: true }]);
    const wrapper = await open();
    expect(wrapper.get("[data-testid='webhooks-table']").classes()).toContain("nx-table--compact");
    expect(wrapper.get("[data-testid='webhooks-table'] [data-testid='nx-status-tag']").text()).toBe("启用");
  });

  it("offers creation as the next step when there is no subscription", async () => {
    p5.webhooks.mockResolvedValue([]);
    const wrapper = await open();
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("暂无 Webhook");
    expect(wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("#create-webhook");
  });

  it("keeps delivery summaries compact and does not show request bodies", async () => {
    p5.webhook.mockResolvedValue({ id: "w1", name: "订单回调", endpoint: "https://example.test/hook", active: true });
    p5.webhookDeliveries.mockResolvedValue([{ id: "d1", event_id: "e1", event_type: "order.created", status: "DELIVERED", attempts: 1, requestBody: "must-not-render" }]);
    const wrapper = await open("/p5/webhooks/w1");
    expect(wrapper.get("[data-testid='webhook-deliveries-table']").classes()).toContain("nx-table--compact");
    expect(wrapper.text()).not.toContain("must-not-render");
  });
});
