/**
 * RFC 6238 TOTP (HmacSHA1, 6 digits, 30s). Browser leftover only.
 * Matches nexus-p2-iam Totp. Not a second authenticator stack.
 */

export const TOTP_DIGITS = 6;
export const TOTP_PERIOD_SECONDS = 30;
const SHA1_BLOCK = 64;

export function generate(secret, when = Date.now()) {
  const epoch = typeof when === "number" ? Math.floor(when / 1000) : when;
  return hotp(keyBytes(secret), Math.floor(epoch / TOTP_PERIOD_SECONDS), TOTP_DIGITS);
}

export function verify(secret, code, when = Date.now(), window = 1) {
  const expected = normalizeCode(code);
  if (!/^\d{6}$/.test(expected)) {
    return false;
  }
  const epoch = typeof when === "number" ? Math.floor(when / 1000) : when;
  const counter = Math.floor(epoch / TOTP_PERIOD_SECONDS);
  for (let offset = -window; offset <= window; offset += 1) {
    if (hotp(keyBytes(secret), counter + offset, TOTP_DIGITS) === expected) {
      return true;
    }
  }
  return false;
}

export function normalizeCode(code) {
  return String(code ?? "").replace(/\s+/g, "");
}

export function groupSecret(secret) {
  const compact = compactSecret(secret);
  return compact.replace(/(.{4})/g, "$1 ").trim();
}

export function otpauthUri({ issuer = "NexusOne", account = "admin", secret }) {
  const compact = compactSecret(secret);
  const label = `${encodeURIComponent(issuer)}:${encodeURIComponent(account)}`;
  const query = new URLSearchParams({
    secret: compact,
    issuer,
    algorithm: "SHA1",
    digits: String(TOTP_DIGITS),
    period: String(TOTP_PERIOD_SECONDS),
  });
  return `otpauth://totp/${label}?${query}`;
}

export function randomBase32Secret(byteLength = 20) {
  const bytes = new Uint8Array(byteLength);
  if (globalThis.crypto?.getRandomValues) {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < byteLength; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  return encodeBase32(bytes);
}

export function keyBytes(secret) {
  if (secret == null || String(secret).trim() === "") {
    throw new Error("TOTP secret is blank");
  }
  const compact = compactSecret(secret);
  if (looksLikeBase32(compact)) {
    return decodeBase32(compact);
  }
  return new TextEncoder().encode(compact);
}

export function looksLikeBase32(compact) {
  const upper = compactSecret(compact);
  if (!/^[A-Z2-7]+=*$/.test(upper)) {
    return false;
  }
  const withoutPad = upper.replace(/=+$/g, "");
  return withoutPad.length >= 8 && withoutPad.length % 8 === 0;
}

export function hotp(key, counter, digits = TOTP_DIGITS) {
  const payload = new Uint8Array(8);
  let value = BigInt(counter);
  for (let i = 7; i >= 0; i -= 1) {
    payload[i] = Number(value & 0xffn);
    value >>= 8n;
  }
  const hash = hmacSha1(key, payload);
  const offset = hash[hash.length - 1] & 0x0f;
  const binary =
    ((hash[offset] & 0x7f) << 24) |
    ((hash[offset + 1] & 0xff) << 16) |
    ((hash[offset + 2] & 0xff) << 8) |
    (hash[offset + 3] & 0xff);
  const otp = binary % 10 ** digits;
  return String(otp).padStart(digits, "0");
}

function compactSecret(secret) {
  return String(secret).trim().replace(/[\s-]/g, "").toUpperCase();
}

function encodeBase32(bytes) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = 0;
  let buffer = 0;
  let out = "";
  for (const byte of bytes) {
    buffer = (buffer << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      out += alphabet[(buffer >> bits) & 31];
    }
  }
  if (bits > 0) {
    out += alphabet[(buffer << (5 - bits)) & 31];
  }
  while (out.length % 8 !== 0) {
    out += "=";
  }
  return out;
}

function decodeBase32(raw) {
  const compact = compactSecret(raw);
  const pad = (8 - (compact.length % 8)) % 8;
  const padded = compact + "=".repeat(pad);
  const map = new Int16Array(128).fill(-1);
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  for (let i = 0; i < alphabet.length; i += 1) {
    map[alphabet.charCodeAt(i)] = i;
  }
  let buffer = 0;
  let bits = 0;
  const out = [];
  for (const ch of padded) {
    if (ch === "=") {
      break;
    }
    const value = map[ch.charCodeAt(0)];
    if (value < 0) {
      throw new Error("invalid Base32 TOTP secret");
    }
    buffer = (buffer << 5) | value;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      out.push((buffer >> bits) & 0xff);
    }
  }
  return Uint8Array.from(out);
}

function rotl(n, x) {
  return ((x << n) | (x >>> (32 - n))) >>> 0;
}

function sha1(bytes) {
  const msg = bytes instanceof Uint8Array ? bytes : Uint8Array.from(bytes);
  const bitLen = msg.length * 8;
  const withOne = msg.length + 1;
  const padLen = (56 - (withOne % 64) + 64) % 64;
  const total = withOne + padLen + 8;
  const buf = new Uint8Array(total);
  buf.set(msg);
  buf[msg.length] = 0x80;
  const view = new DataView(buf.buffer);
  view.setUint32(total - 4, bitLen >>> 0);

  let h0 = 0x67452301;
  let h1 = 0xefcdab89;
  let h2 = 0x98badcfe;
  let h3 = 0x10325476;
  let h4 = 0xc3d2e1f0;
  const w = new Uint32Array(80);
  for (let i = 0; i < total; i += 64) {
    for (let t = 0; t < 16; t += 1) {
      w[t] = view.getUint32(i + t * 4);
    }
    for (let t = 16; t < 80; t += 1) {
      w[t] = rotl(1, w[t - 3] ^ w[t - 8] ^ w[t - 14] ^ w[t - 16]);
    }
    let a = h0;
    let b = h1;
    let c = h2;
    let d = h3;
    let e = h4;
    for (let t = 0; t < 80; t += 1) {
      let f;
      let k;
      if (t < 20) {
        f = (b & c) | (~b & d);
        k = 0x5a827999;
      } else if (t < 40) {
        f = b ^ c ^ d;
        k = 0x6ed9eba1;
      } else if (t < 60) {
        f = (b & c) | (b & d) | (c & d);
        k = 0x8f1bbcdc;
      } else {
        f = b ^ c ^ d;
        k = 0xca62c1d6;
      }
      const temp = (rotl(5, a) + f + e + k + w[t]) >>> 0;
      e = d;
      d = c;
      c = rotl(30, b);
      b = a;
      a = temp;
    }
    h0 = (h0 + a) >>> 0;
    h1 = (h1 + b) >>> 0;
    h2 = (h2 + c) >>> 0;
    h3 = (h3 + d) >>> 0;
    h4 = (h4 + e) >>> 0;
  }
  const out = new Uint8Array(20);
  const ov = new DataView(out.buffer);
  ov.setUint32(0, h0);
  ov.setUint32(4, h1);
  ov.setUint32(8, h2);
  ov.setUint32(12, h3);
  ov.setUint32(16, h4);
  return out;
}

function hmacSha1(key, message) {
  const raw = key instanceof Uint8Array ? key : Uint8Array.from(key);
  const k = raw.length > SHA1_BLOCK ? sha1(raw) : raw;
  const keyPad = new Uint8Array(SHA1_BLOCK);
  keyPad.set(k);
  const inner = new Uint8Array(SHA1_BLOCK + message.length);
  const outer = new Uint8Array(SHA1_BLOCK + 20);
  for (let i = 0; i < SHA1_BLOCK; i += 1) {
    inner[i] = keyPad[i] ^ 0x36;
    outer[i] = keyPad[i] ^ 0x5c;
  }
  inner.set(message, SHA1_BLOCK);
  outer.set(sha1(inner), SHA1_BLOCK);
  return sha1(outer);
}

/** RFC 6238 Appendix B SHA-1 seed. */
export function rfc6238Sha1Seed() {
  return new TextEncoder().encode("12345678901234567890");
}
