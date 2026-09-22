#!/usr/bin/env node
/**
 * Honesty gate for the G7 live-JAR / IdP browser withdraw leftover.
 * PASS is only legal with a live JAR + issued context + live/Keycloak IdP.
 * Product G7 close is never legal. G18 stays closed.
 */
import { readFileSync } from "node:fs";

const file = process.argv[2];
if (!file) {
  console.error("usage: verify-withdraw-live-report.mjs FILE");
  process.exit(2);
}

const body = JSON.parse(readFileSync(file, "utf8"));
const fail = (msg) => {
  console.error(`FAIL: ${msg}`);
  process.exit(1);
};

if (body.g7_product_close === true || body.g7_close === "CLOSED") {
  fail("must not claim product G7 close");
}
if (!/stays closed|closed_unchanged/i.test(String(body.g18 || ""))) {
  fail("g18 must stay closed");
}
if (body.audit_port && !/untouched|unchanged/i.test(String(body.audit_port))) {
  fail("audit_port must stay untouched");
}

if (body.result === "PASS") {
  if (body.live_jar !== true) {
    fail("PASS requires live_jar=true");
  }
  if (body.issued_context !== true) {
    fail("PASS requires issued_context=true");
  }
  if (body.chrome_present !== true) {
    fail("PASS requires chrome_present=true");
  }
  if (!["live", "keycloak"].includes(String(body.idp))) {
    fail("PASS requires live/keycloak IdP (stub is not a live walk)");
  }
  const walked = body.types_walked || [];
  for (const type of ["leave", "expense", "travel"]) {
    if (!walked.includes(type)) {
      fail(`PASS requires ${type} walked`);
    }
  }
} else if (body.result === "SKIP") {
  if (!body.reason) {
    fail("SKIP requires reason");
  }
} else {
  fail(`result must be PASS or SKIP, got ${body.result}`);
}

console.log(`PASS honesty: ${file} result=${body.result}`);
