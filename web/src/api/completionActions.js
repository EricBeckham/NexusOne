/**
 * FA5: post-completion actions ("办结后动作") — docs/planning/FA1-办结后动作设计.md.
 * Tenant scope comes from the issued X-Nexus-Context-Id (ARCH-024); the wire is snake_case.
 */
import { api } from "./client";
import { pick } from "./p3-live";

export const TRIGGERS = [
  { value: "APPROVED", label: "审批通过" },
  { value: "REJECTED", label: "审批驳回" },
  { value: "ANY", label: "通过或驳回" },
];

export const SOURCE_KINDS = [
  { value: "", label: "不填" },
  { value: "FIELD", label: "源单字段" },
  { value: "CONST", label: "固定值" },
  { value: "SYSTEM", label: "系统值" },
];

export const SYSTEM_VALUES = [
  { value: "INITIATOR", label: "发起人" },
  { value: "LAST_APPROVER", label: "最后审批人" },
  { value: "COMPLETED_AT", label: "办结时间" },
  { value: "SOURCE_RECORD_ID", label: "来源单号" },
  { value: "OUTCOME", label: "审批结果" },
];

/** Target fields a mapping may fill: business fields only (system columns and approval status are server-owned). */
export const NOT_MAPPABLE = new Set([
  "id", "tenant_id", "owner_id", "department_id", "created_at", "created_by", "updated_at", "updated_by",
  "deleted_at", "version", "schema_version", "ext_data", "approval_status",
]);

export function mapAction(row) {
  if (!row) return row;
  return {
    id: pick(row, "id"),
    name: pick(row, "name") || "",
    sourceLedgerCode: pick(row, "source_ledger_code", "sourceLedgerCode") || "",
    processKey: pick(row, "process_key", "processKey") || "",
    trigger: pick(row, "trigger") || "APPROVED",
    targetLedgerCode: pick(row, "target_ledger_code", "targetLedgerCode") || "",
    fieldMapping: (pick(row, "field_mapping", "fieldMapping") || []).map((item) => ({
      target: pick(item, "target"),
      kind: pick(item, "kind"),
      value: pick(item, "value") ?? "",
    })),
    enabled: pick(row, "enabled") !== false,
    version: pick(row, "version") ?? 1,
    updatedAt: pick(row, "updated_at", "updatedAt"),
  };
}

function toWire(draft) {
  return {
    name: draft.name,
    source_ledger_code: draft.sourceLedgerCode,
    process_key: draft.processKey,
    trigger: draft.trigger,
    target_ledger_code: draft.targetLedgerCode,
    field_mapping: (draft.fieldMapping || [])
      .filter((item) => item.kind)
      .map((item) => ({ target: item.target, kind: item.kind, value: item.value ?? "" })),
    enabled: draft.enabled,
    version: draft.version,
  };
}

export const completionActions = {
  list: async () => {
    const body = await api("/api/v1/p3/completion-actions");
    return (Array.isArray(body) ? body : []).map(mapAction);
  },
  create: async (draft) => mapAction(await api("/api/v1/p3/completion-actions", { method: "POST", body: toWire(draft) })),
  update: async (id, draft) =>
    mapAction(await api(`/api/v1/p3/completion-actions/${encodeURIComponent(id)}`, { method: "PUT", body: toWire(draft) })),
  preview: async (id, sourceRecordId, outcome) => {
    const body = await api(`/api/v1/p3/completion-actions/${encodeURIComponent(id)}/preview`, {
      method: "POST",
      body: { source_record_id: sourceRecordId, outcome: outcome || null },
    });
    return body?.row || {};
  },
};

/** Server validation problems ("field: reason") from a P3.ACTION.0001 answer, for inline display. */
export function actionProblems(err) {
  const problems = err?.body?.details?.problems;
  return Array.isArray(problems) ? problems.map(String) : [];
}
