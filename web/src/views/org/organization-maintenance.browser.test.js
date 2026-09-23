// @vitest-environment happy-dom
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import OrgTreeView from "./OrgTreeView.vue";
import PositionView from "./PositionView.vue";

const mocks = vi.hoisted(() => ({
  departments: vi.fn(), persons: vi.fn(), createDepartment: vi.fn(), disableDepartment: vi.fn(),
  positions: vi.fn(), allEmployments: vi.fn(), createPosition: vi.fn(), disablePosition: vi.fn(),
  assignEmployment: vi.fn(), endEmployment: vi.fn(),
}));

vi.mock("../../api/client", async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, p2: { ...actual.p2, ...mocks } };
});

const person = { id: "person-1", displayName: "Alice", employeeNo: "E-001", status: "ACTIVE" };
const department = { id: "dept-1", code: "ROOT", name: "Head Office", status: "ACTIVE" };
const position = { id: "pos-1", code: "ENG", name: "Engineer", category: "IC", status: "ACTIVE" };
const employment = {
  id: "emp-1", personId: person.id, departmentId: department.id, positionId: position.id,
  kind: "PRIMARY", status: "ACTIVE", validFrom: "2026-09-01", validTo: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.departments.mockResolvedValue([{ department, children: [] }]);
  mocks.persons.mockResolvedValue([person]);
  mocks.positions.mockResolvedValue([position]);
  mocks.allEmployments.mockResolvedValue([employment]);
  mocks.createDepartment.mockResolvedValue({});
  mocks.disableDepartment.mockResolvedValue({});
  mocks.createPosition.mockResolvedValue({});
  mocks.disablePosition.mockResolvedValue({});
  mocks.assignEmployment.mockResolvedValue({});
  mocks.endEmployment.mockResolvedValue({});
});

describe("T2-B organization maintenance", () => {
  it("creates a child department with camelCase API fields and can disable it", async () => {
    const wrapper = mount(OrgTreeView);
    await flushPromises();
    const form = wrapper.get("[data-testid=department-form]");
    const inputs = form.findAll("input");
    const selects = form.findAll("select");
    await inputs[0].setValue("ENG");
    await inputs[1].setValue("Engineering");
    await inputs[2].setValue("20");
    await selects[0].setValue(department.id);
    await selects[1].setValue(person.id);
    await form.trigger("submit");
    await flushPromises();

    expect(mocks.createDepartment).toHaveBeenCalledWith({
      code: "ENG", name: "Engineering", parentId: department.id,
      leaderPersonId: person.id, sortOrder: 20,
    });

    await wrapper.get(`[data-testid=disable-department-${department.id}]`).trigger("click");
    await flushPromises();
    expect(mocks.disableDepartment).toHaveBeenCalledWith(department.id);
  });

  it("assigns and ends an employment through selectable tenant data", async () => {
    const wrapper = mount(PositionView);
    await flushPromises();
    const form = wrapper.get("[data-testid=employment-form]");
    const selects = form.findAll("select");
    const dates = form.findAll('input[type="date"]');
    await selects[0].setValue(person.id);
    await selects[1].setValue(department.id);
    await selects[2].setValue(position.id);
    await selects[3].setValue("SECONDARY");
    await dates[0].setValue("2026-09-23");
    await form.trigger("submit");
    await flushPromises();

    expect(mocks.assignEmployment).toHaveBeenCalledWith({
      personId: person.id, departmentId: department.id, positionId: position.id,
      kind: "SECONDARY", validFrom: "2026-09-23", validTo: null,
    });

    await wrapper.get(`[data-testid=end-employment-${employment.id}]`).trigger("click");
    await flushPromises();
    expect(mocks.endEmployment).toHaveBeenCalledWith(employment.id);
  });
});
