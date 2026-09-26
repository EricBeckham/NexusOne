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

// Real wire shapes: the product mapper is SNAKE_CASE.
const app = { id: "app-1", code: "crm", name: "CRM", summary: "客户管理", created_at: "2026-01-01T00:00:00Z" };
const version = { id: "version-1", app_id: "app-1", version: "1.0.0", status: "PUBLISHED", release_notes: "首发" };
const sku = { code: "crm-pro", app_id: "app-1", name: "专业版", entitlements: { reports: { enabled: true, quota: 20, attributes: {} } } };

async function mountPage(path) {
  const routes = [
    ["/p4/packages", "p4-packages"], ["/p4/skus", "p4-skus"],
    ["/p4/subscriptions", "p4-subscriptions"], ["/p4/installations", "p4-installations"],
  ].map(([routePath, name]) => ({ path: routePath, name, component: P4ManagementView }));
  routes.push({ path: "/p4/market", component: { template: "<div />" } });
  routes.push({ path: "/p4/installations/:id/start", component: { template: "<div />" } });
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
    expect(wrapper.get("[data-testid='package-declarations']").text()).toBe("暂未开放查看");
    // S9: no read-model / API wording on the tenant path.
    expect(wrapper.text()).not.toMatch(/读模型|接口|Product/);
  });

  it("keeps Product and SKU as separate rows with the returned entitlement", async () => {
    const wrapper = await mountPage("/p4/skus");
    expect(wrapper.text()).toContain("商品 · crm"); expect(wrapper.text()).not.toMatch(/PRODUCT|Product|app-1/); expect(wrapper.text()).toContain("专业版");
    expect(wrapper.text()).toContain("reports"); expect(wrapper.text()).toContain("20");
  });

  it("lists subscriptions and installations from tenant-context APIs and submits a selected upgrade", async () => {
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1", tenant_id: "tenant-1", sku_code: "crm-pro", status: "ACTIVE", started_at: "2026-01-01T00:00:00Z" }]);
    const subscriptions = await mountPage("/p4/subscriptions");
    expect(mocks.subscriptions).toHaveBeenCalledOnce(); expect(subscriptions.text()).toContain("专业版");
    subscriptions.unmount();

    mocks.installations.mockResolvedValue([{ id: "install-1", tenant_id: "tenant-1", app_id: "app-1", version_id: "version-1", status: "INSTALLED", updated_at: "2026-01-02T03:04:05Z" }]);
    const installations = await mountPage("/p4/installations");
    expect(installations.text()).toContain("我的已开通");
    expect(installations.get('[data-testid="installation-status"]').text()).toBe("已开通");
    expect(installations.get('[data-testid="start-application"]').text()).toBe("发起申请");
    expect(installations.get('[data-testid="installation-upgrade"]').attributes("open")).toBeUndefined();
    // O11: one Primary per installed-product card; the upgrade submit is secondary and waits for a target version.
    const card = installations.get(".installation-card");
    expect(card.findAll(".primary, .nx-btn--primary").map((node) => node.text())).toEqual(["发起申请"]);
    expect(card.get('[data-testid="upgrade-submit"]').classes()).toContain("nx-btn--default");
    expect(card.get('[data-testid="upgrade-submit"]').attributes("disabled")).toBeDefined();
    expect(card.text()).toContain("预检");
    expect(card.text()).not.toContain("Precheck");
    expect(installations.text()).toContain("当前版本 1.0.0");
    expect(installations.get(".installation-identity h3").text()).toBe("CRM");
    // Q8: a flat list of product cards (no card-in-card); status beside the name; code and version in the meta line.
    expect(installations.find(".installations.nx-card").exists()).toBe(false);
    expect(installations.get(".installation-title [data-testid='installation-status']").text()).toBe("已开通");
    expect(installations.get(".installation-meta").text()).toContain("crm");
    expect(installations.get('[data-testid="installations-count"]').text()).toBe("共 1 个已开通产品");
    // S8: the fixed "暂无数据" upgrade facts are a developer note, not shown in the demo.
    expect(installations.find('[data-testid="upgrade-facts"]').exists()).toBe(false);
    expect(installations.get('[data-testid="installation-upgrade"]').text()).not.toContain("Schema 变更");
    const crumb = installations.get('[data-testid="nx-page-crumb"]');
    expect(crumb.get("a").attributes("href")).toBe("/p4/market");
    expect(crumb.get('[aria-current="page"]').text()).toBe("我的已开通");
    expect(installations.findAll(".installation-card")[0].findAll(".primary, .nx-btn--primary")).toHaveLength(1);
    expect(installations.findAll('[data-testid="nx-page-header"] .primary, [data-testid="nx-page-header"] .nx-btn--primary')).toHaveLength(0);
    expect(installations.text()).not.toContain("未命名应用");
    await installations.get("select").setValue("version-1");
    await installations.get("form").trigger("submit"); await flushPromises();
    expect(mocks.installations).toHaveBeenCalled(); expect(mocks.upgrade).toHaveBeenCalledWith("install-1", "version-1");
  });

  it("does not request tenant data without a server tenant context", async () => {
    mocks.session.contextId = "";
    const wrapper = await mountPage("/p4/subscriptions");
    expect(wrapper.text()).toContain("需要先进入租户"); expect(wrapper.text()).not.toContain("上下文"); expect(mocks.subscriptions).not.toHaveBeenCalled();
  });

  it("gives an empty subscription list a reason and the next step (S10)", async () => {
    mocks.subscriptions.mockResolvedValue([]);
    const wrapper = await mountPage("/p4/subscriptions");
    const empty = wrapper.get('[data-testid="subscriptions-empty"]');
    expect(empty.text()).toContain("还没有订阅");
    expect(empty.text()).toContain("在应用目录选择 SKU 订阅开通后");
    expect(empty.get("a").attributes("href")).toBe("/p4/market");
  });
});
