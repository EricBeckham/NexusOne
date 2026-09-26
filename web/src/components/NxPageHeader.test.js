/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { RouterLinkStub } from "@vue/test-utils";
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
    expect(wrapper.find("[data-testid='nx-page-crumb']").exists()).toBe(false);
  });

  it("renders declarative crumbs without changing the route tree (§6 / Q5)", () => {
    const wrapper = mount(NxPageHeader, {
      props: {
        title: "发起申请",
        crumbs: [
          { label: "应用目录", to: "/p4/market" },
          { label: "我的已开通", to: "/p4/installations" },
          { label: "发起申请" },
        ],
      },
      global: { stubs: { RouterLink: RouterLinkStub } },
    });

    const crumb = wrapper.get("[data-testid='nx-page-crumb']");
    expect(crumb.text()).toContain("应用目录");
    expect(crumb.text()).toContain("我的已开通");
    expect(crumb.get("[aria-current='page']").text()).toBe("发起申请");
    expect(crumb.findAllComponents(RouterLinkStub)).toHaveLength(2);
  });

  it("prefers the breadcrumb slot when provided", () => {
    const wrapper = mount(NxPageHeader, {
      props: { title: "详情", crumbs: [{ label: "ignored" }] },
      slots: { breadcrumb: '<span data-testid="custom-crumb">自定义路径</span>' },
    });
    expect(wrapper.get("[data-testid='custom-crumb']").text()).toBe("自定义路径");
    expect(wrapper.text()).not.toContain("ignored");
  });
});
