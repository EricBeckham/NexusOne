/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxStatCard from "./NxStatCard.vue";

describe("NxStatCard", () => {
  it("renders a real value, unit, hint, and semantic tone", () => {
    const wrapper = mount(NxStatCard, {
      props: { label: "租户数", value: 12, unit: "个", hint: "较昨日新增 1", tone: "success" },
    });

    expect(wrapper.attributes("data-tone")).toBe("success");
    expect(wrapper.get(".value").text()).toBe("12");
    expect(wrapper.get(".unit").text()).toBe("个");
    expect(wrapper.text()).toContain("较昨日新增 1");
  });

  it("uses an honest placeholder when data is unavailable", () => {
    const wrapper = mount(NxStatCard, { props: { label: "调用量" } });
    expect(wrapper.get(".value").text()).toBe("暂无");
    expect(wrapper.find(".unit").exists()).toBe(false);
  });
});
