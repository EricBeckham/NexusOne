import { describe, expect, it } from "vitest";
import {
  generate,
  groupSecret,
  hotp,
  looksLikeBase32,
  otpauthUri,
  rfc6238Sha1Seed,
  verify,
} from "./totp.js";

describe("RFC 6238 TOTP leftover helper", () => {
  it("matches Appendix B SHA-1 8-digit vectors", () => {
    const seed = rfc6238Sha1Seed();
    expect(hotp(seed, 1n, 8)).toBe("94287082");
    expect(hotp(seed, Math.floor(1111111109 / 30), 8)).toBe("07081804");
    expect(hotp(seed, Math.floor(1111111111 / 30), 8)).toBe("14050471");
  });

  it("matches the Java 6-digit Base32 fixture at T=59", () => {
    expect(generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 59_000)).toBe("287082");
  });

  it("accepts grouped secrets and a ±1 window", () => {
    const secret = "GEZDG NB VG Y3TQ OJQG EZDG NBVG Y3TQ OJQ";
    expect(verify(secret, "287082", 59_000, 0)).toBe(true);
    expect(verify(secret, "287082", 89_000, 1)).toBe(true);
    expect(verify(secret, "000000", 59_000, 1)).toBe(false);
  });

  it("treats Keycloak-shaped raw secrets as ASCII, not Base32", () => {
    const raw = "No2rS9ueOiCId3xDKeAq";
    expect(looksLikeBase32(raw)).toBe(false);
    expect(generate(raw, 1_700_000_000_000)).toHaveLength(6);
  });

  it("builds an otpauth URI for manual authenticator entry", () => {
    const uri = otpauthUri({
      issuer: "NexusOne",
      account: "admin-a@example.com",
      secret: "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
    });
    expect(uri).toContain("otpauth://totp/NexusOne:admin-a%40example.com");
    expect(uri).toContain("algorithm=SHA1");
    expect(uri).toContain("digits=6");
    expect(groupSecret("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).toBe(
      "GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ",
    );
  });
});
