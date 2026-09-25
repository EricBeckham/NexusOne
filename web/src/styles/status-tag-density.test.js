import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const root = dirname(fileURLToPath(import.meta.url));
const tokens = readFileSync(join(root, "tokens.css"), "utf8");
const status = readFileSync(join(root, "../components/NxStatus.vue"), "utf8");

describe("Q4 status tag density", () => {
  it("pins Badge/Tag height / weight / line-height to §2.3 + §12.4", () => {
    expect(tokens).toMatch(/--nx-status-tag-height:\s*24px/);
    expect(tokens).toMatch(/--nx-status-tag-font-weight:\s*500/);
    expect(tokens).toMatch(/--nx-status-tag-line-height:\s*18px/);
    expect(tokens).toMatch(/--nx-status-tag-font-size:\s*var\(--nx-font-size-xs\)/);
  });

  it("NxStatus consumes shared density tokens (not hard-coded 600 weight)", () => {
    expect(status).toMatch(/height:\s*var\(--nx-status-tag-height\)/);
    expect(status).toMatch(/font-weight:\s*var\(--nx-status-tag-font-weight\)/);
    expect(status).toMatch(/line-height:\s*var\(--nx-status-tag-line-height\)/);
    expect(status).not.toMatch(/font-weight:\s*600/);
  });
});
