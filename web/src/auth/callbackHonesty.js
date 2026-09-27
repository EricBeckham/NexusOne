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

/**
 * TC3: why the user is signed in but not inside a tenant. Product wording only.
 * - `no_membership`: the identity came back but has no ACTIVE tenant membership → back to login / ask an admin;
 * - `auto_enter_failed`: there are ACTIVE memberships but entering one did not succeed → retry, or switch manually
 *   from the user menu (offered only when there is more than one membership to choose from).
 */
export function activeMembershipCount(memberships) {
  return (Array.isArray(memberships) ? memberships : [])
    .filter((item) => String(item?.status || "").toUpperCase() === "ACTIVE").length;
}

export function tenantContextMissingCopy(memberships) {
  const active = activeMembershipCount(memberships);
  if (active === 0) {
    return {
      kind: "no_membership",
      title: "还没有可进入的租户",
      description: "登录已成功，但这个账号还没有开通任何租户。请回到登录页换一个账号，或联系管理员为你开通租户后再登录。",
      canRetry: false,
      canSwitchManually: false,
    };
  }
  const manual = active > 1;
  return {
    kind: "auto_enter_failed",
    title: "未能自动进入租户",
    description: manual
      ? `登录已成功，你有 ${active} 个可用租户，但没能自动进入。请点「重试」，或进入首页后在右上角用户菜单里手动切换租户。`
      : "登录已成功，但没能进入你的租户。请点「重试」；仍不行请联系管理员。",
    canRetry: true,
    canSwitchManually: manual,
  };
}

export function sessionHasTenantContext(view) {
  const id = view?.context_id || view?.contextId || "";
  return Boolean(String(id).trim());
}
