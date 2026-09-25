/**
 * Readable explanation for a failed POST /api/v1/p4/tenants/{id}/subscriptions.
 * The server's own message is always kept alongside (never replaced by a guess).
 */
export function describeSubscribeError(err) {
  const status = Number(err?.status) || 0;
  const serverMessage = String(err?.message || "").trim();
  const lower = serverMessage.toLowerCase();
  let hint;
  let action = null;
  if (!status) {
    hint = "无法连接服务端，请检查网络后重试。";
  } else if (status === 404 && lower.includes("product definition")) {
    hint = "该 SKU 还没有绑定产品定义，需要平台管理员绑定后才能开通。";
  } else if (status === 404) {
    hint = "要开通的应用版本或 SKU 已不存在，请刷新目录后重试。";
  } else if (status === 409) {
    hint = "本租户已订阅该 SKU，可以在「我的已开通」中直接使用。";
    action = { to: "/p4/installations", label: "前往我的已开通" };
  } else if (status === 401) {
    hint = "登录已失效，请重新登录后再开通。";
  } else if (status === 403) {
    hint = "只有租户管理员可以订阅开通。";
  } else if (status >= 500) {
    hint = "服务端暂时无法完成开通，请稍后重试；如持续失败，请把追踪号提供给管理员。";
  } else {
    hint = "开通请求未被接受，请检查后重试。";
  }
  return {
    hint,
    serverMessage: serverMessage && serverMessage !== hint ? serverMessage : "",
    code: err?.code || "",
    traceId: err?.traceId || "",
    retryable: status !== 409 && status !== 403,
    action,
  };
}
