import { describe, expect, it } from "vitest";
import { authorizeUrlHasSecret, challengeS256, createS256 } from "./pkce.js";

describe("H5 PKCE S256", () => {
  it("matches the RFC 7636 appendix B vector", async () => {
    const verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    await expect(challengeS256(verifier)).resolves.toBe("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  });

  it("mints a fresh S256 pair the browser can hold", async () => {
    const pair = await createS256();
    expect(pair.method).toBe("S256");
    expect(pair.verifier).toMatch(/^[A-Za-z0-9_-]{43,128}$/);
    expect(pair.challenge).toBe(await challengeS256(pair.verifier));
    const other = await createS256();
    expect(other.verifier).not.toBe(pair.verifier);
  });

  it("rejects authorize URLs that leak the verifier or a token", () => {
    expect(authorizeUrlHasSecret("https://idp.example/auth?code=x&code_verifier=leak")).toBe(true);
    expect(authorizeUrlHasSecret("/m/callback?code=x&access_token=leak")).toBe(true);
    expect(authorizeUrlHasSecret("/m/callback?code=x&state=s&code_challenge=abc")).toBe(false);
  });
});
