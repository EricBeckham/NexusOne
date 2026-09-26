import { describe, expect, it } from "vitest";
import { DEV_NOTES_SWITCH_HINT, devNotesEnabled } from "./devNotes";

const storage = (value) => ({ getItem: () => value });

describe("developer notes switch (O9 / S4)", () => {
  it("is off by default, including on a dev server", () => {
    expect(devNotesEnabled({ DEV: true }, storage(null))).toBe(false);
    expect(devNotesEnabled({ DEV: false, PROD: true }, storage("1"))).toBe(false);
    expect(devNotesEnabled({ MODE: "production", DEV: true }, storage("1"))).toBe(false);
  });

  it("turns on with the explicit env flag or the dev-only local switch", () => {
    expect(devNotesEnabled({ PROD: true, VITE_NEXUS_DEV_NOTES: "true" }, storage(null))).toBe(true);
    expect(devNotesEnabled({ DEV: true, VITE_NEXUS_DEV_NOTES: "1" }, storage(null))).toBe(true);
    expect(devNotesEnabled({ DEV: true }, storage("1"))).toBe(true);
    expect(devNotesEnabled({ DEV: true, VITE_NEXUS_DEV_NOTES: "false" }, storage("1"))).toBe(false);
  });

  it("survives storage that throws", () => {
    expect(devNotesEnabled({ DEV: true }, { getItem() { throw new Error("blocked"); } })).toBe(false);
  });

  it("documents the switch hint for demo operators", () => {
    expect(DEV_NOTES_SWITCH_HINT).toContain("默认关闭");
    expect(DEV_NOTES_SWITCH_HINT).toContain("VITE_NEXUS_DEV_NOTES");
  });
});
