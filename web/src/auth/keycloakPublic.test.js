/** @vitest-environment happy-dom */
import { describe, expect, it } from "vitest";
import { IDP_UNCONFIGURED_COPY, keycloakAuthorizeHref, keycloakPublicConfig } from "./keycloakPublic";

describe("keycloak public config (DPO-C1)", () => {
  it("stays unconfigured when the public issuer is absent", () => {
    const config = keycloakPublicConfig({});
    expect(config.configured).toBe(false);
    expect(config.issuer).toBe("");
    expect(config.unconfiguredCopy).toBe(IDP_UNCONFIGURED_COPY);
    expect(JSON.stringify(config)).not.toMatch(/SECRET/i);
  });

  it("exposes only the public issuer and client id when configured", () => {
    const config = keycloakPublicConfig({
      VITE_NEXUS_KEYCLOAK_ISSUER: "https://id.example.test/realms/nexus/",
      VITE_NEXUS_KEYCLOAK_CLIENT_ID: "nexus-web",
      VITE_NEXUS_KEYCLOAK_CLIENT_SECRET: "must-not-leak",
      NEXUS_IAM_KEYCLOAK_CLIENT_SECRET: "must-not-leak",
    });
    expect(config.configured).toBe(true);
    expect(config.issuer).toBe("https://id.example.test/realms/nexus");
    expect(config.clientId).toBe("nexus-web");
    expect(Object.keys(config)).toEqual(["configured", "issuer", "clientId", "unconfiguredCopy"]);
    expect(JSON.stringify(config)).not.toContain("must-not-leak");
  });

  it("builds an authorize URL without a secret parameter", () => {
    const href = keycloakAuthorizeHref({
      issuer: "https://id.example.test/realms/nexus",
      clientId: "nexus-web",
      redirectUri: "http://127.0.0.1:4173/callback",
      state: "dpo-c1",
    });
    const url = new URL(href);
    expect(url.pathname).toContain("/protocol/openid-connect/auth");
    expect(url.searchParams.get("client_id")).toBe("nexus-web");
    expect(url.searchParams.get("response_type")).toBe("code");
    expect(url.searchParams.has("client_secret")).toBe(false);
    expect(href).not.toMatch(/SECRET/i);
  });
});
