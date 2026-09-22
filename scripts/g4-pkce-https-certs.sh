#!/usr/bin/env bash
# G4 leftover: local self-signed HTTPS material for Keycloak + product loopback.
# Writes under .local/g4-https (gitignored). Password file is 0600, not for git.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="${NEXUS_G4_HTTPS_DIR:-${ROOT}/.local/g4-https}"
ENV_FILE="${NEXUS_G4_HTTPS_ENV_FILE:-/tmp/g4-https.env}"
mkdir -p "${DIR}"
chmod 700 "${DIR}"

if [[ -z "${NEXUS_G4_HTTPS_STORE_PASSWORD:-}" ]]; then
  NEXUS_G4_HTTPS_STORE_PASSWORD="$(openssl rand -hex 16)"
fi

openssl req -x509 -newkey rsa:2048 -sha256 -days 3 -nodes \
  -keyout "${DIR}/server.key" \
  -out "${DIR}/server.crt" \
  -subj "/CN=127.0.0.1" \
  -addext "subjectAltName=IP:127.0.0.1,DNS:localhost"

openssl pkcs12 -export \
  -inkey "${DIR}/server.key" \
  -in "${DIR}/server.crt" \
  -out "${DIR}/server.p12" \
  -name loopback \
  -passout "pass:${NEXUS_G4_HTTPS_STORE_PASSWORD}"

rm -f "${DIR}/truststore.p12"
keytool -importcert -noprompt \
  -alias g4-loopback \
  -file "${DIR}/server.crt" \
  -keystore "${DIR}/truststore.p12" \
  -storetype PKCS12 \
  -storepass "${NEXUS_G4_HTTPS_STORE_PASSWORD}" >/dev/null

chmod 600 "${DIR}/server.key" "${DIR}/server.p12" "${DIR}/truststore.p12"
chmod 644 "${DIR}/server.crt"

umask 077
cat >"${ENV_FILE}" <<EOF
export NEXUS_G4_HTTPS_DIR='${DIR}'
export NEXUS_G4_HTTPS_KEYSTORE='${DIR}/server.p12'
export NEXUS_G4_HTTPS_TRUSTSTORE='${DIR}/truststore.p12'
export NEXUS_G4_HTTPS_CERT='${DIR}/server.crt'
export NEXUS_G4_HTTPS_KEY='${DIR}/server.key'
export NEXUS_G4_HTTPS_STORE_PASSWORD='${NEXUS_G4_HTTPS_STORE_PASSWORD}'
EOF
chmod 600 "${ENV_FILE}"
printf '%s\n' "Wrote ${DIR}/server.{crt,key,p12} and ${ENV_FILE} (mode 0600; not for git)"
