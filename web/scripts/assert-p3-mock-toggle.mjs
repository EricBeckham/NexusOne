#!/usr/bin/env node
/**
 * Guard: P3 UI keeps a MOCK adapter that is easy to swap, and never claims G18.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const files = {
  facade: readFileSync(join(root, "src/api/p3.js"), "utf8"),
  live: readFileSync(join(root, "src/api/p3-live.js"), "utf8"),
  mock: readFileSync(join(root, "src/api/mocks/p3-mock.js"), "utf8"),
  readme: readFileSync(join(root, "src/api/mocks/README.md"), "utf8"),
  webReadme: readFileSync(join(root, "README.md"), "utf8"),
};

const failures = [];

if (!files.facade.includes("VITE_NEXUS_P3_USE_MOCK")) {
  failures.push("p3.js must document VITE_NEXUS_P3_USE_MOCK");
}
if (!files.facade.includes("useP3Mock") || !files.facade.includes("p3-live") || !files.facade.includes("p3-mock")) {
  failures.push("p3.js must switch between live and mock adapters");
}
if (!files.mock.includes("MOCK")) {
  failures.push("p3-mock.js must carry a MOCK marker");
}
if (!files.mock.includes("_source: \"MOCK\"") && !files.mock.includes('_source: "MOCK"')) {
  failures.push("mock payloads must set _source MOCK");
}
if (!files.mock.includes("-mock-")) {
  failures.push("mock entity ids must include the mock UUID group");
}
if (!files.live.includes("/api/v1/p3/ledgers") || !files.live.includes("/api/v1/p3/forms") || !files.live.includes("/api/v1/p3/todos")) {
  failures.push("p3-live.js must call the three P3 placeholder routes");
}
if (files.live.includes("X-Tenant-Id") || files.mock.includes("X-Tenant-Id")) {
  failures.push("P3 adapters must not send X-Tenant-Id");
}
if (!files.readme.includes("VITE_NEXUS_P3_USE_MOCK") || !files.webReadme.includes("VITE_NEXUS_P3_USE_MOCK")) {
  failures.push("README must explain how to toggle mocks");
}
if (!files.webReadme.includes("G18") || files.webReadme.includes("G18 已关闭") || files.webReadme.includes("closes G18")) {
  failures.push("web README must mention G18 without claiming it closed");
}

if (failures.length) {
  console.error("FAIL P3 mock toggle");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log("PASS: P3 MOCK adapter is marked, documented, and swappable");
