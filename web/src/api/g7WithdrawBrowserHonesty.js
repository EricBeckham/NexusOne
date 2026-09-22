/**
 * G7 leftover beyond #113/#159: Vue todo-withdraw / browser evidence honesty.
 *
 * #113/#159 already proved stub Vue click + HTTP reachability + Playwright
 * stub walk. This module is honesty only: live product-JAR + live/Keycloak
 * IdP browser withdraw is PASS only when those are present AND a live
 * withdraw click was observed. Missing JAR/IdP is honest SKIP.
 *
 * Does not redo #103 / #113 / #140 / #159. Does not claim product G7 close.
 * Does not reopen G18. Does not redo P0/M0. AuditPort stays untouched.
 * Keep both leftover rows: g7-withdraw-browser and g7-query-expense-travel.
 */

export const RESULTS = Object.freeze({
  SKIP: "SKIP",
  PASS: "PASS",
  FAIL: "FAIL",
});

export const REASONS = Object.freeze({
  LIVE_JAR_UNHEALTHY: "live_jar_not_healthy",
  ISSUED_CONTEXT_MISSING: "issued_context_missing",
  LIVE_IDP_ABSENT: "live_idp_absent",
  STUB_LOGIN_NOT_LIVE: "stub_login_is_not_live_walk",
  CHROME_MISSING: "chrome_missing",
  WITHDRAW_NOT_OBSERVED: "live_withdraw_click_not_observed",
});

export const HTTP_WITHDRAW = "POST /api/v1/p3/path/instances/{id}/withdraw";
export const CONTROL = "data-testid=todo-withdraw";
export const BOTH_LEFTOVER_ROWS = Object.freeze([
  "g7-withdraw-browser",
  "g7-query-expense-travel",
]);

export function describeWithdrawBrowserHonesty() {
  return {
    leftover: "g7-withdraw-browser-honesty",
    siblingBrowserLeftover: "#113/#159",
    siblingQueryLeftover: "#140",
    siblingExpenseTravelWithdraw: "#103",
    control: CONTROL,
    httpPath: HTTP_WITHDRAW,
    stubWalkIsNotLive: true,
    vueReachabilityIsNotLiveJar: true,
    g7Close: "NOT_CLAIMED",
    g18: "closed_unchanged",
    auditPortChanged: false,
    p0M0Redone: false,
    redo103: false,
    redo113: false,
    redo140: false,
    redo159: false,
    keepBothLeftoverRows: [...BOTH_LEFTOVER_ROWS],
    livePassRequires: [
      "healthy live product JAR",
      "issued X-Nexus-Context-Id",
      "live/Keycloak IdP (stub login is not a live walk)",
      "system Chrome",
      "observed live withdraw click on leave + expense + travel",
    ],
  };
}

export function evaluateLiveJarIdpWithdraw(inventory = {}) {
  const liveJar = inventory.liveJar === true;
  const issuedContext = inventory.issuedContext === true;
  const idp = String(inventory.idp || "absent");
  const chromePresent = inventory.chromePresent === true;
  const observed = inventory.withdrawClickObserved === true;
  const typesWalked = Array.isArray(inventory.typesWalked) ? inventory.typesWalked : [];
  const allTypes = ["leave", "expense", "travel"].every((type) => typesWalked.includes(type));

  let reason = "";
  if (!liveJar) {
    reason = REASONS.LIVE_JAR_UNHEALTHY;
  } else if (!issuedContext) {
    reason = REASONS.ISSUED_CONTEXT_MISSING;
  } else if (idp === "stub") {
    reason = REASONS.STUB_LOGIN_NOT_LIVE;
  } else if (!["live", "keycloak"].includes(idp)) {
    reason = REASONS.LIVE_IDP_ABSENT;
  } else if (!chromePresent) {
    reason = REASONS.CHROME_MISSING;
  } else if (!observed || !allTypes) {
    reason = REASONS.WITHDRAW_NOT_OBSERVED;
  }

  if (reason) {
    return {
      result: RESULTS.SKIP,
      reason,
      liveJar,
      issuedContext,
      idp,
      chromePresent,
      withdrawClickObserved: false,
      typesWalked,
      stubWalkIsNotLive: true,
      g7Close: "NOT_CLAIMED",
    };
  }

  return {
    result: RESULTS.PASS,
    reason: "",
    liveJar: true,
    issuedContext: true,
    idp,
    chromePresent: true,
    withdrawClickObserved: true,
    typesWalked: ["leave", "expense", "travel"],
    stubWalkIsNotLive: true,
    g7Close: "NOT_CLAIMED",
  };
}

export function parseReport(text) {
  const values = {};
  for (const raw of String(text || "").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const idx = line.indexOf("=");
    values[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return values;
}

const REQUIRED_KEYS = Object.freeze([
  "schema_version",
  "timestamp_utc",
  "java_feature",
  "boot_parent_version",
  "boot_parent_match",
  "docker_used",
  "stub_walk_is_not_live",
  "vue_reachability_is_not_live_jar",
  "live_jar",
  "issued_context",
  "idp",
  "chrome_present",
  "withdraw_click_observed",
  "live_jar_idp_withdraw",
  "live_jar_idp_skip_reason",
  "g7_close",
  "g18_status",
  "audit_port_changed",
  "p0_m0_redone",
  "g7_103_redone",
  "g7_113_redone",
  "g7_140_redone",
  "g7_159_redone",
  "leftover_rows_kept",
]);

function requireValue(report, key, expected) {
  if (report[key] !== expected) {
    throw new Error(`${key} must be ${expected}, got ${report[key]}`);
  }
}

export function verifyHonestyReport(report) {
  if (!report || typeof report !== "object") {
    throw new Error("empty report");
  }
  for (const key of REQUIRED_KEYS) {
    if (report[key] === undefined || report[key] === "") {
      if (key === "live_jar_idp_skip_reason" && report.live_jar_idp_withdraw === "PASS") {
        continue;
      }
      throw new Error(`missing key ${key}`);
    }
  }
  requireValue(report, "java_feature", "21");
  requireValue(report, "boot_parent_version", "4.1.1");
  requireValue(report, "boot_parent_match", "PASS");
  requireValue(report, "docker_used", "false");
  requireValue(report, "g18_status", "closed_unchanged");
  requireValue(report, "audit_port_changed", "false");
  requireValue(report, "p0_m0_redone", "false");
  requireValue(report, "g7_103_redone", "false");
  requireValue(report, "g7_113_redone", "false");
  requireValue(report, "g7_140_redone", "false");
  requireValue(report, "g7_159_redone", "false");
  requireValue(report, "g7_close", "NOT_CLAIMED");
  requireValue(report, "stub_walk_is_not_live", "true");
  requireValue(report, "vue_reachability_is_not_live_jar", "true");

  const kept = String(report.leftover_rows_kept || "");
  for (const row of BOTH_LEFTOVER_ROWS) {
    if (!kept.includes(row)) {
      throw new Error(`leftover_rows_kept must include ${row}`);
    }
  }

  const verdict = report.live_jar_idp_withdraw;
  if (!["PASS", "SKIP", "FAIL"].includes(verdict)) {
    throw new Error("live_jar_idp_withdraw must be PASS/SKIP/FAIL");
  }
  if (verdict === "SKIP" && !report.live_jar_idp_skip_reason) {
    throw new Error("live_jar_idp_withdraw=SKIP needs live_jar_idp_skip_reason");
  }
  if (verdict === "PASS") {
    if (report.live_jar !== "true") {
      throw new Error("live_jar_idp_withdraw=PASS requires live_jar=true");
    }
    if (report.issued_context !== "true") {
      throw new Error("live_jar_idp_withdraw=PASS requires issued_context=true");
    }
    if (!["live", "keycloak"].includes(report.idp)) {
      throw new Error("live_jar_idp_withdraw=PASS requires live/keycloak IdP");
    }
    if (report.chrome_present !== "true") {
      throw new Error("live_jar_idp_withdraw=PASS requires chrome_present=true");
    }
    if (report.withdraw_click_observed !== "true") {
      throw new Error("live_jar_idp_withdraw=PASS requires withdraw_click_observed=true");
    }
  } else if (report.withdraw_click_observed === "true") {
    throw new Error("withdraw_click_observed cannot be true unless live walk PASS");
  }

  const body = Object.entries(report)
    .map(([key, value]) => `${key}=${value}`)
    .join("\n");
  if (/(PASSWORD|passwd|secret)=/i.test(body)) {
    throw new Error("report must not contain password/secret material");
  }
  return { ok: true };
}

export function honestSkipTemplate(evaluation) {
  const evaluated = evaluation || evaluateLiveJarIdpWithdraw();
  return {
    schema_version: "1",
    timestamp_utc: "",
    java_feature: "21",
    boot_parent_version: "4.1.1",
    boot_parent_match: "PASS",
    docker_used: "false",
    stub_walk_is_not_live: "true",
    vue_reachability_is_not_live_jar: "true",
    live_jar: String(evaluated.liveJar === true),
    issued_context: String(evaluated.issuedContext === true),
    idp: evaluated.idp || "absent",
    chrome_present: String(evaluated.chromePresent === true),
    withdraw_click_observed: "false",
    live_jar_idp_withdraw: RESULTS.SKIP,
    live_jar_idp_skip_reason: evaluated.reason || REASONS.LIVE_JAR_UNHEALTHY,
    g7_close: "NOT_CLAIMED",
    g18_status: "closed_unchanged",
    audit_port_changed: "false",
    p0_m0_redone: "false",
    g7_103_redone: "false",
    g7_113_redone: "false",
    g7_140_redone: "false",
    g7_159_redone: "false",
    leftover_rows_kept: BOTH_LEFTOVER_ROWS.join(","),
  };
}
