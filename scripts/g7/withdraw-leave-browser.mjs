#!/usr/bin/env node
/**
 * Playwright walk of Vue data-testid=todo-withdraw against stub login.
 * Uses system Chrome (no Playwright browser download, no Docker).
 * Leave is required. Expense/travel are optional extra clicks on the same control.
 */
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "../..");
const args = parseArgs(process.argv.slice(2));
const baseUrl = args.base || process.env.NEXUS_WEB_ORIGIN || "http://127.0.0.1:4173";
const stubUrl = args.stub || process.env.NEXUS_G7_STUB_ORIGIN || "http://127.0.0.1:18087";
const jsonOut = args["json-out"] || join(root, "docs/g7-withdraw-browser/walk.json");
const shotDir = args["shot-dir"] || join(root, "docs/g7-withdraw-browser");
const types = (args.types || "leave,expense,travel")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);

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
    g7_product_close: false,
    http_already_proven: [
      "docs/g7-withdraw-evidence.md",
      "docs/g7-withdraw-expense-travel.md",
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
  const res = await fetch(`${stubUrl}/__g7/state`);
  if (!res.ok) {
    throw new Error(`stub state HTTP ${res.status}`);
  }
  return res.json();
}

async function walk() {
  const playwright = loadPlaywright();
  if (!playwright?.chromium) {
    skip("playwright-core is not installed (run scripts/g7/run-withdraw-browser.sh)");
  }
  const chrome = findChrome();
  if (!chrome) {
    skip("system Chrome/Chromium not found; HTTP withdraw already proven on main");
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
  const clicks = [];

  try {
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    await page.locator('[data-testid="stub-login"]').waitFor();
    await page.locator('input[type="email"]').fill("g7-ui@example.com");
    await page.locator("form.nx-card input[required]:not([type=email])").fill("G7 UI Withdraw");
    await page.screenshot({ path: join(shotDir, "01-stub-login.png"), fullPage: true });
    await Promise.all([
      page.waitForURL((url) => url.pathname === "/" || url.pathname === "/p3/todos", { timeout: 15_000 }).catch(() => {}),
      page.locator('[data-testid="stub-login"]').click(),
    ]);
    await page.waitForTimeout(400);
    if (!page.url().includes("/p3/todos")) {
      await page.goto(`${baseUrl}/p3/todos?box=initiated`, { waitUntil: "networkidle" });
    } else {
      await page.goto(`${baseUrl}/p3/todos?box=initiated`, { waitUntil: "networkidle" });
    }
    await page.locator('[data-testid="todo-table"]').waitFor();
    await page.screenshot({ path: join(shotDir, "02-initiated.png"), fullPage: true });

    let capturedControl = false;
    for (const type of types) {
      const label = type === "leave" ? "请假审批" : type === "expense" ? "报销审批" : "出差审批";
      const row = page.locator("[data-testid=todo-table] tbody tr").filter({ hasText: label }).first();
      await row.click();
      const button = page.locator('[data-testid="todo-withdraw"]');
      await button.waitFor({ state: "visible" });
      if (!capturedControl) {
        await page.screenshot({ path: join(shotDir, "02b-todo-withdraw.png"), fullPage: true });
        capturedControl = true;
      }
      await button.click();
      await page.locator(".nx-muted", { hasText: "已 withdraw" }).waitFor();
      const notice = await page.locator(".nx-muted", { hasText: "已 withdraw" }).innerText();
      if (!/WITHDRAWN/i.test(notice)) {
        throw new Error(`${type} notice did not show WITHDRAWN: ${notice}`);
      }
      await button.waitFor({ state: "hidden" });
      clicks.push({
        type,
        control: "data-testid=todo-withdraw",
        notice,
        withdrawn: true,
      });
    }

    await page.screenshot({ path: join(shotDir, "03-withdrawn.png"), fullPage: true });
    const remote = await stubState();
    const required = types.includes("leave") ? ["leave"] : types;
    for (const type of required) {
      const hit = remote.withdraws.find((row) => row.type === type);
      if (!hit) {
        throw new Error(`stub did not record ${type} withdraw`);
      }
      if (hit.x_tenant_id) {
        throw new Error("UI sent X-Tenant-Id");
      }
      if (!hit.context_id) {
        throw new Error("UI did not send X-Nexus-Context-Id");
      }
    }

    const body = {
      recorded_at: new Date().toISOString(),
      result: "PASS",
      command: "./scripts/g7/run-withdraw-browser.sh",
      docker: false,
      chrome,
      playwright: "playwright-core + system Chrome",
      base_url: baseUrl,
      stub_url: stubUrl,
      control: "data-testid=todo-withdraw",
      stub_login: "data-testid=stub-login",
      types_walked: clicks.map((item) => item.type),
      leave_ui_withdraw: clicks.some((item) => item.type === "leave" && item.withdrawn),
      expense_ui_withdraw: clicks.some((item) => item.type === "expense" && item.withdrawn),
      travel_ui_withdraw: clicks.some((item) => item.type === "travel" && item.withdrawn),
      clicks,
      stub: remote,
      screenshots: [
        "docs/g7-withdraw-browser/01-stub-login.png",
        "docs/g7-withdraw-browser/02-initiated.png",
        "docs/g7-withdraw-browser/02b-todo-withdraw.png",
        "docs/g7-withdraw-browser/03-withdrawn.png",
      ],
      chrome_path: chrome,
      audit_port: "untouched",
      g18: "stays closed",
      g7_product_close: false,
      not_claimed: [
        "G7 three-type product close",
        "G18 browser reopen",
        "live product JAR withdraw click",
        "AuditPort rewrite",
      ],
      http_already_proven: [
        "docs/g7-withdraw-evidence.md",
        "docs/g7-withdraw-expense-travel.md",
      ],
    };
    mkdirSync(dirname(jsonOut), { recursive: true });
    writeFileSync(jsonOut, `${JSON.stringify(body, null, 2)}\n`);
    console.log("PASS: Vue todo-withdraw clicked after stub login");
    console.log(`types=${body.types_walked.join(",")}`);
    console.log(`wrote ${jsonOut}`);
  } finally {
    await browser.close();
  }
}

walk().catch((err) => {
  console.error(err);
  process.exit(1);
});
