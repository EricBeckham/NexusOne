/**
 * Readable description of a failed API call (see api() in api/client.js: status, message, code, traceId).
 * The server's own message is kept alongside the hint, except the opaque catch-all "Internal error",
 * which carries no information beyond the status and trace id.
 */
const GENERIC_SERVER_MESSAGES = new Set(["internal error"]);

const DEFAULT_HINTS = {
  network: "无法连接服务端，请检查网络后重试。",
  400: "请求未被接受，请检查填写内容后重试。",
  401: "登录已失效，请重新登录后再试。",
  403: "当前账号没有执行此操作的权限。",
  404: "要操作的对象不存在或已被移除，请刷新后重试。",
  409: "操作与当前状态冲突，请刷新后确认。",
  422: "填写内容未通过校验，请按提示修改。",
  server: "服务端暂时无法完成请求，请稍后重试；如持续失败，请把追踪号提供给管理员。",
  // ApiExceptionHandler: CORE.API.0006 = a database constraint the server itself violated (not a user input error).
  dataIntegrity: "服务端写入数据时违反了数据库约束，操作已回滚。这是服务端问题，不是填写错误；请把下方服务端信息和追踪号提供给管理员。",
  duplicate: "与已有数据重复，请修改后重试。",
};

/**
 * @param {Error & {status?: number, code?: string, traceId?: string}} err
 * @param {{ hints?: Record<string, string>, match?: Array<{ status: number, includes: string, hint: string }> }} options
 */
export function describeRequestError(err, options = {}) {
  const status = Number(err?.status) || 0;
  const message = String(err?.message || "").trim();
  const hints = { ...DEFAULT_HINTS, ...(options.hints || {}) };
  const specific = (options.match || []).find((rule) =>
    rule.status === status && message.toLowerCase().includes(rule.includes.toLowerCase()));
  let hint;
  if (specific) hint = specific.hint;
  else if (!status) hint = hints.network;
  else if (err?.code === "CORE.API.0006") hint = hints.dataIntegrity;
  else if (status === 409 && message.toLowerCase().startsWith("unique constraint")) hint = hints.duplicate;
  else if (status >= 500) hint = hints.server;
  else hint = hints[status] || hints[400];
  return {
    status,
    hint,
    serverMessage: message && message !== hint && !GENERIC_SERVER_MESSAGES.has(message.toLowerCase()) ? message : "",
    code: err?.code || "",
    traceId: err?.traceId || "",
    retryable: !status || status >= 500,
  };
}
