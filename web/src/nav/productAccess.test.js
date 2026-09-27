/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ visibleProducts: vi.fn() }));
vi.mock("../api/client", async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, p2: { ...original.p2, visibleProducts: mocks.visibleProducts } };
});

import { clearIdentity, ensureProducts, session } from "../api/session";
import { productAllowed, productGuard, visibleProducts } from "./productAccess";
import ProductSwitcher from "../layout/ProductSwitcher.vue";
import ProductPickerView from "../views/ProductPickerView.vue";
import ProductNoAccessView from "../views/ProductNoAccessView.vue";

const ids = (items) => items.map((item) => item.id);
const route = (path, product) => ({ path, meta: product ? { product } : {} });

describe("PV3 product filtering", () => {
  it("shows only the allowed products, and everything while the list is unknown", () => {
    expect(ids(visibleProducts(["p3"]))).toEqual(["p3"]);
    expect(ids(visibleProducts(["p4", "p1", "p2"]))).toEqual(["p1", "p2", "p4"]);
    expect(ids(visibleProducts([]))).toEqual([]);
    expect(ids(visibleProducts(null))).toEqual(["p1", "p2", "p3", "p4", "p5"]);
    expect(productAllowed(["p3"], "p2")).toBe(false);
    expect(productAllowed(null, "p2")).toBe(true);
  });
});

describe("PV3 product guard", () => {
  it('keeps platform-default design out of tenant-admin and member routes', async () => {
    const page = {path:'/p4/default-workflow',meta:{product:'p4',platformProcess:true}};
    for (const products of [['p3'],['p2','p3','p4'],null]) {
      await expect(productGuard(page,async()=>products)).resolves.toEqual({path:'/no-access',query:{product:'p1'}});
    }
    await expect(productGuard(page,async()=>['p1','p2','p4'])).resolves.toBe(true);
  });
  it("sends a member with only P3 from / straight to the workbench", async () => {
    await expect(productGuard(route("/"), async () => ["p3"])).resolves.toEqual({ path: "/p3", replace: true });
  });

  it("keeps the picker when several products are open", async () => {
    await expect(productGuard(route("/"), async () => ["p1", "p2", "p4"])).resolves.toBe(true);
  });

  it("sends a product the person may not enter to the no-access page", async () => {
    await expect(productGuard(route("/p2/org", "p2"), async () => ["p3"]))
      .resolves.toEqual({ path: "/no-access", query: { product: "p2" } });
    await expect(productGuard(route("/p1/tenants", "p1"), async () => ["p3"]))
      .resolves.toEqual({ path: "/no-access", query: { product: "p1" } });
    await expect(productGuard(route("/p3/todos", "p3"), async () => ["p3"])).resolves.toBe(true);
  });

  it("never blocks on an unknown list, public / mobile routes, or routes outside a product", async () => {
    const ensure = vi.fn(async () => null);
    await expect(productGuard(route("/p2/org", "p2"), ensure)).resolves.toBe(true);
    const never = vi.fn(async () => ["p3"]);
    await expect(productGuard({ path: "/login", meta: { public: true } }, never)).resolves.toBe(true);
    await expect(productGuard({ path: "/m/todo", meta: { mobile: true, product: "p3" } }, never)).resolves.toBe(true);
    await expect(productGuard(route("/no-access"), never)).resolves.toBe(true);
    expect(never).not.toHaveBeenCalled();
  });
});

describe("PV3 ensureProducts", () => {
  beforeEach(() => {
    mocks.visibleProducts.mockReset();
    clearIdentity();
    Object.assign(session, { accessToken: "token", accountId: "acc-1", contextId: "ctx-1" });
  });

  afterEach(() => clearIdentity());

  it("loads once per account and context, and again after a tenant switch", async () => {
    mocks.visibleProducts.mockResolvedValue({ products: ["p3"] });
    await expect(ensureProducts()).resolves.toEqual(["p3"]);
    await ensureProducts();
    expect(mocks.visibleProducts).toHaveBeenCalledTimes(1);
    session.contextId = "ctx-2";
    mocks.visibleProducts.mockResolvedValue({ products: ["p2", "p3", "p4"] });
    await expect(ensureProducts()).resolves.toEqual(["p2", "p3", "p4"]);
    expect(session.products).toEqual(["p2", "p3", "p4"]);
  });

  it("falls back to the full switcher when the server cannot answer", async () => {
    mocks.visibleProducts.mockRejectedValue(Object.assign(new Error("Not Found"), { status: 404 }));
    await expect(ensureProducts()).resolves.toBeNull();
    expect(ids(visibleProducts(session.products))).toHaveLength(5);
  });

  it("forgets the list on logout", async () => {
    mocks.visibleProducts.mockResolvedValue({ products: ["p3"] });
    await ensureProducts();
    clearIdentity();
    expect(session.products).toBeNull();
  });

  it("does not call the server without a token", async () => {
    session.accessToken = "";
    await ensureProducts();
    expect(mocks.visibleProducts).not.toHaveBeenCalled();
  });
});

describe("PV3 switcher, picker and no-access page", () => {
  async function mountWith(component, path = "/") {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", component: { template: "<div />" } },
        { path: "/no-access", component: { template: "<div />" } },
        ...["p1", "p2", "p3", "p4", "p5"].map((id) => ({ path: `/${id}`, component: { template: "<div />" } })),
      ],
    });
    await router.push(path);
    const wrapper = mount(component, { global: { plugins: [router] } });
    await flushPromises();
    return wrapper;
  }

  afterEach(() => {
    session.products = null;
  });

  it("hides the switcher for a single-product member and narrows it for others", async () => {
    session.products = ["p3"];
    expect((await mountWith(ProductSwitcher)).find("[data-testid='product-switcher']").exists()).toBe(false);
    session.products = ["p1", "p2", "p4"];
    const wrapper = await mountWith(ProductSwitcher);
    expect(wrapper.findAll("[data-product]").map((a) => a.attributes("data-product"))).toEqual(["p1", "p2", "p4"]);
  });

  it("lists only the allowed products on the picker, with an honest empty state", async () => {
    session.products = ["p2", "p3", "p4"];
    const picker = await mountWith(ProductPickerView);
    expect(picker.findAll("[data-product]").map((a) => a.attributes("data-product"))).toEqual(["p2", "p3", "p4"]);
    expect(picker.text()).not.toContain("星域");
    session.products = [];
    const empty = await mountWith(ProductPickerView);
    expect(empty.find("[data-testid='product-picker-empty']").exists()).toBe(true);
  });

  it("explains the refusal and offers the products that are open", async () => {
    session.products = ["p3"];
    const page = await mountWith(ProductNoAccessView, "/no-access?product=p2");
    expect(page.text()).toContain("无权访问该产品");
    expect(page.text()).toContain("星权");
    expect(page.findAll("[data-product]").map((a) => a.attributes("data-product"))).toEqual(["p3"]);
  });
});
