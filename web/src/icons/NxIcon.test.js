/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import { PRODUCTS } from "../nav/products";
import NxIcon from "./NxIcon.vue";

describe("NxIcon", () => {
  it("renders a consistent, accessible-hidden SVG icon at the requested size", () => {
    const wrapper = mount(NxIcon, { props: { name: "search", size: 18 } });
    expect(wrapper.element.tagName.toLowerCase()).toBe("svg");
    expect(wrapper.attributes("width")).toBe("18");
    expect(wrapper.attributes("aria-hidden")).toBe("true");
    expect(wrapper.get("path").attributes("d")).toBeTruthy();
  });

  it("assigns a known icon to every product without emoji or letter marks", () => {
    for (const product of PRODUCTS) {
      expect(product.icon).toBeTruthy();
      expect(product.mark).toBeUndefined();
      expect(mount(NxIcon, { props: { name: product.icon } }).get("path").attributes("d")).toBeTruthy();
    }
  });
});
