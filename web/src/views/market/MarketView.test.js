/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { useNxToastState } from "../../feedback/nxMessage";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), subscribe: vi.fn(), requestActivation: vi.fn(), subscriptions: vi.fn(), activationApplications: vi.fn(), session: { tenantId: "tenant-1", contextId: "" } }));
vi.mock("../../api/client", () => ({ p4: mocks }));
vi.mock("../../api/session", () => ({ session: mocks.session }));

import MarketView from "./MarketView.vue";

// GET /api/v1/p4/catalog exposes stable subscription fields directly on each SKU entry.
const BOUND_BASIC = {
  code: "basic",
  name: "基础版",
  entitlements: { approval: { enabled: true, quota: 10, attributes: {} } },
  product_definition: { id: "pd-1", code: "crm_basic", name: "CRM 基础审批", status: "PUBLISHED" },
};

async function mountCatalog() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/p4/market", component: MarketView },
      { path: "/p4/installations", component: { template: "<div>opened</div>" } },
    ],
  });
  await router.push("/p4/market");
  const wrapper = mount(MarketView, { global: { plugins: [router] } });
  await flushPromises();
  wrapper.router = router;
  return wrapper;
}

describe("P4 catalog V11", () => {
  beforeEach(() => {
    mocks.catalog.mockReset(); mocks.subscribe.mockReset(); mocks.requestActivation.mockReset();
    mocks.subscriptions.mockReset(); mocks.activationApplications.mockReset();
    mocks.subscriptions.mockResolvedValue([]);
    mocks.activationApplications.mockResolvedValue([]);
    mocks.session.contextId = "";
    mocks.catalog.mockResolvedValue([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [BOUND_BASIC] },
      { app: { id: "app-2", name: "财务平台", code: "finance", summary: "账务与报表" }, version: { id: "v2", version: "2.0.0", status: "PUBLISHED" }, skus: [] },
    ]);
  });

  it("renders published catalog cards and filters without category data", async () => {
    const wrapper = await mountCatalog();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    // Q7: the corner badge is what the tenant can do (all entries are published); version sits in the title meta.
    expect(wrapper.findAll('[data-testid="catalog-card-badge"]').map((node) => node.text())).toEqual(["可申请", "暂不可申请"]);
    expect(wrapper.findAll('[data-testid="catalog-version"]').map((node) => node.text())).toEqual(["版本 1.2.0", "版本 2.0.0"]);
    expect(wrapper.get(".app-identity h2").text()).toBe("星辰 CRM");
    expect(wrapper.get(".app-logo").attributes("aria-label")).toBe("应用标识占位");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("还没有可申请的 SKU");
    expect(wrapper.text()).not.toContain("分类");
    await wrapper.get('[data-testid="catalog-search"]').setValue("BASIC");
    await flushPromises();
    expect(wrapper.get('[data-testid="catalog-filter"]').classes()).not.toContain("nx-card");
    expect(wrapper.router.currentRoute.value.query.q).toBe("BASIC");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).not.toContain("财务平台");
    expect(wrapper.text()).toContain("1 个可用版本");
    await wrapper.get('[data-testid="catalog-reset"]').trigger("click");
    await flushPromises();
    expect(wrapper.router.currentRoute.value.query.q).toBeUndefined();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("财务平台");
  });

  it("requests activation without payment and stays for platform review", async () => {
    mocks.requestActivation.mockResolvedValue({ id: "app-1", status: "PENDING", sku_code: "basic" });
    const wrapper = await mountCatalog();
    expect(wrapper.text()).toContain("无支付");
    expect(wrapper.get(".install-button").text()).toContain("申请开通");
    await wrapper.get(".install-button").trigger("click");
    await flushPromises();
    expect(mocks.requestActivation).toHaveBeenCalledWith("tenant-1", { sku_code: "basic", version_id: "v1" });
    expect(useNxToastState().items.at(-1)).toMatchObject({ tone: "success", message: "开通申请已提交「基础版」，等待平台审订" });
  });

  it("reads the nested SKU fields instead of rendering undefined", async () => {
    const wrapper = await mountCatalog();
    const sku = wrapper.get('[data-testid="catalog-sku"]');
    expect(wrapper.text()).not.toContain("undefined");
    expect(sku.get('[data-testid="catalog-sku-name"]').text()).toBe("基础版");
    expect(sku.text()).toContain("basic");
    expect(sku.get('[data-testid="catalog-sku-product"]').text()).toContain("CRM 基础审批");
    expect(sku.text()).toContain("approval · 配额 10");
    expect(sku.get('[data-testid="catalog-subscribe"]').text()).toBe("申请开通");
    expect(sku.get('[data-testid="catalog-subscribe"]').attributes("aria-label")).toBe("申请开通 基础版");
    expect(wrapper.get('[data-testid="catalog-sku-count"]').text()).toBe("1 个");
    await wrapper.get('[data-testid="catalog-search"]').setValue("基础版");
    await flushPromises();
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("基础版");
  });

  it("makes subscribe the primary action and shows 已开通可用 for a SKU the tenant already has (N1)", async () => {
    mocks.session.contextId = "ctx-1";
    // Real /api/v1/p4/subscriptions wire shape (SNAKE_CASE).
    mocks.subscriptions.mockResolvedValue([{ id: "sub-1", tenant_id: "tenant-1", sku_code: "basic", status: "ACTIVE" }]);
    const subscribed = await mountCatalog();
    const sku = subscribed.get('[data-testid="catalog-sku"]');
    expect(sku.find('[data-testid="catalog-subscribe"]').exists()).toBe(false);
    expect(sku.get('[data-testid="catalog-subscribed"]').text()).toContain("已开通可用");
    expect(sku.get('[data-testid="catalog-open-installed"]').attributes("href")).toBe("/p4/installations");
    expect(subscribed.get('[data-testid="catalog-card-badge"]').text()).toBe("已开通可用");
    subscribed.unmount();

    mocks.subscriptions.mockResolvedValue([{ id: "sub-1", sku_code: "basic", status: "CANCELLED" }]);
    const open = await mountCatalog();
    expect(open.get('[data-testid="catalog-subscribe"]').classes()).toContain("ready");
    expect(open.get('[data-testid="catalog-subscribe"]').classes()).toContain("nx-btn--primary");
    open.unmount();

    mocks.subscriptions.mockRejectedValue(new Error("TenantContext is required"));
    const unknown = await mountCatalog();
    expect(unknown.find('[data-testid="catalog-subscribe"]').exists()).toBe(true);
    expect(unknown.find('[data-testid="nx-error-state"]').exists()).toBe(false);
    mocks.session.contextId = "";
  });

  it("marks a SKU without a product definition as not subscribable instead of letting subscribe 404", async () => {
    mocks.catalog.mockResolvedValue([
      {
        app: { id: "app-1", name: "演示数据 · 简单审批", code: "demo_simple_approval" },
        version: { id: "v1", version: "1.0.0", status: "PUBLISHED" },
        skus: [{ code: "demo.simple-approval", name: "演示数据 · 标准", entitlements: {} }],
      },
    ]);
    const wrapper = await mountCatalog();
    expect(wrapper.get('[data-testid="catalog-sku-unbound"]').text()).toContain("未绑定产品定义");
    const button = wrapper.get('[data-testid="catalog-subscribe"]');
    expect(button.attributes("disabled")).toBeDefined();
    expect(button.classes()).not.toContain("ready");
    await button.trigger("click");
    expect(mocks.requestActivation).not.toHaveBeenCalled();
  });

  it("keeps the catalog on a subscribe failure, explains it, and retries the same SKU", async () => {
    mocks.requestActivation.mockRejectedValueOnce(Object.assign(new Error("catalog SKU has no product definition"), {
      status: 404, code: "CORE.API.0002", traceId: "trace-h6",
    }));
    const wrapper = await mountCatalog();
    await wrapper.get('[data-testid="catalog-subscribe"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[data-testid="catalog-grid"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="nx-error-state"]').exists()).toBe(false);
    const failure = wrapper.get('[data-testid="subscribe-failure"]');
    expect(failure.text()).toContain("开通申请失败：基础版");
    expect(failure.get('[data-testid="subscribe-failure-hint"]').text()).toContain("绑定产品定义");
    expect(failure.get('[data-testid="subscribe-failure-server"]').text()).toContain("catalog SKU has no product definition");
    expect(failure.get('[data-testid="subscribe-failure-trace"]').text()).toContain("trace-h6");
    expect(wrapper.text()).not.toContain("Internal error");

    mocks.requestActivation.mockResolvedValueOnce({ id: "install-1" });
    await failure.get('[data-testid="subscribe-retry"]').trigger("click");
    await flushPromises();
    expect(mocks.requestActivation).toHaveBeenCalledTimes(2);
    expect(mocks.requestActivation).toHaveBeenLastCalledWith("tenant-1", { sku_code: "basic", version_id: "v1" });
    expect(wrapper.router.currentRoute.value.path).toBe("/p4/market");
    expect(useNxToastState().items.at(-1).message).toContain("开通申请已提交");
  });

  it("links a duplicate subscription to 已开通可用 without a retry button", async () => {
    mocks.requestActivation.mockRejectedValueOnce(Object.assign(new Error("tenant already has an active subscription for this SKU"), { status: 409 }));
    const wrapper = await mountCatalog();
    await wrapper.get('[data-testid="catalog-subscribe"]').trigger("click");
    await flushPromises();
    const failure = wrapper.get('[data-testid="subscribe-failure"]');
    expect(failure.find('[data-testid="subscribe-retry"]').exists()).toBe(false);
    expect(failure.get("a").attributes("href")).toBe("/p4/installations");
    await failure.get('[data-testid="subscribe-dismiss"]').trigger("click");
    expect(wrapper.find('[data-testid="subscribe-failure"]').exists()).toBe(false);
  });

  it("shows empty and the server failure text", async () => {
    mocks.catalog.mockResolvedValueOnce([]);
    const empty = await mountCatalog();
    // S10: reason + next step.
    expect(empty.get('[data-testid="catalog-empty"]').text()).toContain("目录里还没有应用");
    expect(empty.get('[data-testid="catalog-empty-reload"]').text()).toBe("刷新目录");
    empty.unmount();

    mocks.catalog.mockResolvedValueOnce([
      { app: { id: "app-1", name: "星辰 CRM", code: "crm", summary: "客户关系管理" }, version: { id: "v1", version: "1.2.0", status: "PUBLISHED" }, skus: [BOUND_BASIC] },
    ]);
    mocks.requestActivation.mockRejectedValueOnce(new Error("tenant already has an active subscription for this SKU"));
    const failed = await mountCatalog();
    await failed.get(".install-button").trigger("click");
    await flushPromises();
    expect(failed.text()).toContain("tenant already has an active subscription for this SKU");
    expect(failed.router.currentRoute.value.path).toBe("/p4/market");
  });

  it("explains a filter with no match and clears it, and titles a load failure (S10)", async () => {
    const wrapper = await mountCatalog();
    await wrapper.get('[data-testid="catalog-search"]').setValue("不存在的应用");
    await flushPromises();
    const noMatch = wrapper.get('[data-testid="catalog-no-match"]');
    expect(noMatch.text()).toContain("没有匹配的应用");
    expect(noMatch.text()).toContain("「不存在的应用」");
    await noMatch.get('[data-testid="catalog-no-match-clear"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[data-testid="catalog-no-match"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="catalog-grid"]').text()).toContain("星辰 CRM");
    wrapper.unmount();

    mocks.catalog.mockRejectedValueOnce(new Error("catalog unavailable"));
    const failed = await mountCatalog();
    const state = failed.get('[data-testid="nx-error-state"]');
    expect(state.text()).toContain("应用目录暂时无法加载");
    expect(state.text()).toContain("prod-safe 内容手册");
    expect(state.text()).not.toContain("catalog unavailable");
    expect(state.get("button").text()).toBe("重新加载");
  });
});
