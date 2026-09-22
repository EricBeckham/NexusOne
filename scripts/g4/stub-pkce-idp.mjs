#!/usr/bin/env node
/**
 * Stub Authorization Code + PKCE IdP + AuditPort-shaped hash chain
 * for the G4 leftover desktop click walk.
 *
 * Same event types as AuditCaptures. Same CanonicalAudit + SHA-256 linking
 * as InMemoryHashChainedAuditPort. Does not change product AuditPort
 * same-TX rules. Not a product JAR and not a G4 close.
 */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { URL } from "node:url";

const HOST = process.env.NEXUS_G4_STUB_HOST || "127.0.0.1";
const PORT = Number(process.env.NEXUS_G4_STUB_PORT || 18089);
const TRACE = process.env.NEXUS_G4_PKCE_TRACE || "0199a1c0-0000-7000-8000-00000000c219";
const CONTEXT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e914";
const TENANT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e911";
const ACCOUNT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e912";
const PERSON_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e913";
const ROLE_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e915";
const FORBIDDEN = ["x-tenant-id", "x-nexus-demo-tenant-id"];
const LOGIN_ACCEPTED = "p2.auth.login.accepted";
const PERMISSION_GRANTED = "p2.permission.granted";
const GENESIS = Buffer.alloc(32, 0);

const state = {
  startedAt: new Date().toISOString(),
  authorizes: 0,
  logins: 0,
  grants: 0,
  rejectedTenantHeaders: 0,
  pkceValidated: 0,
  pendingAuth: new Map(),
  codes: new Map(),
  events: [],
  lastHash: Buffer.from(GENESIS),
  cells: [],
};

function sha256(buf) {
  return createHash("sha256").update(buf).digest();
}

function challengeS256(verifier) {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

function b64url(bytes) {
  return Buffer.from(bytes).toString("base64url");
}

function hex(buf) {
  return Buffer.from(buf).toString("hex");
}

function escapeJson(value) {
  return String(value)
    .replaceAll("\\", "\\\\")
    .replaceAll("\"", "\\\"")
    .replaceAll("\n", "\\n")
    .replaceAll("\r", "\\r")
    .replaceAll("\t", "\\t");
}

function detailsJson(details) {
  const keys = Object.keys(details || {}).sort();
  if (!keys.length) {
    return "{}";
  }
  return `{${keys.map((key) => `"${escapeJson(key)}":"${escapeJson(details[key] ?? "")}"`).join(",")}}`;
}

function occurredAtIso() {
  const now = new Date();
  const iso = now.toISOString();
  const [head, fracZ] = iso.split(".");
  const micros = `${(fracZ || "000Z").replace("Z", "")}000`.slice(0, 6);
  return `${head}.${micros}Z`;
}

function canonicalEncode(chainIndex, previousHash, event) {
  const lines = [
    "v1",
    String(chainIndex),
    hex(previousHash),
    event.eventId,
    String(event.eventVersion),
    event.occurredAt,
    event.category,
    event.eventType,
    event.tenantId || "-",
    event.actorSubjectId || "-",
    event.actorTenantId || "-",
    String(event.platformContext),
    event.reason || "-",
    event.traceId,
    event.correlationId || "-",
    event.causationId || "-",
    event.outcome,
    event.resourceType,
    event.resourceId || "-",
    detailsJson(event.details),
    "",
  ];
  return Buffer.from(lines.join("\n"), "utf8");
}

function appendEvent(partial) {
  const event = {
    eventId: randomUUID(),
    eventVersion: 1,
    occurredAt: occurredAtIso(),
    platformContext: false,
    correlationId: partial.traceId,
    causationId: null,
    ...partial,
  };
  const chainIndex = state.events.length;
  const previousHash = Buffer.from(state.lastHash);
  const eventHash = sha256(canonicalEncode(chainIndex, previousHash, event));
  const linked = {
    chainIndex,
    event,
    previousHash,
    eventHash,
    suite: "INTERNATIONAL",
    digestKeyVersion: "local:v1",
  };
  state.events.push(linked);
  state.lastHash = eventHash;
  return linked;
}

function verifyChain() {
  let prev = Buffer.from(GENESIS);
  for (let i = 0; i < state.events.length; i += 1) {
    const linked = state.events[i];
    if (linked.chainIndex !== i) {
      return { intact: false, checked: i, failure: `unexpected chain index ${linked.chainIndex}` };
    }
    if (!prev.equals(linked.previousHash)) {
      return { intact: false, checked: i, failure: `previous hash mismatch at ${i}` };
    }
    const expected = sha256(canonicalEncode(i, linked.previousHash, linked.event));
    if (!expected.equals(linked.eventHash)) {
      return { intact: false, checked: i, failure: `event hash mismatch at ${i}` };
    }
    prev = linked.eventHash;
  }
  return { intact: true, checked: state.events.length, failure: null };
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "Authorization, Content-Type, X-Nexus-Context-Id, X-Trace-Id, X-Nexus-Demo-Actor-Id, X-Nexus-Demo-Reason, X-Nexus-Bootstrap-Token",
    "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS",
  });
  res.end(payload);
}

function html(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
  });
  res.end(body);
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
      } catch {
        resolve(Object.fromEntries(new URLSearchParams(raw)));
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
    email: "g4-pkce@example.com",
    display_name: "G4 PKCE Click",
    displayName: "G4 PKCE Click",
    context_id: CONTEXT_ID,
    contextId: CONTEXT_ID,
    tenant_id: TENANT_ID,
    tenantId: TENANT_ID,
    person_id: PERSON_ID,
    personId: PERSON_ID,
    access_token: accessToken(),
    accessToken: accessToken(),
    memberships: [{ tenant_id: TENANT_ID, tenantId: TENANT_ID, person_id: PERSON_ID, status: "ACTIVE" }],
  };
}

function accessToken() {
  const claims = JSON.stringify({
    sub: "g4-pkce-click",
    email: "g4-pkce@example.com",
    preferred_username: "g4-pkce",
    name: "G4 PKCE Click",
    acr: "mfa",
    amr: "otp",
  });
  return `stub.${Buffer.from(claims, "utf8").toString("base64url")}`;
}

function loginForm(query) {
  const err = query.get("error")
    ? `<p class="err" data-testid="idp-error">${query.get("error")}</p>`
    : "";
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Sign in to nexus</title>
<style>
body{font-family:system-ui,sans-serif;background:#0b1324;color:#e8eef8;display:flex;justify-content:center;padding:48px}
.card{background:#152238;border:1px solid #2a3b58;border-radius:12px;padding:24px;width:360px}
label{display:block;margin:12px 0 4px;font-size:13px;color:#9db0c9}
input{width:100%;box-sizing:border-box;padding:8px;border-radius:6px;border:1px solid #3a4d6b;background:#0b1324;color:#fff}
button{margin-top:16px;width:100%;padding:10px;border:0;border-radius:6px;background:#3d8bfd;color:#fff;cursor:pointer}
.err{color:#ff8a8a}
.muted{color:#9db0c9;font-size:12px}
</style></head>
<body>
<form class="card" method="post" action="/protocol/openid-connect/auth" data-testid="idp-login-form">
  <h1>Sign in to nexus</h1>
  <p class="muted">Stub IdP · Authorization Code + PKCE S256 · G4 leftover (not Keycloak product UI)</p>
  ${err}
  <input type="hidden" name="client_id" value="${query.get("client_id") || "nexus-web"}" />
  <input type="hidden" name="redirect_uri" value="${query.get("redirect_uri") || ""}" />
  <input type="hidden" name="state" value="${query.get("state") || ""}" />
  <input type="hidden" name="code_challenge" value="${query.get("code_challenge") || ""}" />
  <input type="hidden" name="code_challenge_method" value="${query.get("code_challenge_method") || "S256"}" />
  <label>Username or email</label>
  <input name="username" data-testid="idp-username" value="g4-pkce@example.com" autocomplete="username" />
  <label>Password</label>
  <input name="password" type="password" data-testid="idp-password" value="password" autocomplete="current-password" />
  <button type="submit" data-testid="idp-login">Sign In</button>
</form>
</body></html>`;
}

function matrix() {
  return {
    policy_version: 1,
    policyVersion: 1,
    demo_seed_present: true,
    demoSeedPresent: true,
    differentiated_policy: true,
    differentiatedPolicy: true,
    completeness_note: "g4 pkce browser leftover stub",
    roles: [{ id: ROLE_ID, code: "g2_self_reader", name: "G2 self reader", system_role: false }],
    assignments: [],
    cells: state.cells,
  };
}

function auditJson(traceId) {
  const items = state.events
    .filter((linked) => linked.event.traceId === traceId && linked.event.tenantId === TENANT_ID)
    .map((linked) => ({
      chain_index: linked.chainIndex,
      event_id: linked.event.eventId,
      event_type: linked.event.eventType,
      category: linked.event.category,
      occurred_at: linked.event.occurredAt,
      outcome: linked.event.outcome,
      resource_type: linked.event.resourceType,
      resource_id: linked.event.resourceId,
      reason: linked.event.reason,
      details: linked.event.details,
      event_hash: hex(linked.eventHash),
      previous_hash: hex(linked.previousHash),
      cipher_suite: linked.suite,
    }));
  return { trace_id: traceId, tenant_id: TENANT_ID, events: items };
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
    if (req.method === "GET" && (url.pathname === "/actuator/health" || url.pathname === "/api/v1/health")) {
      json(res, 200, { status: "UP", stub: "g4-pkce-browser-click" });
      return;
    }
    if (req.method === "GET" && url.pathname === "/__g4/state") {
      const chain = verifyChain();
      json(res, 200, {
        context_id: CONTEXT_ID,
        tenant_id: TENANT_ID,
        trace_id: TRACE,
        authorizes: state.authorizes,
        logins: state.logins,
        grants: state.grants,
        pkce_validated: state.pkceValidated,
        rejected_tenant_headers: state.rejectedTenantHeaders,
        event_types: state.events.map((item) => item.event.eventType),
        verify_chain: chain,
        audit_port: "InMemoryHashChainedAuditPort-compatible CanonicalAudit SHA-256 (same event types as AuditCaptures)",
      });
      return;
    }
    if (req.method === "GET" && url.pathname === "/__g4/verify-chain") {
      json(res, 200, verifyChain());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/auth/authorize-url") {
      const pkce = url.searchParams.get("pkce");
      if (pkce && pkce.toLowerCase() !== "s256") {
        json(res, 400, { code: "P2.IAM.PKCE", message: "pkce must be s256 when set" });
        return;
      }
      state.authorizes += 1;
      const authState = randomUUID();
      const redirectUri = url.searchParams.get("redirect_uri") || `http://127.0.0.1:${process.env.NEXUS_G4_WEB_PORT || 4174}/callback`;
      const body = { url: "", state: authState };
      if (pkce) {
        const verifier = b64url(randomBytes(32));
        const challenge = challengeS256(verifier);
        const authorize = new URL(`http://${HOST}:${PORT}/protocol/openid-connect/auth`);
        authorize.searchParams.set("client_id", "nexus-web");
        authorize.searchParams.set("response_type", "code");
        authorize.searchParams.set("redirect_uri", redirectUri);
        authorize.searchParams.set("state", authState);
        authorize.searchParams.set("code_challenge", challenge);
        authorize.searchParams.set("code_challenge_method", "S256");
        body.url = authorize.toString();
        body.code_challenge = challenge;
        body.code_verifier = verifier;
        body.code_challenge_method = "S256";
        state.pendingAuth.set(authState, {
          challenge,
          verifierHint: verifier.length,
          redirectUri,
          traceId: headers["x-trace-id"] || TRACE,
        });
      } else {
        body.url = `${redirectUri}?code=stub-code&state=${authState}`;
      }
      json(res, 200, body);
      return;
    }
    if (req.method === "GET" && url.pathname === "/protocol/openid-connect/auth") {
      html(res, 200, loginForm(url.searchParams));
      return;
    }
    if (req.method === "POST" && url.pathname === "/protocol/openid-connect/auth") {
      const form = await readBody(req);
      const redirectUri = String(form.redirect_uri || "");
      const authState = String(form.state || "");
      const challenge = String(form.code_challenge || "");
      if (!form.username || !form.password) {
        const next = new URL(`http://${HOST}:${PORT}/protocol/openid-connect/auth`);
        next.search = new URLSearchParams({ ...form, error: "missing credentials" }).toString();
        res.writeHead(302, { Location: next.toString() });
        res.end();
        return;
      }
      const pending = state.pendingAuth.get(authState);
      if (!pending || pending.challenge !== challenge) {
        html(res, 400, loginForm(new URLSearchParams({ error: "unknown authorization request", ...form })));
        return;
      }
      const code = `g4-${b64url(randomBytes(12))}`;
      state.codes.set(code, {
        ...pending,
        username: form.username,
        challenge,
      });
      const loc = new URL(redirectUri);
      loc.searchParams.set("code", code);
      loc.searchParams.set("state", authState);
      res.writeHead(302, { Location: loc.toString() });
      res.end();
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/v1/auth/login") {
      const body = await readBody(req);
      if (body.access_token || body.accessToken) {
        state.logins += 1;
        json(res, 200, sessionView());
        return;
      }
      const code = body.code;
      const verifier = body.code_verifier || body.codeVerifier;
      const stored = state.codes.get(code);
      if (!stored) {
        json(res, 401, { code: "P2.IAM.LOGIN", message: "unknown authorization code" });
        return;
      }
      if (!verifier || challengeS256(verifier) !== stored.challenge) {
        json(res, 401, { code: "P2.IAM.PKCE", message: "code_verifier does not match code_challenge" });
        return;
      }
      state.pkceValidated += 1;
      state.logins += 1;
      state.codes.delete(code);
      const traceId = headers["x-trace-id"] || stored.traceId || TRACE;
      appendEvent({
        category: "LOGIN",
        eventType: LOGIN_ACCEPTED,
        tenantId: TENANT_ID,
        actorSubjectId: ACCOUNT_ID,
        actorTenantId: TENANT_ID,
        reason: "accepted",
        traceId,
        outcome: "ACCEPTED",
        resourceType: "session",
        resourceId: CONTEXT_ID,
        details: { account_id: ACCOUNT_ID, email: "g4-pkce@example.com", mfa_satisfied: "true" },
      });
      json(res, 200, sessionView());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/auth/session") {
      json(res, 200, sessionView());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/permission/health") {
      json(res, 200, { status: "UP", stub: "g4-pkce-browser-click" });
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/permission/snapshot") {
      json(res, 200, { grant_count: state.cells.length, grantCount: state.cells.length, policy_version: 1 });
      return;
    }
    if (req.method === "GET" && (url.pathname === "/api/v1/permission/matrix" || url.pathname === "/api/v1/permission/grants")) {
      json(res, 200, matrix());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/permission/roles") {
      json(res, 200, { items: matrix().roles });
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/permission/assignments") {
      json(res, 200, { items: [] });
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/v1/permission/grants") {
      const body = await readBody(req);
      const cell = {
        id: randomUUID(),
        effect: body.effect || "GRANT",
        principal_type: body.principal_type || "role",
        principalType: body.principal_type || "role",
        principal_id: body.principal_id || "g2_self_reader",
        principalId: body.principal_id || "g2_self_reader",
        resource: body.resource || "ledger",
        action: body.action || "read",
        scope_kind: body.scope_kind || "SELF",
        scopeKind: body.scope_kind || "SELF",
        fields: body.fields || {},
      };
      state.cells.push(cell);
      state.grants += 1;
      const traceId = headers["x-trace-id"] || TRACE;
      appendEvent({
        category: "PERMISSION",
        eventType: PERMISSION_GRANTED,
        tenantId: TENANT_ID,
        actorSubjectId: ACCOUNT_ID,
        actorTenantId: TENANT_ID,
        reason: "grant",
        traceId,
        outcome: "ACCEPTED",
        resourceType: cell.resource,
        resourceId: `${cell.principal_id}:${cell.action}`,
        details: { effect: "GRANT", scope_kind: cell.scope_kind },
      });
      json(res, 200, cell);
      return;
    }
    if (req.method === "GET" && url.pathname.startsWith("/api/v1/audit/traces/")) {
      const traceId = decodeURIComponent(url.pathname.slice("/api/v1/audit/traces/".length));
      json(res, 200, auditJson(traceId));
      return;
    }
    json(res, 404, { code: "STUB.NOT_FOUND", message: url.pathname });
  } catch (err) {
    json(res, 500, { code: "STUB.ERROR", message: err.message });
  }
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`g4 stub PKCE IdP + AuditPort-shaped chain http://${HOST}:${PORT}\n`);
});
