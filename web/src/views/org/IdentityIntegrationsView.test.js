/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { p2 } from "../../api/client";
import IdentityIntegrationsView from "./IdentityIntegrationsView.vue";

vi.mock("../../api/client", () => ({ p2: {
  identityProviders: vi.fn(), identityProviderSecretStatus: vi.fn(), createIdentityProvider: vi.fn(),
  updateIdentityProvider: vi.fn(), deleteIdentityProvider: vi.fn(), writeIdentityProviderSecret: vi.fn(),
  rotateIdentityProviderSecret: vi.fn(), testIdentityProviderConnection: vi.fn(), syncIdentityProvider: vi.fn(),
} }));

const provider = {
  id: "provider-1", tenantId: "tenant-1", providerType: "OIDC", displayName: "公司 SSO",
  clientId: "portal", issuer: "https://id.example.test/realm", status: "ENABLED",
  lastSyncAt: "2026-09-24T08:00:00Z", lastSyncOk: false, syncFailCount: 3,
};

describe("P2 identity integrations page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    p2.identityProviders.mockResolvedValue([provider]);
    p2.identityProviderSecretStatus.mockResolvedValue({ configured: true, version: 2 });
  });

  it("shows only API-returned connection data and never a secret value", async () => {
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();

    expect(wrapper.text()).toContain("公司 SSO");
    expect(wrapper.text()).toContain("最近同步");
    expect(wrapper.text()).toContain("失败数");
    expect(wrapper.text()).toContain("连接失败次数");
    expect(wrapper.text()).toContain("3");
    expect(wrapper.text()).toContain("失败");
    expect(wrapper.text()).toContain("2026");
    expect(wrapper.text()).not.toContain("同步人数");
    expect(wrapper.text()).not.toContain("secret");
    expect(p2.identityProviders).toHaveBeenCalledOnce();
    expect(p2.identityProviderSecretStatus).toHaveBeenCalledWith("provider-1");
  });

  it("writes a one-way secret and clears the password field after submit", async () => {
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "轮转密钥").trigger("click");
    const input = wrapper.get('input[type="password"]');
    await input.setValue("new-private-secret");
    await wrapper.get(".dialog form").trigger("submit");
    await flushPromises();

    expect(p2.rotateIdentityProviderSecret).toHaveBeenCalledWith("provider-1", "new-private-secret");
    expect(p2.writeIdentityProviderSecret).not.toHaveBeenCalled();
    expect(wrapper.find('input[type="password"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain("new-private-secret");
  });

  it("uses zero and absent test values honestly instead of inventing sync metrics", async () => {
    p2.identityProviders.mockResolvedValueOnce([{ ...provider, lastSyncAt: null, lastSyncOk: null, syncFailCount: 0 }]);
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();

    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).toContain("暂无测试结果");
    expect(wrapper.text()).toContain("0");
    expect(wrapper.text()).not.toContain("0 人");
  });

  it("shows retryable API errors without rendering provider cards", async () => {
    p2.identityProviders.mockRejectedValueOnce(Object.assign(new Error("forbidden"), { traceId: "trace-n11" }));
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();

    expect(wrapper.text()).toContain("forbidden");
    expect(wrapper.text()).toContain("trace-n11");
    expect(wrapper.find(".provider-card").exists()).toBe(false);
  });

  it("syncs now and shows the returned time and failure count", async () => {
    p2.syncIdentityProvider.mockResolvedValue({
      status: "FAILED", lastSyncAt: "2026-09-24T09:00:00Z", lastSyncOk: false, syncFailCount: 4,
    });
    p2.identityProviders
      .mockResolvedValueOnce([provider])
      .mockResolvedValueOnce([{ ...provider, lastSyncAt: "2026-09-24T09:00:00Z", syncFailCount: 4 }]);
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "立即同步").trigger("click");
    await flushPromises();

    expect(p2.syncIdentityProvider).toHaveBeenCalledWith("provider-1");
    expect(wrapper.text()).toContain("同步失败");
    expect(wrapper.text()).toContain("失败数 4");
    expect(wrapper.text()).not.toContain("同步人数");
  });

  it("hides a sync error that would expose a secret", async () => {
    p2.syncIdentityProvider.mockRejectedValue(new Error("password=must-not-leak"));
    const wrapper = mount(IdentityIntegrationsView);
    await flushPromises();
    await wrapper.findAll("button").find((button) => button.text() === "立即同步").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("同步请求失败");
    expect(wrapper.text()).not.toContain("must-not-leak");
    expect(wrapper.text()).not.toContain("password=");
  });
});
