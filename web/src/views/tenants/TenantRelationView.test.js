/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, createRouter } from "vue-router";
import { p1 } from "../../api/client";
import TenantRelationView from "./TenantRelationView.vue";

vi.mock("../../api/client", () => ({
  p1: {
    tenantTree: vi.fn(),
    tenantChildren: vi.fn(),
    tenantAncestors: vi.fn(),
    reparentTenant: vi.fn(),
  },
}));

const routes = [
  { path: "/p1/relations", name: "p1-relations", component: TenantRelationView },
  { path: "/p1/relations/:id", name: "p1-relation", component: TenantRelationView },
];

const tree = {
  nodes: [
    {
      id: "root",
      code: "group",
      name: "集团",
      status: "ACTIVE",
      children: [
        {
          id: "child",
          code: "branch",
          name: "分部",
          status: "DISABLED",
          parent_tenant_id: "root",
          children: [],
        },
      ],
    },
  ],
};

async function open(path = "/p1/relations") {
  const router = createRouter({ history: createMemoryHistory(), routes });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(TenantRelationView, { global: { plugins: [router] } });
  await flushPromises();
  return { wrapper, router };
}

describe("P1 tenant relation page", () => {
  beforeEach(() => {
    p1.tenantTree.mockReset();
    p1.tenantChildren.mockReset();
    p1.tenantAncestors.mockReset();
    p1.reparentTenant.mockReset();
    p1.tenantChildren.mockResolvedValue([]);
    p1.tenantAncestors.mockResolvedValue([]);
  });

  it("shows the parent tree and loads children plus ancestors for the selected tenant", async () => {
    p1.tenantTree.mockResolvedValue(tree);
    p1.tenantChildren.mockResolvedValue([{ id: "leaf", code: "shop", name: "门店", status: "ACTIVE" }]);
    p1.tenantAncestors.mockResolvedValue([{ id: "root", code: "group", name: "集团", status: "ACTIVE" }]);
    const { wrapper, router } = await open("/p1/relations/child");

    expect(wrapper.get('[role="tree"]').text()).toContain("集团");
    expect(wrapper.get('[role="tree"]').text()).toContain("分部");
    expect(wrapper.get("[data-testid=relation-detail]").text()).toContain("集团");
    expect(wrapper.get("[data-testid=relation-detail]").text()).toContain("分部");
    expect(wrapper.get("[data-testid=relation-children]").text()).toContain("门店");
    expect(wrapper.text()).not.toContain("配额");
    expect(p1.tenantChildren).toHaveBeenCalledWith("child");
    expect(p1.tenantAncestors).toHaveBeenCalledWith("child");

    await wrapper.findAll('[role="treeitem"]')[0].trigger("click");
    await flushPromises();
    expect(router.currentRoute.value.params.id).toBe("root");
  });

  it("shows 暂无 when the tree or the child list is empty", async () => {
    p1.tenantTree.mockResolvedValue({ nodes: [] });
    const empty = await open();
    expect(empty.wrapper.text()).toContain("暂无");
    expect(empty.wrapper.find('[role="treeitem"]').exists()).toBe(false);
    expect(p1.tenantChildren).not.toHaveBeenCalled();

    p1.tenantTree.mockResolvedValue(tree);
    p1.tenantChildren.mockResolvedValue([]);
    p1.tenantAncestors.mockResolvedValue([]);
    const { wrapper } = await open("/p1/relations/root");
    expect(wrapper.get("[data-testid=relation-children-empty]").text()).toBe("暂无");
    expect(wrapper.get("[data-testid=relation-detail]").text()).toContain("暂无");
  });

  it("shows permission and API errors without fabricating a tree", async () => {
    p1.tenantTree.mockRejectedValueOnce(Object.assign(new Error("需要平台上下文"), { status: 403, traceId: "trace-n3" }));
    const denied = await open();
    expect(denied.wrapper.get("[data-testid=nx-no-permission]").text()).toContain("需要平台上下文");
    expect(denied.wrapper.find('[role="treeitem"]').exists()).toBe(false);

    p1.tenantTree.mockRejectedValueOnce(Object.assign(new Error("关系服务不可用"), { traceId: "trace-n3b" }));
    const failed = await open();
    expect(failed.wrapper.text()).toContain("关系服务不可用");
    expect(failed.wrapper.text()).toContain("trace-n3b");
    expect(failed.wrapper.find('[role="treeitem"]').exists()).toBe(false);
  });

  it("filters the tree from the URL query and keeps an unmatched search empty", async () => {
    p1.tenantTree.mockResolvedValue(tree);
    const { wrapper } = await open("/p1/relations?q=没有这个租户");
    expect(wrapper.text()).toContain("没有匹配的租户");
    expect(wrapper.find('[role="treeitem"]').exists()).toBe(false);
    expect(wrapper.find("[data-testid=reparent-form]").exists()).toBe(false);
  });

  it("asks for confirmation and shows the server cycle error", async () => {
    p1.tenantTree.mockResolvedValue(tree);
    p1.reparentTenant.mockRejectedValue(Object.assign(new Error("不能挂到自己的后代"), { status: 409 }));
    const { wrapper } = await open("/p1/relations/root");

    const form = wrapper.get("[data-testid=reparent-form]");
    expect(form.text()).toContain("分部");
    expect(form.get("button").attributes("disabled")).toBeDefined();
    await form.get('input[type="checkbox"]').setValue(true);
    await form.get("select").setValue("child");
    await form.trigger("submit");
    await flushPromises();

    expect(p1.reparentTenant).toHaveBeenCalledWith("root", "child");
    expect(form.text()).toContain("不能挂到自己的后代");
  });

  it("clears the parent after confirmation and reloads the tree", async () => {
    p1.tenantTree.mockResolvedValue(tree);
    p1.reparentTenant.mockResolvedValue({ id: "child", code: "branch", name: "分部", parentTenantId: null });
    const { wrapper } = await open("/p1/relations/child");
    const form = wrapper.get("[data-testid=reparent-form]");

    await form.get("select").setValue("");
    await form.get('input[type="checkbox"]').setValue(true);
    await form.trigger("submit");
    await flushPromises();

    expect(p1.reparentTenant).toHaveBeenCalledWith("child", null);
    expect(p1.tenantTree).toHaveBeenCalledTimes(2);
    expect(form.text()).not.toContain("不能挂到自己的后代");
  });
});
