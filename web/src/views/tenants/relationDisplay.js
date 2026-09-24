export const LAZY_TREE_LIMIT = 1000;

export function relationNode(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  const id = row.id == null || row.id === "" ? "" : String(row.id);
  if (!id) {
    return null;
  }
  const parent = row.parent_tenant_id ?? row.parentTenantId;
  const children = Array.isArray(row.children) ? row.children.map(relationNode).filter(Boolean) : [];
  return {
    id,
    code: row.code == null ? "" : String(row.code),
    name: row.name == null ? "" : String(row.name),
    status: row.status == null ? "" : String(row.status),
    parentTenantId: parent == null || parent === "" ? "" : String(parent),
    children,
  };
}

export function treeRoots(body) {
  if (!body || typeof body !== "object" || !Array.isArray(body.nodes)) {
    return null;
  }
  return body.nodes.map(relationNode).filter(Boolean);
}

export function relationList(body) {
  if (!Array.isArray(body)) {
    return null;
  }
  return body.map((row) => {
    const node = relationNode({ ...row, children: [] });
    return node;
  }).filter(Boolean);
}

export function filterTree(nodes, query) {
  const needle = String(query || "").trim().toLowerCase();
  if (!needle) {
    return nodes || [];
  }
  const matched = [];
  for (const node of nodes || []) {
    const children = filterTree(node.children, needle);
    const hit = node.name.toLowerCase().includes(needle) || node.code.toLowerCase().includes(needle);
    if (hit || children.length) {
      matched.push({ ...node, children: hit ? node.children : children });
    }
  }
  return matched;
}

export function findNode(nodes, id) {
  for (const node of nodes || []) {
    if (node.id === id) {
      return node;
    }
    const found = findNode(node.children, id);
    if (found) {
      return found;
    }
  }
  return null;
}

export function nodeCount(nodes) {
  return (nodes || []).reduce((sum, node) => sum + 1 + nodeCount(node.children), 0);
}

export function visibleRows(nodes, { lazy = false, expanded = new Set(), depth = 0 } = {}) {
  const rows = [];
  for (const node of nodes || []) {
    const open = !lazy || expanded.has(node.id);
    rows.push({
      id: node.id,
      code: node.code,
      name: node.name,
      status: node.status,
      depth,
      hasChildren: node.children.length > 0,
      open,
    });
    if (open) {
      rows.push(...visibleRows(node.children, { lazy, expanded, depth: depth + 1 }));
    }
  }
  return rows;
}
