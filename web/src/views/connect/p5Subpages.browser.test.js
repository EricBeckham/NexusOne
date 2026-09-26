/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import ClientsView from "./ClientsView.vue";
import WebhooksView from "./WebhooksView.vue";
import ConnectorsView from "./ConnectorsView.vue";
import CallLogsView from "./CallLogsView.vue";

const mocks = vi.hoisted(() => ({
  clients: vi.fn(),
  client: vi.fn(),
  issueClient: vi.fn(),
  webhooks: vi.fn(),
  webhook: vi.fn(),
  webhookDeliveries: vi.fn(),
  createWebhook: vi.fn(),
  connectors: vi.fn(),
  connectorRuns: vi.fn(),
  runConnector: vi.fn(),
  callLogs: vi.fn(),
}));

vi.mock("../../api/client", () => ({ p5: mocks }));

async function mountAt(path, component) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p5/clients", name: "p5-clients", component: ClientsView },
      { path: "/p5/clients/:id", name: "p5-client", component: ClientsView },
      { path: "/p5/webhooks", name: "p5-webhooks", component: WebhooksView },
      { path: "/p5/webhooks/:id", name: "p5-webhook", component: WebhooksView },
      { path: "/p5/connectors", name: "p5-connectors", component: ConnectorsView },
      { path: "/p5/call-logs", name: "p5-call-logs", component: CallLogsView },
      { path: "/p5/connect", component: { template: "<div />" } },
    ],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(component, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("X5 P5 subpages", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
  });

  it("lists API clients from the API and never renders a stored secret", async () => {
    mocks.clients.mockResolvedValue([
      { id: "c1", name: "scheduler", key_prefix: "nx", scopes: ["integration:call"], rate_limit_per_minute: 60, active: true, secret: "hidden-secret" },
    ]);
    const wrapper = await mountAt("/p5/clients", ClientsView);
    expect(wrapper.text()).toContain("scheduler");
    expect(wrapper.text()).toContain("nx");
    expect(wrapper.text()).not.toContain("hidden-secret");
    expect(wrapper.get("[data-testid='nx-stat-card'] .value").text()).toBe("1");
    expect(wrapper.get("a[href='/p5/clients/c1']").text()).toBe("查看");
  });

  it("shows 暂无 when there are no API clients and reveals a secret only after issue", async () => {
    mocks.clients.mockResolvedValue([]);
    mocks.issueClient.mockResolvedValue({ id: "c2", secret: "once-secret" });
    const wrapper = await mountAt("/p5/clients", ClientsView);
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.get("[data-testid='nx-stat-card'] .value").text()).toBe("0");
    await wrapper.get("input").setValue("billing");
    await wrapper.get("button.primary").trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("once-secret");
    expect(mocks.issueClient).toHaveBeenCalledWith({ name: "billing", scopes: [] });
  });

  it("shows an API client detail without the secret", async () => {
    mocks.client.mockResolvedValue({ id: "c1", name: "scheduler", key_prefix: "nx", secret: "hidden-secret", active: true });
    const wrapper = await mountAt("/p5/clients/c1", ClientsView);
    expect(wrapper.text()).toContain("scheduler");
    expect(wrapper.text()).toContain("nx");
    expect(wrapper.text()).not.toContain("hidden-secret");
  });

  it("lists webhooks and hides delivery payloads", async () => {
    mocks.webhooks.mockResolvedValue([
      { id: "w1", name: "sink", endpoint: "https://hooks.example.test", event_types: ["record.changed"], active: true },
    ]);
    const list = await mountAt("/p5/webhooks", WebhooksView);
    expect(list.text()).toContain("https://hooks.example.test");
    mocks.webhook.mockResolvedValue({ id: "w1", name: "sink", endpoint: "https://hooks.example.test", eventTypes: ["record.changed"], active: true });
    mocks.webhookDeliveries.mockResolvedValue([
      { id: "d1", event_id: "event-1", status: "DELIVERED", attempts: 1, payload: { token: "sekret" } },
    ]);
    const detail = await mountAt("/p5/webhooks/w1", WebhooksView);
    expect(detail.text()).toContain("已送达");
    expect(detail.text()).toContain("签名材料");
    expect(detail.text()).not.toContain("sekret");
  });

  it("shows connector cards from the API and 暂无 when the list is empty", async () => {
    mocks.connectors.mockResolvedValue([{ code: "health", name: "自检", type: "INTERNAL", active: true }]);
    mocks.connectorRuns.mockResolvedValue([
      { connector_code: "health", status: "FAILED", error: "timeout", started_at: "2026-09-24T01:00:00Z", output: { token: "sekret" } },
    ]);
    const wrapper = await mountAt("/p5/connectors", ConnectorsView);
    expect(wrapper.text()).toContain("自检");
    expect(wrapper.text()).toContain("timeout");
    expect(wrapper.text()).toContain("2026-09-24T01:00:00Z");
    expect(wrapper.text()).not.toContain("sekret");
    mocks.connectors.mockResolvedValue([]);
    mocks.connectorRuns.mockResolvedValue([]);
    const empty = await mountAt("/p5/connectors", ConnectorsView);
    expect(empty.text()).toContain("暂无");
    expect(empty.get("[data-testid='nx-stat-card'] .value").text()).toBe("暂无");
  });

  it("lists call logs without request or response bodies", async () => {
    mocks.callLogs.mockResolvedValue([
      {
        id: "log-1",
        client_id: "c1",
        api_path: "/internal/call",
        http_method: "POST",
        status: "SUCCEEDED",
        trace_id: "trace-1",
        created_at: "2026-09-24T00:00:00Z",
        response: { body: "raw-body" },
      },
    ]);
    const wrapper = await mountAt("/p5/call-logs", CallLogsView);
    expect(wrapper.text()).toContain("/internal/call");
    expect(wrapper.text()).toContain("trace-1");
    expect(wrapper.text()).not.toContain("raw-body");
    expect(wrapper.text()).not.toContain("发布成功率");
    mocks.callLogs.mockResolvedValue([]);
    const empty = await mountAt("/p5/call-logs", CallLogsView);
    expect(empty.get("[data-testid='nx-stat-card'] .value").text()).toBe("暂无");
  });
});
