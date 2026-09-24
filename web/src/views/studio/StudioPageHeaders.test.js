import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pages = [
  "StudioHomeView.vue",
  "LedgerListView.vue",
  "LedgerDetailView.vue",
  "LedgerRecordDetailView.vue",
  "FormListView.vue",
  "FormStudioView.vue",
  "TodoInboxView.vue",
  "NotifyInboxView.vue",
  "AuditTraceView.vue",
  "ReportView.vue",
  "DumpView.vue",
  "ExportWizardView.vue",
];

describe("W3 Studio page headings", () => {
  it.each(pages)("uses the shared NxPageHeader in %s", (page) => {
    const source = readFileSync(new URL(page, import.meta.url), "utf8");
    expect(source).toContain("<NxPageHeader");
    expect(source).toContain('from "../../components"');
  });
});
