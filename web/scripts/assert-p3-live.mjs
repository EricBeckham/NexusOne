#!/usr/bin/env node
/**
 * Guard: P3 UI talks to live M0-06/07/08 HTTP APIs. MOCK adapter is gone.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const files = {
  facade: readFileSync(join(root, "src/api/p3.js"), "utf8"),
  live: readFileSync(join(root, "src/api/p3-live.js"), "utf8"),
  client: readFileSync(join(root, "src/api/client.js"), "utf8"),
  webReadme: readFileSync(join(root, "README.md"), "utf8"),
  pkg: readFileSync(join(root, "package.json"), "utf8"),
};

const failures = [];

if (existsSync(join(root, "src/api/mocks/p3-mock.js"))) {
  failures.push("src/api/mocks/p3-mock.js must be removed");
}
if (files.facade.includes("p3-mock") || files.facade.includes("useP3Mock") || files.facade.includes("VITE_NEXUS_P3_USE_MOCK")) {
  failures.push("p3.js must not import MOCK or honor VITE_NEXUS_P3_USE_MOCK");
}
if (!files.live.includes("/api/v1/p3/ledgers") || !files.live.includes("/api/v1/p3/forms") || !files.live.includes("/api/v1/p3/todos")) {
  failures.push("p3-live.js must call the three P3 live list routes");
}
if (!files.live.includes("/api/v1/process/tasks/") || !files.live.includes("/api/v1/p3/forms/") || !files.live.includes("/submissions")) {
  failures.push("p3-live.js must call process approve/reject and form submissions");
}
if (/headers\[["']X-Tenant-Id["']\]\s*=/.test(files.live + files.facade) || /["']X-Tenant-Id["']\s*:/.test(files.live + files.facade)) {
  failures.push("P3 adapters must not send X-Tenant-Id");
}
if (!files.client.includes("/api/v1/permission")) {
  failures.push("client.js must call /api/v1/permission");
}
if (files.pkg.includes("assert:p3-mock")) {
  failures.push("package.json must retire assert:p3-mock");
}
if (files.webReadme.includes("src/api/mocks") || files.webReadme.includes("VITE_NEXUS_P3_USE_MOCK=true")) {
  failures.push("web README must not document MOCK as the default");
}
if (!files.webReadme.includes("G18") || files.webReadme.includes("G18 已关闭") || files.webReadme.includes("closes G18")) {
  failures.push("web README must mention G18 without claiming it closed");
}

if (failures.length) {
  console.error("FAIL P3 live wiring");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: P3 UI is live-only (no MOCK adapter) and permission uses /api/v1/permission");
