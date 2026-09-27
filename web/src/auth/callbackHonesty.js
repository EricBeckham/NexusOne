/**
 * DL-C5: categories for callback / TenantContext failures.
 * Callers must render these strings only, never raw error text or query fragments.
 */

const NEXT_STEP = "请检查 redirect、公开 issuer，以及反向代理是否把 /callback 指回本站。不宣称已登录。";

export function callbackFailureCopy({ queryError, message } = {}) {
  const code = String(queryError || "").trim();
  if (code === "access_denied") {
    return { title: "回调失败", description: `授权被拒绝。${NEXT_STEP}` };
  }
  if (code) {
    return { title: "回调失败", description: `授权端点返回了失败。${NEXT_STEP}` };
  }
  const text = String(message || "");
  if (text.includes("state mismatch")) {
    return { title: "回调失败", description: `state 不一致。请回到登录页重试。${NEXT_STEP}` };
  }
  if (text.includes("missing authorization code")) {
    return { title: "回调失败", description: `没有授权码。${NEXT_STEP}` };
  }
  if (text.includes("code_verifier")) {
    return { title: "回调失败", description: `本机找不到 PKCE 校验值。请回到登录页重新开始。${NEXT_STEP}` };
  }
  return { title: "回调失败", description: `换会话没有完成。${NEXT_STEP}` };
}

export function sessionHasTenantContext(view) {
  const id = view?.context_id || view?.contextId || "";
  return Boolean(String(id).trim());
}
