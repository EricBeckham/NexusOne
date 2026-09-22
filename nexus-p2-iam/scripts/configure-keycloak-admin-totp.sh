#!/usr/bin/env bash
# Attach AMR mapper + authenticator reference values so a live OTP walk
# produces amr=otp. Native Keycloak 26.5.2 only. Secrets from env.
set -euo pipefail

KC_VER="${KEYCLOAK_VERSION:-26.5.2}"
KC_HOME="${KEYCLOAK_HOME:-/home/ubuntu/keycloak-${KC_VER}}"
KC_BASE="${NEXUS_KEYCLOAK_BASE:-http://127.0.0.1:8180}"
CLIENT_ID="${NEXUS_KEYCLOAK_CLIENT_ID:-nexus-platform}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-kcadmin}"

if [[ ! -x "${KC_HOME}/bin/kcadm.sh" ]]; then
  echo "ERROR: kcadm.sh not found at ${KC_HOME}/bin/kcadm.sh" >&2
  exit 1
fi
if [[ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD:-}" ]]; then
  echo "ERROR: KC_BOOTSTRAP_ADMIN_PASSWORD is required" >&2
  exit 1
fi

"${KC_HOME}/bin/kcadm.sh" config credentials \
  --server "${KC_BASE}" --realm master \
  --user "${ADMIN_USER}" --password "${KC_BOOTSTRAP_ADMIN_PASSWORD}" >/dev/null

CLIENT_UUID="$("${KC_HOME}/bin/kcadm.sh" get clients -r nexus -q "clientId=${CLIENT_ID}" --fields id \
  | python3 -c "import json,sys; data=json.load(sys.stdin); print(data[0]['id'] if data else '')")"
if [[ -z "${CLIENT_UUID}" ]]; then
  echo "ERROR: client ${CLIENT_ID} not found" >&2
  exit 1
fi

python3 - <<PY
import json, subprocess, os, sys
kc = os.environ.get("KEYCLOAK_HOME", "${KC_HOME}")
kcadm = os.path.join(kc, "bin", "kcadm.sh")
client_uuid = "${CLIENT_UUID}"

def kcadm_json(*args):
    out = subprocess.check_output([kcadm, *args], text=True)
    return json.loads(out) if out.strip() else None

mappers = kcadm_json("get", f"clients/{client_uuid}/protocol-mappers/models", "-r", "nexus") or []
if not any(m.get("protocolMapper") == "oidc-amr-mapper" for m in mappers):
    subprocess.check_call([
        kcadm, "create", f"clients/{client_uuid}/protocol-mappers/models", "-r", "nexus",
        "-s", "name=amr",
        "-s", "protocol=openid-connect",
        "-s", "protocolMapper=oidc-amr-mapper",
        "-s", "consentRequired=false",
        "-s", 'config.access.token.claim=true',
        "-s", 'config.id.token.claim=true',
        "-s", 'config.userinfo.token.claim=true',
        "-s", 'config.introspection.token.claim=true',
    ], stdout=subprocess.DEVNULL)
    print("created oidc-amr-mapper on ${CLIENT_ID}")
else:
    print("oidc-amr-mapper already present on ${CLIENT_ID}")

executions = kcadm_json("get", "authentication/flows/browser/executions", "-r", "nexus") or []
want = {
    "auth-username-password-form": "pwd",
    "auth-otp-form": "otp",
}
for execution in executions:
    provider = execution.get("providerId")
    if provider not in want:
        continue
    reference = want[provider]
    exec_id = execution.get("id")
    config_id = execution.get("authenticationConfig")
    body = {
        "alias": f"{provider}-amr",
        "config": {
            "default.reference.value": reference,
            "default.reference.maxAge": "3600",
        },
    }
    if config_id:
        current = kcadm_json("get", f"authentication/config/{config_id}", "-r", "nexus") or {}
        body["id"] = config_id
        body["alias"] = current.get("alias") or body["alias"]
        merged = dict(current.get("config") or {})
        merged.update(body["config"])
        body["config"] = merged
        subprocess.check_call([
            kcadm, "update", f"authentication/config/{config_id}", "-r", "nexus",
            "-s", f"alias={body['alias']}",
            "-s", f"config.default.reference.value={reference}",
            "-s", "config.default.reference.maxAge=3600",
        ], stdout=subprocess.DEVNULL)
        print(f"updated {provider} reference={reference} maxAge=3600")
    else:
        subprocess.check_call([
            kcadm, "create", f"authentication/executions/{exec_id}/config", "-r", "nexus",
            "-s", f"alias={provider}-amr",
            "-s", f"config.default.reference.value={reference}",
            "-s", "config.default.reference.maxAge=3600",
        ], stdout=subprocess.DEVNULL)
        print(f"created {provider} reference={reference} maxAge=3600")
PY
