/**
 * Email MFA leftover: copy + request contract only.
 * Product SMTP is invite / password-reset (`nexus.iam.smtp.enabled`).
 * This leftover does not send mail and does not write Keycloak otp.
 */

export const EMAIL_MFA_CODES = {
  SMTP_DISABLED: "P2.IAM.SMTP_DISABLED",
  EMAIL_OTP_NOT_DELIVERED: "P2.IAM.EMAIL_OTP_NOT_DELIVERED",
  EMAIL_BLANK: "EMAIL_BLANK",
  EMAIL_FORMAT: "EMAIL_FORMAT",
};

export const EMAIL_MFA_COPY = {
  title: "管理员邮箱 MFA leftover",
  body: "产品 SMTP 只做邀请 / 重置口令（Keycloak execute-actions-email）。本页只走请求契约：未启用 SMTP 或没有投递主机时诚实 SKIP，不发送邮件，也不把动态码写回 Keycloak。",
  hint: "不是短信 / WebAuthn / 密码机。默认 Stub 登录仍带演示 MFA，不重开 G18。",
  cta: "请求邮箱验证码",
  [EMAIL_MFA_CODES.SMTP_DISABLED]:
    "SMTP 未启用（nexus.iam.smtp.enabled=false）。邮箱 MFA 投递在本 leftover 诚实 SKIP。",
  [EMAIL_MFA_CODES.EMAIL_OTP_NOT_DELIVERED]:
    "SMTP 开关打开但没有可投递的邮件主机 / Keycloak email OTP。本 leftover 不发送邮件。",
  [EMAIL_MFA_CODES.EMAIL_BLANK]: "请输入管理员邮箱。",
  [EMAIL_MFA_CODES.EMAIL_FORMAT]: "邮箱格式不正确。",
};

export function formatEmailMfaError(code) {
  return EMAIL_MFA_COPY[code] || "邮箱 MFA 失败，请重试。";
}

export function looksLikeEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value || "").trim());
}

export function requestEmailOtp(
  email,
  { smtpEnabled = false, smtpHost = "", keycloakReachable = false } = {},
) {
  const trimmed = String(email || "").trim();
  if (!trimmed) {
    return fail(EMAIL_MFA_CODES.EMAIL_BLANK);
  }
  if (!looksLikeEmail(trimmed)) {
    return fail(EMAIL_MFA_CODES.EMAIL_FORMAT);
  }
  if (!smtpEnabled) {
    return skip(EMAIL_MFA_CODES.SMTP_DISABLED);
  }
  if (!smtpHost || !keycloakReachable) {
    return skip(EMAIL_MFA_CODES.EMAIL_OTP_NOT_DELIVERED);
  }
  return skip(EMAIL_MFA_CODES.EMAIL_OTP_NOT_DELIVERED);
}

function fail(code) {
  return {
    ok: false,
    skip: false,
    code,
    message: formatEmailMfaError(code),
  };
}

function skip(code) {
  return {
    ok: false,
    skip: true,
    code,
    message: formatEmailMfaError(code),
  };
}
