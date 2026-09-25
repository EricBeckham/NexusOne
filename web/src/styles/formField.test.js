import { describe, expect, it } from "vitest";
import { FORM_FIELD_CLASSES, FORM_FIELD_EXAMPLE } from "./formField.js";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

describe("O4 form field style export", () => {
  it("exports stable class names and an example snippet", () => {
    expect(FORM_FIELD_CLASSES.required).toBe("nx-req");
    expect(FORM_FIELD_CLASSES.error).toBe("nx-field-error");
    expect(FORM_FIELD_EXAMPLE).toContain("nx-req");
    expect(FORM_FIELD_EXAMPLE).toContain("nx-field-error");
  });

  it("ships shared CSS loaded by main", () => {
    const css = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "form-field.css"), "utf8");
    expect(css).toMatch(/\.nx-req/);
    expect(css).toMatch(/\.nx-field-error/);
    const main = readFileSync(join(dirname(fileURLToPath(import.meta.url)), "../main.js"), "utf8");
    expect(main).toMatch(/form-field\.css/);
  });
});
