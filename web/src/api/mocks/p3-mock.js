/**
 * MOCK — in-browser P3 fixtures until M0-06 Schema/CRUD, M0-07 form publish,
 * and M0-08 sequential approval HTTP APIs exist.
 *
 * Markers:
 *   - every entity id uses the UUIDv7-shaped group `mock`
 *   - every payload includes `_source: "MOCK"`
 *   - sessionStorage key is `nexus.m012.p3.mock`
 *
 * Swap: set VITE_NEXUS_P3_USE_MOCK=false (see ../p3.js and README.md).
 * This store is UI-only. It does not close G18 / G5.
 */
const STORAGE_KEY = "nexus.m012.p3.mock";
const SOURCE = "MOCK";

const APP_ID = "0196c0a0-mock-7000-8000-00000000app1";
const LEDGER_ID = "0196c0a0-mock-7000-8000-00000000led1";
const FORM_ID = "0196c0a0-mock-7000-8000-00000000frm1";

const SYSTEM_FIELDS = [
  { code: "id", name: "id", type: "uuid", system: true, required: true },
  { code: "tenant_id", name: "tenant_id", type: "uuid", system: true, required: true },
  { code: "owner_id", name: "owner_id", type: "person", system: true, required: true },
  { code: "created_at", name: "created_at", type: "datetime", system: true },
  { code: "updated_at", name: "updated_at", type: "datetime", system: true },
  { code: "created_by", name: "created_by", type: "person", system: true },
  { code: "updated_by", name: "updated_by", type: "person", system: true },
  { code: "version", name: "version", type: "number", system: true },
  { code: "deleted_at", name: "deleted_at", type: "datetime", system: true },
  { code: "schema_version", name: "schema_version", type: "number", system: true },
  { code: "ext_data", name: "ext_data", type: "json", system: true },
];

const BUSINESS_FIELDS = [
  { id: "0196c0a0-mock-7000-8000-00000000fld1", code: "title", name: "事由", type: "text", required: true, running: true },
  { id: "0196c0a0-mock-7000-8000-00000000fld2", code: "amount", name: "金额", type: "money", required: true, running: true },
  { id: "0196c0a0-mock-7000-8000-00000000fld3", code: "applicant", name: "申请人", type: "person", required: true, running: true },
  { id: "0196c0a0-mock-7000-8000-00000000fld4", code: "department", name: "部门", type: "department", required: true, running: true },
  { id: "0196c0a0-mock-7000-8000-00000000fld5", code: "claim_date", name: "发生日期", type: "date", required: true, running: true },
  { id: "0196c0a0-mock-7000-8000-00000000fld6", code: "category", name: "费用类别", type: "enum", required: true, running: true, options: ["差旅", "招待", "办公", "其他"] },
  { id: "0196c0a0-mock-7000-8000-00000000fld7", code: "remark", name: "备注", type: "longtext", required: false, running: true },
];

function seed() {
  return {
    seq: 200,
    app: { id: APP_ID, code: "expense", name: "费用报销", status: "RUNNING" },
    ledger: {
      id: LEDGER_ID,
      app_id: APP_ID,
      code: "expense_claim",
      name: "费用报销单",
      source: "TENANT_CUSTOM",
      status: "RUNNING",
      physical_table: "l_{tenant_code}_expense_claim",
      draft_schema_version: 2,
      running_schema_version: 1,
      quota: { metric: "custom_ledgers", used: 1, limit: 10 },
    },
    fields: BUSINESS_FIELDS.map((field) => ({ ...field })),
    records: [
      rec("rec1", { title: "上海客户拜访差旅", amount: 2860.5, applicant: "王敏", department: "销售一部", claim_date: "2026-09-18", category: "差旅", remark: "高铁+住宿", status: "PENDING" }),
      rec("rec2", { title: "办公文具采购", amount: 420, applicant: "李强", department: "行政", claim_date: "2026-09-16", category: "办公", remark: "", status: "APPROVED" }),
      rec("rec3", { title: "团队聚餐", amount: 980, applicant: "陈静", department: "研发", claim_date: "2026-09-12", category: "招待", remark: "超标待确认", status: "REJECTED" }),
    ],
    form: {
      id: FORM_ID,
      ledger_id: LEDGER_ID,
      code: "expense_claim_form",
      name: "费用报销填报",
      draft_status: "DRAFT",
      published_version: 1,
      running_version: 1,
      last_validation: { ok: true, issues: [], at: "2026-09-20T02:00:00Z" },
    },
    todos: [
      todo("tdo1", { box: "inbox", title: "审批：上海客户拜访差旅", biz_no: "EXP-20260918-001", applicant: "王敏", amount: 2860.5, priority: "HIGH", arrived_at: "2026-09-18T09:12:00Z", status: "PENDING", record_id: "0196c0a0-mock-7000-8000-00000000rec1" }),
      todo("tdo2", { box: "done", title: "审批：办公文具采购", biz_no: "EXP-20260916-002", applicant: "李强", amount: 420, priority: "NORMAL", arrived_at: "2026-09-16T11:00:00Z", status: "APPROVED", record_id: "0196c0a0-mock-7000-8000-00000000rec2" }),
      todo("tdo3", { box: "initiated", title: "我发起：华东渠道拜访", biz_no: "EXP-20260910-008", applicant: "当前用户", amount: 1540, priority: "NORMAL", arrived_at: "2026-09-10T08:30:00Z", status: "PENDING", record_id: null }),
    ],
  };
}

function rec(suffix, values) {
  return {
    id: `0196c0a0-mock-7000-8000-00000000${suffix}`,
    ledger_id: LEDGER_ID,
    schema_version: 1,
    ...values,
  };
}

function todo(suffix, values) {
  return {
    id: `0196c0a0-mock-7000-8000-00000000${suffix}`,
    process: "费用报销顺序审批",
    comment: "",
    ...values,
  };
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function load() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch {
    /* fall through */
  }
  const fresh = seed();
  persist(fresh);
  return fresh;
}

function persist(state) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function nextId(state, kind) {
  state.seq += 1;
  return `0196c0a0-mock-7000-8000-${kind}${String(state.seq).padStart(8, "0")}`;
}

function tagged(payload) {
  return { _source: SOURCE, ...payload };
}

function ledgerView(state) {
  return {
    ...state.ledger,
    app: state.app,
    field_count: state.fields.length,
    record_count: state.records.length,
    _source: SOURCE,
  };
}

export async function listLedgers() {
  const state = load();
  return tagged({ items: [ledgerView(state)] });
}

export async function getLedger(id) {
  const state = load();
  if (id !== state.ledger.id) {
    const error = new Error("MOCK ledger not found");
    error.status = 404;
    error.code = "P3.LEDGER.MOCK_NOT_FOUND";
    throw error;
  }
  return ledgerView(state);
}

export async function listFields(ledgerId) {
  const state = load();
  if (ledgerId !== state.ledger.id) {
    return tagged({ items: [], system: SYSTEM_FIELDS });
  }
  return tagged({ items: clone(state.fields), system: SYSTEM_FIELDS });
}

export async function addField(ledgerId, body) {
  const state = load();
  if (ledgerId !== state.ledger.id) {
    throw Object.assign(new Error("MOCK ledger not found"), { status: 404 });
  }
  const field = {
    id: nextId(state, "fld"),
    code: body.code || `field_${state.seq}`,
    name: body.name || "未命名字段",
    type: body.type || "text",
    required: Boolean(body.required),
    running: false,
    options: body.options || [],
  };
  state.fields.push(field);
  state.ledger.draft_schema_version += 1;
  persist(state);
  return tagged({ field, ledger: ledgerView(state) });
}

export async function updateField(ledgerId, fieldId, body) {
  const state = load();
  const field = state.fields.find((item) => item.id === fieldId);
  if (!field) {
    throw Object.assign(new Error("MOCK field not found"), { status: 404 });
  }
  Object.assign(field, {
    name: body.name ?? field.name,
    required: body.required ?? field.required,
    options: body.options ?? field.options,
  });
  state.ledger.draft_schema_version += 1;
  persist(state);
  return tagged({ field, ledger: ledgerView(state) });
}

export async function listRecords(ledgerId) {
  const state = load();
  const items = ledgerId === state.ledger.id ? clone(state.records) : [];
  return tagged({ items, total: items.length });
}

export async function listForms() {
  const state = load();
  return tagged({
    items: [
      {
        ...state.form,
        ledger_name: state.ledger.name,
        _source: SOURCE,
      },
    ],
  });
}

export async function getForm(id) {
  const state = load();
  if (id !== state.form.id) {
    throw Object.assign(new Error("MOCK form not found"), { status: 404, code: "P3.FORM.MOCK_NOT_FOUND" });
  }
  return tagged({
    ...state.form,
    ledger: ledgerView(state),
    running_fields: state.fields.filter((field) => field.running),
    draft_fields: clone(state.fields),
  });
}

export async function fillForm(id, body) {
  const state = load();
  if (id !== state.form.id) {
    throw Object.assign(new Error("MOCK form not found"), { status: 404 });
  }
  const record = rec("new", {
    id: nextId(state, "rec"),
    title: body.title,
    amount: Number(body.amount || 0),
    applicant: body.applicant,
    department: body.department,
    claim_date: body.claim_date,
    category: body.category,
    remark: body.remark || "",
    status: "PENDING",
  });
  state.records.unshift(record);
  const item = todo("new", {
    id: nextId(state, "tdo"),
    box: "inbox",
    title: `审批：${record.title}`,
    biz_no: `EXP-MOCK-${state.seq}`,
    applicant: record.applicant,
    amount: record.amount,
    priority: record.amount >= 2000 ? "HIGH" : "NORMAL",
    arrived_at: new Date().toISOString(),
    status: "PENDING",
    record_id: record.id,
  });
  state.todos.unshift(item);
  persist(state);
  return tagged({ record, todo: item, note: "MOCK fill only — no Flowable instance" });
}

export async function validateForm(id) {
  const state = load();
  const issues = [];
  if (!state.fields.some((field) => field.code === "title")) {
    issues.push({ kind: "Field Rule", message: "缺少必填字段 title" });
  }
  if (!state.fields.some((field) => field.code === "amount")) {
    issues.push({ kind: "Validation Rule", message: "金额字段未定义" });
  }
  state.form.last_validation = { ok: issues.length === 0, issues, at: new Date().toISOString() };
  state.form.draft_status = issues.length === 0 ? "VALIDATED" : "DRAFT";
  persist(state);
  return tagged({ ...state.form.last_validation });
}

export async function publishForm(id) {
  const state = load();
  const check = await validateForm(id);
  if (!check.ok) {
    return tagged({ published: false, running_version: state.form.running_version, validation: check });
  }
  state.form.published_version += 1;
  state.form.running_version = state.form.published_version;
  state.form.draft_status = "RUNNING";
  state.ledger.running_schema_version = state.ledger.draft_schema_version;
  state.fields.forEach((field) => {
    field.running = true;
  });
  persist(state);
  return tagged({
    published: true,
    running_version: state.form.running_version,
    published_version: state.form.published_version,
    note: "MOCK publish — no SchemaService / G5 rollback path",
  });
}

export async function simulatePublishFailure() {
  const state = load();
  const before = {
    running_version: state.form.running_version,
    published_version: state.form.published_version,
    draft_status: state.form.draft_status,
  };
  return tagged({
    published: false,
    rolled_back: true,
    before,
    after: before,
    note: "MOCK: running version unchanged. Real G5 rollback waits for M0-07.",
  });
}

export async function listTodos(box = "inbox") {
  const state = load();
  const items = state.todos.filter((item) => item.box === box);
  return tagged({ items: clone(items), total: items.length });
}

export async function getTodo(id) {
  const state = load();
  const item = state.todos.find((row) => row.id === id);
  if (!item) {
    throw Object.assign(new Error("MOCK todo not found"), { status: 404, code: "P3.TODO.MOCK_NOT_FOUND" });
  }
  const record = state.records.find((row) => row.id === item.record_id) || null;
  return tagged({ ...item, record });
}

function moveTodo(id, status, box, comment) {
  const state = load();
  const item = state.todos.find((row) => row.id === id);
  if (!item) {
    throw Object.assign(new Error("MOCK todo not found"), { status: 404 });
  }
  item.status = status;
  item.box = box;
  item.comment = comment || "";
  item.acted_at = new Date().toISOString();
  const record = state.records.find((row) => row.id === item.record_id);
  if (record && status !== "WITHDRAWN") {
    record.status = status;
  }
  persist(state);
  return tagged({ ...item, record: record || null });
}

export async function approveTodo(id, comment) {
  return moveTodo(id, "APPROVED", "done", comment);
}

export async function rejectTodo(id, comment) {
  return moveTodo(id, "REJECTED", "done", comment);
}

export async function withdrawTodo(id, comment) {
  return moveTodo(id, "WITHDRAWN", "initiated", comment);
}

export async function resetMock() {
  sessionStorage.removeItem(STORAGE_KEY);
  return tagged({ reset: true, seed: seed().ledger.id });
}
