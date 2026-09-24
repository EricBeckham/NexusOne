/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxPageHeader from "./NxPageHeader.vue";

describe("NxPageHeader", () => {
  it("renders the heading, supporting copy, and page actions", () => {
    const wrapper = mount(NxPageHeader, {
      props: { title: "租户目录", description: "管理全部租户", eyebrow: "星域" },
      slots: { actions: "<button>新建租户</button>" },
    });

    expect(wrapper.get("h1").text()).toBe("租户目录");
    expect(wrapper.text()).toContain("管理全部租户");
    expect(wrapper.text()).toContain("星域");
    expect(wrapper.get("button").text()).toBe("新建租户");
  });
});
