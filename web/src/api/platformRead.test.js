/** @vitest-environment happy-dom */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AUTHENTICATED_PLATFORM_READ, STORAGE_KEY, audit, p1, p4 } from "./client";
import { explainPlatformRead, platformReadFailure } from "./platformReadError";

describe("AUTH6G5 platform writes are left alone", () => {
  let fetchMock;

  beforeEach(() => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken: "access-token-value", contextId: "ctx-platform" }));
    fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });

  it.each([
    ["reparent", () => p1.reparentTenant("t-1", null)],
    // AUTH6G6: chain verify is a POST and is not bridged.
    ["verify audit chain", () => audit.verifyChain()],
    // AUTH6H3: writes around the new reads stay as they were.
    ["consume quota", () => p1.consume("t-1", { metric: "APP", amount: 1 })],
  ])("%s keeps its existing unauthenticated options", async (_, call) => {
    await call();
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBeUndefined();
    expect(init.headers["X-Nexus-Context-Id"]).toBeUndefined();
  });

  it.each([
    ["create template", () => p1.createFormTemplate({ name: "x" })],
    ["publish template", () => p1.publishFormTemplate("f-1")],
    ["create product", () => p4.createProductDefinition({ name: "x" })],
    ["create app", () => p4.createApp({ code: "expense" })],
    ["create version", () => p4.createVersion("a-1", { version: "1.0" })],
    ["publish app", () => p4.transition("v-1", "publish")],
    ["create SKU", () => p4.createSku({ code: "expense.standard" })],
    ["approve activation", () => p4.approveActivation("r-1", "批准")],
  ])("%s sends real issued identity", async (_, call) => {
    await call();
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer access-token-value");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("ctx-platform");
    expect(init.headers["X-Tenant-Id"]).toBeUndefined();
  });
});

describe("AUTH6G6 shared options", () => {
  it("is frozen and never turns authentication off", () => {
    expect(Object.isFrozen(AUTHENTICATED_PLATFORM_READ)).toBe(true);
    expect(AUTHENTICATED_PLATFORM_READ).toEqual({ platform: true });
    expect(AUTHENTICATED_PLATFORM_READ.auth).toBeUndefined();
  });

  it("uses audit wording for the audit center and system-admin wording elsewhere", () => {
    const denied = { code: "AUTH6.PLATFORM_READ_DENIED", body: { reason: "duty_set" } };
    expect(explainPlatformRead(denied, "x", { audit: true })).toContain("审计管理员");
    expect(explainPlatformRead(denied, "x")).toContain("系统管理员");
    const noGrant = { code: "AUTH6.PLATFORM_READ_DENIED", body: { reason: "permission_denied" } };
    expect(explainPlatformRead(noGrant, "x", { audit: true })).toContain("audit:read");
  });
});

describe("AUTH6G5 explainPlatformRead", () => {
  it("explains the two platform-context refusals and keeps other messages untouched", () => {
    const denied = { code: "AUTH6.PLATFORM_READ_DENIED", body: { reason: "missing_credentials" } };
    expect(explainPlatformRead(denied, "租户关系加载失败")).toContain("重新登录");
    expect(explainPlatformRead({ code: "P1.CONTEXT.0001", message: "platform context required for tenant.relation.tree" }, "x"))
      .toContain("没有平台上下文");
    expect(explainPlatformRead({ code: "P1.M02.001", message: "服务端原文" }, "租户关系加载失败")).toBe("服务端原文");
    expect(explainPlatformRead({}, "租户关系加载失败")).toBe("租户关系加载失败");
  });
});

describe("AUTH6F4 platform read lists are authenticated", () => {
  let fetchMock;

  beforeEach(() => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken: "access-token-value", contextId: "ctx-platform" }));
    fetchMock = vi.fn(async () => new Response("[]", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });

  it.each([
    ["tenant directory", () => p1.listTenants(), "/api/v1/p1/tenants"],
    ["registration review", () => p1.listTenantRegistrations("PENDING"), "/api/v1/p1/tenant-registrations?status=PENDING"],
    // AUTH6G5
    ["tenant relation tree", () => p1.tenantTree(), "/api/v1/p1/tenants/tree"],
    ["tenant relation children", () => p1.tenantChildren("t-1"), "/api/v1/p1/tenants/t-1/children"],
    ["tenant relation ancestors", () => p1.tenantAncestors("t-1"), "/api/v1/p1/tenants/t-1/ancestors"],
    ["platform service status", () => p1.serviceStatus(), "/api/v1/p1/platform/services"],
    ["form template list", () => p1.formTemplates(), "/api/v1/p1/platform/form-templates"],
    ["form template detail", () => p1.formTemplate("f-1"), "/api/v1/p1/platform/form-templates/f-1"],
    // AUTH6G6
    ["audit event search", () => audit.platformEvents({ action: "p1.tenant.disabled" }), "/api/v1/audit/events?action=p1.tenant.disabled"],
    // AUTH6H3
    ["tenant entitlement", () => p1.entitlement("t-1"), "/api/v1/p1/tenants/t-1/entitlement"],
    ["tenant quotas", () => p1.quotas("t-1"), "/api/v1/p1/tenants/t-1/quotas"],
    ["product definitions", () => p4.productDefinitions(), "/api/v1/p4/product-definitions"],
    ["product config readiness", () => p4.productConfigReadiness("d-1"), "/api/v1/p4/product-definitions/d-1/config-readiness"],
  ])("%s sends the Bearer token and the issued context", async (_, call, path) => {
    await call();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(path);
    expect(init.headers.Authorization).toBe("Bearer access-token-value");
    expect(init.headers["X-Nexus-Context-Id"]).toBe("ctx-platform");
    expect(Object.keys(init.headers).map((key) => key.toLowerCase())).not.toContain("x-tenant-id");
  });

  it("surfaces the AUTH6 deny body on the thrown error", async () => {
    fetchMock.mockResolvedValueOnce(new Response(
      JSON.stringify({ code: "AUTH6.PLATFORM_READ_DENIED", message: "platform read context denied", reason: "duty_set" }),
      { status: 403 },
    ));
    const error = await p1.listTenants().catch((err) => err);
    expect(error.status).toBe(403);
    expect(error.code).toBe("AUTH6.PLATFORM_READ_DENIED");
    expect(platformReadFailure(error, "无法读取租户目录")).toContain("系统管理员");
  });
});

describe("AUTH6F4 platform read failure copy", () => {
  const denied = (reason) => ({ code: "AUTH6.PLATFORM_READ_DENIED", body: { code: "AUTH6.PLATFORM_READ_DENIED", reason } });

  it("maps each deny reason to product wording and keeps the code for support", () => {
    expect(platformReadFailure(denied("missing_credentials"))).toContain("重新登录");
    expect(platformReadFailure(denied("session_invalid"))).toContain("已过期");
    expect(platformReadFailure(denied("not_platform_context"))).toContain("平台运营");
    expect(platformReadFailure(denied("duty_set"))).toContain("DEMO-U2");
    expect(platformReadFailure(denied("permission_denied"))).toContain("tenant:list");
    expect(platformReadFailure(denied("mfa_required"))).toContain("多因素");
    expect(platformReadFailure(denied("duty_set"))).toContain("AUTH6.PLATFORM_READ_DENIED · duty_set");
  });

  it("stays honest for an older server without a reason and for unknown reasons", () => {
    expect(platformReadFailure({ code: "AUTH6.PLATFORM_READ_DENIED", body: {} })).toBe("平台只读列表拒绝了这次请求。（AUTH6.PLATFORM_READ_DENIED）");
    expect(platformReadFailure(denied("<script>"))).not.toContain("<script>");
  });

  it("explains the missing platform context and keeps other errors as before", () => {
    expect(platformReadFailure({ code: "P1.CONTEXT.0001", message: "platform context required" })).toContain("开关");
    expect(platformReadFailure({ message: "平台服务暂不可用" }, "无法读取租户目录")).toBe("平台服务暂不可用");
    expect(platformReadFailure({}, "无法读取租户目录")).toBe("无法读取租户目录");
  });
});
