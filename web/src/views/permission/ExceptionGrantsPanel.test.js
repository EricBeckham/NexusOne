/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({
  exceptions: vi.fn(), revokeExceptionGrant: vi.fn(), revokeExceptionAssignment: vi.fn(),
}));
vi.mock("../../api/client", () => ({ perm: mocks }));

import ExceptionGrantsPanel from "./ExceptionGrantsPanel.vue";

const rows = [
  { kind: "GRANT", id: "g-1", person_id: "p-1", person_name: "Alice", effect: "GRANT", resource: "report", action: "read", scope: "SELF", system: false },
  { kind: "GRANT", id: "g-2", person_id: "p-2", person_name: "Bob", effect: "DENY", resource: "ledger", action: "write", scope: "SELF", system: false },
  { kind: "ROLE", id: "a-1", person_id: "p-1", person_name: "Alice", role_code: "temp_auditor", role_name: "临时审计", system: false },
  { kind: "ROLE", id: "a-2", person_id: "p-2", person_name: "Bob", role_code: "tenant_member", role_name: "Tenant member", system: true },
];

describe("PLA4 exception grants panel", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.exceptions.mockResolvedValue(rows);
  });

  it("lists exceptions with their kind and marks system-made ones as not revocable", async () => {
    const wrapper = mount(ExceptionGrantsPanel); await flushPromises();
    const table = wrapper.get("[data-testid='exception-table']");
    expect(table.get("[data-exception='g-1']").text()).toContain("个人授权");
    expect(table.get("[data-exception='g-2']").text()).toContain("个人拒绝");
    expect(table.get("[data-exception='a-1']").text()).toContain("临时审计");
    expect(table.get("[data-exception='a-2']").text()).toContain("由系统维护");
    expect(table.find("[data-exception='a-2'] button").exists()).toBe(false);
    expect(mocks.exceptions).toHaveBeenCalledWith({ kind: "", q: "", includeSystem: false });
  });

  it("filters through the API", async () => {
    const wrapper = mount(ExceptionGrantsPanel); await flushPromises();
    await wrapper.get("select").setValue("ROLE");
    await wrapper.get("input[type='search']").setValue("审计");
    await wrapper.get("input[type='checkbox']").setValue(true);
    await wrapper.get("[data-testid='exception-filters']").trigger("submit"); await flushPromises();
    expect(mocks.exceptions).toHaveBeenLastCalledWith({ kind: "ROLE", q: "审计", includeSystem: true });
  });

  it("revokes a grant or a role with the matching call and drops the row", async () => {
    mocks.revokeExceptionGrant.mockResolvedValue(null);
    mocks.revokeExceptionAssignment.mockResolvedValue(null);
    const wrapper = mount(ExceptionGrantsPanel); await flushPromises();

    await wrapper.get("[data-exception='g-1'] button").trigger("click"); await flushPromises();
    await wrapper.get("[data-exception='a-1'] button").trigger("click"); await flushPromises();

    expect(mocks.revokeExceptionGrant).toHaveBeenCalledWith("g-1");
    expect(mocks.revokeExceptionAssignment).toHaveBeenCalledWith("a-1");
    expect(wrapper.find("[data-exception='g-1']").exists()).toBe(false);
    expect(wrapper.find("[data-exception='a-1']").exists()).toBe(false);
  });

  it("explains a refused revoke", async () => {
    mocks.revokeExceptionGrant.mockRejectedValue(Object.assign(new Error("denied"), { status: 403, code: "P2.PERM.0003" }));
    const wrapper = mount(ExceptionGrantsPanel); await flushPromises();
    await wrapper.get("[data-exception='g-1'] button").trigger("click"); await flushPromises();
    expect(wrapper.text()).toContain("你没有执行此操作的权限");
    expect(wrapper.find("[data-exception='g-1']").exists()).toBe(true);
  });
});
