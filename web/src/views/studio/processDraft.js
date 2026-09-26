const BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
const FLOWABLE_NS = "http://flowable.org/bpmn";

const escapeXml = (value) => String(value ?? "")
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&apos;");

export function parseDraftXml(xml) {
  const document = new DOMParser().parseFromString(String(xml || ""), "application/xml");
  if (document.querySelector("parsererror")) {
    throw new Error("草稿 BPMN XML 无法解析");
  }
  const process = document.getElementsByTagNameNS(BPMN_NS, "process")[0]
    || document.querySelector("process");
  if (!process) {
    throw new Error("草稿中缺少流程定义");
  }
  const nodes = [];
  const flows = [];
  for (const child of Array.from(process.children)) {
    const type = child.localName || child.tagName.split(":").at(-1);
    if (type === "sequenceFlow") {
      const condition = child.getElementsByTagNameNS(BPMN_NS, "conditionExpression")[0]
        || child.querySelector("conditionExpression");
      flows.push({
        id: child.getAttribute("id"),
        source_ref: child.getAttribute("sourceRef"),
        target_ref: child.getAttribute("targetRef"),
        condition: condition?.textContent?.trim() || null,
      });
      continue;
    }
    const id = child.getAttribute("id");
    if (id) {
      nodes.push({
        id,
        name: child.getAttribute("name") || "",
        type,
        assignee: child.getAttributeNS(FLOWABLE_NS, "assignee") || child.getAttribute("flowable:assignee") || "",
      });
    }
  }
  return { key: process.getAttribute("id"), name: process.getAttribute("name") || "", nodes, flows };
}

export function serializeDraftXml(definition) {
  const nodes = Array.isArray(definition?.nodes) ? definition.nodes : [];
  const flows = Array.isArray(definition?.flows) ? definition.flows : [];
  const nodeXml = nodes.map((node) => {
    const type = ["startEvent", "userTask", "endEvent", "exclusiveGateway", "parallelGateway"].includes(node.type)
      ? node.type
      : "userTask";
    const assignee = type === "userTask" && node.assignee
      ? ` flowable:assignee="${escapeXml(node.assignee)}"`
      : "";
    return `    <${type} id="${escapeXml(node.id)}" name="${escapeXml(node.name)}"${assignee}/>`;
  });
  const flowXml = flows.map((flow) => {
    const condition = flow.condition
      ? `\n      <conditionExpression xsi:type="bpmn:tFormalExpression">${escapeXml(flow.condition)}</conditionExpression>`
      : "";
    return `    <sequenceFlow id="${escapeXml(flow.id)}" sourceRef="${escapeXml(flow.source_ref)}" targetRef="${escapeXml(flow.target_ref)}">${condition}\n    </sequenceFlow>`;
  });
  return `<?xml version="1.0" encoding="UTF-8"?>\n<definitions xmlns="${BPMN_NS}" xmlns:bpmn="${BPMN_NS}" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:flowable="${FLOWABLE_NS}" targetNamespace="http://nexus.one/process-drafts">\n  <process id="${escapeXml(definition?.key)}" name="${escapeXml(definition?.name)}" isExecutable="true">\n${[...nodeXml, ...flowXml].join("\n")}\n  </process>\n</definitions>`;
}

export function createBlankDraft(key, name = "新流程草稿") {
  return {
    key,
    name,
    source: "draft",
    nodes: [
      { id: "start", name: "开始", type: "startEvent" },
      { id: "approveTask", name: "审批", type: "userTask", assignee: "" },
      { id: "end", name: "结束", type: "endEvent" },
    ],
    flows: [
      { id: "flow_start_task", source_ref: "start", target_ref: "approveTask", condition: null },
      { id: "flow_task_end", source_ref: "approveTask", target_ref: "end", condition: null },
    ],
  };
}

export function addDraftNode(definition, type, options = {}) {
  const next = {
    ...definition,
    nodes: (definition?.nodes || []).map((node) => ({ ...node })),
    flows: (definition?.flows || []).map((flow) => ({ ...flow })),
  };
  const existing = next.nodes || [];
  const ids = new Set(existing.map((node) => node.id));
  const base = type === "startEvent" ? "start" : type === "endEvent" ? "end" : "userTask";
  let suffix = existing.filter((node) => node.id === base || node.id.startsWith(`${base}_`)).length;
  let id = suffix ? `${base}_${suffix + 1}` : base;
  while (ids.has(id)) id = `${base}_${++suffix}`;
  const node = {
    id,
    name: type === "startEvent" ? "开始" : type === "endEvent" ? "结束" : "新用户任务",
    type,
    ...(type === "userTask" ? { assignee: "" } : {}),
  };

  if (type === "startEvent") {
    const first = existing.find((item) => item.type !== "startEvent");
    next.nodes.unshift(node);
    if (first) next.flows.push({ id: `flow_${id}_${first.id}`, source_ref: id, target_ref: first.id, condition: null });
  } else if (type === "userTask") {
    const ends = existing.filter((item) => item.type === "endEvent");
    if (ends.length) {
      const endIds = new Set(ends.map((item) => item.id));
      const inbound = next.flows.filter((flow) => endIds.has(flow.target_ref));
      const predecessors = [...new Set(inbound.map((flow) => flow.source_ref))];
      next.flows = next.flows.filter((flow) => !endIds.has(flow.target_ref));
      next.nodes.splice(Math.max(0, next.nodes.length - ends.length), 0, node);
      const predecessor = predecessors.at(-1) || existing.find((item) => item.type !== "endEvent")?.id;
      if (predecessor) next.flows.push({ id: `flow_${predecessor}_${id}`, source_ref: predecessor, target_ref: id, condition: null });
      for (const flow of inbound) next.flows.push({ ...flow, id: `${flow.id}_after_${id}`, source_ref: id });
    } else {
      const predecessor = existing.at(-1);
      next.nodes.push(node);
      if (predecessor) next.flows.push({ id: `flow_${predecessor.id}_${id}`, source_ref: predecessor.id, target_ref: id, condition: null });
    }
  } else if (type === "endEvent") {
    const predecessor = [...existing].reverse().find((item) => item.type !== "endEvent");
    next.nodes.push(node);
    if (predecessor) next.flows.push({ id: `flow_${predecessor.id}_${id}`, source_ref: predecessor.id, target_ref: id, condition: null });
  } else if (type === "exclusiveGateway") {
    return insertExclusiveGateway(next, options.after);
  } else if (type === "parallelGateway") {
    return insertParallelBlock(next, options.after);
  } else {
    throw new Error("仅支持开始、用户任务、结束、条件分支和并行分支节点");
  }
  return next;
}

function uniqueNodeId(nodes, base) {
  const taken = new Set(nodes.map((node) => node.id));
  if (!taken.has(base)) return base;
  let n = 2;
  while (taken.has(`${base}_${n}`)) n += 1;
  return `${base}_${n}`;
}

/**
 * Z6: insert an exclusive gateway right after `afterId` (a start or task with a single outgoing flow).
 * The existing path stays as branch 1; branch 2 gets a new approval task and rejoins at the same successor.
 * Conditions start empty — they are edited on the branch flows (Z7).
 */
function insertExclusiveGateway(next, afterId) {
  const after = next.nodes.find((node) => node.id === afterId);
  if (!after || !["startEvent", "userTask"].includes(after.type)) {
    throw new Error("请先选择开始或审批节点，再在其后添加条件分支");
  }
  const outgoing = next.flows.filter((flow) => flow.source_ref === after.id);
  if (outgoing.length !== 1) {
    throw new Error("该节点后已经有分支或没有后续节点，无法再添加条件分支");
  }
  const successor = outgoing[0].target_ref;
  const gateway = { id: uniqueNodeId(next.nodes, "gateway"), name: "条件分支", type: "exclusiveGateway" };
  const branchTask = { id: uniqueNodeId([...next.nodes, gateway], "branch_task"), name: "分支审批", type: "userTask", assignee: "" };
  next.flows = next.flows.filter((flow) => flow !== outgoing[0]);
  next.flows.push(
    { id: `flow_${after.id}_${gateway.id}`, source_ref: after.id, target_ref: gateway.id, condition: null },
    { id: `flow_${gateway.id}_${successor}`, source_ref: gateway.id, target_ref: successor, condition: null },
    { id: `flow_${gateway.id}_${branchTask.id}`, source_ref: gateway.id, target_ref: branchTask.id, condition: null },
    { id: `flow_${branchTask.id}_${successor}`, source_ref: branchTask.id, target_ref: successor, condition: null },
  );
  const at = next.nodes.findIndex((node) => node.id === after.id);
  next.nodes.splice(at + 1, 0, gateway, branchTask);
  return next;
}

/**
 * Z6: remove an exclusive gateway. Its first outgoing branch is kept (predecessors connect straight to it);
 * nodes reachable only through the other branches are removed with it. Nodes on the kept path are never removed.
 */
export function removeExclusiveGateway(definition, gatewayId) {
  const nodes = (definition?.nodes || []).map((node) => ({ ...node }));
  const flows = (definition?.flows || []).map((flow) => ({ ...flow }));
  const gateway = nodes.find((node) => node.id === gatewayId);
  if (!gateway || gateway.type !== "exclusiveGateway") {
    throw new Error("只能删除条件分支节点");
  }
  const outgoing = flows.filter((flow) => flow.source_ref === gatewayId);
  const inbound = flows.filter((flow) => flow.target_ref === gatewayId);
  const keep = outgoing[0]?.target_ref;

  const kept = new Set();
  const walk = keep ? [keep] : [];
  while (walk.length) {
    const id = walk.pop();
    if (kept.has(id) || id === gatewayId) continue;
    kept.add(id);
    for (const flow of flows) if (flow.source_ref === id) walk.push(flow.target_ref);
  }

  const removed = new Set([gatewayId]);
  const queue = outgoing.slice(1).map((flow) => flow.target_ref);
  while (queue.length) {
    const id = queue.shift();
    if (removed.has(id) || kept.has(id)) continue;
    const node = nodes.find((item) => item.id === id);
    if (!node || node.type === "startEvent" || node.type === "endEvent") continue;
    const sources = flows.filter((flow) => flow.target_ref === id).map((flow) => flow.source_ref);
    if (!sources.every((source) => removed.has(source))) continue;
    removed.add(id);
    for (const flow of flows) if (flow.source_ref === id) queue.push(flow.target_ref);
  }

  const nextFlows = flows.filter((flow) => !removed.has(flow.source_ref) && !removed.has(flow.target_ref));
  if (keep) {
    for (const flow of inbound) nextFlows.push({ ...flow, target_ref: keep, condition: null });
  }
  return { ...definition, nodes: nodes.filter((node) => !removed.has(node.id)), flows: nextFlows };
}

/** Z9: a parallel gateway is a fork when it has several outgoing flows, a join when it has several incoming. */
export function parallelRole(definition, id) {
  const flows = definition?.flows || [];
  const outgoing = flows.filter((flow) => flow.source_ref === id).length;
  const incoming = flows.filter((flow) => flow.target_ref === id).length;
  if (outgoing > 1) return "fork";
  if (incoming > 1) return "join";
  return "";
}

/**
 * Z9: insert a parallel block after `afterId` (a start or task with one outgoing flow):
 * after → fork → { 并行审批 1, 并行审批 2 } → join → the original successor.
 * Pairing convention: ids fork/join, fork_2/join_2, …; roles are read from structure (parallelRole).
 */
function insertParallelBlock(next, afterId) {
  const after = next.nodes.find((node) => node.id === afterId);
  if (!after || !["startEvent", "userTask"].includes(after.type)) {
    throw new Error("请先选择开始或审批节点，再在其后添加并行分支");
  }
  const outgoing = next.flows.filter((flow) => flow.source_ref === after.id);
  if (outgoing.length !== 1) {
    throw new Error("该节点后已经有分支或没有后续节点，无法再添加并行分支");
  }
  const successor = outgoing[0].target_ref;
  const fork = { id: uniqueNodeId(next.nodes, "fork"), name: "并行开始", type: "parallelGateway" };
  const suffix = fork.id.slice("fork".length);
  const join = { id: uniqueNodeId([...next.nodes, fork], `join${suffix}`), name: "并行汇合", type: "parallelGateway" };
  const taken = [...next.nodes, fork, join];
  const first = { id: uniqueNodeId(taken, "parallel_task"), name: "并行审批 1", type: "userTask", assignee: "" };
  const second = { id: uniqueNodeId([...taken, first], "parallel_task"), name: "并行审批 2", type: "userTask", assignee: "" };
  next.flows = next.flows.filter((flow) => flow !== outgoing[0]);
  next.flows.push(
    { id: `flow_${after.id}_${fork.id}`, source_ref: after.id, target_ref: fork.id, condition: null },
    { id: `flow_${fork.id}_${first.id}`, source_ref: fork.id, target_ref: first.id, condition: null },
    { id: `flow_${fork.id}_${second.id}`, source_ref: fork.id, target_ref: second.id, condition: null },
    { id: `flow_${first.id}_${join.id}`, source_ref: first.id, target_ref: join.id, condition: null },
    { id: `flow_${second.id}_${join.id}`, source_ref: second.id, target_ref: join.id, condition: null },
    { id: `flow_${join.id}_${successor}`, source_ref: join.id, target_ref: successor, condition: null },
  );
  const at = next.nodes.findIndex((node) => node.id === after.id);
  next.nodes.splice(at + 1, 0, fork, first, second, join);
  return next;
}

/**
 * Z9: remove a whole parallel block given its fork or join. Every branch from the fork must reach the
 * same join; the nodes in between are removed and the fork's predecessors connect to the join's successor.
 */
export function removeParallelBlock(definition, gatewayId) {
  const nodes = (definition?.nodes || []).map((node) => ({ ...node }));
  const flows = (definition?.flows || []).map((flow) => ({ ...flow }));
  const gateway = nodes.find((node) => node.id === gatewayId);
  if (!gateway || gateway.type !== "parallelGateway") {
    throw new Error("只能删除并行分支节点");
  }
  const byId = new Map(nodes.map((node) => [node.id, node]));
  const out = (id) => flows.filter((flow) => flow.source_ref === id).map((flow) => flow.target_ref);
  const into = (id) => flows.filter((flow) => flow.target_ref === id).map((flow) => flow.source_ref);
  let fork = gateway;
  if (parallelRole({ flows }, gateway.id) === "join") {
    // Walk one branch back to its fork.
    let cursor = into(gateway.id)[0];
    while (cursor && !(byId.get(cursor)?.type === "parallelGateway" && out(cursor).length > 1)) cursor = into(cursor)[0];
    fork = byId.get(cursor);
  }
  if (!fork || out(fork.id).length < 2) {
    throw new Error("无法识别配对的并行开始与汇合节点");
  }
  const inner = new Set();
  let join = null;
  for (const start of out(fork.id)) {
    let cursor = start;
    const seen = new Set();
    while (cursor && !seen.has(cursor)) {
      seen.add(cursor);
      const node = byId.get(cursor);
      if (node?.type === "parallelGateway" && into(cursor).length > 1) break;
      inner.add(cursor);
      cursor = out(cursor)[0];
    }
    if (!cursor || (join && join !== cursor)) throw new Error("无法识别配对的并行开始与汇合节点");
    join = cursor;
  }
  const successor = out(join)[0];
  const removed = new Set([fork.id, join, ...inner]);
  const inbound = flows.filter((flow) => flow.target_ref === fork.id);
  const nextFlows = flows.filter((flow) => !removed.has(flow.source_ref) && !removed.has(flow.target_ref));
  if (successor) {
    for (const flow of inbound) nextFlows.push({ ...flow, target_ref: successor, condition: null });
  }
  return { ...definition, nodes: nodes.filter((node) => !removed.has(node.id)), flows: nextFlows };
}