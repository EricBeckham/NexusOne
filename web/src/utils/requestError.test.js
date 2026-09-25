import { describe, expect, it } from "vitest";
import { describeRequestError } from "./requestError";

const failure = (status, message, extra = {}) => Object.assign(new Error(message), { status, ...extra });

describe("describeRequestError", () => {
  it("hides the opaque catch-all text but keeps the code and trace for 5xx, and allows retry", () => {
    const result = describeRequestError(failure(500, "Internal error", { code: "CORE.API.0099", traceId: "t-1" }));
    expect(result.serverMessage).toBe("");
    expect(result.code).toBe("CORE.API.0099");
    expect(result.traceId).toBe("t-1");
    expect(result.retryable).toBe(true);
  });

  it("keeps the server message for deliberate 4xx and does not offer retry", () => {
    const result = describeRequestError(failure(409, "only draft templates can be updated"));
    expect(result.serverMessage).toBe("only draft templates can be updated");
    expect(result.retryable).toBe(false);
  });

  it("prefers a matching rule, then a status override, then the default", () => {
    const options = {
      match: [{ status: 422, includes: "PERSON field", hint: "选择审批人" }],
      hints: { 404: "产品不存在" },
    };
    expect(describeRequestError(failure(422, "a PERSON field is required"), options).hint).toBe("选择审批人");
    expect(describeRequestError(failure(422, "other"), options).hint).toContain("未通过校验");
    expect(describeRequestError(failure(404, "x"), options).hint).toBe("产品不存在");
    expect(describeRequestError(new TypeError("Failed to fetch")).hint).toContain("网络");
    expect(describeRequestError(new TypeError("Failed to fetch")).retryable).toBe(true);
  });
});
