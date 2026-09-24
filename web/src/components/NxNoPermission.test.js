/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxNoPermission from "./NxNoPermission.vue";

describe("NxNoPermission", () => {
  it("renders default guidance and accepts a recovery action", () => {
    const wrapper = mount(NxNoPermission, {
      slots: { action: "<a href='/'>返回首页</a>" },
    });

    expect(wrapper.attributes("role")).toBe("status");
    expect(wrapper.text()).toContain("暂无权限");
    expect(wrapper.text()).toContain("请联系管理员");
    expect(wrapper.get("a").attributes("href")).toBe("/");
  });
});
