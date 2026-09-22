#!/usr/bin/env node
/**
 * Playwright walk of H5 Authorization Code + PKCE on /m/login.
 * Uses system Chrome (no Playwright browser download, no Docker).
 * Does not claim product G15. Does not walk native APP / WeChat.
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const args = parseArgs(process.argv.slice(2));
const baseUrl = args.base || process.env.NEXUS_WEB_ORIGIN || "http://127.0.0.1:4173";
const stubUrl = args.stub || process.env.NEXUS_G15_STUB_ORIGIN || "http://127.0.0.1:18089";
const jsonOut = args["json-out"] || join(root, "docs/g15-h5-pkce-browser/walk.json");
const shotDir = args["shot-dir"] || join(root, "docs/g15-h5-pkce-browser");

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
    g15_product_close: false,
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
  const res = await fetch(`${stubUrl}/__g15/state`);
  if (!res.ok) {
    throw new Error(`stub state HTTP ${res.status}`);
  }
  return res.json();
}

function challengeS256(verifier) {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

async function walk() {
  const playwright = loadPlaywright();
  if (!playwright?.chromium) {
    skip("playwright-core is not installed (run scripts/g15/run-h5-pkce-browser.sh)");
  }
  const chrome = findChrome();
  if (!chrome) {
    skip("system Chrome/Chromium not found; H5 PKCE wiring still asserted by npm");
  }

  const { chromium } = playwright;
  mkdirSync(shotDir, { recursive: true });
  const browser = await chromium.launch({
    executablePath: chrome,
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"],
  });
  const page = await browser.newPage({
    viewport: { width: 375, height: 812 },
    userAgent:
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
  });
  page.setDefaultTimeout(20_000);

  try {
    await page.goto(`${baseUrl}/m/p3/files`, { waitUntil: "networkidle" });
    await page.waitForURL((url) => url.pathname === "/m/login");
    if (!page.url().includes("return=")) {
      throw new Error(`logged-out /m/p3/files did not keep return: ${page.url()}`);
    }
    await page.locator('[data-testid="mobile-pkce-login"]').waitFor();
    await page.screenshot({ path: join(shotDir, "01-login-pkce.png"), fullPage: true });
    await page.locator('[data-testid="mobile-pkce-login"]').click();
    await page.waitForURL((url) => url.pathname === "/m/p3/files" || url.pathname === "/m/p3/home", {
      timeout: 15_000,
    });
    const landed = new URL(page.url());
    if (landed.searchParams.has("code") || landed.searchParams.has("code_verifier") || landed.searchParams.has("access_token")) {
      throw new Error(`secrets leaked into landed URL: ${page.url()}`);
    }
    if (landed.searchParams.has("tenant_id")) {
      throw new Error("tenant_id leaked into URL");
    }
    await page.waitForTimeout(200);
    await page.screenshot({ path: join(shotDir, "02-landed.png"), fullPage: true });
    const stored = await page.evaluate(() => {
      try {
        return JSON.parse(sessionStorage.getItem("nexus.m012.session") || "{}");
      } catch {
        return {};
      }
    });
    if (stored.loginMethod !== "pkce") {
      throw new Error(`session.loginMethod=${stored.loginMethod || ""}`);
    }
    if (!stored.contextId) {
      throw new Error("PKCE session missing contextId");
    }
    if (stored.accessToken) {
      throw new Error("PKCE leftover must not write an access token into the SPA");
    }
    const method = stored.loginMethod;
    const context = stored.contextId;
    const remote = await stubState();
    const pkceLogin = remote.logins.find((row) => row.kind === "pkce");
    if (!pkceLogin) {
      throw new Error("stub did not record a PKCE login");
    }
    if (!remote.authorizes.some((row) => row.client_held && !row.returned_verifier)) {
      throw new Error("stub did not see a client-held challenge without BFF verifier");
    }

    const mismatch = await fetch(`${stubUrl}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        code: "h5-pkce-code",
        redirect_uri: `${baseUrl}/m/callback`,
        code_verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
      }),
    });
    if (mismatch.status !== 400) {
      throw new Error(`wrong verifier should be 400, got ${mismatch.status}`);
    }
    const mismatchBody = await mismatch.json();
    if (mismatchBody.code !== "P2.IAM.PKCE") {
      throw new Error(`wrong verifier code ${mismatchBody.code}`);
    }

    await page.goto(`${baseUrl}/p3/ledgers`, { waitUntil: "networkidle" });
    await page.screenshot({ path: join(shotDir, "03-desktop-untouched.png"), fullPage: true });
    const desktop = page.url();

    const body = {
      recorded_at: new Date().toISOString(),
      result: "PASS",
      command: "./scripts/g15/run-h5-pkce-browser.sh",
      docker: false,
      chrome,
      playwright: "playwright-core + system Chrome",
      viewport: "375x812",
      base_url: baseUrl,
      stub_url: stubUrl,
      control: "data-testid=mobile-pkce-login",
      landed: landed.toString(),
      desktop_url: desktop,
      login_method: method,
      context,
      rfc7636_vector: challengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
      stub: remote,
      wrong_verifier: { status: mismatch.status, code: mismatchBody.code },
      screenshots: [
        "docs/g15-h5-pkce-browser/01-login-pkce.png",
        "docs/g15-h5-pkce-browser/02-landed.png",
        "docs/g15-h5-pkce-browser/03-desktop-untouched.png",
      ],
      audit_port: "untouched",
      g18: "stays closed",
      g15_product_close: false,
      not_claimed: [
        "product G15 close",
        "native APP WebView / iOS / Android package",
        "WeChat / DingTalk production",
        "live Keycloak human laptop click on the G4 hash chain",
        "AuditPort rewrite",
      ],
      builds_on: [
        "docs/g4-pkce-login-audit.md",
        "docs/g4-pkce-https-browser.md",
      ],
    };
    mkdirSync(dirname(jsonOut), { recursive: true });
    writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
    console.log("PASS: H5 /m/* Authorization Code + PKCE after stub authorize-url");
    console.log(`landed=${body.landed}`);
    console.log(`wrote ${jsonOut}`);
  } finally {
    await browser.close();
  }
}

walk().catch((err) => {
  console.error(err);
  process.exit(1);
});
