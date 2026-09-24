function pick(row, names) {
  if (!row || typeof row !== "object") {
    return null;
  }
  for (const name of names) {
    const value = row[name];
    if (value != null && value !== "") {
      return value;
    }
  }
  return null;
}

export function show(value) {
  if (value == null || value === "") {
    return "暂无";
  }
  if (typeof value === "boolean") {
    return value ? "是" : "否";
  }
  return String(value);
}

export function asRows(body) {
  return Array.isArray(body) ? body : null;
}

export function clientFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  const rate = row.rate_limit_per_minute ?? row.rateLimitPerMinute;
  return {
    id: pick(row, ["id"]),
    name: pick(row, ["name"]),
    keyPrefix: pick(row, ["key_prefix", "keyPrefix"]),
    scopes: Array.isArray(row.scopes) ? row.scopes.filter((item) => item != null && item !== "") : [],
    rateLimit: Number.isFinite(rate) ? rate : null,
    active: typeof row.active === "boolean" ? row.active : null,
    createdAt: pick(row, ["created_at", "createdAt"]),
  };
}

export function webhookFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  const events = row.event_types ?? row.eventTypes;
  return {
    id: pick(row, ["id"]),
    name: pick(row, ["name"]),
    endpoint: pick(row, ["endpoint"]),
    eventTypes: Array.isArray(events) ? events.filter((item) => item != null && item !== "") : [],
    active: typeof row.active === "boolean" ? row.active : null,
    createdAt: pick(row, ["created_at", "createdAt"]),
  };
}

export function deliveryFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  const attempts = row.attempts;
  return {
    id: pick(row, ["id"]),
    eventId: pick(row, ["event_id", "eventId"]),
    eventType: pick(row, ["event_type", "eventType"]),
    status: pick(row, ["status"]),
    attempts: Number.isFinite(attempts) ? attempts : null,
    nextAttemptAt: pick(row, ["next_attempt_at", "nextAttemptAt"]),
    createdAt: pick(row, ["created_at", "createdAt"]),
    deliveredAt: pick(row, ["delivered_at", "deliveredAt"]),
  };
}

export function callLogFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  return {
    id: pick(row, ["id"]),
    clientId: pick(row, ["client_id", "clientId"]),
    scope: pick(row, ["scope"]),
    apiPath: pick(row, ["api_path", "apiPath"]),
    httpMethod: pick(row, ["http_method", "httpMethod"]),
    status: pick(row, ["status"]),
    traceId: pick(row, ["trace_id", "traceId"]),
    createdAt: pick(row, ["created_at", "createdAt"]),
  };
}

export function connectorFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  return {
    code: pick(row, ["code"]),
    name: pick(row, ["name"]),
    type: pick(row, ["type"]),
    active: typeof row.active === "boolean" ? row.active : null,
  };
}

export function runFields(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) {
    return null;
  }
  return {
    id: pick(row, ["id"]),
    connectorCode: pick(row, ["connector_code", "connectorCode"]),
    status: pick(row, ["status"]),
    error: pick(row, ["error"]),
    startedAt: pick(row, ["started_at", "startedAt"]),
    finishedAt: pick(row, ["finished_at", "finishedAt"]),
  };
}

export function latestRun(runs, code) {
  if (!Array.isArray(runs) || !code) {
    return null;
  }
  const matches = runs.filter((row) => pick(row, ["connector_code", "connectorCode"]) === code);
  if (!matches.length) {
    return null;
  }
  return matches.slice().sort((left, right) => {
    const leftAt = String(pick(left, ["started_at", "startedAt"]) || "");
    const rightAt = String(pick(right, ["started_at", "startedAt"]) || "");
    return rightAt.localeCompare(leftAt);
  })[0];
}

export function methodTone(method) {
  const value = String(method || "").toUpperCase();
  if (value === "GET") return "info";
  if (value === "POST") return "success";
  if (value === "PUT") return "warning";
  if (value === "DELETE") return "danger";
  return "neutral";
}

const DELIVERY_LABELS = {
  PENDING: "待处理",
  DELIVERED: "已送达",
  RETRY: "重试中",
  FAILED: "失败",
};

export function deliveryLabel(status) {
  if (status == null || status === "") {
    return "";
  }
  return DELIVERY_LABELS[String(status).toUpperCase()] || String(status);
}
