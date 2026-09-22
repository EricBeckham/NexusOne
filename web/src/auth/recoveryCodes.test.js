import { describe, expect, it } from "vitest";
import {
  RECOVERY_CODES,
  RECOVERY_COPY,
  consumeRecoveryCode,
  formatRecoveryCode,
  hashRecoveryCode,
  issueRecoveryCodes,
  looksLikeRecoveryCode,
} from "./recoveryCodes";

describe("recovery code leftover", () => {
  it("issues eight grouped codes and consumes one once", async () => {
    let n = 0;
    const issued = issueRecoveryCodes({ random: () => n++ });
    expect(issued.ok).toBe(true);
    expect(issued.session.plaintext).toHaveLength(8);
    expect(issued.session.plaintext[0]).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    const first = issued.session.plaintext[0];
    expect(looksLikeRecoveryCode(first)).toBe(true);
    const digest = await hashRecoveryCode(first);
    expect(digest).toHaveLength(64);
    const consumed = await consumeRecoveryCode(issued.session, first.toLowerCase());
    expect(consumed.ok).toBe(true);
    expect(consumed.session.remaining).toHaveLength(7);
    const reuse = await consumeRecoveryCode(consumed.session, first);
    expect(reuse.code).toBe(RECOVERY_CODES.RECOVERY_ALREADY_USED);
  });

  it("rejects blank, format, wrong, and no-issued paths", async () => {
    expect((await consumeRecoveryCode(null, "AB2D-EF3G")).code).toBe(RECOVERY_CODES.RECOVERY_NO_ISSUED);
    const issued = issueRecoveryCodes({ random: () => 3 });
    expect((await consumeRecoveryCode(issued.session, "")).code).toBe(RECOVERY_CODES.RECOVERY_BLANK);
    expect((await consumeRecoveryCode(issued.session, "12")).code).toBe(RECOVERY_CODES.RECOVERY_FORMAT);
    expect((await consumeRecoveryCode(issued.session, "ZZZZ-ZZZZ")).code).toBe(
      RECOVERY_CODES.RECOVERY_VERIFY_FAILED,
    );
    expect(formatRecoveryCode("ab2def3g")).toBe("AB2D-EF3G");
    expect(RECOVERY_COPY.body).toContain("不重开 G18");
    expect(RECOVERY_COPY.hint).toContain("不把动态码写回 Keycloak");
  });
});
