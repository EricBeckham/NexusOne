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
    const type = ["startEvent", "userTask", "endEvent", "exclusiveGateway"].includes(node.type)
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

export function addDraftNode(definition, type) {
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
  } else {
    throw new Error("仅支持开始、用户任务和结束节点");
  }
  return next;
}
