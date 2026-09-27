/**
 * In-app TOTP enrollment leftover: copy + error paths only.
 * Does not write a Keycloak otp credential and is not a second MFA stack.
 */
import { generate, groupSecret, normalizeCode, otpauthUri, randomBase32Secret, verify } from "./totp.js";

export const MFA_REQUIRED_CODE = "P2.IAM.MFA_REQUIRED";
export const ENROLL_TTL_MS = 10 * 60 * 1000;

export const ENROLL_CODES = {
  TOTP_CODE_BLANK: "TOTP_CODE_BLANK",
  TOTP_CODE_FORMAT: "TOTP_CODE_FORMAT",
  TOTP_VERIFY_FAILED: "TOTP_VERIFY_FAILED",
  TOTP_ALREADY_ENROLLED: "TOTP_ALREADY_ENROLLED",
  TOTP_NO_PENDING: "TOTP_NO_PENDING",
  TOTP_SECRET_BLANK: "TOTP_SECRET_BLANK",
  TOTP_STALE: "TOTP_STALE",
};

export const MFA_REQUIRED_COPY = {
  productBody: "此账号需要完成多因素验证。请先注册验证器，再返回登录。",
  productHint: "注册完成后，请重新登录以完成身份验证。",
  title: "管理员需要先注册 TOTP",
  body: "口令 token 没有 acr=mfa / amr=otp。产品门禁 P2.IAM.MFA_REQUIRED 拒绝签发特权会话，也不会签发 TenantContext。请用验证器完成注册后再登录。默认 Stub 登录仍带演示 MFA，不重开 G18。",
  cta: "去注册验证器",
  hint: "这是注册界面 leftover，不是短信 / WebAuthn / 恢复码，也不把动态码写回 Keycloak。",
};

export const ENROLL_COPY = {
  [ENROLL_CODES.TOTP_CODE_BLANK]: "请输入验证器当前显示的 6 位动态码。",
  [ENROLL_CODES.TOTP_CODE_FORMAT]: "动态码必须是 6 位数字，不要空格或字母。",
  [ENROLL_CODES.TOTP_VERIFY_FAILED]: "动态码不正确或已过期。请使用验证器此刻显示的 6 位数字，不要用上一个周期的码。",
  [ENROLL_CODES.TOTP_ALREADY_ENROLLED]: "此账号已在本页完成注册确认。重新绑定请先到 Keycloak 移除 otp 凭据（#118 走通，本页不重做）。",
  [ENROLL_CODES.TOTP_NO_PENDING]: "还没有待确认的密钥。请先点击「开始注册」生成密钥。",
  [ENROLL_CODES.TOTP_SECRET_BLANK]: "密钥为空，无法生成动态码。请重新开始注册。",
  [ENROLL_CODES.TOTP_STALE]: "待确认密钥已过期（超过 10 分钟）。请重新开始注册，不要使用旧密钥。",
};

export function formatEnrollmentError(code) {
  return ENROLL_COPY[code] || "注册失败，请重试。";
}

export function startEnrollment({
  account = "admin-a@example.com",
  issuer = "NexusOne",
  secret,
  now = Date.now(),
} = {}) {
  const nextSecret = secret == null || String(secret).trim() === "" ? randomBase32Secret(20) : String(secret);
  if (!nextSecret.trim()) {
    return fail(ENROLL_CODES.TOTP_SECRET_BLANK);
  }
  return {
    ok: true,
    session: {
      status: "pending",
      account,
      issuer,
      secret: nextSecret.replace(/\s+/g, "").toUpperCase(),
      secretGrouped: groupSecret(nextSecret),
      otpauth: otpauthUri({ issuer, account, secret: nextSecret }),
      createdAt: now,
    },
  };
}

export function confirmEnrollment(session, code, { now = Date.now(), window = 1 } = {}) {
  if (!session || session.status === "idle" || session.status == null) {
    return fail(ENROLL_CODES.TOTP_NO_PENDING);
  }
  if (session.status === "enrolled") {
    return fail(ENROLL_CODES.TOTP_ALREADY_ENROLLED);
  }
  if (!session.secret || String(session.secret).trim() === "") {
    return fail(ENROLL_CODES.TOTP_SECRET_BLANK);
  }
  const normalized = normalizeCode(code);
  if (!normalized) {
    return fail(ENROLL_CODES.TOTP_CODE_BLANK);
  }
  if (!/^\d{6}$/.test(normalized)) {
    return fail(ENROLL_CODES.TOTP_CODE_FORMAT);
  }
  if (now - (session.createdAt || 0) > ENROLL_TTL_MS) {
    return fail(ENROLL_CODES.TOTP_STALE);
  }
  if (!verify(session.secret, normalized, now, window)) {
    return fail(ENROLL_CODES.TOTP_VERIFY_FAILED);
  }
  return {
    ok: true,
    session: {
      ...session,
      status: "enrolled",
      confirmedAt: now,
    },
  };
}

export function currentCode(session, now = Date.now()) {
  if (!session?.secret) {
    throw new Error(ENROLL_COPY[ENROLL_CODES.TOTP_SECRET_BLANK]);
  }
  return generate(session.secret, now);
}

export function isMfaRequiredError(err) {
  return err?.code === MFA_REQUIRED_CODE;
}

function fail(code) {
  return {
    ok: false,
    code,
    message: formatEnrollmentError(code),
  };
}
