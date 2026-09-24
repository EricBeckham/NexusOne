/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({ connectors: vi.fn(), connectorRuns: vi.fn(), issueClient: vi.fn(), createWebhook: vi.fn(), runConnector: vi.fn() }));
vi.mock("../../api/client", () => ({ p5: mocks }));

import ConnectView from "./ConnectView.vue";

describe("P5 integration overview V13", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.connectors.mockResolvedValue([{ code: "health", name: "自检", type: "INTERNAL", active: true }]);
    mocks.connectorRuns.mockResolvedValue([{ id: "run-1", connectorCode: "health", status: "SUCCESS", startedAt: "2026-09-24T01:00:00Z" }]);
    mocks.issueClient.mockResolvedValue({ id: "client-1", secret: "shown-once-secret", scopes: ["integration:call"] });
  });

  it("organizes identity, webhook, and connector capabilities into three cards", async () => {
    const wrapper = mount(ConnectView); await flushPromises();
    expect(wrapper.findAll(".integration-card")).toHaveLength(3);
    expect(wrapper.get("[data-testid='identity-card']").text()).toContain("服务身份");
    expect(wrapper.get("[data-testid='webhook-card']").text()).toContain("Webhook 订阅");
    expect(wrapper.get("[data-testid='connector-card']").text()).toContain("自检");
    expect(wrapper.get("[data-testid='connector-runs']").text()).toContain("成功");
    expect(wrapper.text()).not.toContain("最大尝试次数");
  });

  it("shows an issued secret once in the warning treatment without changing the issuance request", async () => {
    const wrapper = mount(ConnectView); await flushPromises();
    await wrapper.get("[data-testid='identity-card'] .primary").trigger("click"); await flushPromises();
    const secret = wrapper.get("[data-testid='one-time-secret']");
    expect(secret.text()).toContain("shown-once-secret");
    expect(secret.text()).toContain("请立即保存，之后不再显示");
    expect(mocks.issueClient).toHaveBeenCalledWith({ name: "nexus-internal", scopes: ["integration:call"], rateLimitPerMinute: 60 });
  });

  it("renders honest empty states for unavailable connector and run data", async () => {
    mocks.connectors.mockResolvedValue([]); mocks.connectorRuns.mockResolvedValue([]);
    const wrapper = mount(ConnectView); await flushPromises();
    expect(wrapper.text()).toContain("暂无连接器");
    expect(wrapper.text()).toContain("暂无运行记录");
  });
});
