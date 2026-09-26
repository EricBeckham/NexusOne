/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  health: vi.fn(),
  matrix: vi.fn(),
  snapshot: vi.fn(),
  roles: vi.fn(),
  assignments: vi.fn(),
  grants: vi.fn(),
  createRole: vi.fn(),
  assign: vi.fn(),
  grant: vi.fn(),
}));

vi.mock("../../api/client", () => ({
  perm: {
    health: mocks.health,
    matrix: mocks.matrix,
    snapshot: mocks.snapshot,
    roles: mocks.roles,
    assignments: mocks.assignments,
    grants: mocks.grants,
    createRole: mocks.createRole,
    assign: mocks.assign,
    grant: mocks.grant,
  },
}));

vi.mock("../../api/session", () => ({ session: { contextId: "context-from-server" } }));

import PermissionView from "./PermissionView.vue";

const role = { id: "role-id-from-api", code: "role-from-api", name: "Role from API", systemRole: false, status: "ACTIVE" };
const assignment = {
  id: "assignment-id-from-api",
  principalType: "user",
  principalId: "person-id-from-api",
  roleId: role.id,
  roleCode: role.code,
};
const cell = {
  id: "grant-id-from-api",
  effect: "GRANT",
  principalType: "role",
  principalId: role.code,
  resource: "resource-from-api",
  action: "action-from-api",
  scopeKind: "SELF",
  specifiedDepartments: [],
  fields: { field_from_api: { visible: true, editable: false, mask: "PARTIAL", secondAuthorization: false } },
};

async function mountPermission(path = "/p2/permissions", contextId = "context-from-server") {
  const session = (await import("../../api/session")).session;
  session.contextId = contextId;
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p2/permissions/:kind?", component: PermissionView }],
  });
  await router.push(path);
  await router.isReady();
  const wrapper = mount(PermissionView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("P2 permissions management", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.health.mockResolvedValue({ status: "UP", module: "permission-api" });
    mocks.matrix.mockResolvedValue({
      roles: [role],
      assignments: [assignment],
      cells: [cell, { ...cell, id: "deny-cell-from-api", effect: "DENY", principalId: "other-role-from-api" }, {
        ...cell, id: "other-resource-cell", resource: "other-resource-from-api", action: "delete-from-api",
      }],
      differentiatedPolicy: true,
      completenessNote: "Completeness from API",
    });
    mocks.snapshot.mockResolvedValue({ tenant_id: "tenant-from-api", version: 12, grant_count: 1, deny_count: 0 });
    mocks.roles.mockResolvedValue([role]);
    mocks.assignments.mockResolvedValue([assignment]);
    mocks.grants.mockResolvedValue([cell]);
    mocks.createRole.mockResolvedValue(role);
    mocks.assign.mockResolvedValue(assignment);
    mocks.grant.mockResolvedValue(cell);
  });

  it("loads role and assignment tables from the existing Permission API", async () => {
    const wrapper = await mountPermission();

    expect(wrapper.get("h1").text()).toBe("角色与分配");
    expect(wrapper.get("[data-testid='permission-explainer']").text()).toContain("谁能做什么");
    expect(wrapper.get("[data-testid='nx-page-header']").text()).not.toContain("PermissionEngine");
    expect(wrapper.get('[data-testid="role-table"]').text()).toContain("role-from-api");
    expect(wrapper.get('[data-testid="assignment-table"]').text()).toContain("person-id-from-api");
    expect(mocks.matrix).toHaveBeenCalledOnce();
    expect(mocks.roles).toHaveBeenCalledOnce();
    expect(mocks.assignments).toHaveBeenCalledOnce();
    expect(wrapper.get('[data-testid="permission-tab-datascope"]').attributes("href")).toBe("/p2/permissions/datascope");
  });

  it("renders returned grants as a searchable Resource × Action matrix without inventing codes", async () => {
    const wrapper = await mountPermission("/p2/permissions/matrix");
    const table = wrapper.get('[data-testid="resource-action-table"]');
    expect(table.text()).toContain("resource-from-api");
    expect(table.text()).toContain("action-from-api");
    expect(table.text()).toContain("other-resource-from-api");
    expect(table.text()).toContain("delete-from-api");
    const grantCell = wrapper.get('[data-testid="matrix-cell-resource-from-api-action-from-api"]');
    expect(grantCell.text()).toContain("GRANT");
    expect(grantCell.text()).toContain("DENY");
    expect(wrapper.get('[data-testid="matrix-cell-resource-from-api-delete-from-api"]').text()).toContain("—");
    await wrapper.get('[data-testid="matrix-search"]').setValue("other-role-from-api");
    expect(wrapper.get('[data-testid="resource-action-table"]').text()).toContain("other-role-from-api");
    expect(wrapper.get('[data-testid="resource-action-table"]').text()).not.toContain("GRANT");
  });

  it("requires a server-issued TenantContext and does not load tenant policy without one", async () => {
    const wrapper = await mountPermission("/p2/permissions/roles", "");

    expect(wrapper.get('[data-testid="nx-no-permission"]').text()).toContain("需要租户上下文");
    expect(mocks.health).toHaveBeenCalledOnce();
    expect(mocks.matrix).not.toHaveBeenCalled();
    expect(mocks.roles).not.toHaveBeenCalled();
  });

  it("creates roles and assignments with the existing API bodies", async () => {
    const wrapper = await mountPermission();
    const roleForm = wrapper.get('[data-testid="create-role-form"]');
    await roleForm.findAll("input")[0].setValue("role-entered-by-admin");
    await roleForm.findAll("input")[1].setValue("Role entered by admin");
    await roleForm.trigger("submit");
    await flushPromises();
    expect(mocks.createRole).toHaveBeenCalledWith({
      code: "role-entered-by-admin",
      name: "Role entered by admin",
      systemRole: false,
    });

    const assignmentForm = wrapper.get('[data-testid="create-assignment-form"]');
    await assignmentForm.findAll("input")[0].setValue("user");
    await assignmentForm.findAll("input")[1].setValue("person-entered-by-admin");
    await assignmentForm.get("select").setValue(role.id);
    await assignmentForm.trigger("submit");
    await flushPromises();
    expect(mocks.assign).toHaveBeenCalledWith({
      principalType: "user",
      principalId: "person-entered-by-admin",
      roleId: role.id,
    });
  });

  it("submits existing DataScope fields without inventing resource or action codes", async () => {
    const wrapper = await mountPermission("/p2/permissions/datascope");
    const form = wrapper.get('[data-testid="create-grant-form"]');
    const inputs = form.findAll("input:not([type])");
    await inputs[0].setValue("role");
    await inputs[1].setValue("role-from-api");
    await inputs[2].setValue("resource-from-admin");
    await inputs[3].setValue("action-from-admin");
    const selects = form.findAll("select");
    await selects[0].setValue("DENY");
    await selects[1].setValue("SPECIFIED");
    await form.get("input[autocomplete='off']").setValue("department-a, department-b");
    await form.trigger("submit");
    await flushPromises();

    expect(mocks.grant).toHaveBeenCalledWith({
      effect: "DENY",
      principalType: "role",
      principalId: "role-from-api",
      resource: "resource-from-admin",
      action: "action-from-admin",
      scopeKind: "SPECIFIED",
      specifiedDepartments: ["department-a", "department-b"],
      fields: {},
    });
  });

  it("renders server-provided field rules and submits only GrantBody fields", async () => {
    const wrapper = await mountPermission("/p2/permissions/fields");

    expect(wrapper.get("h1").text()).toBe("字段权限");
    expect(wrapper.get('[data-testid="field-table"]').text()).toContain("field_from_api");

    const form = wrapper.get('[data-testid="create-field-grant-form"]');
    const textInputs = form.findAll("input:not([type])");
    await textInputs[0].setValue("role");
    await textInputs[1].setValue("role-from-api");
    await textInputs[2].setValue("resource-from-api");
    await textInputs[3].setValue("action-from-api");
    await textInputs[4].setValue("field_from_api");
    await form.findAll("select")[0].setValue("GRANT");
    await form.findAll("select")[1].setValue("SELF");
    await form.findAll("select")[2].setValue("PARTIAL");
    await form.trigger("submit");
    await flushPromises();

    expect(mocks.grant).toHaveBeenCalledWith({
      effect: "GRANT",
      principalType: "role",
      principalId: "role-from-api",
      resource: "resource-from-api",
      action: "action-from-api",
      scopeKind: "SELF",
      specifiedDepartments: [],
      fields: {
        field_from_api: { visible: true, editable: false, mask: "PARTIAL", secondAuthorization: false },
      },
    });
  });
});
