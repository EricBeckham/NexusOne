/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { sanitizeSmokeLog, smokeDecision, smokeLogLine } from "./stubBrowserSmoke";

describe("stub browser smoke gate", () => {
  it("skips when asked or when no browser or URL is available", () => {
    expect(smokeDecision({ skip: "1", appUrl: "http://127.0.0.1:5173/login", browserPath: "C:/chrome.exe" }).action).toBe("skip");
    expect(smokeDecision({ skip: "", appUrl: "http://127.0.0.1:5173/login", browserPath: "" }).reason).toBe("no browser binary");
    expect(smokeDecision({ skip: "", appUrl: "", browserPath: "C:/chrome.exe" }).reason).toBe("NEXUS_STUB_APP_URL unset");
  });

  it("runs only with a browser and a local URL, and logs host without tokens", () => {
    const decision = smokeDecision({
      skip: "",
      appUrl: "http://127.0.0.1:5173/login?access_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.signature",
      browserPath: "C:/chrome.exe",
    });
    expect(decision.action).toBe("run");
    expect(decision.host).toBe("127.0.0.1:5173");
    const line = smokeLogLine({
      status: "probed",
      host: decision.host,
      detail: "http=200 Bearer abc.def password=hunter2",
    });
    expect(line).toContain("host=127.0.0.1:5173");
    expect(line).toContain("http=200");
    expect(line).not.toMatch(/hunter2|Bearer abc|eyJ/);
    expect(sanitizeSmokeLog("access_token=stub.secret")).toBe("access_token=[redacted]");
  });
});
