/** @vitest-environment happy-dom */
import { beforeEach, describe, expect, it, vi } from "vitest";
import { STORAGE_KEY } from "./client";

describe("platform mode persistence", () => {
  beforeEach(() => {
    vi.resetModules();
    sessionStorage.clear();
  });

  it("defaults to tenant mode when no preference was saved", async () => {
    const { session } = await import("./session");
    expect(session.platformMode).toBe(false);
  });

  it("restores platform mode only after the user explicitly enabled it", async () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ platformMode: true }));
    const { clearIdentity, session } = await import("./session");
    expect(session.platformMode).toBe(true);
    clearIdentity();
    expect(session.platformMode).toBe(false);
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY)).platformMode).toBe(false);
  });
});
