/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  apps: vi.fn(), app: vi.fn(), versions: vi.fn(), version: vi.fn(), skus: vi.fn(), sku: vi.fn(),
  subscriptions: vi.fn(), subscription: vi.fn(), installations: vi.fn(), installation: vi.fn(), upgrade: vi.fn(),
  upgradeSteps: vi.fn(), upgradeFacts: vi.fn(), commercialEntitlements: vi.fn(),
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
    mocks.subscriptions.mockResolvedValue([]); mocks.installations.mockResolvedValue([]); mocks.upgrade.mockResolvedValue({}); mocks.commercialEntitlements.mockResolvedValue([]);
  });

  it("shows package details and version history from the read models", async () => {
    const wrapper = await mountPage("/p4/packages");
    expect(mocks.apps).toHaveBeenCalledOnce(); expect(mocks.versions).toHaveBeenCalledWith("app-1");
    expect(wrapper.text()).toContain("CRM"); expect(wrapper.text()).toContain("1.0.0");
    expect(wrapper.get("[data-testid='package-declarations']").text()).toBe("暂未开放查看");
    expect(wrapper.get("[data-testid='packages-table']").exists()).toBe(true);
    expect(wrapper.get("[data-testid='package-versions-table']").exists()).toBe(true);
    // S9: no read-model / API wording on the tenant path.
    expect(wrapper.text()).not.toMatch(/读模型|接口|Product/);
  });

  it("keeps Product and SKU as separate rows with the returned entitlement", async () => {
    const wrapper = await mountPage("/p4/skus");
    expect(wrapper.text()).toContain("商品 · crm"); expect(wrapper.text()).not.toMatch(/PRODUCT|Product|app-1/); expect(wrapper.text()).toContain("专业版");
    expect(wrapper.text()).toContain("reports"); expect(wrapper.text()).toContain("20");
    expect(wrapper.get("[data-testid='sku-table-app-1']").exists()).toBe(true);
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
    const stepper = installations.get('[data-testid="upgrade-stepper"]');
    expect(stepper.findAll("[data-status]")).toHaveLength(6);
    expect(stepper.findAll('[data-status="UNKNOWN"]')).toHaveLength(6);
    expect(stepper.findAll('[data-status="DONE"], [data-status="ACTIVE"]').length).toBe(0);
    expect(stepper.text()).toContain("暂无");
    expect(stepper.text()).not.toMatch(/完成|进行中/);
    expect(installations.text()).toContain("当前版本 1.0.0");
    expect(installations.get(".installation-identity h3").text()).toBe("CRM");
    // Q8: a flat list of product cards (no card-in-card); status beside the name; code and version in the meta line.
    expect(installations.find(".installations.nx-card").exists()).toBe(false);
    expect(installations.get(".installation-title [data-testid='installation-status']").text()).toBe("已开通");
    expect(installations.get(".installation-meta").text()).toContain("crm");
    expect(installations.get('[data-testid="installations-count"]').text()).toBe("共 1 个已开通产品");
    // S8: the fixed "暂无数据" upgrade facts are a developer note, not shown in the demo.
    // XB8: facts come from the server when the fold opens — nothing before that.
    expect(installations.find('[data-testid="upgrade-facts"]').exists()).toBe(false);
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

  it("opens the upgrade fold with the server's step state and facts — UNKNOWN stays neutral (XB8)", async () => {
    mocks.installations.mockResolvedValue([{ id: "install-1", tenant_id: "tenant-1", app_id: "app-1", version_id: "version-1", status: "INSTALLED", updated_at: "2026-01-02T03:04:05Z" }]);
    // Real XB6/XB7 wire shapes (snake_case keys where multi-word).
    mocks.upgradeSteps.mockResolvedValue({
      installation_id: "install-1",
      recorded: false,
      note: "升级步骤没有被记录：当前升级只切换版本，不执行也不记录预检到验证的各个步骤。",
      steps: ["precheck", "dependency", "schema_migration", "config", "switch", "verify"].map((key, index) => ({
        key, label: ["预检", "依赖检查", "结构扩展与迁移", "配置", "切换", "验证"][index], status: "UNKNOWN",
      })),
    });
    mocks.upgradeFacts.mockResolvedValue({
      installation_id: "install-1",
      current_version_id: "version-1",
      facts: [
        { key: "compatibility", label: "兼容范围", value: null, note: "应用版本没有声明兼容范围。" },
        { key: "schema_change", label: "Schema 变更", value: null, note: "应用版本没有声明结构变更。" },
        { key: "data_migration", label: "数据迁移", value: null, note: "升级只切换版本，不执行数据迁移，也没有迁移记录。" },
        { key: "rollback", label: "回滚能力", value: "仅切换版本", note: "可以切回同一应用的已发布版本；数据不会随之回滚。" },
      ],
    });
    const wrapper = await mountPage("/p4/installations");
    const fold = wrapper.get('[data-testid="installation-upgrade"]');
    fold.element.open = true;
    await fold.trigger("toggle");
    await flushPromises();

    expect(mocks.upgradeSteps).toHaveBeenCalledWith("install-1");
    const stepper = wrapper.get('[data-testid="upgrade-stepper"]');
    expect(stepper.findAll('[data-status="UNKNOWN"]')).toHaveLength(6);
    expect(stepper.find('[data-testid="upgrade-step-schema_migration"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="upgrade-steps-note"]').text()).toContain("升级步骤没有被记录");
    expect(wrapper.get('[data-testid="upgrade-fact-compatibility"]').text()).toContain("暂无");
    expect(wrapper.get('[data-testid="upgrade-fact-schema_change"]').text()).toContain("应用版本没有声明结构变更");
    expect(wrapper.get('[data-testid="upgrade-fact-rollback"]').text()).toContain("仅切换版本");
    expect(wrapper.text()).not.toContain("暂无数据");

    // Closing and reopening does not refetch.
    await fold.trigger("toggle");
    expect(mocks.upgradeSteps).toHaveBeenCalledTimes(1);
  });

  it("colors only statuses the server really reports and stays UNKNOWN when the read fails (XB8)", async () => {
    mocks.installations.mockResolvedValue([{ id: "install-1", app_id: "app-1", version_id: "version-1", status: "INSTALLED" }]);
    mocks.upgradeSteps.mockResolvedValueOnce({
      recorded: true,
      steps: [
        { key: "precheck", label: "预检", status: "DONE" },
        { key: "dependency", label: "依赖检查", status: "ACTIVE" },
        { key: "schema_migration", label: "结构扩展与迁移", status: "WEIRD" },
      ],
    });
    mocks.upgradeFacts.mockResolvedValueOnce({ facts: [] });
    const wrapper = await mountPage("/p4/installations");
    const fold = wrapper.get('[data-testid="installation-upgrade"]');
    fold.element.open = true;
    await fold.trigger("toggle");
    await flushPromises();
    const statuses = wrapper.findAll('[data-testid="upgrade-stepper"] [data-status]').map((step) => step.attributes("data-status"));
    expect(statuses).toEqual(["DONE", "ACTIVE", "UNKNOWN"]);
    expect(wrapper.find('[data-testid="upgrade-steps-note"]').exists()).toBe(false);
    wrapper.unmount();

    mocks.upgradeSteps.mockRejectedValueOnce(new Error("offline"));
    mocks.upgradeFacts.mockRejectedValueOnce(new Error("offline"));
    const failed = await mountPage("/p4/installations");
    const failedFold = failed.get('[data-testid="installation-upgrade"]');
    failedFold.element.open = true;
    await failedFold.trigger("toggle");
    await flushPromises();
    expect(failed.get('[data-testid="upgrade-info-error"]').text()).toContain("升级信息暂时无法读取");
    expect(failed.findAll('[data-testid="upgrade-stepper"] [data-status="UNKNOWN"]')).toHaveLength(6);
    expect(failed.findAll('[data-testid="upgrade-stepper"] [data-status="DONE"]')).toHaveLength(0);
  });

  it("fills 权益状态 from the entitlement summary and never shows the old placeholder (XB11)", async () => {
    mocks.subscriptions.mockResolvedValue([
      { id: "sub-1", tenant_id: "tenant-1", sku_code: "crm-pro", status: "ACTIVE", started_at: "2026-01-01T00:00:00Z" },
      { id: "sub-2", tenant_id: "tenant-1", sku_code: "crm-pro", status: "ACTIVE", started_at: "2026-02-01T00:00:00Z" },
      { id: "sub-3", tenant_id: "tenant-1", sku_code: "crm-pro", status: "CANCELLED", started_at: "2026-03-01T00:00:00Z" },
      { id: "sub-4", tenant_id: "tenant-1", sku_code: "crm-pro", status: "ACTIVE", started_at: "2026-04-01T00:00:00Z" },
    ]);
    // Real XB10 wire shape: snake_case summary on each commercial-entitlement view.
    mocks.commercialEntitlements.mockResolvedValue([
      { subscription_id: "sub-1", summary: { enabled_count: 2, total_count: 3, projected: false } },
      { subscription_id: "sub-2", summary: { enabled_count: 3, total_count: 3, projected: true, projection_version: 4, projected_at: "2026-02-01T00:00:00Z" } },
      // XB14: the product mapper omits an unknown `projected`, so the key is absent on the real wire.
      { subscription_id: "sub-3", summary: { enabled_count: 0, total_count: 0 } },
    ]);
    const wrapper = await mountPage("/p4/subscriptions");
    const cells = wrapper.findAll('[data-testid="subscription-entitlement"]').map((cell) => cell.text());
    expect(cells).toEqual(["2/3 项启用未生效", "3/3 项启用当前生效", "无权益项", "暂无"]);
    expect(wrapper.text()).not.toContain("暂无投影读模型");
  });

  it("keeps the subscription list when the entitlement summary cannot be read (XB11)", async () => {
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1", tenant_id: "tenant-1", sku_code: "crm-pro", status: "ACTIVE" }]);
    mocks.commercialEntitlements.mockRejectedValue(new Error("offline"));
    const wrapper = await mountPage("/p4/subscriptions");
    expect(wrapper.find('[data-testid="nx-error-state"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="subscription-entitlement"]').text()).toBe("暂无");
    expect(wrapper.text()).not.toContain("暂无投影读模型");
  });
});
