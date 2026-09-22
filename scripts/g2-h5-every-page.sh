#!/usr/bin/env bash
# G2 leftover: H5 every-page SurfaceGuard coverage honesty.
# States which /m/* pages are covered (#116) vs still open.
# Intentional fixtures fail. Product router + inventory pass.
# Does not redo #116 H5 /check / #150 todo HTTP / #144 tenant_only / #137 dump.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close.
# Sibling leftover g2-ops-pg-dump stays. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 H5 every-page SurfaceGuard honesty"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1
node -v
npm -v

echo
echo "== JS inventory + assert (router classified; sibling leftover rows kept)"
(
  cd web
  npm test -- src/mobile/permission/h5PageInventory.test.js
  npm run assert:h5-every-page
)

echo
echo "== Gate unit tests (unclassified route + fake G2 close fail; product passes)"
mvn -pl nexus-permission-core -am test \
  -Dtest=H5PageSurfaceHonestyGateTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "== Product scan CLI must pass"
mvn -f nexus-permission-core/pom.xml -q exec:java@h5-page-honesty \
  -Dexec.args="--repo ${ROOT}"

echo
echo "== Unclassified /m route fixture must fail the CLI"
set +e
mvn -f nexus-permission-core/pom.xml -q exec:java@h5-page-honesty \
  -Dexec.args="--source ${ROOT}/nexus-permission-core/src/test/resources/h5-every-page-fixtures/negative/unclassified"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "expected unclassified /m route fixture to fail the static gate"
  exit 1
fi
echo "PASS static rejection: unclassified-m-route"

echo
echo "== Fake G2 close fixture must fail the CLI"
set +e
mvn -f nexus-permission-core/pom.xml -q exec:java@h5-page-honesty \
  -Dexec.args="--source ${ROOT}/nexus-permission-core/src/test/resources/h5-every-page-fixtures/negative/fake-close"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "expected fake g2_close=CLOSED fixture to fail the static gate"
  exit 1
fi
echo "PASS static rejection: g2-close-claimed"

echo
echo "== ArchUnit H5SurfaceGuard twin + product scan"
mvn -pl nexus-bootstrap -am test \
  -Dtest=G2H5PageHonestyTest,H5PageSurfaceHonestyArchTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: H5 /m/* coverage honesty; list/detail/form/todo/query covered; home/files/notify/login/host open"
echo "See docs/g2-h5-every-page.md"
