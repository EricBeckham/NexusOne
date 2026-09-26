/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { NEXT_BIND_COPY, PLATFORM_CONFIG_LINKS } from "./platformConfigLinks";

describe("platform config link vocabulary (CF-T4)", () => {
  it("keeps one short label set for header actions", () => {
    expect(PLATFORM_CONFIG_LINKS.templates).toEqual({ label: "表单模板", to: "/p1/form-templates" });
    expect(PLATFORM_CONFIG_LINKS.definitions).toEqual({ label: "产品定义", to: "/p4/product-definitions" });
    expect(PLATFORM_CONFIG_LINKS.wizard.label).toBe("配置向导");
    expect(PLATFORM_CONFIG_LINKS.wizard.fullLabel).toBe("产品配置向导");
    expect(PLATFORM_CONFIG_LINKS.wizard.to).toBe("/p4/product-config-wizard");
    expect(NEXT_BIND_COPY).toBe("下一步：到产品定义绑定");
  });
});
