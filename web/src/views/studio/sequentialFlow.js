/**
 * Sequential (one-after-another) approval model over the BPMN draft shape
 * `{ nodes, flows }`: start → userTask… → end, no gateways, branches or conditions.
 * The step editor only mutates drafts that satisfy this; anything else stays view-only.
 */
const SEQUENTIAL_TYPES = new Set(["startEvent", "userTask", "endEvent"]);

export function toSequence(definition) {
  const nodes = Array.isArray(definition?.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition?.flows) ? definition.flows : [];
  if (nodes.some((node) => !SEQUENTIAL_TYPES.has(node.type))) {
    return { sequential: false, reason: "包含条件分支或并行分支" };
  }
  if (flows.some((flow) => flow.condition)) {
    return { sequential: false, reason: "包含带条件的连线" };
  }
  const starts = nodes.filter((node) => node.type === "startEvent");
  const ends = nodes.filter((node) => node.type === "endEvent");
  if (starts.length > 1 || ends.length > 1) {
    return { sequential: false, reason: "包含多个开始或结束节点" };
  }
  const outgoing = new Map();
  const incoming = new Map();
  for (const flow of flows) {
    outgoing.set(flow.source_ref, [...(outgoing.get(flow.source_ref) || []), flow.target_ref]);
    incoming.set(flow.target_ref, (incoming.get(flow.target_ref) || 0) + 1);
  }
  if ([...outgoing.values()].some((targets) => targets.length > 1) || [...incoming.values()].some((count) => count > 1)) {
    return { sequential: false, reason: "包含分支或并行" };
  }

  const byId = new Map(nodes.map((node) => [node.id, node]));
  const steps = [];
  const seen = new Set();
  let current = starts[0]?.id;
  while (current) {
    const next = outgoing.get(current)?.[0];
    if (!next || seen.has(next)) break;
    seen.add(next);
    const node = byId.get(next);
    if (node?.type === "userTask") steps.push(node);
    current = next;
  }
  for (const node of nodes) {
    if (node.type === "userTask" && !seen.has(node.id)) steps.push(node);
  }
  return { sequential: true, start: starts[0] || null, steps, end: ends[0] || null };
}

/** Rebuilds nodes and flows as start → steps → end, keeping the existing start/end nodes. */
export function fromSequence(definition, steps) {
  const current = toSequence(definition);
  const taken = new Set(steps.map((step) => step.id));
  const start = current.start || { id: uniqueId("start", taken), name: "开始", type: "startEvent" };
  taken.add(start.id);
  const end = current.end || { id: uniqueId("end", taken), name: "结束", type: "endEvent" };
  const nodes = [start, ...steps, end];
  const flows = nodes.slice(1).map((node, index) => ({
    id: `flow_${nodes[index].id}_${node.id}`,
    source_ref: nodes[index].id,
    target_ref: node.id,
    condition: null,
  }));
  return { ...definition, nodes, flows };
}

export function newStep(definition) {
  const taken = new Set((definition?.nodes || []).map((node) => node.id));
  const count = (definition?.nodes || []).filter((node) => node.type === "userTask").length;
  return { id: uniqueId("approve", taken, count + 1), name: `审批节点 ${count + 1}`, type: "userTask", assignee: "" };
}

function uniqueId(base, taken, from = 1) {
  if (!taken.has(base) && from === 1) return base;
  let n = from;
  while (taken.has(`${base}_${n}`)) n += 1;
  return `${base}_${n}`;
}
