/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  apps: vi.fn(), app: vi.fn(), versions: vi.fn(), version: vi.fn(), skus: vi.fn(), sku: vi.fn(),
  subscriptions: vi.fn(), subscription: vi.fn(), installations: vi.fn(), installation: vi.fn(), upgrade: vi.fn(),
  session: { contextId: "ctx-1", tenantId: "tenant-1" },
}));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: mocks.session }));

import P4ManagementView from "./P4ManagementView.vue";

const app = { id: "app-1", code: "crm", name: "CRM", summary: "客户管理", createdAt: "2026-01-01T00:00:00Z" };
const version = { id: "version-1", appId: "app-1", version: "1.0.0", status: "PUBLISHED", releaseNotes: "首发" };
const sku = { code: "crm-pro", appId: "app-1", name: "专业版", entitlements: { reports: { enabled: true, quota: 20, attributes: {} } } };

async function mountPage(path) {
  const routes = [
    ["/p4/packages", "p4-packages"], ["/p4/skus", "p4-skus"],
    ["/p4/subscriptions", "p4-subscriptions"], ["/p4/installations", "p4-installations"],
  ].map(([routePath, name]) => ({ path: routePath, name, component: P4ManagementView }));
  routes.push({ path: "/p4/market", component: { template: "<div />" } });
  const router = createRouter({ history: createMemoryHistory(), routes });
  await router.push(path); await router.isReady();
  const wrapper = mount(P4ManagementView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P4 management subpages", () => {
  beforeEach(() => {
    Object.values(mocks).filter((value) => typeof value === "function").forEach((fn) => fn.mockReset());
    mocks.session.contextId = "ctx-1"; mocks.session.tenantId = "tenant-1";
    mocks.apps.mockResolvedValue([app]); mocks.skus.mockResolvedValue([sku]); mocks.versions.mockResolvedValue([version]);
    mocks.subscriptions.mockResolvedValue([]); mocks.installations.mockResolvedValue([]); mocks.upgrade.mockResolvedValue({});
  });

  it("shows package details and version history from the read models", async () => {
    const wrapper = await mountPage("/p4/packages");
    expect(mocks.apps).toHaveBeenCalledOnce(); expect(mocks.versions).toHaveBeenCalledWith("app-1");
    expect(wrapper.text()).toContain("CRM"); expect(wrapper.text()).toContain("1.0.0");
    expect(wrapper.text()).toContain("当前读模型未提供");
  });

  it("keeps Product and SKU as separate rows with the returned entitlement", async () => {
    const wrapper = await mountPage("/p4/skus");
    expect(wrapper.text()).toContain("PRODUCT · crm"); expect(wrapper.text()).toContain("专业版");
    expect(wrapper.text()).toContain("reports"); expect(wrapper.text()).toContain("20");
  });

  it("lists subscriptions and installations from tenant-context APIs and submits a selected upgrade", async () => {
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1", tenantId: "tenant-1", skuCode: "crm-pro", status: "ACTIVE", startedAt: "2026-01-01T00:00:00Z" }]);
    const subscriptions = await mountPage("/p4/subscriptions");
    expect(mocks.subscriptions).toHaveBeenCalledOnce(); expect(subscriptions.text()).toContain("专业版");
    subscriptions.unmount();

    mocks.installations.mockResolvedValue([{ id: "install-1", tenantId: "tenant-1", appId: "app-1", versionId: "version-1", status: "INSTALLED" }]);
    const installations = await mountPage("/p4/installations");
    await installations.get("select").setValue("version-1");
    await installations.get("form").trigger("submit"); await flushPromises();
    expect(mocks.installations).toHaveBeenCalled(); expect(mocks.upgrade).toHaveBeenCalledWith("install-1", "version-1");
  });

  it("does not request tenant data without a server tenant context", async () => {
    mocks.session.contextId = "";
    const wrapper = await mountPage("/p4/subscriptions");
    expect(wrapper.text()).toContain("需要租户上下文"); expect(mocks.subscriptions).not.toHaveBeenCalled();
  });
});
