import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const tokens = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "tokens.css"), "utf8");

describe("Q1 table density tokens", () => {
  it("pins default / compact / large row heights to the §12.1 band", () => {
    expect(tokens).toMatch(/--nx-table-row-height:\s*48px/);
    expect(tokens).toMatch(/--nx-table-row-height-compact:\s*40px/);
    expect(tokens).toMatch(/--nx-table-row-height-large:\s*56px/);
    expect(tokens).toMatch(/\.nx-table\s+th[\s\S]*height:\s*var\(--nx-table-row-height\)/);
    expect(tokens).toMatch(/\.nx-table--compact/);
    expect(tokens).toMatch(/\.nx-table--large/);
  });
});
