#!/usr/bin/env node
/**
 * G2 leftover: H5 every-page SurfaceGuard coverage honesty.
 * States which /m/* pages are covered (#116) vs still open.
 * Does not claim product-wide G2. Does not redo #116/#150/#144/#137.
 * Sibling leftover rows in docs/README.md must stay (g2-ops-pg-dump).
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { COVERED_PAGES, G2_CLOSE, OPEN_PAGES, coveredPaths, openPaths } from "../src/mobile/permission/h5PageInventory.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (rel) => readFileSync(join(root, rel), "utf8");

const files = {
  router: read("src/router.js"),
  inventory: read("src/mobile/permission/h5PageInventory.js"),
  javaCatalog: read("../nexus-permission-core/src/main/java/one/nexus/permission/surface/H5PageSurfaceCatalog.java"),
  javaGuard: read("../nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/H5SurfaceGuard.java"),
  doc: read("../docs/g2-h5-every-page.md"),
  docsIndex: read("../docs/README.md"),
  claim: read("../docs/planning/团队领取任务清单_V1.1.md"),
  h5Permission: read("../docs/g2-h5-permission.md"),
};

const failures = [];

function pageFile(page) {
  if (page.path.startsWith("/m/p4/")) {
    return `src/mobile/products/p4-market/pages/${page.vue}`;
  }
  if (page.path.startsWith("/m/p5/")) {
    return `src/mobile/products/p5-connect/pages/${page.vue}`;
  }
  return `src/mobile/products/p3-studio/pages/${page.vue}`;
}

function requireIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (!text.includes(snippet)) {
      failures.push(`${name} must include ${snippet}`);
    }
  }
}

function forbidIncludes(name, text, snippets) {
  for (const snippet of snippets) {
    if (text.includes(snippet)) {
      failures.push(`${name} must not include ${snippet}`);
    }
  }
}

const routePaths = [...files.router.matchAll(/path:\s*"(\/m[^"]*)"/g)].map((match) => match[1]);
const uniqueRoutes = [...new Set(routePaths)];
const catalogPaths = [...coveredPaths(), ...openPaths()];

for (const path of uniqueRoutes) {
  if (!catalogPaths.includes(path)) {
    failures.push(`router /m path is unclassified: ${path}`);
  }
}
for (const path of catalogPaths) {
  if (!uniqueRoutes.includes(path)) {
    failures.push(`catalog path missing from router.js: ${path}`);
  }
}

for (const page of COVERED_PAGES) {
  const vue = read(pageFile(page));
  if (!vue.includes("useH5Surface") || !vue.includes("H5_SURFACES")) {
    failures.push(`${page.path} (${page.vue}) must keep useH5Surface / H5_SURFACES`);
  }
  if (!files.router.includes(page.vue)) {
    failures.push(`router must import ${page.vue} for ${page.path}`);
  }
}

for (const page of OPEN_PAGES) {
  if (!page.vue) {
    continue;
  }
  const vue = read(pageFile(page));
  if (vue.includes("useH5Surface") || vue.includes("H5_SURFACES")) {
    failures.push(`${page.path} is OPEN; reclassify before adding useH5Surface`);
  }
}

if (G2_CLOSE !== "NOT_CLAIMED") {
  failures.push("JS inventory G2_CLOSE must stay NOT_CLAIMED");
}

requireIncludes("Java catalog", files.javaCatalog, [
  "NOT_CLAIMED",
  "COVERED",
  "OPEN",
  "/m/p3/lists",
  "/m/p3/home",
  "/m/p3/files",
  "/m/p3/notifications",
  "Does not redo #116",
]);
requireIncludes("Java H5SurfaceGuard", files.javaGuard, [
  "SurfaceGuard.require",
  "requireListRead",
  "requireFormWrite",
  "requireTodoRead",
]);
requireIncludes("doc", files.doc, [
  "NOT_CLAIMED",
  "SurfaceGuard",
  "/m/p3/lists",
  "/m/p3/home",
  "/m/p3/files",
  "g2-ops-pg-dump",
  "不宣称产品",
  "G18 stays closed",
]);
requireIncludes("docs/README leftover rows", files.docsIndex, [
  "g2-h5-every-page",
  "g2-ops-pg-dump",
  "g2-h5-permission",
  "g2-todo-http-surface",
  "s0-secret-rotate-without-restart",
  "g1-fieldpolicy-mask-second-auth",
]);
requireIncludes("claim list", files.claim, [
  "g2-h5-every-page.md",
  "g2-ops-pg-dump.md",
  "不宣称产品 G2 关闭",
]);
forbidIncludes("doc", files.doc, ["closes product G2", "product G2 closed", "g2_close=CLOSED"]);
forbidIncludes("JS inventory", files.inventory, ["g2Close: \"CLOSED\"", "g2_close=CLOSED"]);
forbidIncludes("claim list", files.claim, ["产品 G2 已关闭", "完整 G2 关闭"]);

if (failures.length) {
  console.error("FAIL G2 H5 every-page honesty");
  for (const item of failures) {
    console.error(" -", item);
  }
  process.exit(1);
}
console.log(
  "PASS: H5 /m/* coverage honesty (covered list/detail/form/todo/query; open home/files/notify/login/host); product G2 leftover",
);
