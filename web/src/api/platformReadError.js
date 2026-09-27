/**
 * AUTH6F4: readable copy for the two authenticated platform read lists (tenant directory, registration review).
 * The server names the deny branch in `reason` (AUTH6F2); only these fixed tokens are mapped, never raw text.
 */
export const PLATFORM_READ_DENIED = "AUTH6.PLATFORM_READ_DENIED";

const REASONS = {
  missing_credentials: "请求没有带登录凭据。请退出后重新登录，再打开本页。",
  session_invalid: "登录已过期或会话已失效。请退出后重新登录。",
  session_forbidden: "当前会话与登录账号不一致。请退出后重新登录。",
  mfa_required: "平台管理员需要先完成多因素认证。",
  not_platform_context: "当前所在租户不是平台租户；只有平台运营可以查看。",
  duty_set: "当前账号的平台职责不是「系统管理员」（或同时持有其他平台职责）。请联系环境负责人核对 DEMO-U2 的职责安装与指派。",
  permission_denied: "当前账号没有读取租户目录的授权（tenant:list）。",
  snapshot_unavailable: "权限服务暂时不可用，请稍后重试。",
  client_tenant_header: "请求带了不允许的租户头，已被拒绝。",
};

/** Returns the description line for a failed platform read list call. */
export function platformReadFailure(err, fallback) {
  const code = err?.code || err?.body?.code || "";
  if (code === PLATFORM_READ_DENIED) {
    const reason = String(err?.body?.reason || "");
    // Unknown reasons are not echoed: only the fixed tokens above reach the page.
    const known = Object.hasOwn(REASONS, reason);
    const copy = known ? REASONS[reason] : "平台只读列表拒绝了这次请求。";
    return known ? `${copy}（${code} · ${reason}）` : `${copy}（${code}）`;
  }
  if (code === "P1.CONTEXT.0001") {
    return `没有平台上下文：平台只读列表开关未开启，或本次请求未经平台运营身份认证。（${code}）`;
  }
  return `${code} ${err?.message || fallback}`.trim();
}
