/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const mocks = vi.hoisted(() => ({
  appCategories: vi.fn(),
  createAppCategory: vi.fn(),
  updateAppCategory: vi.fn(),
  deleteAppCategory: vi.fn(),
  setAppCategory: vi.fn(),
  manifest: vi.fn(),
  validateManifest: vi.fn(),
  saveManifest: vi.fn(),
}));
vi.mock("../../api/client", () => ({ p4: mocks }));

import AppDesignPanel from "./AppDesignPanel.vue";

const app = { id: "app-1", code: "leave", name: "请假" };
const versions = [
  { id: "v-draft", version: "1.0.0", status: "DRAFT" },
  { id: "v-live", version: "0.9.0", status: "PUBLISHED" },
];

describe("PLB4 app design", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((fn) => fn.mockReset());
    mocks.appCategories.mockResolvedValue([{ code: "oa", name: "办公", description: "", sort_order: 1, version: 2 }]);
    mocks.manifest.mockResolvedValue({ manifest: null });
    mocks.createAppCategory.mockResolvedValue({});
    mocks.setAppCategory.mockResolvedValue(null);
    mocks.validateManifest.mockResolvedValue({
      valid: false,
      problems: [
        { path: "/forms/0/ledger_code", reason: "unknown_ref" },
        { path: "/entries/1/kind", reason: "kind_purpose_mismatch" },
      ],
    });
    mocks.saveManifest.mockRejectedValue(Object.assign(new Error("invalid application manifest"), {
      body: { problems: [
        { path: "/app_code", reason: "app_code_mismatch" },
        { path: "/category_code", reason: "category_mismatch" },
      ] },
    }));
  });

  async function panel() {
    const wrapper = mount(AppDesignPanel, { props: { app, versions } });
    await flushPromises();
    return wrapper;
  }

  it("lists every validation problem from one check", async () => {
    const wrapper = await panel();
    await wrapper.get("[data-testid='manifest-validate']").trigger("click");
    await flushPromises();
    const items = wrapper.findAll("[data-testid='manifest-problem']");
    expect(items).toHaveLength(2);
    expect(items[0].text()).toContain("/forms/0/ledger_code");
    expect(items[0].text()).toContain("unknown_ref");
    expect(items[1].text()).toContain("/entries/1/kind");
    expect(items[1].text()).toContain("kind_purpose_mismatch");
    expect(wrapper.text()).toContain("共 2 个校验问题");
  });

  it("lists every problem returned when a draft save is rejected", async () => {
    const wrapper = await panel();
    await wrapper.get("[data-testid='manifest-json']").setValue('{"schema":"nexus.app-manifest.v1"}');
    await wrapper.get("[data-testid='manifest-save']").trigger("click");
    await flushPromises();
    const items = wrapper.findAll("[data-testid='manifest-problem']");
    expect(items).toHaveLength(2);
    expect(wrapper.text()).toContain("app_code_mismatch");
    expect(wrapper.text()).toContain("category_mismatch");
    expect(mocks.saveManifest).toHaveBeenCalledWith("v-draft", { schema: "nexus.app-manifest.v1" });
  });

  it("creates a category and assigns it to the selected app", async () => {
    const wrapper = await panel();
    expect(wrapper.get("[data-testid='category-oa']").text()).toContain("办公");
    await wrapper.get("[data-testid='category-code']").setValue("hr");
    await wrapper.get("[data-testid='category-name']").setValue("人事");
    await wrapper.get("[data-testid='category-save']").trigger("click");
    await flushPromises();
    expect(mocks.createAppCategory).toHaveBeenCalledWith("hr", { name: "人事", description: null, sort_order: 0 });
    await wrapper.get("[data-testid='app-category']").setValue("oa");
    await wrapper.get("[data-testid='app-category-save']").trigger("click");
    await flushPromises();
    expect(mocks.setAppCategory).toHaveBeenCalledWith("app-1", "oa");
  });
});
