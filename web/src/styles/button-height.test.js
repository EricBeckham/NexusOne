import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const tokens = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "tokens.css"), "utf8");

describe("O3 button control height tokens", () => {
  it("pins default control height near the 34/36 spec band", () => {
    expect(tokens).toMatch(/--nx-control-height:\s*36px/);
    expect(tokens).toMatch(/--nx-control-height-sm:\s*28px/);
    expect(tokens).toMatch(/--nx-control-height-lg:\s*40px/);
    expect(tokens).toMatch(/\.primary[\s\S]*min-height:\s*var\(--nx-control-height\)/);
    expect(tokens).toMatch(/\.nx-btn--primary/);
  });
});
