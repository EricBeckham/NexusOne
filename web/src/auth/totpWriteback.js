/**
 * G17 leftover: Vue → Keycloak otp / authenticator enrollment write-back bridge.
 *
 * Vue confirmEnrollment success is local only (#133). This module is the
 * honesty probe: it never claims a Keycloak otp credential was stored unless
 * a live issuer accepted a product write-back AND the credential is visible.
 *
 * Does not redo #96 AdminMfaPolicy, #118 HTTP driver, or #133 UX/copy.
 * Does not issue recovery codes or accept email MFA (orthogonal leftover).
 * Does not claim 等保 / 密评 / 完整 G17. G18 stays closed.
 */

export const WRITEBACK_RESULTS = Object.freeze({
  SKIP: "SKIP",
  PASS: "PASS",
  FAIL: "FAIL",
});

export const WRITEBACK_REASONS = Object.freeze({
  KEYCLOAK_ISSUER_MISSING: "keycloak_issuer_missing",
  KEYCLOAK_ISSUER_UNREACHABLE: "keycloak_issuer_unreachable",
  OIDC_DISCOVERY_FAILED: "oidc_discovery_failed",
  PRODUCT_ENDPOINT_ABSENT: "product_vue_writeback_endpoint_absent",
  WRITEBACK_NOT_OPTED_IN: "writeback_not_opted_in",
  ADMIN_TOKEN_MISSING: "admin_token_missing",
  OTP_CREDENTIAL_NOT_STORED: "otp_credential_not_stored",
  VUE_CONFIRM_LOCAL_ONLY: "vue_confirm_is_local_only",
});

export const PRODUCT_WRITEBACK_PATH = "/api/v1/auth/mfa/enroll";

export function describeOtpWritebackBridge() {
  return {
    leftover: "g17-vue-otp-writeback",
    vueConfirmWritesKeycloak: false,
    productWritebackEndpoint: false,
    productWritebackPath: PRODUCT_WRITEBACK_PATH,
    httpDriverWalk: "#118",
    enrollmentUx: "#133",
    sessionGate: "#96",
    recoveryCodes: false,
    emailMfa: false,
    smsMfa: false,
    webauthn: false,
    dengbaoClaimed: false,
    mipinClaimed: false,
    hsmClaimed: false,
    g17Close: "NOT_CLAIMED",
    g18: "closed_unchanged",
    auditPortChanged: false,
    docker: false,
    liveWriteRequires: [
      "reachable Keycloak 26.5.2 issuer",
      "OIDC discovery",
      "product Vue write-back endpoint",
      "visible otp credential after write",
    ],
  };
}

export function defaultIssuers(env = typeof process !== "undefined" ? process.env : {}) {
  const fromEnv = [
    env?.NEXUS_KEYCLOAK_ISSUER,
    env?.VITE_NEXUS_KEYCLOAK_ISSUER,
  ].filter((value) => value && String(value).trim());
  if (fromEnv.length) {
    return fromEnv.map((value) => String(value).replace(/\/$/, ""));
  }
  return [
    "http://127.0.0.1:8080/realms/nexus",
    "http://127.0.0.1:8180/realms/nexus",
  ];
}

export function productWritebackEndpointPresent() {
  return false;
}

function skip(reason, probe, extra = {}) {
  return {
    result: WRITEBACK_RESULTS.SKIP,
    reason,
    vueConfirmWritesKeycloak: false,
    otpCredentialStored: false,
    keycloakIssuerReachable: Boolean(probe?.reachable),
    oidcDiscovery: probe?.discovery || "SKIP",
    issuer: probe?.issuer || "",
    productWritebackEndpoint: false,
    ...extra,
  };
}

export async function probeKeycloakOtpWriteback({
  issuers,
  fetchImpl,
  timeoutMs = 1500,
  env,
} = {}) {
  const list = (issuers && issuers.length ? issuers : defaultIssuers(env)).filter(Boolean);
  if (!list.length) {
    return {
      reachable: false,
      issuer: "",
      discovery: "SKIP",
      reason: WRITEBACK_REASONS.KEYCLOAK_ISSUER_MISSING,
    };
  }
  const fetchFn = fetchImpl || globalThis.fetch;
  if (typeof fetchFn !== "function") {
    return {
      reachable: false,
      issuer: list[0],
      discovery: "SKIP",
      reason: WRITEBACK_REASONS.KEYCLOAK_ISSUER_UNREACHABLE,
    };
  }
  for (const issuer of list) {
    const url = `${String(issuer).replace(/\/$/, "")}/.well-known/openid-configuration`;
    try {
      const ctrl = typeof AbortController === "function" ? new AbortController() : null;
      const timer = ctrl ? setTimeout(() => ctrl.abort(), timeoutMs) : null;
      const res = await fetchFn(url, ctrl ? { signal: ctrl.signal } : {});
      if (timer) {
        clearTimeout(timer);
      }
      if (!res || !res.ok) {
        continue;
      }
      const body = typeof res.json === "function" ? await res.json().catch(() => ({})) : {};
      if (body.issuer || body.authorization_endpoint || body.token_endpoint) {
        return {
          reachable: true,
          issuer: String(issuer).replace(/\/$/, ""),
          discovery: "PASS",
          reason: "",
        };
      }
      return {
        reachable: true,
        issuer: String(issuer).replace(/\/$/, ""),
        discovery: "FAIL",
        reason: WRITEBACK_REASONS.OIDC_DISCOVERY_FAILED,
      };
    } catch {
      // try the next candidate; loopback Keycloak is often down on agent VMs
    }
  }
  return {
    reachable: false,
    issuer: String(list[0]).replace(/\/$/, ""),
    discovery: "SKIP",
    reason: WRITEBACK_REASONS.KEYCLOAK_ISSUER_UNREACHABLE,
  };
}

/**
 * Attempt a Vue-page Keycloak otp write-back.
 * Default leftover: honest SKIP. PASS only when a live issuer, a product
 * write-back endpoint, and a stored otp credential are all proven.
 */
export async function attemptVueOtpWriteback({
  session,
  issuers,
  fetchImpl,
  timeoutMs,
  env,
  productWritebackEndpoint = productWritebackEndpointPresent(),
  otpCredentialStored = false,
  optIn = false,
} = {}) {
  const probe = await probeKeycloakOtpWriteback({ issuers, fetchImpl, timeoutMs, env });
  const enrolled = session?.status === "enrolled";

  if (!enrolled) {
    return skip(WRITEBACK_REASONS.VUE_CONFIRM_LOCAL_ONLY, probe, {
      localConfirmOnly: true,
    });
  }

  if (!probe.reachable) {
    return skip(probe.reason || WRITEBACK_REASONS.KEYCLOAK_ISSUER_UNREACHABLE, probe, {
      localConfirmOnly: true,
    });
  }

  if (probe.discovery === "FAIL") {
    return {
      result: WRITEBACK_RESULTS.FAIL,
      reason: WRITEBACK_REASONS.OIDC_DISCOVERY_FAILED,
      vueConfirmWritesKeycloak: false,
      otpCredentialStored: false,
      keycloakIssuerReachable: true,
      oidcDiscovery: "FAIL",
      issuer: probe.issuer,
      productWritebackEndpoint: false,
      localConfirmOnly: true,
    };
  }

  if (!productWritebackEndpoint) {
    return skip(WRITEBACK_REASONS.PRODUCT_ENDPOINT_ABSENT, probe, {
      localConfirmOnly: true,
    });
  }

  if (!optIn) {
    return skip(WRITEBACK_REASONS.WRITEBACK_NOT_OPTED_IN, probe, {
      productWritebackEndpoint: true,
      localConfirmOnly: true,
    });
  }

  if (!otpCredentialStored) {
    return {
      result: WRITEBACK_RESULTS.FAIL,
      reason: WRITEBACK_REASONS.OTP_CREDENTIAL_NOT_STORED,
      vueConfirmWritesKeycloak: false,
      otpCredentialStored: false,
      keycloakIssuerReachable: true,
      oidcDiscovery: probe.discovery,
      issuer: probe.issuer,
      productWritebackEndpoint: true,
      localConfirmOnly: true,
    };
  }

  return {
    result: WRITEBACK_RESULTS.PASS,
    reason: "",
    vueConfirmWritesKeycloak: true,
    otpCredentialStored: true,
    keycloakIssuerReachable: true,
    oidcDiscovery: "PASS",
    issuer: probe.issuer,
    productWritebackEndpoint: true,
    localConfirmOnly: false,
  };
}

export function formatWritebackStatus(writeback) {
  const result = writeback?.result || WRITEBACK_RESULTS.SKIP;
  const reason = writeback?.reason || WRITEBACK_REASONS.VUE_CONFIRM_LOCAL_ONLY;
  if (result === WRITEBACK_RESULTS.SKIP) {
    return `Keycloak otp 写回 ${result}：${reason}。本页 confirm 成功 ≠ 写入 Keycloak otp。`;
  }
  if (result === WRITEBACK_RESULTS.PASS) {
    return `Keycloak otp 写回 ${result}：活 IdP 已存 otp 凭据。`;
  }
  return `Keycloak otp 写回 ${result}：${reason}。`;
}
