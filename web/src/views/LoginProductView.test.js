/** @vitest-environment happy-dom */
import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import LoginProductView from "./LoginProductView.vue";

describe("LOGIN1 product auth shell", () => {
  it("shows the brand without developer controls", () => {
    const wrapper = mount(LoginProductView);
    expect(wrapper.get("h1").text()).toBe("星枢 NexusOne");
    expect(wrapper.text()).toContain("登录进入工作台");
    expect(wrapper.find("textarea").exists()).toBe(false);
    expect(wrapper.text()).not.toMatch(/Stub|Bearer|PKCE|平台操作头|会话与登录/);
    expect(wrapper.get("[data-testid=product-login]").attributes("data-product")).toBe("p2");
  });
});
