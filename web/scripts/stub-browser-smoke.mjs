#!/usr/bin/env node
/**
 * DL-C3 local stub browser smoke.
 *
 * Enable: set NEXUS_STUB_APP_URL to the local stub login page (for example
 * http://127.0.0.1:5173/login/dev) while Chrome or Edge is installed.
 * Optional browser override: NEXUS_STUB_BROWSER_PATH.
 *
 * Skip exit 0 when NEXUS_STUB_BROWSER_SMOKE_SKIP=1, when no browser binary is
 * found, or when NEXUS_STUB_APP_URL is unset. This variable is not
 * NEXUS_DEMO_KEYCLOAK_SMOKE_URL (DL-X4 / DPO-X3).
 *
 * Logs contain host and status only. A missing local server is a failure, not a pass.
 * npm script: smoke:stub-browser
 */
import { existsSync } from "node:fs";
import { spawn } from "node:child_process";
import { smokeDecision, smokeLogLine } from "../src/demo/stubBrowserSmoke.js";

function findBrowser(env) {
  const override = String(env.NEXUS_STUB_BROWSER_PATH || "").trim();
  const candidates = [
    override,
    env.PROGRAMFILES && `${env.PROGRAMFILES}\\Google\\Chrome\\Application\\chrome.exe`,
    env["PROGRAMFILES(X86)"] && `${env["PROGRAMFILES(X86)"]}\\Google\\Chrome\\Application\\chrome.exe`,
    env.LOCALAPPDATA && `${env.LOCALAPPDATA}\\Google\\Chrome\\Application\\chrome.exe`,
    env.PROGRAMFILES && `${env.PROGRAMFILES}\\Microsoft\\Edge\\Application\\msedge.exe`,
    env["PROGRAMFILES(X86)"] && `${env["PROGRAMFILES(X86)"]}\\Microsoft\\Edge\\Application\\msedge.exe`,
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);
  return candidates.find((path) => existsSync(path)) || "";
}

function runHeadless(browser, url) {
  return new Promise((resolve) => {
    const child = spawn(browser, ["--headless=new", "--disable-gpu", "--dump-dom", url], {
      stdio: ["ignore", "ignore", "ignore"],
      windowsHide: true,
    });
    const timer = setTimeout(() => {
      child.kill();
      resolve(124);
    }, 20000);
    child.on("exit", (code) => {
      clearTimeout(timer);
      resolve(code ?? 1);
    });
    child.on("error", () => {
      clearTimeout(timer);
      resolve(127);
    });
  });
}

const decision = smokeDecision({
  skip: process.env.NEXUS_STUB_BROWSER_SMOKE_SKIP,
  appUrl: process.env.NEXUS_STUB_APP_URL,
  browserPath: findBrowser(process.env),
});

if (decision.action === "skip") {
  console.log(smokeLogLine({ status: "skipped", host: "-", detail: decision.reason }));
  process.exit(0);
}

if (decision.action === "fail") {
  console.log(smokeLogLine({ status: "failed", host: decision.host, detail: decision.reason }));
  process.exit(1);
}

let httpStatus = "unreachable";
try {
  const response = await fetch(decision.url, { redirect: "manual" });
  httpStatus = String(response.status);
} catch {
  console.log(smokeLogLine({ status: "failed", host: decision.host, detail: "unreachable" }));
  process.exit(1);
}

if (Number(httpStatus) >= 500) {
  console.log(smokeLogLine({ status: "failed", host: decision.host, detail: `http=${httpStatus}` }));
  process.exit(1);
}

const browserExit = await runHeadless(findBrowser(process.env), decision.url);
if (browserExit !== 0) {
  console.log(smokeLogLine({ status: "failed", host: decision.host, detail: `http=${httpStatus} browser_exit=${browserExit}` }));
  process.exit(1);
}

console.log(smokeLogLine({ status: "ok", host: decision.host, detail: `http=${httpStatus}` }));
process.exit(0);
