/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({
  health: vi.fn(),
  snapshot: vi.fn(),
  matrix: vi.fn(),
  principals: vi.fn(),
  createRole: vi.fn(),
  assign: vi.fn(),
  grant: vi.fn(),
  installExample: vi.fn(),
  check: vi.fn(),
}));

vi.mock("../../api/client", () => ({ perm: mocks }));
vi.mock("../../api/session", () => ({
  session: { contextId: "t2c-context", lastTraceId: "" },
}));

import PermissionStubView from "./PermissionStubView.vue";

const matrix = {
  policyVersion: 4,
  demoSeedPresent: false,
  differentiatedPolicy: true,
  completenessNote: "T2-C",
  roles: [{ id: "role-1", code: "self_reader", name: "Self reader", systemRole: false }],
  assignments: [],
  cells: [],
};

async function mountPage(kind) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p2/permissions/:kind", component: PermissionStubView }],
  });
  await router.push(`/p2/permissions/${kind}`);
  await router.isReady();
  const wrapper = mount(PermissionStubView, { global: { plugins: [router] } });
  await flushPromises();
  return wrapper;
}

describe("T2-C permission maintenance", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.health.mockResolvedValue({ status: "UP" });
    mocks.snapshot.mockResolvedValue({ version: 4, grant_count: 0, deny_count: 0 });
    mocks.matrix.mockResolvedValue(matrix);
    mocks.principals.mockResolvedValue({
      users: [{ id: "person-1", label: "Alice", code: "E001" }],
      positions: [{ id: "position-1", label: "Manager", code: "POS-MGR" }],
      departments: [{ id: "department-1", label: "Finance", code: "FIN" }],
    });
    mocks.assign.mockResolvedValue({ id: "assignment-1" });
    mocks.grant.mockResolvedValue({ id: "grant-1" });
    mocks.createRole.mockResolvedValue({ id: "role-2" });
  });

  it("creates a role and assigns a selected tenant principal with the camelCase contract", async () => {
    const wrapper = await mountPage("roles");
    await wrapper.get('[data-testid="permission-role-code"]').setValue("auditor");
    await wrapper.get('[data-testid="permission-role-name"]').setValue("Auditor");
    await wrapper.get('[data-testid="permission-role-form"]').trigger("submit");
    await flushPromises();
    expect(mocks.createRole).toHaveBeenCalledWith({
      code: "auditor",
      name: "Auditor",
      systemRole: false,
    });

    await wrapper.get('[data-testid="permission-assignment-principal"]').setValue("person-1");
    await wrapper.get('[data-testid="permission-assignment-role"]').setValue("role-1");
    await wrapper.get('[data-testid="permission-assignment-form"]').trigger("submit");
    await flushPromises();

    expect(mocks.assign).toHaveBeenCalledWith({
      principalType: "user",
      principalId: "person-1",
      roleId: "role-1",
    });
  });

  it("writes SELF and SPECIFIED DataScope with selected roles and departments", async () => {
    const wrapper = await mountPage("datascope");
    await wrapper.get('[data-testid="permission-grant-role"]').setValue("self_reader");
    await wrapper.get('[data-testid="permission-grant-form"]').trigger("submit");
    await flushPromises();
    expect(mocks.grant).toHaveBeenLastCalledWith({
      effect: "GRANT",
      principalType: "role",
      principalId: "self_reader",
      resource: "ledger",
      action: "read",
      scopeKind: "SELF",
      specifiedDepartments: [],
      fields: {},
    });

    await wrapper.get('[data-testid="permission-grant-scope"]').setValue("SPECIFIED");
    await wrapper.get('[data-testid="permission-grant-departments"]').setValue(["department-1"]);
    await wrapper.get('[data-testid="permission-grant-form"]').trigger("submit");
    await flushPromises();
    expect(mocks.grant.mock.calls.at(-1)[0]).toMatchObject({
      scopeKind: "SPECIFIED",
      specifiedDepartments: ["department-1"],
    });
  });
});
