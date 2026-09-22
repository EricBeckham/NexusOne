import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  BOTH_LEFTOVER_ROWS,
  REASONS,
  RESULTS,
  describeWithdrawBrowserHonesty,
  evaluateLiveJarIdpWithdraw,
  honestSkipTemplate,
  parseReport,
  verifyHonestyReport,
} from "./g7WithdrawBrowserHonesty.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "../../..");
const fixture = (name) =>
  readFileSync(join(root, "scripts/g7-withdraw-browser-honesty/fixtures", name), "utf8");

describe("G7 withdraw browser leftover honesty", () => {
  it("describes a leftover beyond #113/#159 that does not close G7", () => {
    const bridge = describeWithdrawBrowserHonesty();
    expect(bridge.leftover).toBe("g7-withdraw-browser-honesty");
    expect(bridge.siblingBrowserLeftover).toBe("#113/#159");
    expect(bridge.siblingQueryLeftover).toBe("#140");
    expect(bridge.siblingExpenseTravelWithdraw).toBe("#103");
    expect(bridge.stubWalkIsNotLive).toBe(true);
    expect(bridge.vueReachabilityIsNotLiveJar).toBe(true);
    expect(bridge.g7Close).toBe("NOT_CLAIMED");
    expect(bridge.g18).toBe("closed_unchanged");
    expect(bridge.auditPortChanged).toBe(false);
    expect(bridge.p0M0Redone).toBe(false);
    expect(bridge.redo103).toBe(false);
    expect(bridge.redo113).toBe(false);
    expect(bridge.redo140).toBe(false);
    expect(bridge.redo159).toBe(false);
    expect(bridge.keepBothLeftoverRows).toEqual(BOTH_LEFTOVER_ROWS);
  });

  it("SKIPs honestly when live JAR / IdP are unavailable", () => {
    const skipped = evaluateLiveJarIdpWithdraw();
    expect(skipped.result).toBe(RESULTS.SKIP);
    expect(skipped.reason).toBe(REASONS.LIVE_JAR_UNHEALTHY);
    expect(skipped.g7Close).toBe("NOT_CLAIMED");
    expect(skipped.stubWalkIsNotLive).toBe(true);
    expect(skipped.withdrawClickObserved).toBe(false);
  });

  it("does not treat stub login as a live walk", () => {
    const skipped = evaluateLiveJarIdpWithdraw({
      liveJar: true,
      issuedContext: true,
      idp: "stub",
      chromePresent: true,
      withdrawClickObserved: true,
      typesWalked: ["leave", "expense", "travel"],
    });
    expect(skipped.result).toBe(RESULTS.SKIP);
    expect(skipped.reason).toBe(REASONS.STUB_LOGIN_NOT_LIVE);
    expect(skipped.g7Close).toBe("NOT_CLAIMED");
  });

  it("SKIPs when JAR+IdP exist but the live withdraw click was not observed", () => {
    const skipped = evaluateLiveJarIdpWithdraw({
      liveJar: true,
      issuedContext: true,
      idp: "keycloak",
      chromePresent: true,
      withdrawClickObserved: false,
    });
    expect(skipped.result).toBe(RESULTS.SKIP);
    expect(skipped.reason).toBe(REASONS.WITHDRAW_NOT_OBSERVED);
  });

  it("PASSes live walk only with JAR + issued context + live IdP + Chrome + observed click", () => {
    const passed = evaluateLiveJarIdpWithdraw({
      liveJar: true,
      issuedContext: true,
      idp: "keycloak",
      chromePresent: true,
      withdrawClickObserved: true,
      typesWalked: ["leave", "expense", "travel"],
    });
    expect(passed.result).toBe(RESULTS.PASS);
    expect(passed.g7Close).toBe("NOT_CLAIMED");
    expect(passed.stubWalkIsNotLive).toBe(true);
  });

  it("accepts the honest SKIP fixture and keeps both leftover rows", () => {
    const report = parseReport(fixture("honest-skip.env"));
    expect(() => verifyHonestyReport(report)).not.toThrow();
    expect(report.g7_close).toBe("NOT_CLAIMED");
    expect(report.g7_113_redone).toBe("false");
    expect(report.g7_159_redone).toBe("false");
    expect(report.g7_140_redone).toBe("false");
    expect(report.leftover_rows_kept).toContain("g7-withdraw-browser");
    expect(report.leftover_rows_kept).toContain("g7-query-expense-travel");
  });

  it("rejects a forged live PASS without JAR/IdP/observed click", () => {
    const fake = parseReport(fixture("fake-live-pass.env"));
    expect(() => verifyHonestyReport(fake)).toThrow(/live_jar_idp_withdraw/);
  });

  it("rejects a product G7 close claim", () => {
    const fake = parseReport(fixture("fake-g7-close.env"));
    expect(() => verifyHonestyReport(fake)).toThrow(/g7_close/);
  });

  it("honestSkipTemplate stays verifyable", () => {
    const report = honestSkipTemplate();
    report.timestamp_utc = "2026-09-22T00:00:00Z";
    expect(() => verifyHonestyReport(report)).not.toThrow();
    expect(report.live_jar_idp_withdraw).toBe(RESULTS.SKIP);
    expect(report.g7_close).toBe("NOT_CLAIMED");
  });
});
