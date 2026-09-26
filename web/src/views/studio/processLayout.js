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

/**
 * Z8: columns by longest path (every node sits right of all its predecessors, so a merge point comes
 * after its branches); rows keep a gateway's first branch on the gateway's row and fan the others out
 * below; a merge node takes its highest predecessor's row. Nodes in cycles fall back to BFS depth.
 * Flows carry an orthogonal elbow `path` plus the straight x1/y1/x2/y2 endpoints.
 */
export function layoutDefinition(definition) {
  const nodes = Array.isArray(definition?.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition?.flows) ? definition.flows : [];
  const ids = nodes.map((node) => node?.id).filter(Boolean);
  const known = new Set(ids);
  const incoming = new Map(ids.map((id) => [id, []]));
  const outgoing = new Map(ids.map((id) => [id, []]));
  for (const flow of flows) {
    if (!known.has(flow?.source_ref) || !known.has(flow?.target_ref) || flow.source_ref === flow.target_ref) {
      continue;
    }
    incoming.get(flow.target_ref).push(flow.source_ref);
    outgoing.get(flow.source_ref).push(flow.target_ref);
  }

  // Longest-path layering (Kahn). Whatever is left belongs to a cycle and is placed by BFS after.
  const depth = new Map();
  const pending = new Map(ids.map((id) => [id, incoming.get(id).length]));
  const order = [];
  const ready = ids.filter((id) => pending.get(id) === 0);
  for (const id of ready) depth.set(id, 0);
  while (ready.length) {
    const id = ready.shift();
    order.push(id);
    for (const next of outgoing.get(id)) {
      depth.set(next, Math.max(depth.get(next) ?? 0, depth.get(id) + 1));
      pending.set(next, pending.get(next) - 1);
      if (pending.get(next) === 0) ready.push(next);
    }
  }
  if (order.length < ids.length) {
    const placed = new Set(order);
    const start = order.length ? [...order] : [ids[0]];
    if (!order.length) depth.set(ids[0], 0);
    const seen = new Set(start);
    const queue = [...start];
    while (queue.length) {
      const id = queue.shift();
      for (const next of outgoing.get(id)) {
        if (placed.has(next) || seen.has(next)) continue;
        depth.set(next, depth.get(id) + 1);
        seen.add(next);
        queue.push(next);
      }
    }
    let extra = Math.max(-1, ...depth.values());
    for (const id of ids) if (!depth.has(id)) depth.set(id, ++extra);
    for (const id of ids) if (!placed.has(id)) order.push(id);
  }
  // Rows: walk in layering order; preferred row from predecessors / gateway fan-out, bump on collision.
  const row = new Map();
  const used = new Map();
  const claim = (column, preferred) => {
    const taken = used.get(column) || new Set();
    let candidate = Math.max(0, preferred);
    while (taken.has(candidate)) candidate += 1;
    taken.add(candidate);
    used.set(column, taken);
    return candidate;
  };
  const byColumn = [...order].sort((a, b) => depth.get(a) - depth.get(b));
  for (const id of byColumn) {
    const preds = incoming.get(id).filter((pred) => row.has(pred));
    let preferred = 0;
    if (preds.length === 1) {
      const pred = preds[0];
      const siblings = outgoing.get(pred);
      preferred = row.get(pred) + Math.max(0, siblings.indexOf(id));
    } else if (preds.length > 1) {
      preferred = Math.min(...preds.map((pred) => row.get(pred)));
    }
    row.set(id, claim(depth.get(id), preferred));
  }

  const positions = new Map(ids.map((id) => [id, { x: PAD + depth.get(id) * COL_W, y: PAD + row.get(id) * ROW_H }]));
  const columnCount = Math.max(1, ...[...depth.values()].map((value) => value + 1));
  const rowCount = Math.max(1, ...[...row.values()].map((value) => value + 1));
  return {
    width: PAD * 2 + (columnCount - 1) * COL_W + NODE_W,
    height: PAD * 2 + (rowCount - 1) * ROW_H + NODE_H,
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
        return { ...flow, x1: null, y1: null, x2: null, y2: null, path: "" };
      }
      const x1 = source.x + NODE_W;
      const y1 = source.y + NODE_H / 2;
      const x2 = target.x;
      const y2 = target.y + NODE_H / 2;
      // Elbow: leave the source horizontally, turn halfway through the gap before the target, enter horizontally.
      const turn = x2 > x1 ? x2 - (COL_W - NODE_W) / 2 : x1 + (COL_W - NODE_W) / 2;
      const path = y1 === y2 ? `M${x1} ${y1} H${x2}` : `M${x1} ${y1} H${turn} V${y2} H${x2}`;
      return { ...flow, x1, y1, x2, y2, path, labelX: y1 === y2 ? (x1 + x2) / 2 : turn, labelY: y1 === y2 ? y1 : (y1 + y2) / 2 };
    }),
  };
}