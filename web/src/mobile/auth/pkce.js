/**
 * RFC 7636 S256 for the H5 public-client leftover.
 * The browser mints the verifier and never puts it in a URL.
 */
export const PKCE_METHOD = "S256";

export function base64Url(bytes) {
  const source = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let bin = "";
  source.forEach((value) => {
    bin += String.fromCharCode(value);
  });
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

export function randomVerifier(cryptoObj = globalThis.crypto) {
  if (!cryptoObj || typeof cryptoObj.getRandomValues !== "function") {
    throw new Error("Web Crypto getRandomValues is required for PKCE");
  }
  const raw = new Uint8Array(32);
  cryptoObj.getRandomValues(raw);
  return base64Url(raw);
}

export async function challengeS256(verifier, cryptoObj = globalThis.crypto) {
  if (!verifier || verifier.length < 43 || verifier.length > 128) {
    throw new Error("PKCE code_verifier must be 43-128 characters");
  }
  if (!cryptoObj?.subtle || typeof cryptoObj.subtle.digest !== "function") {
    throw new Error("Web Crypto SHA-256 is required for PKCE S256");
  }
  const digest = await cryptoObj.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

export async function createS256(cryptoObj = globalThis.crypto) {
  const verifier = randomVerifier(cryptoObj);
  const challenge = await challengeS256(verifier, cryptoObj);
  return { verifier, challenge, method: PKCE_METHOD };
}

export function authorizeUrlHasSecret(url) {
  if (!url) {
    return false;
  }
  try {
    const parsed = new URL(url, "http://127.0.0.1");
    const blob = `${parsed.search}#${parsed.hash}`;
    return /(code_verifier|access_token|id_token|refresh_token|token=)/i.test(blob);
  } catch {
    return /(code_verifier|access_token|id_token|refresh_token|token=)/i.test(String(url));
  }
}
