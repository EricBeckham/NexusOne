const ENTRY = /^fn:[a-z][a-z0-9_]{0,23}:[a-z][a-z0-9_]{0,23}$/;

/**
 * Member portal contents. Menu rows the member cannot read are dropped.
 * Instance entries come only from {@code instances}; an empty query adds nothing.
 * A heading stays only when a visible child still points at it.
 */
export function visiblePortal({ menu, modules, readable, instances }) {
  const allowed = new Set(Array.isArray(readable) ? readable : []);
  const rows = (Array.isArray(menu) ? menu : []).filter((item) => item && !item.hidden);
  const menuItems = rows.filter((item) => !item.entry_key || allowed.has(item.entry_key));
  const menuKeys = new Set(menuItems.map((item) => item.entry_key).filter(Boolean));
  const extras = (Array.isArray(instances) ? instances : [])
    .filter((row) => row && row.status !== "UNINSTALLED")
    .flatMap((row) => (Array.isArray(row.entries) ? row.entries : []))
    .filter((key) => ENTRY.test(key) && allowed.has(key) && !menuKeys.has(key))
    .map((key) => ({ id: key, label: key, entry_key: key, sort: 1000 }));
  const kept = [...menuItems, ...extras];
  const parents = new Set(kept.map((item) => item.parent_id).filter(Boolean));
  const entries = kept
    .filter((item) => item.entry_key || parents.has(item.id))
    .slice()
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || String(a.id).localeCompare(String(b.id)));
  const home = (Array.isArray(modules) ? modules : [])
    .filter((item) => item && !item.hidden)
    .slice()
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || String(a.code).localeCompare(String(b.code)));
  return { entries, modules: home };
}
