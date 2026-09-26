/** @vitest-environment happy-dom */
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import { describe, expect, it, vi } from "vitest";
import FormStudioView from "./FormStudioView.vue";
import { p3 } from "../../api/p3";
import { p2 } from "../../api/client";

vi.mock("../../api/p3", () => ({ p3: { getForm: vi.fn() } }));
vi.mock("../../api/client", async (importOriginal) => ({ ...await importOriginal(), p2: { persons: vi.fn() } }));

describe("form studio visual states", () => {
  it("shows a loading skeleton, then a sequential publish guide and validation empty state", async () => {
    let resolveForm;
    p3.getForm.mockReturnValue(new Promise((resolve) => { resolveForm = resolve; }));
    p2.persons.mockResolvedValue([]);
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/p3/forms/:id", component: FormStudioView }],
    });
    await router.push("/p3/forms/form1?tab=publish");
    const wrapper = mount(FormStudioView, { global: { plugins: [router] } });
    expect(wrapper.get("[data-testid='nx-skeleton']").attributes("aria-busy")).toBe("true");
    resolveForm({ name: "审批申请单", draft_status: "DRAFT", running_version: 0, draft_fields: [], running_fields: [] });
    await flushPromises();
    expect(wrapper.get(".steps").findAll("li")).toHaveLength(4);
    expect(wrapper.get(".steps").text()).toContain("草稿");
    expect(wrapper.get(".steps").text()).toContain("运行");
    expect(wrapper.get("[data-testid='nx-empty-title']").text()).toBe("尚未校验");
  });
});
