/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import NxErrorState from "./NxErrorState.vue";

describe("NxErrorState", () => {
  it("shows diagnostic context and emits retry", async () => {
    const wrapper = mount(NxErrorState, { props: { traceId: "trace-42" } });

    expect(wrapper.attributes("role")).toBe("alert");
    expect(wrapper.text()).toContain("Trace ID: trace-42");
    await wrapper.get("button").trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
  });

  it("can hide its default retry action", () => {
    const wrapper = mount(NxErrorState, { props: { retryLabel: "" } });
    expect(wrapper.find("button").exists()).toBe(false);
  });
});
