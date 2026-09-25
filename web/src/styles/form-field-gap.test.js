import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const tokens = readFileSync(join(root, "styles/tokens.css"), "utf8");

describe("O2 form field gap tokens", () => {
  it("pins field vertical rhythm to the 20px space token", () => {
    expect(tokens).toMatch(/--nx-form-field-gap:\s*var\(--nx-space-5\)/);
    expect(tokens).toMatch(/\.nx-field\s*\{[^}]*margin-bottom:\s*var\(--nx-form-field-gap\)/s);
  });
});
