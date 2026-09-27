/**
 * DL-C3: decide whether the local stub browser smoke runs, and keep logs free of tokens.
 * Skip variable is NEXUS_STUB_BROWSER_SMOKE_SKIP (not the Keycloak probe URL used by DL-X4 / DPO-X3).
 */

export function sanitizeSmokeLog(text) {
  return String(text ?? "")
    .replace(/Bearer\s+\S+/gi, "Bearer [redacted]")
    .replace(/(access_token|refresh_token|id_token|password|client_secret)[=:]\s*\S+/gi, "$1=[redacted]")
    .replace(/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, "[redacted]");
}

export function smokeDecision({ skip, appUrl, browserPath }) {
  if (String(skip || "").trim() === "1") {
    return { action: "skip", reason: "NEXUS_STUB_BROWSER_SMOKE_SKIP=1" };
  }
  if (!String(browserPath || "").trim()) {
    return { action: "skip", reason: "no browser binary" };
  }
  if (!String(appUrl || "").trim()) {
    return { action: "skip", reason: "NEXUS_STUB_APP_URL unset" };
  }
  let host = "unknown";
  try {
    host = new URL(String(appUrl).trim()).host || "unknown";
  } catch {
    return { action: "fail", reason: "NEXUS_STUB_APP_URL is not a URL", host };
  }
  return { action: "run", url: String(appUrl).trim(), host };
}

export function smokeLogLine({ status, host, detail }) {
  const line = `stub-browser-smoke ${status} host=${host || "unknown"}${detail ? ` ${detail}` : ""}`;
  return sanitizeSmokeLog(line);
}
