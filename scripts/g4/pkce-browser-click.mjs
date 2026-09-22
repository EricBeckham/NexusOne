#!/usr/bin/env node
/**
 * Playwright walk of desktop Authorization Code + PKCE leftover.
 * Clicks data-testid=pkce-login, stub IdP Sign In, then permission-grant,
 * and asserts LOGIN + PERMISSION on the AuditPort-shaped hash chain.
 * System Chrome only (no Playwright browser download, no Docker).
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const args = parseArgs(process.argv.slice(2));
const baseUrl = args.base || process.env.NEXUS_WEB_ORIGIN || "http://127.0.0.1:4174";
const stubUrl = args.stub || process.env.NEXUS_G4_STUB_ORIGIN || "http://127.0.0.1:18089";
const jsonOut = args["json-out"] || join(root, "docs/g4-pkce-browser-click/walk.json");
const shotDir = args["shot-dir"] || join(root, "docs/g4-pkce-browser-click");
const TRACE = process.env.NEXUS_G4_PKCE_TRACE || "0199a1c0-0000-7000-8000-00000000c219";

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
    audit_port: "untouched same-TX; stub uses CanonicalAudit SHA-256 + AuditCaptures types",
    g18: "stays closed",
    g4_product_close: false,
    http_already_proven: [
      "docs/g4-pkce-login-audit.md",
      "docs/g4-pkce-https-browser.md",
    ],
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

async function stubState() {
  const res = await fetch(`${stubUrl}/__g4/state`);
  if (!res.ok) {
    throw new Error(`stub state HTTP ${res.status}`);
  }
  return res.json();
}

async function walk() {
  const playwright = loadPlaywright();
  if (!playwright?.chromium) {
    skip("playwright-core is not installed (run scripts/g4/run-pkce-browser-click.sh)");
  }
  const chrome = findChrome();
  if (!chrome) {
    skip("system Chrome/Chromium not found; HTTP PKCE already proven on main");
  }

  const { chromium } = playwright;
  mkdirSync(shotDir, { recursive: true });
  const browser = await chromium.launch({
    executablePath: chrome,
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
  });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.setDefaultTimeout(20_000);

  try {
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    await page.locator('[data-testid="pkce-login"]').waitFor();
    await page.screenshot({ path: join(shotDir, "01-pkce-login.png"), fullPage: true });
    await page.locator('[data-testid="pkce-login"]').click();

    await page.locator('[data-testid="idp-login-form"]').waitFor();
    await page.locator('[data-testid="idp-username"]').fill("g4-pkce@example.com");
    await page.locator('[data-testid="idp-password"]').fill("password");
    await page.screenshot({ path: join(shotDir, "02-idp-login.png"), fullPage: true });
    await page.locator('[data-testid="idp-login"]').click();

    await page.waitForURL((url) => url.pathname === "/p2/permissions/datascope", { timeout: 20_000 });
    await page.locator('[data-testid="permission-grant"]').waitFor();
    await page.screenshot({ path: join(shotDir, "03-permission.png"), fullPage: true });
    await page.locator('[data-testid="permission-grant"]').click();
    await page.locator("[data-testid=g2-scope-table] tbody tr").first().waitFor();
    await page.screenshot({ path: join(shotDir, "04-granted.png"), fullPage: true });

    await page.goto(`${baseUrl}/p3/audit?trace=${TRACE}`, { waitUntil: "networkidle" });
    await page.locator('[data-testid="audit-table"]').waitFor();
    const table = await page.locator('[data-testid="audit-table"]').innerText();
    if (!table.includes("p2.auth.login.accepted")) {
      throw new Error(`audit table missing LOGIN: ${table}`);
    }
    if (!table.includes("p2.permission.granted")) {
      throw new Error(`audit table missing PERMISSION: ${table}`);
    }
    await page.screenshot({ path: join(shotDir, "05-audit-chain.png"), fullPage: true });

    const remote = await stubState();
    if (remote.pkce_validated < 1) {
      throw new Error("stub did not validate PKCE S256");
    }
    if (remote.rejected_tenant_headers > 0) {
      throw new Error("UI sent a tenant identity header");
    }
    if (!remote.verify_chain?.intact) {
      throw new Error(`hash chain broken: ${remote.verify_chain?.failure}`);
    }
    const types = remote.event_types || [];
    if (!types.includes("p2.auth.login.accepted") || !types.includes("p2.permission.granted")) {
      throw new Error(`missing LOGIN/PERMISSION on chain: ${types.join(",")}`);
    }

    const body = {
      recorded_at: new Date().toISOString(),
      result: "PASS",
      command: "./scripts/g4/run-pkce-browser-click.sh",
      docker: false,
      chrome,
      playwright: "playwright-core + system Chrome",
      base_url: baseUrl,
      stub_url: stubUrl,
      control: "data-testid=pkce-login",
      idp_control: "data-testid=idp-login",
      permission_control: "data-testid=permission-grant",
      trace_id: TRACE,
      event_types: types,
      verify_chain: remote.verify_chain,
      pkce_validated: remote.pkce_validated,
      stub: remote,
      screenshots: [
        "docs/g4-pkce-browser-click/01-pkce-login.png",
        "docs/g4-pkce-browser-click/02-idp-login.png",
        "docs/g4-pkce-browser-click/03-permission.png",
        "docs/g4-pkce-browser-click/04-granted.png",
        "docs/g4-pkce-browser-click/05-audit-chain.png",
      ],
      chrome_path: chrome,
      audit_port: "same AuditCaptures types + CanonicalAudit SHA-256 (InMemoryHashChainedAuditPort rules); product append/appendNewTransaction untouched",
      g18: "stays closed",
      g4_product_close: false,
      not_claimed: [
        "product G4 close",
        "human laptop + live Keycloak CA trust",
        "SPA-generated verifier",
        "H5 /m/* PKCE",
        "AuditPort same-TX redesign",
        "G18 reopen",
      ],
      http_already_proven: [
        "docs/g4-pkce-login-audit.md",
        "docs/g4-pkce-https-browser.md",
      ],
    };
    mkdirSync(dirname(jsonOut), { recursive: true });
    writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
    console.log("PASS: desktop PKCE click landed LOGIN+PERMISSION on hash chain");
    console.log(`trace=${TRACE} events=${types.join(",")}`);
    console.log(`wrote ${jsonOut}`);
  } finally {
    await browser.close();
  }
}

walk().catch((err) => {
  console.error(err);
  process.exit(1);
});
