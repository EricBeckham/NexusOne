import { describe, expect, it } from "vitest";
import { logSummary, showText } from "./publishDisplay";

describe("publish center display", () => {
  it("keeps missing logs and values empty", () => {
    expect(showText(null)).toBe("暂无");
    expect(showText("")).toBe("暂无");
    expect(logSummary(null)).toBe("暂无");
    expect(logSummary({ outcome: "PUBLISHED", message: "已发布" })).toBe("PUBLISHED 已发布");
  });
});
