/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p5 } from "../../api/client";
import ApiCatalogView from "./ApiCatalogView.vue";

vi.mock("../../api/client", () => ({
  p5: { apiCatalog: vi.fn(), apiCatalogEntry: vi.fn() },
}));

const routes = [
  { path: "/p5/apis", name: "p5-apis", component: ApiCatalogView },
  { path: "/p5/apis/:id", name: "p5-api", component: ApiCatalogView },
];

const list = [
  {
    id: "shared",
    name: "列出租户",
    http_method: "GET",
    path: "/api/v1/p1/tenants",
    version: "v1",
    scope: "tenant:read",
    status: "ACTIVE",
    updated_at: "2026-09-24T08:00:00Z",
  },
  {
    id: "own",
    tenant_id: "tenant-a",
    name: "创建客户端",
    http_method: "POST",
    path: "/api/v1/p5/clients",
    version: "v1",
    scope: "",
    status: "DRAFT",
    description: "仅元数据",
  },
];

async function open(path = "/p5/apis") {
  const router = createRouter({ history: createMemoryHistory(), routes });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(ApiCatalogView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("self-use API catalog page", () => {
  beforeEach(() => {
    p5.apiCatalog.mockReset();
    p5.apiCatalogEntry.mockReset();
  });

  it("lists metadata with method color and leaves volume and success rate empty", async () => {
    p5.apiCatalog.mockResolvedValue(list);
    const { wrapper } = await open();

    expect(wrapper.text()).toContain("自用 API 目录");
    expect(wrapper.text()).not.toContain("开放平台");
    expect(wrapper.get('[data-method="GET"]').text()).toBe("GET");
    expect(wrapper.get('[data-method="POST"]').text()).toBe("POST");
    expect(wrapper.findAll("[data-testid=call-volume]").map((item) => item.text())).toEqual(["暂无", "暂无"]);
    expect(wrapper.findAll("[data-testid=success-rate]").map((item) => item.text())).toEqual(["暂无", "暂无"]);
    expect(wrapper.text()).not.toMatch(/\d+%/);
    expect(wrapper.text()).not.toMatch(/curl/i);
    expect(wrapper.get('a[href="/p5/apis/shared"]').text()).toContain("列出租户");
    expect(wrapper.get("[data-testid='api-catalog-table']").classes()).toContain("nx-table--compact");
    await wrapper.get("input[aria-label='搜索 API']").setValue("创建");
    expect(wrapper.findAll("tbody tr")).toHaveLength(1);
    expect(wrapper.text()).toContain("显示 1 / 2 个 API");
  });

  it("shows 暂无 for an empty catalog and permission errors without rows", async () => {
    p5.apiCatalog.mockResolvedValueOnce([]);
    const empty = await open();
    expect(empty.wrapper.text()).toContain("暂无自用 API");
    expect(empty.wrapper.get("[data-testid='nx-empty-next'] a").attributes("href")).toBe("/p5/connect");
    expect(empty.wrapper.find("tbody tr").exists()).toBe(false);

    p5.apiCatalog.mockRejectedValueOnce(Object.assign(new Error("需要租户上下文"), { status: 403, traceId: "trace-n18" }));
    const denied = await open();
    expect(denied.wrapper.get("[data-testid=nx-no-permission]").text()).toContain("需要租户上下文");
    expect(denied.wrapper.find("tbody tr").exists()).toBe(false);
  });

  it("opens overview, request, response, and scope without inventing a payload", async () => {
    p5.apiCatalogEntry.mockResolvedValue(list[1]);
    const { wrapper, router } = await open("/p5/apis/own");

    expect(wrapper.text()).toContain("创建客户端");
    expect(wrapper.text()).toContain("本租户");
    expect(wrapper.text()).toContain("仅元数据");
    expect(p5.apiCatalogEntry).toHaveBeenCalledWith("own");

    await wrapper.get('[role="tab"][aria-selected="false"]').trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.query.tab).toBe("request");
    expect(wrapper.text()).toContain("请求体：暂无");
    expect(wrapper.text()).toContain("/api/v1/p5/clients");

    await wrapper.findAll('[role="tab"]')[2].trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("响应结构：暂无");

    await wrapper.findAll('[role="tab"]')[3].trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无");
    expect(wrapper.text()).not.toContain("开放平台");
  });
});
