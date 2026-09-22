/**
 * G17 leftover: admin MFA recovery-code / email delivery honesty beyond #154.
 *
 * #154 already issues local recovery codes and an email MFA request contract.
 * This leftover is the honesty gate: live Keycloak recovery-authn-codes and
 * SMTP / email OTP delivery are PASS only when they were actually observed.
 * Missing Keycloak recovery-authn-codes or SMTP is honest SKIP.
 *
 * Does not redo #96/#118/#133/#154/#158/#160.
 * Does not claim 等保 / 密评 / 完整 G17. G18 stays closed.
 */

export const HONESTY_RESULTS = Object.freeze({
  SKIP: "SKIP",
  PASS: "PASS",
  FAIL: "FAIL",
});

export const HONESTY_REASONS = Object.freeze({
  SMTP_DISABLED: "P2.IAM.SMTP_DISABLED",
  EMAIL_OTP_NOT_DELIVERED: "P2.IAM.EMAIL_OTP_NOT_DELIVERED",
  RECOVERY_NOT_DELIVERED: "P2.IAM.RECOVERY_CODES_NOT_DELIVERED",
  KEYCLOAK_ISSUER_UNREACHABLE: "keycloak_issuer_unreachable",
  KEYCLOAK_ISSUER_MISSING: "keycloak_issuer_missing",
  OIDC_DISCOVERY_FAILED: "oidc_discovery_failed",
  MAILBOX_NOT_OBSERVED: "mailbox_not_observed",
  RECOVERY_AUTHN_CODES_NOT_ENROLLED: "recovery_authn_codes_not_enrolled",
  RECOVERY_CODE_NOT_CONSUMED_LIVE: "recovery_code_not_consumed_live",
});

export function describeRecoveryEmailHonesty() {
  return {
    leftover: "g17-mfa-recovery-email-honesty",
    siblingContract: "g17-admin-mfa-recovery-email",
    localRecoveryCodes: true,
    emailRequestContract: true,
    vueWritesKeycloakOtp: false,
    vueWritesRecoveryAuthnCodes: false,
    smtpSendsMail: false,
    recoveryAuthnCodesEnrolled: false,
    mailboxObserved: false,
    sessionGate: "#96",
    httpDriverWalk: "#118",
    enrollmentUx: "#133",
    recoveryEmailContract: "#154",
    vueOtpWriteback: "#158",
    gmtlsHandshake: "#160",
    dengbaoClaimed: false,
    mipinClaimed: false,
    hsmClaimed: false,
    g17Close: "NOT_CLAIMED",
    g18: "closed_unchanged",
    auditPortChanged: false,
    docker: false,
    liveEmailRequires: [
      "nexus.iam.smtp.enabled=true",
      "SMTP host configured",
      "reachable Keycloak issuer",
      "mailbox observed receiving an email OTP",
    ],
    liveRecoveryRequires: [
      "reachable Keycloak issuer",
      "OIDC discovery",
      "CONFIGURE_RECOVERY_AUTHN_CODES enrolled",
      "live recovery-authn-code consumed",
    ],
  };
}

export function defaultIssuers(env = typeof process !== "undefined" ? process.env : {}) {
  const fromEnv = [env?.NEXUS_KEYCLOAK_ISSUER, env?.VITE_NEXUS_KEYCLOAK_ISSUER]
    .filter((value) => value && String(value).trim());
  if (fromEnv.length) {
    return fromEnv.map((value) => String(value).replace(/\/$/, ""));
  }
  return [
    "http://127.0.0.1:8080/realms/nexus",
    "http://127.0.0.1:8180/realms/nexus",
  ];
}

export function smtpEnabledFromEnv(env = typeof process !== "undefined" ? process.env : {}) {
  const raw = String(env?.NEXUS_IAM_SMTP_ENABLED || "").trim().toLowerCase();
  return raw === "true" || raw === "1";
}

export function smtpHostFromEnv(env = typeof process !== "undefined" ? process.env : {}) {
  for (const name of ["NEXUS_IAM_SMTP_HOST", "NEXUS_KEYCLOAK_SMTP_HOST", "SMTP_HOST"]) {
    const value = env?.[name];
    if (value && String(value).trim()) {
      return String(value).trim();
    }
  }
  return "";
}

export async function probeKeycloakIssuer({
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
      reason: HONESTY_REASONS.KEYCLOAK_ISSUER_MISSING,
    };
  }
  const fetchFn = fetchImpl || globalThis.fetch;
  if (typeof fetchFn !== "function") {
    return {
      reachable: false,
      issuer: list[0],
      discovery: "SKIP",
      reason: HONESTY_REASONS.KEYCLOAK_ISSUER_UNREACHABLE,
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
        reason: HONESTY_REASONS.OIDC_DISCOVERY_FAILED,
      };
    } catch {
      // loopback Keycloak is often down on agent VMs
    }
  }
  return {
    reachable: false,
    issuer: String(list[0]).replace(/\/$/, ""),
    discovery: "SKIP",
    reason: HONESTY_REASONS.KEYCLOAK_ISSUER_UNREACHABLE,
  };
}

function skipEmail(reason, probe, extra = {}) {
  return {
    result: HONESTY_RESULTS.SKIP,
    reason,
    mailboxObserved: false,
    smtpEnabled: Boolean(extra.smtpEnabled),
    smtpHostConfigured: Boolean(extra.smtpHostConfigured),
    keycloakIssuerReachable: Boolean(probe?.reachable),
    oidcDiscovery: probe?.discovery || "SKIP",
    issuer: probe?.issuer || "",
    ...extra,
  };
}

function skipRecovery(reason, probe, extra = {}) {
  return {
    result: HONESTY_RESULTS.SKIP,
    reason,
    recoveryAuthnCodesEnrolled: false,
    recoveryCodeConsumedLive: false,
    keycloakIssuerReachable: Boolean(probe?.reachable),
    oidcDiscovery: probe?.discovery || "SKIP",
    issuer: probe?.issuer || "",
    ...extra,
  };
}

/**
 * Live email OTP delivery honesty.
 * Default leftover: honest SKIP. PASS only when SMTP is on, a host is set,
 * Keycloak is reachable, and a mailbox actually received an OTP.
 */
export async function probeEmailDeliveryHonesty({
  issuers,
  fetchImpl,
  timeoutMs,
  env,
  smtpEnabled = smtpEnabledFromEnv(env),
  smtpHost = smtpHostFromEnv(env),
  mailboxObserved = false,
} = {}) {
  const probe = await probeKeycloakIssuer({ issuers, fetchImpl, timeoutMs, env });
  const hostConfigured = Boolean(smtpHost && String(smtpHost).trim());
  const extras = { smtpEnabled, smtpHostConfigured: hostConfigured };

  if (!smtpEnabled) {
    return skipEmail(HONESTY_REASONS.SMTP_DISABLED, probe, extras);
  }
  if (!hostConfigured) {
    return skipEmail(HONESTY_REASONS.EMAIL_OTP_NOT_DELIVERED, probe, extras);
  }
  if (!probe.reachable) {
    return skipEmail(probe.reason || HONESTY_REASONS.KEYCLOAK_ISSUER_UNREACHABLE, probe, extras);
  }
  if (probe.discovery === "FAIL") {
    return {
      result: HONESTY_RESULTS.FAIL,
      reason: HONESTY_REASONS.OIDC_DISCOVERY_FAILED,
      mailboxObserved: false,
      smtpEnabled: true,
      smtpHostConfigured: true,
      keycloakIssuerReachable: true,
      oidcDiscovery: "FAIL",
      issuer: probe.issuer,
    };
  }
  if (!mailboxObserved) {
    return skipEmail(HONESTY_REASONS.MAILBOX_NOT_OBSERVED, probe, extras);
  }
  return {
    result: HONESTY_RESULTS.PASS,
    reason: "",
    mailboxObserved: true,
    smtpEnabled: true,
    smtpHostConfigured: true,
    keycloakIssuerReachable: true,
    oidcDiscovery: "PASS",
    issuer: probe.issuer,
  };
}

/**
 * Live Keycloak recovery-authn-codes honesty.
 * Default leftover: honest SKIP. PASS only when a live issuer enrolled
 * CONFIGURE_RECOVERY_AUTHN_CODES and a recovery-authn-code was consumed.
 */
export async function probeRecoveryAuthnHonesty({
  issuers,
  fetchImpl,
  timeoutMs,
  env,
  recoveryAuthnCodesEnrolled = false,
  recoveryCodeConsumedLive = false,
} = {}) {
  const probe = await probeKeycloakIssuer({ issuers, fetchImpl, timeoutMs, env });
  if (!probe.reachable) {
    return skipRecovery(probe.reason || HONESTY_REASONS.KEYCLOAK_ISSUER_UNREACHABLE, probe);
  }
  if (probe.discovery === "FAIL") {
    return {
      result: HONESTY_RESULTS.FAIL,
      reason: HONESTY_REASONS.OIDC_DISCOVERY_FAILED,
      recoveryAuthnCodesEnrolled: false,
      recoveryCodeConsumedLive: false,
      keycloakIssuerReachable: true,
      oidcDiscovery: "FAIL",
      issuer: probe.issuer,
    };
  }
  if (!recoveryAuthnCodesEnrolled) {
    return skipRecovery(HONESTY_REASONS.RECOVERY_AUTHN_CODES_NOT_ENROLLED, probe);
  }
  if (!recoveryCodeConsumedLive) {
    return skipRecovery(HONESTY_REASONS.RECOVERY_CODE_NOT_CONSUMED_LIVE, probe, {
      recoveryAuthnCodesEnrolled: true,
    });
  }
  return {
    result: HONESTY_RESULTS.PASS,
    reason: "",
    recoveryAuthnCodesEnrolled: true,
    recoveryCodeConsumedLive: true,
    keycloakIssuerReachable: true,
    oidcDiscovery: "PASS",
    issuer: probe.issuer,
  };
}

export async function probeRecoveryEmailHonesty(options = {}) {
  const email = await probeEmailDeliveryHonesty(options);
  const recovery = await probeRecoveryAuthnHonesty(options);
  return {
    leftover: "g17-mfa-recovery-email-honesty",
    emailDelivery: email.result,
    emailDeliverySkipReason: email.result === HONESTY_RESULTS.SKIP ? email.reason : "",
    mailboxObserved: Boolean(email.mailboxObserved),
    smtpEnabled: Boolean(email.smtpEnabled),
    smtpHostConfigured: Boolean(email.smtpHostConfigured),
    recoveryLive: recovery.result,
    recoveryLiveSkipReason: recovery.result === HONESTY_RESULTS.SKIP ? recovery.reason : "",
    recoveryAuthnCodesEnrolled: Boolean(recovery.recoveryAuthnCodesEnrolled),
    recoveryCodeConsumedLive: Boolean(recovery.recoveryCodeConsumedLive),
    keycloakIssuerReachable: Boolean(email.keycloakIssuerReachable || recovery.keycloakIssuerReachable),
    oidcDiscovery: email.oidcDiscovery === "PASS" || recovery.oidcDiscovery === "PASS"
      ? "PASS"
      : email.oidcDiscovery || recovery.oidcDiscovery || "SKIP",
    issuer: email.issuer || recovery.issuer || "",
    vueWritesKeycloakOtp: false,
    g17Close: "NOT_CLAIMED",
    g18: "closed_unchanged",
  };
}

export function formatHonestyStatus(probe) {
  const email = probe?.emailDelivery || HONESTY_RESULTS.SKIP;
  const recovery = probe?.recoveryLive || HONESTY_RESULTS.SKIP;
  return `email_delivery=${email} (${probe?.emailDeliverySkipReason || "observed"}); recovery_live=${recovery} (${probe?.recoveryLiveSkipReason || "observed"}). Not product G17.`;
}
