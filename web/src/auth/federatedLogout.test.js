import { describe, expect, it } from "vitest";
import { endSessionTarget } from "./federatedLogout";

const ISSUER = "https://id.example.test/realms/nexus";
const IDP = { configured: true, issuer: ISSUER };
const LOGOUT = `${ISSUER}/protocol/openid-connect/logout?client_id=nexus-web&id_token_hint=h.i.t&post_logout_redirect_uri=https%3A%2F%2Fapp.example.test%2Flogin`;

describe("LO4 end_session target", () => {
  it("accepts the issuer logout URL in snake_case or camelCase", () => {
    expect(endSessionTarget({ end_session_url: LOGOUT }, IDP)).toBe(LOGOUT);
    expect(endSessionTarget({ endSessionUrl: LOGOUT }, IDP)).toBe(LOGOUT);
  });

  it("stays on /login when there is no URL", () => {
    for (const response of [null, undefined, {}, { end_session_url: "" }, { end_session_url: "  " }, { end_session_url: 42 }]) {
      expect(endSessionTarget(response, IDP)).toBe("");
    }
  });

  it("refuses token-like query fields, credentials in the URL and non-http schemes", () => {
    expect(endSessionTarget({ end_session_url: `${LOGOUT}&access_token=x` }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: `${LOGOUT}&refresh_token=x` }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: `${LOGOUT}&client_secret=x` }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: `${LOGOUT}&ID_TOKEN=x` }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: LOGOUT.replace("https://", "https://u:p@") }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: "javascript:alert(1)" }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: "not a url" }, IDP)).toBe("");
  });

  it("only follows the configured issuer's logout endpoint", () => {
    expect(endSessionTarget({ end_session_url: LOGOUT.replace("id.example.test", "evil.example.test") }, IDP)).toBe("");
    expect(endSessionTarget({ end_session_url: `${ISSUER}/protocol/openid-connect/auth?x=1` }, IDP)).toBe("");
    // Without a configured public issuer the scheme / token checks still apply.
    expect(endSessionTarget({ end_session_url: LOGOUT }, { configured: false })).toBe(LOGOUT);
  });
});
