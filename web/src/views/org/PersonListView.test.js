/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({
  persons: vi.fn(), getPerson: vi.fn(), employments: vi.fn(), departments: vi.fn(), positions: vi.fn(), createPerson: vi.fn(),
}));
const permMocks = vi.hoisted(() => ({ effectivePerson: vi.fn() }));
vi.mock("../../api/client", () => ({ p2: mocks, perm: permMocks }));
vi.mock("../../api/session", () => ({ session: { contextId: "ctx-1", accessToken: "token" }, ensureContext: vi.fn() }));

import PersonListView from "./PersonListView.vue";

const rows = [{ id: "person-1", displayName: "林晓", employeeNo: "E-001", email: "lin@example.test", status: "ACTIVE", tenantId: "tenant-1" }];
function mountList() { return mount(PersonListView); }

describe("P2 person directory V7", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    mocks.persons.mockResolvedValue(rows); mocks.getPerson.mockResolvedValue({ ...rows[0], phone: "13800000000", accountId: "account-1", hiredOn: "2024-01-02" });
    mocks.employments.mockResolvedValue([{ id: "employment-1", departmentId: "dept-1", positionId: "pos-1", kind: "PRIMARY", status: "ACTIVE", validFrom: "2024-01-02" }]);
    mocks.departments.mockResolvedValue([{ id: "dept-1", name: "研发部" }]); mocks.positions.mockResolvedValue([{ id: "pos-1", name: "工程师" }]);
    permMocks.effectivePerson.mockReset();
    permMocks.effectivePerson.mockResolvedValue({
      subject_type: "PERSON", duty_conflict: false,
      roles: [
        { role_code: "tenant_member", role_name: "成员", via: "POSITION", via_id: "pos-1", via_label: "工程师" },
        { role_code: "auditor", role_name: "临时审计", via: "PERSONAL", via_id: "person-1", via_label: "个人（例外授权）" },
      ],
      permissions: [
        { resource: "ledger", action: "read", outcome: "ALLOW", sources: [{ effect: "GRANT", scope: "DEPT", principal_type: "role", principal_id: "tenant_member", label: "tenant_member" }] },
        { resource: "report", action: "read", outcome: "DENY", sources: [{ effect: "DENY", scope: "ALL", principal_type: "user", principal_id: "person-1", label: "个人（例外授权）" }] },
      ],
    });
  });

  it("renders the user directory and keeps details read-only with real employment data", async () => {
    const wrapper = mountList(); await flushPromises();
    expect(wrapper.get("[data-testid='nx-data-table']").text()).toContain("林晓");
    for (const heading of ["所属组织", "岗位", "角色", "账号状态", "最近登录", "来源"]) expect(wrapper.text()).toContain(heading);
    await wrapper.get("[data-testid='nx-data-table'] .person-name").trigger("click"); await flushPromises();
    const drawer = wrapper.get("[data-testid='person-drawer']");
    expect(drawer.text()).toContain("13800000000"); expect(drawer.text()).toContain("研发部"); expect(drawer.text()).toContain("工程师");
    const effective = drawer.get("[data-testid='effective-permissions']");
    expect(effective.text()).toContain("岗位：工程师");
    expect(effective.text()).toContain("个人例外授权");
    expect(effective.get("[data-permission='ledger:read']").text()).toContain("允许");
    expect(effective.get("[data-permission='report:read']").text()).toContain("拒绝");
    expect(permMocks.effectivePerson).toHaveBeenCalledWith("person-1");
    expect(drawer.text()).toContain("暂无登录记录读接口");
    expect(drawer.find("input").exists()).toBe(false);
    expect(mocks.getPerson).toHaveBeenCalledWith("person-1"); expect(mocks.employments).toHaveBeenCalledWith("person-1");
    await wrapper.get("[aria-label='关闭详情']").trigger("click");
    expect(wrapper.find("[data-testid='person-drawer']").exists()).toBe(false);
  });

  it("preserves the create-person flow", async () => {
    const wrapper = mountList(); await flushPromises();
    mocks.createPerson.mockResolvedValue({});
    await wrapper.get("input[required]").setValue("新用户");
    await wrapper.get("form").trigger("submit"); await flushPromises();
    expect(mocks.createPerson).toHaveBeenCalledWith(expect.objectContaining({ display_name: "新用户" }));
    expect(mocks.persons).toHaveBeenCalledTimes(2);
  });

  it("uses empty and retryable error states via NxDataTable S shells", async () => {
    mocks.persons.mockResolvedValueOnce([]);
    const empty = mountList(); await flushPromises();
    expect(empty.get("[data-testid='nx-data-table-empty']").text()).toContain("暂无用户");
    empty.unmount();
    mocks.persons.mockRejectedValueOnce(new Error("暂不可用"));
    const failed = mountList(); await flushPromises();
    expect(failed.get("[data-testid='nx-data-table-error']").text()).toContain("暂不可用");
  });
});
