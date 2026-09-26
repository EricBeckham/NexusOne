/** @vitest-environment happy-dom */
import { afterEach, describe, expect, it, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { clearToasts, nxMessage } from "../feedback/nxMessage";
import NxToastHost from "./NxToastHost.vue";

afterEach(() => {
  clearToasts();
  vi.useRealTimers();
});

describe("nxMessage / NxToastHost (S2)", () => {
  it("exposes success | error | info and clips long copy", () => {
    nxMessage.success("已保存");
    nxMessage.info("提示");
    const long = "x".repeat(200);
    nxMessage.error(long, { traceId: "trace-s2" });
    expect(nxMessage).toBeTruthy();
    const wrapper = mount(NxToastHost);
    expect(wrapper.get("[data-testid='nx-toast-success']").text()).toContain("已保存");
    expect(wrapper.get("[data-testid='nx-toast-info']").text()).toContain("提示");
    const err = wrapper.get("[data-testid='nx-toast-error']");
    expect(err.text()).toContain("…");
    expect(err.text().length).toBeLessThan(160);
    expect(wrapper.get("[data-testid='nx-toast-trace']").text()).toContain("trace-s2");
  });

  it("auto-dismisses after the default duration band (§21 3–5s)", async () => {
    vi.useFakeTimers();
    const { useNxToastState } = await import("../feedback/nxMessage");
    mount(NxToastHost);
    nxMessage.success("短暂提示");
    expect(useNxToastState().items).toHaveLength(1);
    vi.advanceTimersByTime(4000);
    await flushPromises();
    expect(useNxToastState().items).toHaveLength(0);
  });
});
