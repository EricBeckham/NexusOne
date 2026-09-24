/**
 * Live P3 HTTP client. Paths match M0-06/07/08/G18 on main.
 * Tenant scope is X-Nexus-Context-Id only (ARCH-024). Never X-Tenant-Id.
 */
import { api, newTraceId } from "./client";
import { rememberTrace } from "./session";

const SYSTEM_FIELDS = [
  { code: "id", name: "id", type: "uuid", system: true, required: true },
  { code: "tenant_id", name: "tenant_id", type: "uuid", system: true, required: true },
  { code: "owner_id", name: "owner_id", type: "person", system: true, required: true },
  { code: "department_id", name: "department_id", type: "department", system: true, required: true },
  { code: "created_at", name: "created_at", type: "datetime", system: true },
  { code: "updated_at", name: "updated_at", type: "datetime", system: true },
  { code: "created_by", name: "created_by", type: "person", system: true },
  { code: "updated_by", name: "updated_by", type: "person", system: true },
  { code: "version", name: "version", type: "number", system: true },
  { code: "deleted_at", name: "deleted_at", type: "datetime", system: true },
  { code: "schema_version", name: "schema_version", type: "number", system: true },
  { code: "ext_data", name: "ext_data", type: "json", system: true },
];

const UI_TO_API_FIELD = {
  text: "TEXT",
  longtext: "TEXT",
  number: "NUMERIC",
  money: "NUMERIC",
  date: "TIMESTAMPTZ",
  boolean: "BOOLEAN",
  enum: "TEXT",
  person: "TEXT",
  department: "TEXT",
};

export function asList(body) {
  if (Array.isArray(body)) {
    return body;
  }
  if (body && Array.isArray(body.items)) {
    return body.items;
  }
  return [];
}

export function pick(row, ...names) {
  if (row == null || typeof row !== "object") {
    return undefined;
  }
  for (const name of names) {
    if (row[name] != null) {
      return row[name];
    }
  }
  return undefined;
}

function wrap(data) {
  return { _source: "LIVE", ...data };
}

export function mapLedger(row) {
  if (!row) {
    return row;
  }
  const fields = (pick(row, "fields") || []).map(mapField);
  const schema = pick(row, "schema_version", "schemaVersion") ?? 1;
  return wrap({
    id: pick(row, "id"),
    name: pick(row, "display_name", "displayName", "name"),
    code: pick(row, "ledger_code", "ledgerCode", "code"),
    source: pick(row, "scope") === "APP_SHARED" ? "APP_SHARED" : "TENANT_CUSTOM",
    status: pick(row, "status"),
    physical_table: pick(row, "physical_table", "physicalTable"),
    application_id: pick(row, "application_id", "applicationId"),
    tenant_id: pick(row, "tenant_id", "tenantId"),
    tenant_code: pick(row, "tenant_code", "tenantCode"),
    draft_schema_version: schema,
    running_schema_version: schema,
    field_count: fields.length,
    fields,
    version: pick(row, "version"),
  });
}

export function mapField(row) {
  return {
    id: pick(row, "id"),
    code: pick(row, "field_code", "fieldCode", "code"),
    name: pick(row, "display_name", "displayName", "name"),
    type: String(pick(row, "field_type", "fieldType", "type") || "TEXT").toLowerCase(),
    required: Boolean(pick(row, "required")),
    indexed: Boolean(pick(row, "indexed")),
    sort_order: pick(row, "sort_order", "sortOrder"),
    running: true,
  };
}

export function mapForm(row) {
  if (!row) {
    return row;
  }
  const bindings = pick(row, "bindings") || [];
  const running = pick(row, "running_version", "runningVersion");
  return wrap({
    id: pick(row, "form_id", "formId", "id"),
    name: pick(row, "name"),
    code: pick(row, "form_code", "formCode"),
    app_code: pick(row, "app_code", "appCode"),
    description: pick(row, "description"),
    draft_status: pick(row, "draft_status", "draftStatus"),
    published_version: running || 0,
    running_version: running || 0,
    ledger_name: bindings[0] ? pick(bindings[0], "ledger_code", "ledgerCode") : "",
    bindings,
    draft_fields: bindings.map(bindingToField),
  });
}

function bindingToField(binding) {
  const widget = String(pick(binding, "widget_type", "widgetType") || "TEXT").toUpperCase();
  return {
    id: pick(binding, "widget_code", "widgetCode"),
    code: pick(binding, "field_code", "fieldCode"),
    name: pick(binding, "widget_code", "widgetCode", "field_code", "fieldCode"),
    type: widget === "TEXTAREA" ? "longtext" : widget.toLowerCase(),
    required: Boolean(pick(binding, "required")),
    options: [],
  };
}

export function mapTodo(row, box) {
  if (!row) {
    return row;
  }
  const status = pick(row, "status");
  const outcome = pick(row, "outcome");
  return wrap({
    id: pick(row, "id"),
    instance_id: pick(row, "instance_id", "instanceId", "id"),
    title: pick(row, "task_name", "taskName", "process_key", "processKey") || "待办",
    process: pick(row, "task_name", "taskName", "process_key", "processKey"),
    biz_no: pick(row, "business_key", "businessKey"),
    applicant: pick(row, "assignee_person_id", "assigneePersonId", "initiator_person_id", "initiatorPersonId"),
    amount: pick(row, "amount") ?? "",
    priority: "NORMAL",
    arrived_at: pick(row, "created_at", "createdAt", "started_at", "startedAt"),
    status: outcome || status,
    box,
    comment: "",
    record: null,
  });
}

export function mapApplication(row) {
  if (!row) {
    return row;
  }
  return wrap({
    id: pick(row, "id"),
    app_code: pick(row, "app_code", "appCode"),
    display_name: pick(row, "display_name", "displayName", "name"),
    kind: pick(row, "kind"),
    status: pick(row, "status"),
    tenant_id: pick(row, "tenant_id", "tenantId"),
    tenant_code: pick(row, "tenant_code", "tenantCode"),
  });
}

export async function listApplications() {
  return wrap({ items: asList(await api("/api/v1/p3/applications")).map(mapApplication) });
}

export async function createApplication(body) {
  return mapApplication(
    await api("/api/v1/p3/applications", {
      method: "POST",
      body: {
        app_code: body.app_code || body.appCode,
        display_name: body.display_name || body.displayName || body.name,
        kind: body.kind || "CUSTOM",
      },
    }),
  );
}

export async function listBusinessTypes() {
  const body = (await api("/api/v1/p3/business-types")) || {};
  return wrap({
    items: asList(body.items || body),
    count: pick(body, "count") ?? asList(body.items || body).length,
  });
}

export async function provisionBusinessType(code, body = {}) {
  return wrap(
    await api(`/api/v1/p3/business-types/${code}/provision`, {
      method: "POST",
      body: {
        publish: body.publish !== false,
        suffix: body.suffix,
      },
    }),
  );
}

export async function createLedger(body) {
  const applicationId = body.application_id || body.applicationId;
  const fields = (body.fields || []).map((field) => ({
    field_code: field.field_code || field.code,
    display_name: field.display_name || field.name,
    field_type: UI_TO_API_FIELD[field.type] || String(field.field_type || field.fieldType || field.type || "TEXT").toUpperCase(),
    required: Boolean(field.required),
    indexed: Boolean(field.indexed),
  }));
  return mapLedger(
    await api(`/api/v1/p3/applications/${applicationId}/ledgers`, {
      method: "POST",
      body: {
        ledger_code: body.ledger_code || body.code,
        display_name: body.display_name || body.name,
        scope: body.scope || "TENANT",
        fields,
      },
    }),
  );
}

export async function listLedgers() {
  return wrap({ items: asList(await api("/api/v1/p3/ledgers")).map(mapLedger) });
}

export async function getLedger(id) {
  return mapLedger(await api(`/api/v1/p3/ledgers/${id}`));
}

export async function listFields(ledgerId) {
  const ledger = await getLedger(ledgerId);
  return wrap({ items: ledger.fields || [], system: SYSTEM_FIELDS, ledger });
}

export async function addField(ledgerId, body) {
  const fieldType = UI_TO_API_FIELD[body.type] || String(body.type || "TEXT").toUpperCase();
  const ledger = mapLedger(
    await api(`/api/v1/p3/ledgers/${ledgerId}/fields`, {
      method: "POST",
      body: {
        field_code: body.code,
        display_name: body.name,
        field_type: fieldType,
        required: Boolean(body.required),
        indexed: false,
      },
    }),
  );
  const field = (ledger.fields || []).find((item) => item.code === body.code) || ledger.fields?.at(-1);
  return wrap({ field, ledger });
}

export async function updateField() {
  const error = new Error("M0-06 has POST /api/v1/p3/ledgers/{id}/fields only; no field update API");
  error.status = 501;
  error.code = "P3.LEDGER.NO_FIELD_UPDATE";
  throw error;
}

function recordListQuery(query) {
  const params = new URLSearchParams();
  if (!query || typeof query !== "object") {
    return "";
  }
  for (const [key, value] of Object.entries(query)) {
    if (key === "page" || key === "size") {
      if (value != null && String(value).trim() !== "") {
        params.set(key, String(value).trim());
      }
      continue;
    }
    if (key === "tenant_id" || key === "tenantId") {
      continue;
    }
    if (value == null || String(value).trim() === "") {
      continue;
    }
    if (!/^[a-z][a-z0-9_]{0,23}$/.test(key)) {
      continue;
    }
    params.set(key, String(value).trim());
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

export async function listRecords(ledgerId, query = {}) {
  const page = (await api(`/api/v1/p3/ledgers/${ledgerId}/records${recordListQuery(query)}`)) || {};
  return wrap({
    items: asList(page),
    total: pick(page, "total") ?? asList(page).length,
    page: pick(page, "page") ?? 0,
    size: pick(page, "size") ?? 20,
  });
}

export async function getRecord(ledgerId, recordId) {
  return wrap(await api(`/api/v1/p3/ledgers/${ledgerId}/records/${recordId}`));
}

export async function createRecord(ledgerId, values) {
  return wrap(await api(`/api/v1/p3/ledgers/${ledgerId}/records`, {
    method: "POST",
    body: values || {},
  }));
}

export async function updateRecord(ledgerId, recordId, values, version) {
  return wrap(await api(`/api/v1/p3/ledgers/${ledgerId}/records/${recordId}`, {
    method: "PATCH",
    body: { values: values || {}, version },
  }));
}

export async function deleteRecord(ledgerId, recordId) {
  await api(`/api/v1/p3/ledgers/${ledgerId}/records/${recordId}`, { method: "DELETE" });
}

export async function listForms() {
  return wrap({ items: asList(await api("/api/v1/p3/forms")).map(mapForm) });
}

export async function getForm(id) {
  const form = mapForm(await api(`/api/v1/p3/forms/${id}`));
  let runningFields = [];
  if (form.running_version) {
    try {
      const running = await api(`/api/v1/p3/forms/${id}/running`);
      const snapshot = pick(running, "snapshot") || running;
      const bindings = pick(snapshot, "bindings") || [];
      runningFields = bindings.map(bindingToField);
    } catch {
      /* no running snapshot yet */
    }
  }
  return wrap({ ...form, running_fields: runningFields });
}

export async function createForm(body) {
  return mapForm(
    await api("/api/v1/p3/forms", {
      method: "POST",
      body: {
        app_code: body.app_code || body.appCode,
        form_code: body.form_code || body.formCode || body.code,
        name: body.name,
        description: body.description || "",
        bindings: body.bindings || [],
      },
    }),
  );
}

export async function updateForm(id, body) {
  return mapForm(
    await api(`/api/v1/p3/forms/${id}`, {
      method: "PUT",
      body: {
        app_code: body.app_code || body.appCode,
        form_code: body.form_code || body.formCode || body.code,
        name: body.name,
        description: body.description,
        bindings: body.bindings,
      },
    }),
  );
}

export async function fillForm(id, body) {
  const values = body && body.values ? body.values : body;
  const submission = await api(`/api/v1/p3/forms/${id}/submissions`, {
    method: "POST",
    body: { values },
  });
  return wrap({
    record: submission,
    todo: null,
    note: "POST /submissions 写入 form_submission（可带 ledger_record_id）。绑定 P2 时按 form.write 鉴权。G18 顺序审批请走 submitStart。",
  });
}

export async function submitStart(formId, body) {
  const traceId = body.trace_id || body.traceId || newTraceId();
  const idempotencyKey = body.idempotency_key || body.idempotencyKey || newTraceId();
  rememberTrace(traceId);
  const started = await api("/api/v1/p3/path/submit-start", {
    method: "POST",
    traceId,
    headers: { "Idempotency-Key": idempotencyKey },
    body: {
      form_id: formId,
      values: body.values || {},
      process_key: body.process_key || body.processKey || "nexusSequentialApproval",
      assignee_type: body.assignee_type || body.assigneeType || "PERSON",
      assignee_ref: body.assignee_ref || body.assigneeRef,
      idempotency_key: idempotencyKey,
    },
  });
  return wrap({
    ...started,
    trace_id: traceId,
    idempotency_key: idempotencyKey,
    note: "POST /api/v1/p3/path/submit-start：物理台账行 + Flowable 顺序审批。重复同一 Idempotency-Key 不写第二行。",
  });
}

export async function validateForm(id) {
  try {
    const form = mapForm(await api(`/api/v1/p3/forms/${id}/validate`, { method: "POST", body: {} }));
    return wrap({
      ok: form.draft_status === "VALIDATED",
      issues: [],
      at: new Date().toISOString(),
      form,
    });
  } catch (error) {
    const details = error.body?.details || {};
    const issues = Array.isArray(details.errors)
      ? details.errors.map((item) => ({
          kind: "Validation Rule",
          message: item.reason || item.message || JSON.stringify(item),
        }))
      : [{ kind: "Validation Rule", message: error.message }];
    return wrap({ ok: false, issues, at: new Date().toISOString(), code: error.code });
  }
}

export async function publishForm(id) {
  const form = mapForm(await api(`/api/v1/p3/forms/${id}/publish`, { method: "POST", body: { notes: "m0-12-ui" } }));
  return wrap({
    published: true,
    running_version: form.running_version,
    published_version: form.published_version,
    form,
  });
}

export async function simulatePublishFailure(id) {
  const log = await api(`/api/v1/p3/forms/${id}/publish-log`);
  return wrap({
    published: false,
    rolled_back: false,
    note: "G5 失败回滚由 M0-07 服务端事务覆盖，UI 不再模拟失败。下面是 publish-log。",
    log: asList(log),
  });
}

function todoPath(box) {
  if (box === "done") {
    return "/api/v1/todos/done";
  }
  if (box === "initiated") {
    return "/api/v1/todos/initiated";
  }
  return "/api/v1/p3/todos";
}

export async function listTodos(box = "inbox") {
  const items = asList(await api(todoPath(box))).map((row) => mapTodo(row, box));
  return wrap({ items, total: items.length });
}

export async function getTodo(id) {
  return mapTodo(await api(`/api/v1/todos/${id}`), "inbox");
}

function mapDecision(row, box) {
  const todo = pick(row, "todo") || row;
  const mapped = mapTodo(todo, box);
  return wrap({
    ...mapped,
    id: pick(todo, "id") || pick(row, "record_id", "recordId"),
    instance_id: pick(pick(row, "process") || {}, "id") || mapped.instance_id,
    approval_status: pick(row, "approval_status", "approvalStatus"),
    ledger_id: pick(row, "ledger_id", "ledgerId"),
    record_id: pick(row, "record_id", "recordId"),
    physical_table: pick(row, "physical_table", "physicalTable"),
    process_status: pick(pick(row, "process") || {}, "status"),
  });
}

export async function approveTodo(id) {
  const traceId = newTraceId();
  rememberTrace(traceId);
  return mapDecision(
    await api(`/api/v1/p3/path/tasks/${id}/approve`, { method: "POST", body: {}, traceId }),
    "done",
  );
}

export async function rejectTodo(id) {
  const traceId = newTraceId();
  rememberTrace(traceId);
  return mapDecision(
    await api(`/api/v1/p3/path/tasks/${id}/reject`, { method: "POST", body: {}, traceId }),
    "done",
  );
}

export async function withdrawTodo(_id, _comment, instanceId) {
  if (!instanceId) {
    const error = new Error("撤回需要 process instance id（TodoView.instance_id）");
    error.status = 400;
    error.code = "P3.FLOW.NO_INSTANCE";
    throw error;
  }
  const traceId = newTraceId();
  rememberTrace(traceId);
  return mapDecision(
    await api(`/api/v1/p3/path/instances/${instanceId}/withdraw`, { method: "POST", body: {}, traceId }),
    "initiated",
  );
}

export async function reportDecision() {
  return wrap(await api("/api/v1/p3/reports/decision"));
}

export async function queryReport(body = {}) {
  return wrap(await api("/api/v1/p3/reports/query", { method: "POST", body }));
}

export async function exportReport(body = {}) {
  return wrap(await api("/api/v1/p3/reports/export", { method: "POST", body }));
}

export async function dumpDecision() {
  return wrap(await api("/api/v1/p3/dumps/decision"));
}

export async function exportDump(body = {}) {
  return wrap(await api("/api/v1/p3/dumps/export", { method: "POST", body }));
}

export async function exportWizardDecision() {
  return wrap(await api("/api/v1/p3/exports/wizard/decision"));
}

export async function beginExportWizard(body = {}) {
  return wrap(await api("/api/v1/p3/exports/wizard/begin", { method: "POST", body }));
}

export async function completeExportWizard(body = {}) {
  return wrap(await api("/api/v1/p3/exports/wizard/complete", { method: "POST", body }));
}

export async function listProcessDefinitions() {
  return asList(await api("/api/v1/process/definitions"));
}

export async function getProcessDefinition(key) {
  return api(`/api/v1/process/definitions/${encodeURIComponent(key)}`);
}
