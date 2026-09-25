#!/usr/bin/env bash
# Build nexus-bootstrap JAR and print SHA-256 (M6 / P6).
# Does NOT deploy. Does NOT write secrets. Template ≠ deployed.
#
# Checklist: docs/engineering/runbooks/demo-host-prod-deployment.md
# Env template: docs/engineering/runbooks/demo-host-prod.env.template
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

GIT_SHA="$(git rev-parse HEAD)"
echo "git HEAD: ${GIT_SHA} ($(git rev-parse --abbrev-ref HEAD))"
echo "NOTE: template/script ≠ demo host deployed. No secrets in this script."

mvn -q -pl nexus-bootstrap -am -DskipTests package

JAR="${ROOT}/nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar"
if [[ ! -f "${JAR}" ]]; then
  echo "JAR missing: ${JAR}" >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  HASH="$(sha256sum "${JAR}" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  HASH="$(shasum -a 256 "${JAR}" | awk '{print $1}')"
else
  echo "sha256sum/shasum not found" >&2
  exit 1
fi

SIZE="$(wc -c < "${JAR}" | tr -d ' ')"
echo
echo "JAR:      ${JAR}"
echo "SIZE:     ${SIZE} bytes"
echo "SHA-256:  ${HASH}"
echo "GIT_SHA:  ${GIT_SHA}"
echo
echo "Record GIT_SHA + SHA-256 with the release; re-hash on the demo host after transfer (P6 §0)."
echo "This script does not copy the JAR to any remote host."
