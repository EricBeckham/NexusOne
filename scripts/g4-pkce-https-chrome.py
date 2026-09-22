#!/usr/bin/env python3
"""Optional Chromium CDP walk against an already-running HTTPS product + Keycloak.

The Java HTTP/2 driver in G4PkceHttpsBrowserIT is the required LOGIN evidence.
This script is for a human/agent machine that already has:
  - Keycloak HTTPS (source /tmp/keycloak-https.env)
  - product HTTPS authorize/login
  - Chrome/Chromium

It does not print secrets. It does not claim a full G4 close.
"""
from __future__ import annotations

import json
import os
import ssl
import sys
import urllib.error
import urllib.request


def main() -> int:
    issuer = os.environ.get("NEXUS_KEYCLOAK_ISSUER", "")
    product = os.environ.get("NEXUS_HTTPS_BASE", "https://127.0.0.1:18443")
    cert = os.environ.get("NEXUS_G4_HTTPS_CERT", "")
    if not issuer.startswith("https://"):
        print("BLOCKED: NEXUS_KEYCLOAK_ISSUER is not HTTPS; source /tmp/keycloak-https.env")
        return 2
    if not cert or not os.path.isfile(cert):
        print("BLOCKED: NEXUS_G4_HTTPS_CERT missing; run scripts/g4-pkce-https-certs.sh")
        return 2
    ctx = ssl.create_default_context()
    ctx.load_verify_locations(cert)
    url = product.rstrip("/") + "/api/v1/auth/authorize-url?pkce=s256"
    try:
        with urllib.request.urlopen(url, context=ctx, timeout=10) as response:
            body = json.loads(response.read().decode())
    except urllib.error.URLError as exc:
        print("BLOCKED: product authorize-url not reachable:", exc)
        print("Use G4PkceHttpsBrowserIT for LOGIN evidence, or start the HTTPS product JAR first.")
        return 3
    authorize = body.get("url", "")
    if "code_challenge=" not in authorize:
        print("BLOCKED: authorize URL missing PKCE")
        return 4
    print("G4_HTTPS_CHROME_READY authorize=", authorize.split("?", 1)[0])
    print("Human/CDP next: open authorize URL, sign in, POST /api/v1/auth/login with code + code_verifier.")
    print("GET /api/v1/auth/callback does not carry the verifier.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
