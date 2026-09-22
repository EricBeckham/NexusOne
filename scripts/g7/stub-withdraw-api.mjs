#!/usr/bin/env node
/**
 * Stub login + initiated-todo + path withdraw for the G7 leftover browser walk.
 * Issued context only (X-Nexus-Context-Id). Never accepts X-Tenant-Id.
 * Not a product JAR and not a G7 three-type close.
 */
import { createServer } from "node:http";
import { URL } from "node:url";

const HOST = process.env.NEXUS_G7_STUB_HOST || "127.0.0.1";
const PORT = Number(process.env.NEXUS_G7_STUB_PORT || 18087);
const CONTEXT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e981";
const TENANT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e901";
const ACCOUNT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e902";
const PERSON_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e903";

const FORBIDDEN = ["x-tenant-id", "x-nexus-demo-tenant-id"];

const types = {
  leave: {
    instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7371",
    todoId: "01a0c98c-0407-726d-a725-34a3c9cd3801",
    processKey: "leave",
    taskName: "请假审批",
    businessKey: "LEAVE-G7-UI-001",
    title: "年假",
  },
  expense: {
    instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7372",
    todoId: "01a0c98c-0407-726d-a725-34a3c9cd3802",
    processKey: "expense",
    taskName: "报销审批",
    businessKey: "EXPENSE-G7-UI-001",
    title: "交通费",
  },
  travel: {
    instanceId: "01a0c98c-03ce-70dc-8db1-31ac651f7373",
    todoId: "01a0c98c-0407-726d-a725-34a3c9cd3803",
    processKey: "travel",
    taskName: "出差审批",
    businessKey: "TRAVEL-G7-UI-001",
    title: "客户拜访",
  },
};

const state = {
  startedAt: new Date().toISOString(),
  logins: 0,
  withdraws: [],
  rejectedTenantHeaders: 0,
  items: Object.fromEntries(
    Object.entries(types).map(([code, spec]) => [spec.instanceId, { ...spec, status: "RUNNING", withdrawn: false }]),
  ),
};

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Nexus-Context-Id, X-Trace-Id, X-Nexus-Demo-Actor-Id, X-Nexus-Demo-Reason, X-Nexus-Bootstrap-Token",
    "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS",
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

function headerMap(req) {
  const out = {};
  for (const [key, value] of Object.entries(req.headers)) {
    out[key.toLowerCase()] = Array.isArray(value) ? value.join(",") : value;
  }
  return out;
}

function sessionView() {
  return {
    account_id: ACCOUNT_ID,
    accountId: ACCOUNT_ID,
    email: "g7-ui@example.com",
    display_name: "G7 UI Withdraw",
    displayName: "G7 UI Withdraw",
    context_id: CONTEXT_ID,
    contextId: CONTEXT_ID,
    tenant_id: TENANT_ID,
    tenantId: TENANT_ID,
    person_id: PERSON_ID,
    personId: PERSON_ID,
    memberships: [{ tenant_id: TENANT_ID, tenantId: TENANT_ID, person_id: PERSON_ID, status: "ACTIVE" }],
  };
}

function instanceRow(item) {
  return {
    id: item.instanceId,
    instance_id: item.instanceId,
    instanceId: item.instanceId,
    tenant_id: TENANT_ID,
    process_key: item.processKey,
    processKey: item.processKey,
    business_key: item.businessKey,
    businessKey: item.businessKey,
    initiator_person_id: PERSON_ID,
    status: item.status,
    outcome: item.withdrawn ? "WITHDRAWN" : null,
    started_at: state.startedAt,
    startedAt: state.startedAt,
    task_name: item.taskName,
    taskName: item.taskName,
  };
}

function todoRow(item) {
  return {
    id: item.todoId,
    tenant_id: TENANT_ID,
    instance_id: item.instanceId,
    instanceId: item.instanceId,
    task_name: item.taskName,
    taskName: item.taskName,
    business_key: item.businessKey,
    businessKey: item.businessKey,
    status: item.withdrawn ? "DONE" : "PENDING",
    outcome: item.withdrawn ? "WITHDRAWN" : null,
    instance_status: item.status,
    created_at: state.startedAt,
    createdAt: state.startedAt,
  };
}

function withdrawBody(item, traceId) {
  return {
    ledger_id: "01a0c98b-1b4b-7c83-b528-2f07e3305bc0",
    record_id: item.businessKey,
    physical_table: `l_g7ui_${item.processKey}`,
    approval_status: "WITHDRAWN",
    approvalStatus: "WITHDRAWN",
    process: {
      id: item.instanceId,
      business_key: item.businessKey,
      status: "WITHDRAWN",
    },
    todo: todoRow(item),
    trace_id: traceId,
  };
}

function findByAnyId(id) {
  return Object.values(state.items).find(
    (item) => item.instanceId === id || item.todoId === id || item.businessKey === id,
  );
}

const server = createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    json(res, 204, {});
    return;
  }
  const url = new URL(req.url || "/", `http://${HOST}:${PORT}`);
  const headers = headerMap(req);
  if (FORBIDDEN.some((name) => headers[name])) {
    state.rejectedTenantHeaders += 1;
    json(res, 400, { code: "P2.IAM.CLIENT_TENANT_REJECTED", message: "client must not send tenant identity headers" });
    return;
  }

  try {
    if (req.method === "GET" && url.pathname === "/__g7/state") {
      json(res, 200, {
        context_id: CONTEXT_ID,
        logins: state.logins,
        withdraws: state.withdraws,
        rejected_tenant_headers: state.rejectedTenantHeaders,
        items: Object.values(state.items).map((item) => ({
          type: item.processKey,
          instance_id: item.instanceId,
          status: item.status,
          withdrawn: item.withdrawn,
        })),
      });
      return;
    }
    if (req.method === "GET" && (url.pathname === "/actuator/health" || url.pathname === "/api/v1/health")) {
      json(res, 200, { status: "UP", stub: "g7-withdraw-browser" });
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/v1/auth/login") {
      await readBody(req);
      state.logins += 1;
      json(res, 200, sessionView());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/auth/session") {
      json(res, 200, sessionView());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/todos/initiated") {
      json(res, 200, { items: Object.values(state.items).map(instanceRow) });
      return;
    }
    if (req.method === "GET" && (url.pathname === "/api/v1/todos/done" || url.pathname === "/api/v1/p3/todos")) {
      json(res, 200, { items: [] });
      return;
    }
    const todoMatch = url.pathname.match(/^\/api\/v1\/todos\/([^/]+)$/);
    if (req.method === "GET" && todoMatch) {
      const item = findByAnyId(todoMatch[1]);
      if (!item) {
        json(res, 404, { code: "P3.FLOW.0002", message: "todo not found" });
        return;
      }
      json(res, 200, todoRow(item));
      return;
    }
    const withdrawMatch = url.pathname.match(/^\/api\/v1\/p3\/path\/instances\/([^/]+)\/withdraw$/);
    if (req.method === "POST" && withdrawMatch) {
      const item = findByAnyId(withdrawMatch[1]);
      if (!item) {
        json(res, 404, { code: "P3.FLOW.0002", message: "instance not found" });
        return;
      }
      const already = item.withdrawn;
      item.status = "WITHDRAWN";
      item.withdrawn = true;
      const traceId = headers["x-trace-id"] || "";
      state.withdraws.push({
        at: new Date().toISOString(),
        type: item.processKey,
        instance_id: item.instanceId,
        authorization: Boolean(headers.authorization),
        context_id: headers["x-nexus-context-id"] || "",
        trace_id: traceId,
        x_tenant_id: false,
        idempotent_replay: already,
      });
      json(res, 200, withdrawBody(item, traceId));
      return;
    }
    json(res, 404, { code: "STUB.NOT_FOUND", message: url.pathname });
  } catch (err) {
    json(res, 500, { code: "STUB.ERROR", message: err.message });
  }
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`g7 stub login API http://${HOST}:${PORT}\n`);
});
