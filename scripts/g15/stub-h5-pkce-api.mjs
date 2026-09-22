#!/usr/bin/env node
/**
 * Stub authorize-url + PKCE token exchange for the G15 H5 leftover.
 * Validates RFC 7636 S256. Never accepts X-Tenant-Id.
 * Not a product JAR and not a G15 close.
 */
import { createHash, randomBytes } from "node:crypto";
import { createServer } from "node:http";
import { URL } from "node:url";

const HOST = process.env.NEXUS_G15_STUB_HOST || "127.0.0.1";
const PORT = Number(process.env.NEXUS_G15_STUB_PORT || 18089);
const CONTEXT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e981";
const TENANT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e901";
const ACCOUNT_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e904";
const PERSON_ID = "01a0c98a-1f3c-7f72-b724-6ac34746e905";
const CODE = "h5-pkce-code";
const FORBIDDEN = ["x-tenant-id", "x-nexus-demo-tenant-id"];

const state = {
  startedAt: new Date().toISOString(),
  authorizes: [],
  logins: [],
  rejected: [],
  pending: new Map(),
};

function json(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "Authorization, Content-Type, X-Nexus-Context-Id, X-Trace-Id, X-Nexus-Demo-Actor-Id, X-Nexus-Demo-Reason, X-Nexus-Bootstrap-Token",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
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

function headerMap(req) {
  const out = {};
  for (const [key, value] of Object.entries(req.headers)) {
    out[key.toLowerCase()] = Array.isArray(value) ? value.join(",") : value;
  }
  return out;
}

function challengeS256(verifier) {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

function sessionView() {
  return {
    account_id: ACCOUNT_ID,
    accountId: ACCOUNT_ID,
    email: "h5-pkce@example.com",
    display_name: "H5 PKCE",
    displayName: "H5 PKCE",
    context_id: CONTEXT_ID,
    contextId: CONTEXT_ID,
    tenant_id: TENANT_ID,
    tenantId: TENANT_ID,
    person_id: PERSON_ID,
    personId: PERSON_ID,
    memberships: [{ tenant_id: TENANT_ID, tenantId: TENANT_ID, person_id: PERSON_ID, status: "ACTIVE" }],
  };
}

function publicState() {
  return {
    context_id: CONTEXT_ID,
    authorizes: state.authorizes,
    logins: state.logins,
    rejected: state.rejected,
    pending: [...state.pending.values()].map((item) => ({
      state: item.state,
      challenge: item.challenge,
      client_held: item.clientHeld,
    })),
  };
}

const server = createServer(async (req, res) => {
  if (req.method === "OPTIONS") {
    json(res, 204, {});
    return;
  }
  const url = new URL(req.url || "/", `http://${HOST}:${PORT}`);
  const headers = headerMap(req);
  if (FORBIDDEN.some((name) => headers[name])) {
    json(res, 400, { code: "P2.IAM.CLIENT_TENANT_REJECTED", message: "client must not send tenant identity headers" });
    return;
  }

  try {
    if (req.method === "GET" && url.pathname === "/__g15/state") {
      json(res, 200, publicState());
      return;
    }
    if (req.method === "GET" && (url.pathname === "/actuator/health" || url.pathname === "/api/v1/health")) {
      json(res, 200, { status: "UP", stub: "g15-h5-pkce" });
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/auth/authorize-url") {
      const pkce = url.searchParams.get("pkce") || "";
      const clientChallenge = url.searchParams.get("code_challenge") || "";
      const authState = `st-${randomBytes(6).toString("hex")}`;
      if (clientChallenge && pkce.toLowerCase() !== "s256") {
        json(res, 400, { code: "P2.IAM.PKCE", message: "pkce must be s256 when code_challenge is set" });
        return;
      }
      let challenge = "";
      let verifier = "";
      let clientHeld = false;
      if (pkce.toLowerCase() === "s256") {
        if (clientChallenge) {
          if (!/^[A-Za-z0-9_-]{43,128}$/.test(clientChallenge)) {
            json(res, 400, { code: "P2.IAM.PKCE", message: "invalid code_challenge" });
            return;
          }
          challenge = clientChallenge;
          clientHeld = true;
        } else {
          verifier = randomBytes(32).toString("base64url");
          challenge = challengeS256(verifier);
        }
      }
      const redirect = `/m/callback?code=${CODE}&state=${authState}${
        challenge ? `&code_challenge=${challenge}&code_challenge_method=S256` : ""
      }`;
      const body = { url: redirect, state: authState };
      if (challenge) {
        body.code_challenge = challenge;
        body.code_challenge_method = "S256";
        if (verifier) {
          body.code_verifier = verifier;
        }
      }
      state.pending.set(authState, { state: authState, challenge, clientHeld });
      state.authorizes.push({
        at: new Date().toISOString(),
        pkce: pkce || "",
        client_held: clientHeld,
        challenge,
        returned_verifier: Boolean(verifier),
      });
      json(res, 200, body);
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/v1/auth/login") {
      const body = await readBody(req);
      if (body.access_token || body.accessToken) {
        state.logins.push({ at: new Date().toISOString(), kind: "access_token" });
        json(res, 200, sessionView());
        return;
      }
      const verifier = body.code_verifier || body.codeVerifier || "";
      const pending = [...state.pending.values()].at(-1);
      if (!pending?.challenge) {
        state.rejected.push({ at: new Date().toISOString(), reason: "no_pending_pkce" });
        json(res, 400, { code: "P2.IAM.PKCE", message: "PKCE challenge is required" });
        return;
      }
      if (!verifier || challengeS256(verifier) !== pending.challenge) {
        state.rejected.push({
          at: new Date().toISOString(),
          reason: "verifier_mismatch",
          had_verifier: Boolean(verifier),
        });
        json(res, 400, { code: "P2.IAM.PKCE", message: "code_verifier does not match code_challenge" });
        return;
      }
      if (body.code !== CODE && body.code !== "stub-code") {
        json(res, 400, { code: "P2.IAM.LOGIN", message: "unknown authorization code" });
        return;
      }
      state.logins.push({
        at: new Date().toISOString(),
        kind: "pkce",
        code: body.code,
        redirect_uri: body.redirect_uri || body.redirectUri || "",
        x_tenant_id: false,
        context_id: CONTEXT_ID,
      });
      json(res, 200, sessionView());
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/v1/auth/session") {
      json(res, 200, sessionView());
      return;
    }
    json(res, 404, { code: "STUB.NOT_FOUND", message: url.pathname });
  } catch (err) {
    json(res, 500, { code: "STUB.ERROR", message: err.message });
  }
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`g15 H5 PKCE stub http://${HOST}:${PORT}\n`);
});
