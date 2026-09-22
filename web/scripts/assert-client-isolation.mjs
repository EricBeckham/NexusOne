#!/usr/bin/env node
/**
 * Static guard: the Vue API client must never attach tenant identity headers.
 */
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const apiDir = join(root, "src/api");
const client = readdirSync(apiDir)
  .filter((name) => name.endsWith(".js"))
  .map((name) => readFileSync(join(apiDir, name), "utf8"))
  .join("\n");

const failures = [];
if (!client.includes("ARCH-024")) {
  failures.push("client.js must document ARCH-024");
}
if (!client.includes("FORBIDDEN_TENANT_HEADERS")) {
  failures.push("client.js must keep a denylist of tenant identity headers");
}
if (!client.includes('["x-tenant-id"') && !client.includes('"x-tenant-id"')) {
  failures.push("denylist must include x-tenant-id");
}
if (/headers\[["']X-Tenant-Id["']\]\s*=/.test(client) || /["']X-Tenant-Id["']\s*:/.test(client)) {
  failures.push("client must not assign X-Tenant-Id");
}
if (client.includes("tenant_id=") && client.includes("?")) {
  failures.push("do not pass tenant_id as a query identity");
}

if (failures.length) {
  console.error("FAIL UI client isolation");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: Vue API client refuses client-supplied tenant identity (ARCH-024)");
