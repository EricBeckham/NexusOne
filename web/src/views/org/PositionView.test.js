/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({
  p2: { positions: vi.fn(), createPosition: vi.fn(), assignEmployment: vi.fn() },
  perm: { positionRoles: vi.fn(), roles: vi.fn(), setPositionRoles: vi.fn(), effectivePosition: vi.fn() },
}));
vi.mock("../../api/client", () => mocks);

import PositionView from "./PositionView.vue";

const reader = { assignment_id: "a-1", role_id: "role-r", role_code: "ledger_reader", role_name: "台账查看" };

describe("PLA2 position ↔ role binding", () => {
  beforeEach(() => {
    [...Object.values(mocks.p2), ...Object.values(mocks.perm)].forEach((mock) => mock.mockReset());
    mocks.p2.positions.mockResolvedValue([
      { id: "pos-1", code: "clerk", name: "文员", category: "ic" },
      { id: "pos-2", code: "lead", name: "组长", category: "mgr" },
    ]);
    mocks.perm.positionRoles.mockResolvedValue([{ position_id: "pos-1", roles: [reader] }]);
    mocks.perm.roles.mockResolvedValue([
      { id: "role-r", code: "ledger_reader", name: "台账查看", status: "ACTIVE" },
      { id: "role-w", code: "ledger_writer", name: "台账编辑", status: "ACTIVE" },
      { id: "role-x", code: "retired", name: "停用角色", status: "DISABLED" },
    ]);
  });

  it("shows each position's roles and replaces them from the editor", async () => {
    const wrapper = mount(PositionView); await flushPromises();
    expect(wrapper.get("[data-testid='position-pos-1']").text()).toContain("台账查看");
    expect(wrapper.get("[data-testid='position-pos-2']").text()).toContain("未绑定");

    await wrapper.get("[data-testid='position-pos-1'] button").trigger("click");
    const editor = wrapper.get("[data-testid='role-editor']");
    expect(editor.text()).not.toContain("停用角色");
    const boxes = editor.findAll("input[type='checkbox']");
    expect(boxes.map((box) => box.element.checked)).toEqual([true, false]);

    const writer = { assignment_id: "a-2", role_id: "role-w", role_code: "ledger_writer", role_name: "台账编辑" };
    mocks.perm.setPositionRoles.mockResolvedValue([writer]);
    await boxes[0].setValue(false);
    await boxes[1].setValue(true);
    await editor.trigger("submit"); await flushPromises();

    expect(mocks.perm.setPositionRoles).toHaveBeenCalledWith("pos-1", ["role-w"]);
    expect(wrapper.find("[data-testid='role-editor']").exists()).toBe(false);
    expect(wrapper.get("[data-testid='position-pos-1']").text()).toContain("台账编辑");
    expect(wrapper.get("[data-testid='position-pos-1']").text()).not.toContain("台账查看");
  });

  it("keeps the editor open with a clear message when saving is refused", async () => {
    const wrapper = mount(PositionView); await flushPromises();
    await wrapper.get("[data-testid='position-pos-2'] button").trigger("click");
    mocks.perm.setPositionRoles.mockRejectedValue(Object.assign(new Error("duty conflict"), { status: 409, code: "P2.PERM.0009" }));
    await wrapper.get("[data-testid='role-editor']").trigger("submit"); await flushPromises();
    expect(wrapper.get("[data-testid='role-editor']").text()).toContain("duty conflict");

    mocks.perm.setPositionRoles.mockRejectedValue(Object.assign(new Error("denied"), { status: 403, code: "P2.PERM.0003" }));
    await wrapper.get("[data-testid='role-editor']").trigger("submit"); await flushPromises();
    expect(wrapper.get("[data-testid='role-editor']").text()).toContain("你没有修改岗位角色的权限");
  });

  it("previews what a position brings (PLA3)", async () => {
    mocks.perm.effectivePosition.mockResolvedValue({
      subject_type: "POSITION", duty_conflict: false,
      roles: [{ role_code: "ledger_reader", role_name: "台账查看", via: "POSITION", via_id: "pos-1", via_label: "文员" }],
      permissions: [{ resource: "ledger", action: "read", outcome: "ALLOW",
        sources: [{ effect: "GRANT", scope: "DEPT", principal_type: "role", principal_id: "ledger_reader", label: "ledger_reader" }] }],
    });
    const wrapper = mount(PositionView); await flushPromises();
    await wrapper.get("[data-testid='position-pos-1'] [data-action='preview']").trigger("click"); await flushPromises();

    const panel = wrapper.get("[data-testid='position-preview']");
    expect(panel.text()).toContain("「文员」的有效权限");
    expect(panel.text()).toContain("台账查看");
    expect(panel.get("[data-permission='ledger:read']").text()).toContain("本部门");
    expect(mocks.perm.effectivePosition).toHaveBeenCalledWith("pos-1");
  });

  it("falls back to a plain position list without permission read access", async () => {
    mocks.perm.positionRoles.mockRejectedValue(Object.assign(new Error("denied"), { status: 403, code: "P2.PERM.0003" }));
    const wrapper = mount(PositionView); await flushPromises();
    expect(wrapper.get("[data-testid='binding-notice']").text()).toContain("没有查看角色绑定的权限");
    expect(wrapper.text()).toContain("文员");
    expect(wrapper.find("[data-testid='position-pos-1'] button").exists()).toBe(false);
  });
});
