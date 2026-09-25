import { describe, expect, it } from "vitest";
import { describeSubscribeError } from "./subscribeError";

const error = (status, message, extra = {}) => Object.assign(new Error(message), { status, ...extra });

describe("describeSubscribeError", () => {
  it("explains the unbound-SKU 404 and keeps the server text, code and trace", () => {
    const result = describeSubscribeError(error(404, "catalog SKU has no product definition", { code: "CORE.API.0002", traceId: "t-1" }));
    expect(result.hint).toContain("绑定产品定义");
    expect(result.serverMessage).toBe("catalog SKU has no product definition");
    expect(result.code).toBe("CORE.API.0002");
    expect(result.traceId).toBe("t-1");
    expect(result.retryable).toBe(true);
  });

  it("points a duplicate subscription to 我的已开通 and does not offer retry", () => {
    const result = describeSubscribeError(error(409, "tenant already has an active subscription for this SKU"));
    expect(result.action).toEqual({ to: "/p4/installations", label: "前往我的已开通" });
    expect(result.retryable).toBe(false);
  });

  it("covers forbidden, server and network failures", () => {
    expect(describeSubscribeError(error(403, "tenant administrator role is required")).hint).toContain("租户管理员");
    expect(describeSubscribeError(error(500, "Internal error")).hint).toContain("追踪号");
    expect(describeSubscribeError(new TypeError("Failed to fetch")).hint).toContain("网络");
  });
});
