/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { p4 } from "./client";

// XB13: the stepper's reads hit the XB6/XB7 paths and pass the server's honest answer through unchanged.
describe("installation upgrade steps / facts HTTP", () => {
  const steps = {
    installation_id: "install 1",
    recorded: false,
    note: "升级步骤没有被记录",
    steps: [{ key: "precheck", label: "预检", status: "UNKNOWN" }],
  };
  // Wire shape pinned by MarketUpgradeWireNamingTest: an undeclared fact has no `value` key at all.
  const facts = { installation_id: "install 1", facts: [{ key: "compatibility", label: "兼容范围", note: "应用版本没有声明兼容范围。" }] };

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async (path) => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(path.endsWith("/upgrade-steps") ? steps : facts),
    })));
  });

  afterEach(() => vi.unstubAllGlobals());

  it("reads steps and facts with GET on the installation's encoded path, without a client tenant header", async () => {
    expect(await p4.upgradeSteps("install 1")).toEqual(steps);
    expect(await p4.upgradeFacts("install 1")).toEqual(facts);
    const [[stepsPath, stepsInit], [factsPath, factsInit]] = fetch.mock.calls;
    expect(stepsPath).toBe("/api/v1/p4/installations/install%201/upgrade-steps");
    expect(factsPath).toBe("/api/v1/p4/installations/install%201/upgrade-facts");
    expect(stepsInit.method).toBe("GET");
    expect(factsInit.method).toBe("GET");
    expect(stepsInit.headers["X-Tenant-Id"]).toBeUndefined();
  });

  it("does not invent a value for an undeclared fact", async () => {
    const result = await p4.upgradeFacts("install 1");
    expect(result.facts[0]).not.toHaveProperty("value");
    expect(result.facts[0].value ?? "暂无").toBe("暂无");
  });
});