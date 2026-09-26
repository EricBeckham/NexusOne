import { describe, expect, it } from "vitest";
import { clearToasts, nxMessage, useNxToastState } from "./nxMessage";

describe("nxMessage API", () => {
  it("pushes tonal items onto shared state", () => {
    clearToasts();
    const id = nxMessage.error("失败", { traceId: "t1", durationMs: 0 });
    const state = useNxToastState();
    expect(state.items).toHaveLength(1);
    expect(state.items[0]).toMatchObject({ id, tone: "error", message: "失败", traceId: "t1" });
    nxMessage.dismiss(id);
    expect(state.items).toHaveLength(0);
  });
});
