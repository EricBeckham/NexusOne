#!/usr/bin/env bash
# G17 leftover: local GM TLS dual-cert material + TLS 1.3 SM4 handshake proof.
# Does not claim GM/T 0024 Nginx, classified delivery, or 密码机/HSM.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"

mvn -pl nexus-crypto-core -am test \
  -Dtest=GmTlsDualCertTest,GmTlsHandshakeTest \
  -Dsurefire.failIfNoSpecifiedTests=false

if [[ "${NEXUS_GM_TLS_WRITE:-}" == "1" ]]; then
  OUT="${NEXUS_GM_TLS_DIR:-${ROOT}/.local/g17-gm-tls}"
  mkdir -p "$OUT"
  mvn -pl nexus-crypto-core -am -DskipTests package \
    dependency:build-classpath -DincludeScope=runtime \
    -Dmdep.outputFile=nexus-crypto-core/target/runtime.cp
  java -cp "nexus-crypto-core/target/classes:nexus-common/target/classes:$(cat nexus-crypto-core/target/runtime.cp)" \
    one.nexus.crypto.tls.GmTlsMaterialWriter "$OUT"
fi
