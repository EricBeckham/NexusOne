#!/usr/bin/env node
/**
 * Playwright walk of recovery-code + email MFA leftover pages.
 * Reuses the #133 stub login API. System Chrome, no Docker, no Keycloak write.
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const args = parseArgs(process.argv.slice(2));
const baseUrl = args.base || process.env.NEXUS_WEB_ORIGIN || "http://127.0.0.1:4176";
const stubUrl = args.stub || process.env.NEXUS_G17_STUB_ORIGIN || "http://127.0.0.1:18092";
const jsonOut = args["json-out"] || join(root, "docs/g17-admin-mfa-recovery-email/browser.json");
const shotDir = args["shot-dir"] || join(root, "docs/g17-admin-mfa-recovery-email");

const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  "/usr/bin/google-chrome-stable",
  "/usr/local/bin/google-chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
  "/usr/bin/chromium-browser",
].filter(Boolean);

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) {
      continue;
    }
    const key = token.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      out[key] = true;
      continue;
    }
    out[key] = next;
    i += 1;
  }
  return out;
}

function skip(reason, extra = {}) {
  const body = {
    recorded_at: new Date().toISOString(),
    result: "SKIP",
    reason,
    docker: false,
    audit_port: "untouched",
    g18: "stays closed",
    g17_product_close: false,
    vue_writes_keycloak_otp: false,
    ...extra,
  };
  mkdirSync(dirname(jsonOut), { recursive: true });
  writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
  console.log(`SKIP: ${reason}`);
  console.log(`wrote ${jsonOut}`);
  process.exit(0);
}

function loadPlaywright() {
  const require = createRequire(import.meta.url);
  const candidates = [
    join(dirname(fileURLToPath(import.meta.url)), "node_modules/playwright-core"),
    join(root, "web/node_modules/playwright-core"),
  ];
  for (const file of candidates) {
    try {
      return require(file);
    } catch {
      // try next
    }
  }
  return null;
}

function findChrome() {
  for (const candidate of CHROME_CANDIDATES) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return "";
}

async function walk() {
  const playwright = loadPlaywright();
  if (!playwright?.chromium) {
    skip("playwright-core is not installed");
  }
  const chrome = findChrome();
  if (!chrome) {
    skip("system Chrome/Chromium not found; Vitest leftover already covers copy/errors");
  }

  const { chromium } = playwright;
  mkdirSync(shotDir, { recursive: true });
  const browser = await chromium.launch({
    executablePath: chrome,
    headless: true,
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const shots = [];

  async function shot(name) {
    const file = join(shotDir, `${name}.png`);
    await page.screenshot({ path: file, fullPage: true });
    shots.push(file);
  }

  try {
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    if (!(await page.locator("[data-testid=stub-login]").count())) {
      throw new Error("stub-login missing — G18 path must stay");
    }
    if (!(await page.locator("[data-testid=pkce-login]").count())) {
      throw new Error("pkce-login missing — desktop PKCE leftover must stay");
    }
    await page.locator("[data-testid=admin-password-login]").click();
    await page.locator("[data-testid=mfa-required-panel]").waitFor();
    if (!(await page.locator("[data-testid=goto-totp-enroll]").count())) {
      throw new Error("goto-totp-enroll missing — #133 leftover must stay");
    }
    await shot("01-mfa-required-siblings");

    await page.goto(`${baseUrl}/p2/mfa/recovery`, { waitUntil: "networkidle" });
    await page.locator("[data-testid=recovery-issue]").click();
    await page.locator("[data-testid=recovery-list]").waitFor();
    const list = (await page.locator("[data-testid=recovery-list]").innerText()).trim();
    const first = list.split(/\s+/).find((item) => /^[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(item));
    await shot("02-recovery-issued");
    await page.locator("[data-testid=recovery-code]").fill("12");
    await page.locator("[data-testid=recovery-consume]").click();
    const formatError = (await page.locator("[data-testid=recovery-error]").innerText()).trim();
    await page.locator("[data-testid=recovery-code]").fill(first);
    await page.locator("[data-testid=recovery-consume]").click();
    const success = (await page.locator("[data-testid=recovery-success]").innerText()).trim();
    await shot("03-recovery-consumed");

    await page.goto(`${baseUrl}/p2/mfa/email`, { waitUntil: "networkidle" });
    await page.locator("[data-testid=email-mfa-request]").click();
    const skipText = (await page.locator("[data-testid=email-mfa-skip]").innerText()).trim();
    await shot("04-email-skip");
    if (!skipText.includes("P2.IAM.SMTP_DISABLED")) {
      throw new Error("email leftover must SKIP with SMTP_DISABLED");
    }

    const stub = await (await fetch(`${stubUrl}/__g17/state`)).json();
    const body = {
      recorded_at: new Date().toISOString(),
      result: "PASS",
      docker: false,
      audit_port: "untouched",
      g18: "stays closed",
      g17_product_close: false,
      vue_writes_keycloak_otp: false,
      stub_login_present: true,
      pkce_login_present: true,
      totp_enroll_link_present: true,
      format_error: formatError,
      recovery_success: success,
      email_skip: skipText,
      stub,
      screenshots: shots,
    };
    mkdirSync(dirname(jsonOut), { recursive: true });
    writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
    console.log("PASS recovery / email leftover walk");
    console.log(JSON.stringify({ formatError, success, skipText }, null, 2));
  } finally {
    await browser.close();
  }
}

walk().catch((err) => {
  console.error(err);
  process.exit(1);
});
