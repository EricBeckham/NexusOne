const ENTRY = /^fn:[a-z][a-z0-9_]{0,23}:[a-z][a-z0-9_]{0,23}$/;
const CODE = /^[a-z][a-z0-9_]{0,23}$/;

const PROBLEM_TEXT = {
  duplicate_id: "菜单项标识重复",
  label_required: "菜单名称不能为空",
  invalid_entry_key: "功能入口键须为 fn:应用:表单",
  unknown_parent: "分组不存在",
  invalid_code: "模块编码须为小写字母、数字或下划线，且以字母开头",
  duplicate_code: "模块编码重复",
  title_required: "模块标题不能为空",
};

export function newId() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  return "0199a1c0-0000-7000-8000-00000000c400";
}

export function blankGroup() {
  return { id: newId(), parent_id: null, label: "", entry_key: "", section: "", sort: 0, hidden: false };
}

export function blankEntry(parentId = null) {
  return { id: newId(), parent_id: parentId || null, label: "", entry_key: "", section: "", sort: 0, hidden: false };
}

export function blankModule() {
  return { code: "", title: "", sort: 0, hidden: false };
}

export function problemText(code) {
  return PROBLEM_TEXT[code] || code;
}

export function fromMenuBody(body) {
  const items = Array.isArray(body?.items) ? body.items : [];
  return items.map((item) => ({
    id: item.id,
    parent_id: item.parent_id || null,
    label: item.label || "",
    entry_key: item.entry_key || "",
    section: item.section || "",
    sort: item.sort ?? 0,
    hidden: Boolean(item.hidden),
  }));
}

export function fromModuleBody(body) {
  const modules = Array.isArray(body?.modules) ? body.modules : [];
  return modules.map((module) => ({
    code: module.code || "",
    title: module.title || "",
    sort: module.sort ?? 0,
    hidden: Boolean(module.hidden),
  }));
}

export function menuProblems(items) {
  const rows = Array.isArray(items) ? items : [];
  const ids = new Set();
  const problems = [];
  for (const item of rows) {
    if (!item?.id || ids.has(item.id)) problems.push("duplicate_id");
    else ids.add(item.id);
    if (!String(item?.label || "").trim()) problems.push("label_required");
    const key = String(item?.entry_key || "").trim();
    if (key && !ENTRY.test(key)) problems.push("invalid_entry_key");
  }
  for (const item of rows) {
    if (item?.parent_id && !ids.has(item.parent_id)) problems.push("unknown_parent");
  }
  return [...new Set(problems)];
}

export function moduleProblems(modules) {
  const rows = Array.isArray(modules) ? modules : [];
  const codes = new Set();
  const problems = [];
  for (const module of rows) {
    const code = String(module?.code || "").trim();
    if (!CODE.test(code)) problems.push("invalid_code");
    else if (codes.has(code)) problems.push("duplicate_code");
    else codes.add(code);
    if (!String(module?.title || "").trim()) problems.push("title_required");
  }
  return [...new Set(problems)];
}

export function groupsOf(items) {
  return (items || []).filter((item) => !String(item.entry_key || "").trim());
}

export function orderedMenu(items) {
  const rows = Array.isArray(items) ? items : [];
  const byParent = new Map();
  for (const item of rows) {
    const key = item.parent_id || "";
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key).push(item);
  }
  for (const list of byParent.values()) {
    list.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || String(a.id).localeCompare(String(b.id)));
  }
  const seen = new Set();
  const out = [];
  function walk(parentId, depth) {
    for (const item of byParent.get(parentId || "") || []) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      out.push({ item, depth });
      walk(item.id, depth + 1);
    }
  }
  walk("", 0);
  for (const item of rows) {
    if (!seen.has(item.id)) out.push({ item, depth: 1 });
  }
  return out;
}

export function moveSibling(items, id, direction) {
  const rows = (items || []).map((item) => ({ ...item }));
  const current = rows.find((item) => item.id === id);
  if (!current) return rows;
  const family = rows
    .filter((item) => (item.parent_id || null) === (current.parent_id || null))
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || String(a.id).localeCompare(String(b.id)));
  const index = family.findIndex((item) => item.id === id);
  const next = index + direction;
  if (index < 0 || next < 0 || next >= family.length) return rows;
  const reordered = family.slice();
  const [row] = reordered.splice(index, 1);
  reordered.splice(next, 0, row);
  reordered.forEach((item, order) => {
    rows.find((candidate) => candidate.id === item.id).sort = order;
  });
  return rows;
}

export function moveByIndex(rows, index, direction) {
  const next = index + direction;
  if (!Array.isArray(rows) || index < 0 || next < 0 || next >= rows.length) {
    return (rows || []).map((row) => ({ ...row }));
  }
  const copy = rows.map((row) => ({ ...row }));
  const [row] = copy.splice(index, 1);
  copy.splice(next, 0, row);
  return copy.map((item, order) => ({ ...item, sort: order }));
}

export function menuPayload(items) {
  return {
    items: (items || []).map((item) => ({
      id: item.id,
      parent_id: item.parent_id || null,
      label: String(item.label || "").trim(),
      entry_key: String(item.entry_key || "").trim() || null,
      section: String(item.section || "").trim() || null,
      sort: Number(item.sort) || 0,
      hidden: Boolean(item.hidden),
    })),
  };
}

export function modulePayload(modules) {
  return {
    modules: (modules || []).map((module, index) => ({
      code: String(module.code || "").trim(),
      title: String(module.title || "").trim(),
      sort: Number.isFinite(Number(module.sort)) ? Number(module.sort) : index,
      hidden: Boolean(module.hidden),
    })),
  };
}
