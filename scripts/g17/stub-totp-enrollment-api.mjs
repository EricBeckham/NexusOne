#!/usr/bin/env node
/**
 * Stub login for the G17 TOTP enrollment UX leftover.
 * Password-only stub tokens → 403 P2.IAM.MFA_REQUIRED.
 * Default acr=mfa tokens still issue a context (G18 path unchanged).
 * Never accepts X-Tenant-Id. Not a product JAR.
 */
import { createServer } from "node:http";

const HOST = process.env.NEXUS_G17_STUB_HOST || "127.0.0.1";
const PORT = Number(process.env.NEXUS_G17_STUB_PORT || 18091);
const CONTEXT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e981";
const TENANT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e901";
const ACCOUNT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e902";
const FORBIDDEN = ["x-tenant-id", "x-nexus-demo-tenant-id"];

const state = {
  startedAt: new Date().toISOString(),
  logins: 0,
  rejected: 0,
  rejectedTenantHeaders: 0,
};

function json(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "Authorization, Content-Type, X-Nexus-Context-Id, X-Trace-Id, X-Nexus-Demo-Actor-Id, X-Nexus-Demo-Reason, X-Nexus-Bootstrap-Token",
    "Access-Control-Allow-Methods": "GET,POST,PUT,OPTIONS",
  });
  res.end(JSON.stringify(body));
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

function decodeStub(token) {
  if (!token || !String(token).startsWith("stub.")) {
    return {};
  }
  const b64 = String(token).slice(5).replace(/-/g, "+").replace(/_/g, "/");
  const pad = "=".repeat((4 - (b64.length % 4)) % 4);
  try {
    return JSON.parse(Buffer.from(b64 + pad, "base64").toString("utf8"));
  } catch {
    return {};
  }
}

function hasForbidden(req) {
  return Object.keys(req.headers || {}).some((key) => FORBIDDEN.includes(key.toLowerCase()));
}

const server = createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    json(res, 204, {});
    return;
  }
  if (hasForbidden(req)) {
    state.rejectedTenantHeaders += 1;
    json(res, 400, { code: "ARCH-024", message: "client must not send tenant identity headers" });
    return;
  }
  const url = new URL(req.url, `http://${HOST}:${PORT}`);
  if (req.method === "GET" && url.pathname === "/actuator/health") {
    json(res, 200, { status: "UP" });
    return;
  }
  if (req.method === "GET" && url.pathname === "/__g17/state") {
    json(res, 200, state);
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/v1/auth/login") {
    let body;
    try {
      body = await readBody(req);
    } catch {
      json(res, 400, { code: "P2.IAM.LOGIN", message: "invalid json" });
      return;
    }
    const claims = decodeStub(body.access_token || body.accessToken);
    const mfa = claims.acr === "mfa" || String(claims.amr || "").includes("otp");
    if (!mfa) {
      state.rejected += 1;
      json(res, 403, {
        code: "P2.IAM.MFA_REQUIRED",
        message: "admin privileged session requires MFA",
      });
      return;
    }
    state.logins += 1;
    json(res, 200, {
      account_id: ACCOUNT_ID,
      email: claims.email || "admin-a@example.com",
      display_name: claims.name || "Admin A",
      context_id: CONTEXT_ID,
      tenant_id: TENANT_ID,
    });
    return;
  }
  json(res, 404, { code: "NOT_FOUND", message: url.pathname });
});

server.listen(PORT, HOST, () => {
  console.log(`g17 totp enrollment stub http://${HOST}:${PORT}`);
});
