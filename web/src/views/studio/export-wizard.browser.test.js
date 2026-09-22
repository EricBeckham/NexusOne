/**
 * @vitest-environment happy-dom
 *
 * G4 leftover: export wizard begin / complete clicks. Stubs p3 HTTP
 * (no JAR, no Docker). Not a product G4 close.
 */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";

const { beginExportWizard, completeExportWizard, exportWizardDecision } = vi.hoisted(() => ({
  beginExportWizard: vi.fn(),
  completeExportWizard: vi.fn(),
  exportWizardDecision: vi.fn(),
}));

vi.mock("../../api/p3", () => ({
  p3: { beginExportWizard, completeExportWizard, exportWizardDecision },
}));

vi.mock("../../api/client", () => ({
  perm: { check: vi.fn().mockResolvedValue({ allowed: true, reason: "allowed", engine: "PermissionEngine" }) },
}));

vi.mock("../../api/session", () => ({
  session: { contextId: "ctx-1", tenantId: "tenant-1" },
}));

import ExportWizardView from "./ExportWizardView.vue";

async function mountWizard() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/p3/exports/wizard", component: ExportWizardView }],
  });
  await router.push("/p3/exports/wizard");
  await router.isReady();
  return mount(ExportWizardView, { global: { plugins: [router] } });
}

describe("G4 export wizard leftover", () => {
  beforeEach(() => {
    beginExportWizard.mockReset();
    completeExportWizard.mockReset();
    exportWizardDecision.mockReset();
    exportWizardDecision.mockResolvedValue({
      export: { allowed: true, reason: "allowed", engine: "PermissionEngine" },
      wizard: true,
    });
  });

  it("begin then complete posts wizard_id", async () => {
    beginExportWizard.mockResolvedValue({
      wizard_id: "wiz-1",
      step: "begin",
      completed: false,
      decision: { allowed: true, engine: "PermissionEngine" },
    });
    completeExportWizard.mockResolvedValue({
      wizard_id: "wiz-1",
      step: "complete",
      completed: true,
      task_type: "ledger.export.csv",
    });
    const wrapper = await mountWizard();
    await flushPromises();
    await wrapper.get("[data-testid=g4-export-wizard-begin]").trigger("click");
    await flushPromises();
    expect(beginExportWizard).toHaveBeenCalledTimes(1);
    expect(wrapper.get("[data-testid=g4-export-wizard-id]").text()).toContain("wiz-1");
    await wrapper.get("[data-testid=g4-export-wizard-complete]").trigger("click");
    await flushPromises();
    expect(completeExportWizard).toHaveBeenCalledWith({ wizard_id: "wiz-1" });
    expect(wrapper.get("[data-testid=g4-export-wizard-complete-result]").text()).toContain("complete");
  });
});
