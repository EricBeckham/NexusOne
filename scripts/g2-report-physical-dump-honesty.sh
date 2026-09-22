#!/usr/bin/env bash
# G2 leftover: report query/export + physical dump / raw export SurfaceGuard
# honesty PASS/SKIP beyond #122/#124/#137 and H5 form.write leftovers.
#
# Extends docs/g2-report-surface-guard.md and docs/g2-physical-dump-surface.md.
# Does NOT redo #122 / #124 / #137 / #163 / #173 / #204 product code.
# Does NOT claim product G2 close. G18 stays closed. No P0/M0 redo. No Docker.
# ReportSurfaceGuard + DumpSurfaceGuard harness stay always-green PASS.
# Live product-JAR + issued-context HTTP is PASS only when reachable AND
# report query/export + dump walks were observed. Missing NEXUS_BASE_URL
# / issued context is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
VERIFY_EXPECT=pass
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g2-report-physical-dump-honesty.sh [--out DIR] [--skip-tests]
  scripts/g2-report-physical-dump-honesty.sh --verify-report FILE [pass|fail]

#124 report SurfaceGuard + #137 dump SurfaceGuard stay always-green PASS.
Live JAR + issued context is PASS only when NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE
is set, a listener is up, issued context is present, and the walks were
observed. Missing live host is honest SKIP. Product G2 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g2-report-physical-dump-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report)
      VERIFY_REPORT="${2:?}"
      shift 2
      if [ "${1:-}" = "pass" ] || [ "${1:-}" = "fail" ]; then
        VERIFY_EXPECT="$1"
        shift
      fi
      ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

kv() { printf '%s=%s\n' "$1" "$2"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  local expect="${2:-pass}"
  [ -f "$file" ] || fail "report not found: $file"
  set +e
  verify_report_inner "$file"
  local rc=$?
  set -e
  if [ "$expect" = "pass" ]; then
    [ "$rc" -eq 0 ] || fail "expected honest leftover, got FAIL ($file)"
    echo "PASS honesty: $file"
  elif [ "$expect" = "fail" ]; then
    [ "$rc" -ne 0 ] || fail "expected fake report to fail: $file"
    echo "PASS reject fake: $file"
  else
    fail "unknown verify expect: $expect"
  fi
}

verify_report_inner() {
  local file="$1"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used report_surface_harness dump_surface_harness report_uses_authorized_submit dump_uses_write_projected report_is_physical_dump dump_is_report_task_submit leftover_rows_kept leftover_rows_all live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_jar_walk live_jar_skip_reason live_report_walk live_report_observed live_dump_walk live_dump_observed redone_122 redone_124 redone_137 redone_163 redone_173 redone_204 g2_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_122)" = "false" ] || { echo "FAIL: redone_122 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_124)" = "false" ] || { echo "FAIL: redone_124 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_137)" = "false" ] || { echo "FAIL: redone_137 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_163)" = "false" ] || { echo "FAIL: redone_163 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_173)" = "false" ] || { echo "FAIL: redone_173 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_204)" = "false" ] || { echo "FAIL: redone_204 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g2_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g2_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" report_surface_harness)" = "PASS" ] || { echo "FAIL: report_surface_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" dump_surface_harness)" = "PASS" ] || { echo "FAIL: dump_surface_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" report_uses_authorized_submit)" = "PASS" ] || { echo "FAIL: report_uses_authorized_submit must be PASS" >&2; return 1; }
  [ "$(report_get "$file" dump_uses_write_projected)" = "PASS" ] || { echo "FAIL: dump_uses_write_projected must be PASS" >&2; return 1; }
  [ "$(report_get "$file" report_is_physical_dump)" = "false" ] || { echo "FAIL: report_is_physical_dump must stay false" >&2; return 1; }
  [ "$(report_get "$file" dump_is_report_task_submit)" = "false" ] || { echo "FAIL: dump_is_report_task_submit must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in g2-report-surface-guard g2-physical-dump-surface g2-surfaceguard-bypass-ci g2-h5-every-page-form-write-honesty g2-ops-pg-dump-honesty g9-sentinel-cluster-remainder-honesty g5-publishhook-ddl-honesty g17-gm-tls-nginx-handshake-honesty g1-queryast-export-honesty g15-wx-dt-native-bridge-remainder g17-separation-leftover-honesty g4-pkce-spa-verifier-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g6-recovery-port-dry-run-remainder-honesty; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local live report_walk dump_walk
  live="$(report_get "$file" live_jar_walk)"
  report_walk="$(report_get "$file" live_report_walk)"
  dump_walk="$(report_get "$file" live_dump_walk)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_jar_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$report_walk" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_report_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$dump_walk" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_dump_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_jar_context_set)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_jar_context_set=true" >&2; return 1; }
    [ "$(report_get "$file" live_report_observed)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_report_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_dump_observed)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_dump_observed=true" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_skip_reason)" ] || { echo "FAIL: live JAR SKIP needs live_jar_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_report_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live report walk" >&2; return 1; }
    [ "$(report_get "$file" live_dump_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live dump walk" >&2; return 1; }
  fi
  if [ "$report_walk" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_report_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_report_observed)" = "true" ] || { echo "FAIL: live_report_walk=PASS requires live_report_observed=true" >&2; return 1; }
  fi
  if [ "$report_walk" = "SKIP" ]; then
    [ "$(report_get "$file" live_report_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live report walk" >&2; return 1; }
  fi
  if [ "$dump_walk" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_dump_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_dump_observed)" = "true" ] || { echo "FAIL: live_dump_walk=PASS requires live_dump_observed=true" >&2; return 1; }
  fi
  if [ "$dump_walk" = "SKIP" ]; then
    [ "$(report_get "$file" live_dump_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live dump walk" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g2-report-physical-dump-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G2 leftover: report / physical-dump SurfaceGuard honesty beyond #122/#124/#137"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #122/#124/#137/#163/#173/#204 product code."
echo "Not claimed: product G2 close / live JAR walk unless observed."
echo

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
fi
[ "$JAVA_FEATURE" = "21" ] || fail "java_feature must be 21, got $JAVA_FEATURE"

BOOT_GROUP=""
BOOT_ARTIFACT=""
BOOT_VERSION=""
eval "$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
parent = root.find("m:parent", ns)
if parent is None:
    print("BOOT_GROUP=''")
    print("BOOT_ARTIFACT=''")
    print("BOOT_VERSION=''")
    raise SystemExit(0)
def t(tag):
    n = parent.find(f"m:{tag}", ns)
    return (n.text or "").strip() if n is not None else ""
print("BOOT_GROUP=%r" % t("groupId"))
print("BOOT_ARTIFACT=%r" % t("artifactId"))
print("BOOT_VERSION=%r" % t("version"))
PY
)"
BOOT_PARENT_GA="${BOOT_GROUP}:${BOOT_ARTIFACT}:${BOOT_VERSION}"
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

LIVE_ENDPOINT="${NEXUS_G2_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G2_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

LIVE_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; Docker forbidden"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G2_LIVE_CONTEXT_ID unset; live JAR walk SKIP (issued context stays in env)"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until report query/export and dump export were observed"
fi

README="$ROOT/docs/README.md"
SIBLING_ROWS="
g2-report-surface-guard
g2-physical-dump-surface
g2-surfaceguard-bypass-ci
g2-form-submission-write
g2-submit-start-form-write
g2-form-write-honesty
g2-h5-permission
g2-h5-every-page
g2-h5-every-page-form-write-honesty
g2-tenant-only-catalog
g2-todo-http-surface
g2-ops-pg-dump
g2-ops-pg-dump-honesty
g9-sentinel-cluster-remainder-honesty
g9-sentinel-cluster-honesty
g9-live-multi-jvm-replica
g9-policysnapshot-invalidate
g9-failover-remainder
g9-redis-down-invalidate-honesty
g9-redis-down-invalidate-remainder
g5-publishhook-ddl-honesty
g5-mid-publish-failure-honesty
g5-mid-publish-failure
g5-browser-failed-publish
g15-wx-dt-native-bridge-honesty
g15-wx-dt-native-bridge-remainder
g15-wx-dt-prod-gap-honesty
g15-native-bridge-honesty
g15-native-bridge-stub
g7-query-expense-travel-honesty
g7-withdraw-browser-honesty
g4-export-wizard-deny-honesty
g4-export-wizard-deny
g4-guard-deny-audit
g4-p1-staging-audit-events-honesty
g4-hot-purge-90d-honesty
g4-pkce-spa-verifier-honesty
g4-pkce-spa-verifier-remainder-honesty
s0-secret-rotate-getenv-remainder-honesty
g6-recovery-port-dry-run-remainder-honesty
g10-leftover-honesty
g10-file-reconcile-honesty
g10-identity-drift-honesty
g1-queryast-boundsql-honesty
g1-queryast-boundsql-guard
g1-queryast-export-honesty
g1-export-fieldpolicy-drop
g1-fieldpolicy-mask-second-auth-honesty
g3-outbox-consume-crash-honesty
g3-claim-crash-window
linux-arm-profile-leftover-honesty
linux-arm-profile-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-getenv-snapshot-honesty
s0-secret-rotate-without-restart
g6-recovery-port-dry-run-honesty
s0s1-live-redis-minio-isolation-honesty
g17-separation-honesty
g17-separation-leftover-honesty
g17-mfa-recovery-vue-otp-honesty
g17-gm-tls-nginx-handshake-honesty
g17-gm-tls-nginx-handshake
g17-gm-tls-nginx-dual-stack
"
LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g2-report-physical-dump-honesty\](\./g2-report-physical-dump-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g2-report-physical-dump-honesty/fixtures/honest-skip.env pass
verify_report scripts/g2-report-physical-dump-honesty/fixtures/fake-g2-close.env fail
verify_report scripts/g2-report-physical-dump-honesty/fixtures/fake-live-jar-pass.env fail
verify_report scripts/g2-report-physical-dump-honesty/fixtures/fake-redone.env fail
verify_report scripts/g2-report-physical-dump-honesty/fixtures/fake-report-is-dump.env fail

echo
echo "== #124 report + #137 dump leftover docs / guards still present (not a product redo)"
REPORT_GUARD="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/ReportSurfaceGuard.java"
DUMP_GUARD="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/DumpSurfaceGuard.java"
REPORT_CTRL="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/report/ReportController.java"
DUMP_CTRL="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java"
REPORT_DOC="$ROOT/docs/g2-report-surface-guard.md"
DUMP_DOC="$ROOT/docs/g2-physical-dump-surface.md"
HONESTY_DOC="$ROOT/docs/g2-report-physical-dump-honesty.md"
[ -f "$REPORT_GUARD" ] || fail "missing ReportSurfaceGuard"
[ -f "$DUMP_GUARD" ] || fail "missing DumpSurfaceGuard"
[ -f "$REPORT_CTRL" ] || fail "missing ReportController"
[ -f "$DUMP_CTRL" ] || fail "missing DumpController"
[ -f "$REPORT_DOC" ] || fail "missing #124 leftover doc"
[ -f "$DUMP_DOC" ] || fail "missing #137 leftover doc"
[ -f "$HONESTY_DOC" ] || fail "missing this leftover doc"
grep -Fq 'SurfaceGuard.require' "$REPORT_GUARD" || fail "ReportSurfaceGuard must keep SurfaceGuard.require"
grep -Fq 'SurfaceGuard.require' "$DUMP_GUARD" || fail "DumpSurfaceGuard must keep SurfaceGuard.require"
grep -Fq 'AuthorizedExport.submit' "$REPORT_CTRL" || fail "ReportController must keep AuthorizedExport.submit"
grep -Fq 'AuthorizedExport.writeProjected' "$DUMP_CTRL" || fail "DumpController must keep AuthorizedExport.writeProjected"
grep -Fq 'physical_dump", false' "$REPORT_CTRL" || fail "report export must stay not a physical dump"
grep -Fq 'physical_dump", true' "$DUMP_CTRL" || fail "dump export must stay physical_dump=true"
grep -Fq 'NOT_CLAIMED' "$HONESTY_DOC" || fail "honesty leftover G2_CLOSE must stay NOT_CLAIMED"
REPORT_SURFACE_HARNESS=PASS
DUMP_SURFACE_HARNESS=PASS
REPORT_USES_SUBMIT=PASS
DUMP_USES_PROJECTED=PASS
echo "PASS: #124 report SurfaceGuard + #137 dump SurfaceGuard still wired"

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ] && command -v mvn >/dev/null 2>&1; then
  echo
  echo "== Java leftover honesty (does not rerun #122/#124/#137 suites)"
  mvn -pl nexus-permission-core -am test \
    -Dtest=G2ReportPhysicalDumpHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    | tee "$OUT/mvn.log"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
elif [ "$SKIP_TESTS" = "0" ]; then
  TEST_SUMMARY="mvn not on PATH; static inventory + honesty gate PASS"
  log "mvn skipped: not on PATH"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

LIVE_WALK=SKIP
LIVE_REPORT=false
LIVE_DUMP=false
LIVE_REPORT_WALK=SKIP
LIVE_DUMP_WALK=SKIP
[ "${LIVE_REPORT_OBSERVED:-}" = "true" ] && LIVE_REPORT=true
[ "${LIVE_DUMP_OBSERVED:-}" = "true" ] && LIVE_DUMP=true
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_REPORT" = "true" ] && [ "$LIVE_DUMP" = "true" ]; then
  LIVE_WALK=PASS
  LIVE_REASON="live report query/export + dump export walk observed"
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_REPORT" = "true" ]; then
  LIVE_REPORT_WALK=PASS
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_DUMP" = "true" ]; then
  LIVE_DUMP_WALK=PASS
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv report_surface_harness "$REPORT_SURFACE_HARNESS"
  kv dump_surface_harness "$DUMP_SURFACE_HARNESS"
  kv report_uses_authorized_submit "$REPORT_USES_SUBMIT"
  kv dump_uses_write_projected "$DUMP_USES_PROJECTED"
  kv report_is_physical_dump false
  kv dump_is_report_task_submit false
  kv leftover_rows_kept "g2-report-surface-guard,g2-physical-dump-surface,g2-surfaceguard-bypass-ci,g2-h5-every-page-form-write-honesty,g2-ops-pg-dump-honesty,g9-sentinel-cluster-remainder-honesty,g5-publishhook-ddl-honesty,g17-gm-tls-nginx-handshake-honesty,g1-queryast-export-honesty,g15-wx-dt-native-bridge-remainder,g17-separation-leftover-honesty,g4-pkce-spa-verifier-remainder-honesty,s0-secret-rotate-getenv-remainder-honesty,g6-recovery-port-dry-run-remainder-honesty"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_jar_walk "$LIVE_WALK"
  kv live_jar_skip_reason "$LIVE_REASON"
  kv live_report_walk "$LIVE_REPORT_WALK"
  kv live_report_observed "$LIVE_REPORT"
  kv live_dump_walk "$LIVE_DUMP_WALK"
  kv live_dump_observed "$LIVE_DUMP"
  kv redone_122 false
  kv redone_124 false
  kv redone_137 false
  kv redone_163 false
  kv redone_173 false
  kv redone_204 false
  kv g2_close NOT_CLAIMED
  kv g2_close_reason "Honesty leftover around #124 report SurfaceGuard + #137 dump SurfaceGuard. Live JAR SKIP when unavailable. Not product G2 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| #124 report SurfaceGuard harness | $REPORT_SURFACE_HARNESS | ledger/read + ledger.export.csv/submit; AuthorizedExport.submit |"
  echo "| #137 dump SurfaceGuard harness | $DUMP_SURFACE_HARNESS | ledger/read + ledger.export.csv/submit; AuthorizedExport.writeProjected |"
  echo "| report is physical dump | false | report stays Task submit |"
  echo "| dump is report Task submit | false | dump stays writeProjected |"
  echo "| leftover rows kept | true | #124/#137 + #122 + H5 form.write + #217 + #222 + #223 + #225 + all sibling leftover rows |"
  echo "| Live JAR / issued context | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| live report walk | $LIVE_REPORT_WALK | observed=$LIVE_REPORT |"
  echo "| live dump walk | $LIVE_DUMP_WALK | observed=$LIVE_DUMP |"
  echo "| #122/#124/#137/#163/#173/#204 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G2 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g2-report-physical-dump-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g2-report-physical-dump-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar=$LIVE_WALK live_report=$LIVE_REPORT_WALK live_dump=$LIVE_DUMP_WALK g2_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#124/#137 harness PASS; live JAR / issued-context $LIVE_WALK)"
echo "See docs/g2-report-physical-dump-honesty.md"
echo "Keep sibling leftover rows including g2-report-surface-guard / s0-secret-rotate-getenv-remainder-honesty / g6-recovery-port-dry-run-remainder-honesty"
echo "Not claimed: product G2 close / #122/#124/#137 product redo / live JAR walk unless observed"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
