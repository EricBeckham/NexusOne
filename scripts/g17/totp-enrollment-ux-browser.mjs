#!/usr/bin/env node
/**
 * Playwright walk of TOTP enrollment UX leftover.
 * System Chrome, no Playwright browser download, no Docker.
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { generate } from "../../web/src/auth/totp.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const args = parseArgs(process.argv.slice(2));
const baseUrl = args.base || process.env.NEXUS_WEB_ORIGIN || "http://127.0.0.1:4175";
const stubUrl = args.stub || process.env.NEXUS_G17_STUB_ORIGIN || "http://127.0.0.1:18091";
const jsonOut = args["json-out"] || join(root, "docs/g17-totp-enrollment-ux/walk.json");
const shotDir = args["shot-dir"] || join(root, "docs/g17-totp-enrollment-ux");

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
    dengbao_claimed: false,
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
    skip("playwright-core is not installed (run scripts/g17/run-totp-enrollment-ux.sh)");
  }
  const chrome = findChrome();
  if (!chrome) {
    skip("system Chrome/Chromium not found; Vitest enrollment paths already cover copy/errors");
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
    await shot("01-mfa-required");

    await page.locator("[data-testid=goto-totp-enroll]").click();
    await page.locator("[data-testid=totp-start]").waitFor();
    await page.locator("[data-testid=enroll-mfa-reason]").waitFor();
    await page.locator("[data-testid=totp-start]").click();
    const grouped = (await page.locator("[data-testid=totp-secret]").innerText()).trim();
    await shot("02-enroll-secret");

    await page.locator("[data-testid=totp-code]").fill("12");
    await page.locator("[data-testid=totp-confirm]").click();
    const formatError = (await page.locator("[data-testid=totp-error]").innerText()).trim();
    await shot("03-format-error");

    await page.locator("[data-testid=totp-code]").fill("000000");
    await page.locator("[data-testid=totp-confirm]").click();
    const wrongError = (await page.locator("[data-testid=totp-error]").innerText()).trim();
    await shot("04-wrong-code");

    const compact = grouped.replace(/\s+/g, "");
    await page.locator("[data-testid=totp-code]").fill(generate(compact));
    await page.locator("[data-testid=totp-confirm]").click();
    const success = (await page.locator("[data-testid=totp-success]").innerText()).trim();
    await shot("05-success");

    await page.locator("[data-testid=totp-code]").fill(generate(compact));
    await page.locator("[data-testid=totp-confirm]").click();
    const already = (await page.locator("[data-testid=totp-error]").innerText()).trim();
    await shot("06-already-enrolled");

    const stub = await (await fetch(`${stubUrl}/__g17/state`)).json();
    const body = {
      recorded_at: new Date().toISOString(),
      result: "PASS",
      docker: false,
      audit_port: "untouched",
      g18: "stays closed",
      g17_product_close: false,
      dengbao_claimed: false,
      stub_login_present: true,
      pkce_login_present: true,
      mfa_required: true,
      format_error: formatError,
      wrong_code_error: wrongError,
      success,
      already_enrolled: already,
      stub,
      screenshots: shots,
    };
    mkdirSync(dirname(jsonOut), { recursive: true });
    writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
    console.log("PASS TOTP enrollment UX walk");
    console.log(JSON.stringify({ formatError, wrongError, success, already, stub }, null, 2));
  } finally {
    await browser.close();
  }
}

walk().catch((err) => {
  console.error(err);
  process.exit(1);
});
