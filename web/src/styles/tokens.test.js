import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const styles = new URL("./tokens.css", import.meta.url);
const read = (relativePath) => readFileSync(new URL(relativePath, styles), "utf8");

describe("V0 global design tokens", () => {
  const tokens = read("./tokens.css");

  it("provides the frozen six-color chart palette and P3/P5 secondary colors", () => {
    for (const name of ["blue", "green", "purple", "orange", "red", "cyan"]) {
      expect(tokens).toMatch(new RegExp(`--nx-chart-${name}:\\s*#[0-9a-f]{6}`, "i"));
    }
    expect(tokens).toContain('[data-product="p3"] { --nx-product: #2475ff; --nx-product-light: #eef5ff; --nx-product-secondary: #13a77a; }');
    expect(tokens).toContain('[data-product="p5"] { --nx-product: #1687ff; --nx-product-light: #e8f6ff; --nx-product-secondary: #18afc8; }');
  });

  it("uses the page-title and dialog-shadow tokens", () => {
    expect(tokens).toContain("--nx-font-size-page-title: 28px;");
    expect(read("../components/NxPageHeader.vue")).toContain("font-size: var(--nx-font-size-page-title)");
    expect(tokens).toContain("--nx-shadow-dialog: 0 20px 60px rgba(15, 38, 72, 0.22);");
    expect(tokens).toContain("box-shadow: var(--nx-shadow-dialog);");
  });

  it("keeps platform mode on the normal page background and retains a narrow warning cue", () => {
    expect(read("../layout/AppShell.vue")).toContain(".shell.platform { background: var(--nx-bg-page); }");
    expect(read("../layout/GlobalHeader.vue")).toContain(".top.platform { box-shadow: inset 0 -3px 0 var(--nx-warning); }");
  });
});
