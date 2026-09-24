const COL_W = 196;
const ROW_H = 96;
const NODE_W = 148;
const NODE_H = 56;
const PAD = 24;

export function showText(value) {
  if (value == null || value === "") {
    return "暂无";
  }
  return String(value);
}

export function layoutDefinition(definition) {
  const nodes = Array.isArray(definition?.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition?.flows) ? definition.flows : [];
  const ids = nodes.map((node) => node?.id).filter(Boolean);
  const known = new Set(ids);
  const incoming = new Map(ids.map((id) => [id, 0]));
  const outgoing = new Map(ids.map((id) => [id, []]));
  for (const flow of flows) {
    if (!known.has(flow?.source_ref) || !known.has(flow?.target_ref)) {
      continue;
    }
    incoming.set(flow.target_ref, incoming.get(flow.target_ref) + 1);
    outgoing.get(flow.source_ref).push(flow.target_ref);
  }
  const depth = new Map();
  const starts = ids.filter((id) => incoming.get(id) === 0);
  const queue = starts.length ? [...starts] : ids.slice(0, 1);
  for (const id of queue) {
    depth.set(id, 0);
  }
  while (queue.length) {
    const id = queue.shift();
    for (const next of outgoing.get(id) || []) {
      if (depth.has(next)) {
        continue;
      }
      depth.set(next, depth.get(id) + 1);
      queue.push(next);
    }
  }
  let extra = Math.max(-1, ...[...depth.values(), -1]);
  for (const id of ids) {
    if (!depth.has(id)) {
      extra += 1;
      depth.set(id, extra);
    }
  }
  const columns = new Map();
  for (const id of ids) {
    const column = depth.get(id);
    if (!columns.has(column)) {
      columns.set(column, []);
    }
    columns.get(column).push(id);
  }
  const positions = new Map();
  let maxRow = 1;
  for (const columnIds of columns.values()) {
    maxRow = Math.max(maxRow, columnIds.length);
    columnIds.forEach((id, row) => {
      positions.set(id, {
        x: PAD + depth.get(id) * COL_W,
        y: PAD + row * ROW_H,
      });
    });
  }
  const columnCount = Math.max(columns.size, 1);
  return {
    width: PAD * 2 + (columnCount - 1) * COL_W + NODE_W,
    height: PAD * 2 + (maxRow - 1) * ROW_H + NODE_H,
    nodeWidth: NODE_W,
    nodeHeight: NODE_H,
    nodes: nodes.map((node) => ({
      ...node,
      ...(positions.get(node.id) || { x: PAD, y: PAD }),
    })),
    flows: flows.map((flow) => {
      const source = positions.get(flow?.source_ref);
      const target = positions.get(flow?.target_ref);
      if (!source || !target) {
        return { ...flow, x1: null, y1: null, x2: null, y2: null };
      }
      return {
        ...flow,
        x1: source.x + NODE_W,
        y1: source.y + NODE_H / 2,
        x2: target.x,
        y2: target.y + NODE_H / 2,
      };
    }),
  };
}
