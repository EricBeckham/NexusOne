/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const mocks = vi.hoisted(() => ({ baselineDrift: vi.fn(), mergeBaseline: vi.fn() }));
vi.mock("../../api/portal", () => ({ baselineDrift: mocks.baselineDrift, mergeBaseline: mocks.mergeBaseline }));

import BaselineDiffView from "./BaselineDiffView.vue";

describe("PLC8 baseline diff", () => {
  beforeEach(() => {
    mocks.baselineDrift.mockReset();
    mocks.mergeBaseline.mockReset();
    mocks.mergeBaseline.mockResolvedValue({ drift: false });
  });

  it("shows every change and merges only after each conflict has a side", async () => {
    mocks.baselineDrift.mockResolvedValue([{
      instance_id: "inst-1", object_type: "FORM", object_code: "leave_apply",
      base_version: "1.0.0", platform_version: "1.1.0", drift: true,
      changes: [
        { path: "/fields/days", kind: "PLATFORM_ONLY" },
        { path: "/title", kind: "CONFLICT" },
        { path: "/fields/note", kind: "TENANT_ONLY" },
      ],
    }]);
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: "/p3/portal/baseline", component: BaselineDiffView }] });
    await router.push("/p3/portal/baseline");
    const wrapper = mount(BaselineDiffView, { global: { plugins: [router] } });
    await flushPromises();
    const changes = wrapper.findAll("[data-testid='baseline-change']");
    expect(changes).toHaveLength(3);
    expect(wrapper.text()).toContain("/fields/days");
    expect(wrapper.text()).toContain("/title");
    expect(wrapper.text()).toContain("/fields/note");
    expect(wrapper.get("[data-testid='baseline-merge']").attributes("disabled")).toBeDefined();
    await wrapper.get("input[value='TENANT']").setValue(true);
    await wrapper.get("[data-testid='baseline-merge']").trigger("click");
    await flushPromises();
    expect(mocks.mergeBaseline).toHaveBeenCalledWith("inst-1", "FORM", "leave_apply", { "/title": "TENANT" });
  });
});
