/**
 * In-app recovery-code leftover. Matches nexus-p2-iam RecoveryCodes.
 * Issues one-time SHA-256 hashed codes after TOTP leftover confirm.
 * Does not write Keycloak recovery-authn-codes and is not a second MFA stack.
 */

export const RECOVERY_COUNT = 8;
export const RECOVERY_BODY_LEN = 8;
export const RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export const RECOVERY_CODES = {
  RECOVERY_BLANK: "RECOVERY_BLANK",
  RECOVERY_FORMAT: "RECOVERY_FORMAT",
  RECOVERY_VERIFY_FAILED: "RECOVERY_VERIFY_FAILED",
  RECOVERY_ALREADY_USED: "RECOVERY_ALREADY_USED",
  RECOVERY_NO_ISSUED: "RECOVERY_NO_ISSUED",
};

export const RECOVERY_COPY = {
  title: "管理员恢复码 leftover",
  body: "TOTP 确认后本页签发一次性恢复码。只保存在本页会话的 SHA-256 哈希，不写回 Keycloak otp / recovery-authn-codes。默认 Stub 登录仍带演示 MFA，不重开 G18。",
  hint: "这是恢复码 leftover，不是短信 / WebAuthn，也不把动态码写回 Keycloak。",
  issueCta: "签发本页恢复码",
  verifyCta: "核销一枚恢复码",
  [RECOVERY_CODES.RECOVERY_BLANK]: "请输入一枚恢复码。",
  [RECOVERY_CODES.RECOVERY_FORMAT]: "恢复码必须是 8 位字母数字（可带中间短横线），不要用动态码。",
  [RECOVERY_CODES.RECOVERY_VERIFY_FAILED]: "恢复码不正确，或不属于本页刚签发的集合。",
  [RECOVERY_CODES.RECOVERY_ALREADY_USED]: "这枚恢复码已经核销，不能再次使用。",
  [RECOVERY_CODES.RECOVERY_NO_ISSUED]: "还没有签发恢复码。请先点击「签发本页恢复码」。",
};

export function formatRecoveryError(code) {
  return RECOVERY_COPY[code] || "恢复码失败，请重试。";
}

export function normalizeRecoveryCode(code) {
  return String(code ?? "")
    .trim()
    .replace(/[\s-]/g, "")
    .toUpperCase();
}

export function looksLikeRecoveryCode(code) {
  return new RegExp(`^[A-Z2-9]{${RECOVERY_BODY_LEN}}$`).test(normalizeRecoveryCode(code));
}

export function formatRecoveryCode(body) {
  const compact = normalizeRecoveryCode(body);
  if (compact.length !== RECOVERY_BODY_LEN) {
    throw new Error(RECOVERY_COPY[RECOVERY_CODES.RECOVERY_FORMAT]);
  }
  return `${compact.slice(0, 4)}-${compact.slice(4)}`;
}

export async function hashRecoveryCode(code) {
  const compact = normalizeRecoveryCode(code);
  if (!compact) {
    throw new Error(RECOVERY_COPY[RECOVERY_CODES.RECOVERY_BLANK]);
  }
  const bytes = new TextEncoder().encode(compact);
  if (globalThis.crypto?.subtle?.digest) {
    const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes);
    return hex(new Uint8Array(digest));
  }
  return sha256Hex(bytes);
}

export function issueRecoveryCodes({ count = RECOVERY_COUNT, random = randomByte } = {}) {
  const plaintext = [];
  const hashes = [];
  let attempt = 0;
  while (plaintext.length < count) {
    const code = formatRecoveryCode(randomBody(random, attempt++));
    const digest = sha256Hex(new TextEncoder().encode(normalizeRecoveryCode(code)));
    if (hashes.includes(digest)) {
      continue;
    }
    plaintext.push(code);
    hashes.push(digest);
  }
  return {
    ok: true,
    session: {
      status: "issued",
      plaintext,
      hashes,
      remaining: hashes.slice(),
      used: [],
    },
  };
}

export async function consumeRecoveryCode(session, code) {
  if (!session || session.status !== "issued" || !Array.isArray(session.remaining)) {
    return fail(RECOVERY_CODES.RECOVERY_NO_ISSUED);
  }
  const normalized = normalizeRecoveryCode(code);
  if (!normalized) {
    return fail(RECOVERY_CODES.RECOVERY_BLANK);
  }
  if (!looksLikeRecoveryCode(normalized)) {
    return fail(RECOVERY_CODES.RECOVERY_FORMAT);
  }
  const digest = await hashRecoveryCode(normalized);
  if ((session.used || []).includes(digest)) {
    return fail(RECOVERY_CODES.RECOVERY_ALREADY_USED);
  }
  const index = session.remaining.findIndex((item) => item === digest);
  if (index < 0) {
    return fail(RECOVERY_CODES.RECOVERY_VERIFY_FAILED);
  }
  const remaining = session.remaining.slice();
  remaining.splice(index, 1);
  return {
    ok: true,
    session: {
      ...session,
      remaining,
      used: [...(session.used || []), digest],
      lastUsed: formatRecoveryCode(normalized),
    },
  };
}

function fail(code) {
  return {
    ok: false,
    code,
    message: formatRecoveryError(code),
  };
}

function randomBody(random, salt = 0) {
  let out = "";
  for (let i = 0; i < RECOVERY_BODY_LEN; i += 1) {
    out += RECOVERY_ALPHABET[(random() + salt) % RECOVERY_ALPHABET.length];
  }
  return out;
}

function randomByte() {
  if (globalThis.crypto?.getRandomValues) {
    const buf = new Uint8Array(1);
    globalThis.crypto.getRandomValues(buf);
    return buf[0];
  }
  return Math.floor(Math.random() * 256);
}

function hex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function sha256Hex(bytes) {
  const hash = sha256(bytes instanceof Uint8Array ? bytes : Uint8Array.from(bytes));
  return hex(hash);
}

function rotr(n, x) {
  return (x >>> n) | (x << (32 - n));
}

function sha256(msg) {
  const K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ];
  const bitLen = msg.length * 8;
  const withOne = msg.length + 1;
  const padLen = (56 - (withOne % 64) + 64) % 64;
  const total = withOne + padLen + 8;
  const buf = new Uint8Array(total);
  buf.set(msg);
  buf[msg.length] = 0x80;
  const view = new DataView(buf.buffer);
  view.setUint32(total - 4, bitLen >>> 0);
  let h0 = 0x6a09e667;
  let h1 = 0xbb67ae85;
  let h2 = 0x3c6ef372;
  let h3 = 0xa54ff53a;
  let h4 = 0x510e527f;
  let h5 = 0x9b05688c;
  let h6 = 0x1f83d9ab;
  let h7 = 0x5be0cd19;
  const w = new Uint32Array(64);
  for (let i = 0; i < total; i += 64) {
    for (let t = 0; t < 16; t += 1) {
      w[t] = view.getUint32(i + t * 4);
    }
    for (let t = 16; t < 64; t += 1) {
      const s0 = rotr(7, w[t - 15]) ^ rotr(18, w[t - 15]) ^ (w[t - 15] >>> 3);
      const s1 = rotr(17, w[t - 2]) ^ rotr(19, w[t - 2]) ^ (w[t - 2] >>> 10);
      w[t] = (w[t - 16] + s0 + w[t - 7] + s1) >>> 0;
    }
    let a = h0;
    let b = h1;
    let c = h2;
    let d = h3;
    let e = h4;
    let f = h5;
    let g = h6;
    let h = h7;
    for (let t = 0; t < 64; t += 1) {
      const S1 = rotr(6, e) ^ rotr(11, e) ^ rotr(25, e);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + K[t] + w[t]) >>> 0;
      const S0 = rotr(2, a) ^ rotr(13, a) ^ rotr(22, a);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) >>> 0;
      h = g;
      g = f;
      f = e;
      e = (d + temp1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) >>> 0;
    }
    h0 = (h0 + a) >>> 0;
    h1 = (h1 + b) >>> 0;
    h2 = (h2 + c) >>> 0;
    h3 = (h3 + d) >>> 0;
    h4 = (h4 + e) >>> 0;
    h5 = (h5 + f) >>> 0;
    h6 = (h6 + g) >>> 0;
    h7 = (h7 + h) >>> 0;
  }
  const out = new Uint8Array(32);
  const ov = new DataView(out.buffer);
  ov.setUint32(0, h0);
  ov.setUint32(4, h1);
  ov.setUint32(8, h2);
  ov.setUint32(12, h3);
  ov.setUint32(16, h4);
  ov.setUint32(20, h5);
  ov.setUint32(24, h6);
  ov.setUint32(28, h7);
  return out;
}
