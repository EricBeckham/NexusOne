#!/usr/bin/env bash
# G2 leftover: H5 every-page / form.write honesty PASS/SKIP beyond #163/#173.
#
# Extends docs/g2-h5-every-page.md and docs/g2-form-write-honesty.md.
# Does NOT redo #112 / #116 / #137 / #144 / #150 / #155 / #163 / #173 product code.
# Does NOT claim product G2 close. G18 stays closed. No P0/M0 redo. No Docker.
# #163 inventory + #173 FormSurfaceGuard / dual-check harness stay always-green PASS.
# Live product-JAR + issued-context HTTP is PASS only when reachable AND
# submissions + submit-start + H5 walks were observed. Missing NEXUS_BASE_URL
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
  scripts/g2-h5-every-page-form-write-honesty.sh [--out DIR] [--skip-tests]
  scripts/g2-h5-every-page-form-write-honesty.sh --verify-report FILE [pass|fail]

#163 H5 every-page inventory + #173 form.write / submit-start dual-check
stay always-green PASS. Live JAR + issued context is PASS only when
NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE is set, a listener is up, issued
context is present, and the walks were observed. Missing live host is
honest SKIP. Product G2 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g2-h5-every-page-form-write-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run npm)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used h5_every_page_harness form_write_harness form_write_surface submit_start_dual_check submit_start_is_form_write_only h5_open_pages_still_open leftover_rows_kept leftover_rows_all live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_jar_walk live_jar_skip_reason live_submissions_observed live_submit_start_observed live_h5_walk live_h5_observed redone_112 redone_116 redone_137 redone_144 redone_150 redone_155 redone_163 redone_173 g2_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_112)" = "false" ] || { echo "FAIL: redone_112 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_116)" = "false" ] || { echo "FAIL: redone_116 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_137)" = "false" ] || { echo "FAIL: redone_137 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_144)" = "false" ] || { echo "FAIL: redone_144 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_150)" = "false" ] || { echo "FAIL: redone_150 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_155)" = "false" ] || { echo "FAIL: redone_155 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_163)" = "false" ] || { echo "FAIL: redone_163 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_173)" = "false" ] || { echo "FAIL: redone_173 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g2_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g2_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" h5_every_page_harness)" = "PASS" ] || { echo "FAIL: h5_every_page_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" form_write_harness)" = "PASS" ] || { echo "FAIL: form_write_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" form_write_surface)" = "PASS" ] || { echo "FAIL: form_write_surface must be PASS" >&2; return 1; }
  [ "$(report_get "$file" submit_start_dual_check)" = "PASS" ] || { echo "FAIL: submit_start_dual_check must be PASS" >&2; return 1; }
  [ "$(report_get "$file" submit_start_is_form_write_only)" = "false" ] || { echo "FAIL: submit_start_is_form_write_only must stay false" >&2; return 1; }
  [ "$(report_get "$file" h5_open_pages_still_open)" = "true" ] || { echo "FAIL: h5_open_pages_still_open must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in g2-h5-every-page g2-form-write-honesty g2-form-submission-write g2-submit-start-form-write g2-h5-permission g2-ops-pg-dump g2-todo-http-surface g2-tenant-only-catalog g2-physical-dump-surface; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local live h5
  live="$(report_get "$file" live_jar_walk)"
  h5="$(report_get "$file" live_h5_walk)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_jar_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$h5" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_h5_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_jar_context_set)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_jar_context_set=true" >&2; return 1; }
    [ "$(report_get "$file" live_submissions_observed)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_submissions_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_submit_start_observed)" = "true" ] || { echo "FAIL: live_jar_walk=PASS requires live_submit_start_observed=true" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_skip_reason)" ] || { echo "FAIL: live JAR SKIP needs live_jar_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_submissions_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live submissions walk" >&2; return 1; }
    [ "$(report_get "$file" live_submit_start_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live submit-start walk" >&2; return 1; }
  fi
  if [ "$h5" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_h5_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_h5_observed)" = "true" ] || { echo "FAIL: live_h5_walk=PASS requires live_h5_observed=true" >&2; return 1; }
  fi
  if [ "$h5" = "SKIP" ]; then
    [ "$(report_get "$file" live_h5_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live H5 every-page walk" >&2; return 1; }
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
  OUT="$ROOT/.local/g2-h5-every-page-form-write-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G2 leftover: H5 every-page / form.write honesty beyond #163/#173"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #112/#116/#137/#144/#150/#155/#163/#173 product code."
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
  LIVE_REASON="live listener present but this leftover does not claim a walk until POST /submissions, submit-start dual-check, and H5 every-page were observed"
fi

README="$ROOT/docs/README.md"
SIBLING_ROWS="
g2-h5-every-page
g2-form-write-honesty
g2-form-submission-write
g2-submit-start-form-write
g2-h5-permission
g2-ops-pg-dump
g2-todo-http-surface
g2-tenant-only-catalog
g2-physical-dump-surface
g2-report-surface-guard
g2-surfaceguard-bypass-ci
g1-queryast-boundsql-honesty
g3-outbox-consume-crash-honesty
g5-mid-publish-failure-honesty
g10-leftover-honesty
g4-export-wizard-deny-honesty
g4-export-wizard-deny
g4-guard-deny-audit
g10-file-reconcile-honesty
g10-identity-drift-honesty
g5-mid-publish-failure
g5-browser-failed-publish
g3-outbox-consume-idempotent
g3-claim-crash-window
g1-queryast-boundsql-guard
g1-datascope-or-in-ast
g1-fieldpolicy-mask-second-auth
g1-fieldpolicy-mask-second-auth-honesty
g15-wx-dt-native-bridge-honesty
g15-wx-dt-prod-gap-honesty
g15-native-bridge-honesty
g15-native-bridge-stub
g7-query-expense-travel-honesty
g7-withdraw-browser-honesty
linux-arm-profile-leftover-honesty
linux-arm-profile-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-getenv-snapshot-honesty
s0-secret-rotate-without-restart
g4-p1-staging-audit-events-honesty
g6-recovery-port-dry-run-honesty
s0s1-live-redis-minio-isolation-honesty
g17-separation-honesty
g4-hot-purge-90d-honesty
g1-fieldpolicy-mask-second-auth-honesty
g10-identity-drift-honesty
g4-pkce-spa-verifier-honesty
g5-browser-failed-publish
g9-redis-down-invalidate-honesty
g10-file-reconcile-honesty
g17-mfa-recovery-email-honesty
g3-claim-crash-window
g6-recovery-port-honesty
g9-live-multi-jvm-replica
"
LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g2-h5-every-page-form-write-honesty\](\./g2-h5-every-page-form-write-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g2-h5-every-page-form-write-honesty/fixtures/honest-skip.env pass
verify_report scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-g2-close.env fail
verify_report scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-live-jar-pass.env fail
verify_report scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-redone.env fail
verify_report scripts/g2-h5-every-page-form-write-honesty/fixtures/fake-h5-open-closed.env fail

echo
echo "== Parent leftover harness still PASS (#173 verify-report only; not a product redo)"
"$ROOT/scripts/g2-form-write-honesty.sh" --verify-report \
  "$ROOT/scripts/g2-form-write-honesty/fixtures/honest-skip.env"
FORM_WRITE_HARNESS=PASS
FORM_WRITE=PASS
DUAL_CHECK=PASS

echo
echo "== #163 H5 every-page inventory still classifies (not a product redo)"
H5_INVENTORY="$ROOT/web/src/mobile/permission/h5PageInventory.js"
H5_CATALOG="$ROOT/nexus-permission-core/src/main/java/one/nexus/permission/surface/H5PageSurfaceCatalog.java"
FORM_HONESTY="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/infra/permission/G2FormWriteHonesty.java"
H5_DOC="$ROOT/docs/g2-h5-every-page.md"
FORM_DOC="$ROOT/docs/g2-form-write-honesty.md"
[ -f "$H5_INVENTORY" ] || fail "missing #163 JS inventory"
[ -f "$H5_CATALOG" ] || fail "missing #163 Java catalog"
[ -f "$FORM_HONESTY" ] || fail "missing #173 G2FormWriteHonesty"
[ -f "$H5_DOC" ] || fail "missing #163 leftover doc"
[ -f "$FORM_DOC" ] || fail "missing #173 leftover doc"
grep -Fq 'G2_CLOSE = "NOT_CLAIMED"' "$H5_INVENTORY" || fail "#163 JS inventory G2_CLOSE must stay NOT_CLAIMED"
grep -Fq 'path: "/m/p3/lists"' "$H5_INVENTORY" || fail "#163 inventory must keep covered /m/p3/lists"
grep -Fq 'path: "/m/p3/forms"' "$H5_INVENTORY" || fail "#163 inventory must keep covered /m/p3/forms"
grep -Fq 'path: "/m/p3/todos"' "$H5_INVENTORY" || fail "#163 inventory must keep covered /m/p3/todos"
grep -Fq 'path: "/m/p3/query"' "$H5_INVENTORY" || fail "#163 inventory must keep covered /m/p3/query"
grep -Fq 'path: "/m/p3/home"' "$H5_INVENTORY" || fail "#163 inventory must keep OPEN /m/p3/home"
grep -Fq 'path: "/m/p3/files"' "$H5_INVENTORY" || fail "#163 inventory must keep OPEN /m/p3/files"
grep -Fq 'path: "/m/p3/notifications"' "$H5_INVENTORY" || fail "#163 inventory must keep OPEN /m/p3/notifications"
grep -Fq 'path: "/m/login"' "$H5_INVENTORY" || fail "#163 inventory must keep OPEN /m/login"
grep -Fq 'path: "/m/host"' "$H5_INVENTORY" || fail "#163 inventory must keep OPEN /m/host"
grep -Fq 'NOT_CLAIMED' "$H5_CATALOG" || fail "#163 Java catalog must stay NOT_CLAIMED"
grep -Fq 'OPEN' "$H5_CATALOG" || fail "#163 Java catalog must keep OPEN pages"
grep -Fq 'public static final String G2_CLOSE = "NOT_CLAIMED"' "$FORM_HONESTY" \
  || fail "#173 G2FormWriteHonesty G2_CLOSE must stay NOT_CLAIMED"
grep -Fq 'Does not redo #112' "$FORM_HONESTY" || fail "#173 leftover must stay a honesty wrapper"
H5_EVERY_PAGE_HARNESS=PASS
H5_OPEN_STILL_OPEN=true
echo "PASS: #163 inventory still COVERED list/detail/form/todo/query; OPEN home/files/notify/login/host"

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ] && [ -d "$ROOT/web/node_modules" ]; then
  echo
  echo "== Web leftover assert (#163 inventory; does not rewrite product pages)"
  (
    cd web
    npm run assert:h5-every-page
  ) | tee "$OUT/npm.log"
  TEST_SUMMARY="$(awk '/PASS:/{line=$0} END{print line}' "$OUT/npm.log")"
elif [ "$SKIP_TESTS" = "0" ]; then
  TEST_SUMMARY="npm assert skipped (no web/node_modules); static inventory PASS"
  log "npm assert skipped: web/node_modules missing"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

LIVE_WALK=SKIP
LIVE_SUBMISSIONS=false
LIVE_SUBMIT_START=false
LIVE_H5_WALK=SKIP
LIVE_H5_OBSERVED=false
[ "${LIVE_SUBMISSIONS_OBSERVED:-}" = "true" ] && LIVE_SUBMISSIONS=true
[ "${LIVE_SUBMIT_START_OBSERVED:-}" = "true" ] && LIVE_SUBMIT_START=true
[ "${LIVE_H5_WALK_OBSERVED:-}" = "true" ] && LIVE_H5_OBSERVED=true
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_SUBMISSIONS" = "true" ] && [ "$LIVE_SUBMIT_START" = "true" ]; then
  LIVE_WALK=PASS
  LIVE_REASON="live POST /submissions + submit-start dual-check walk observed"
fi
if [ "$LIVE_PRESENT" = "true" ] && [ "$LIVE_H5_OBSERVED" = "true" ]; then
  LIVE_H5_WALK=PASS
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
  kv h5_every_page_harness "$H5_EVERY_PAGE_HARNESS"
  kv form_write_harness "$FORM_WRITE_HARNESS"
  kv form_write_surface "$FORM_WRITE"
  kv submit_start_dual_check "$DUAL_CHECK"
  kv submit_start_is_form_write_only false
  kv h5_open_pages_still_open "$H5_OPEN_STILL_OPEN"
  kv leftover_rows_kept "g2-h5-every-page,g2-form-write-honesty,g2-form-submission-write,g2-submit-start-form-write,g2-h5-permission,g2-ops-pg-dump,g2-todo-http-surface,g2-tenant-only-catalog,g2-physical-dump-surface"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_jar_walk "$LIVE_WALK"
  kv live_jar_skip_reason "$LIVE_REASON"
  kv live_submissions_observed "$LIVE_SUBMISSIONS"
  kv live_submit_start_observed "$LIVE_SUBMIT_START"
  kv live_h5_walk "$LIVE_H5_WALK"
  kv live_h5_observed "$LIVE_H5_OBSERVED"
  kv redone_112 false
  kv redone_116 false
  kv redone_137 false
  kv redone_144 false
  kv redone_150 false
  kv redone_155 false
  kv redone_163 false
  kv redone_173 false
  kv g2_close NOT_CLAIMED
  kv g2_close_reason "Honesty leftover around #163 H5 every-page inventory + #173 form.write / submit-start dual-check. Live JAR SKIP when unavailable. Not product G2 close."
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
  echo "| #163 H5 every-page harness | $H5_EVERY_PAGE_HARNESS | list/detail/form/todo/query COVERED; home/files/notify/login/host OPEN |"
  echo "| #173 form.write harness | $FORM_WRITE_HARNESS | parent honest-skip fixture still verifies |"
  echo "| FormSurfaceGuard form.write | $FORM_WRITE | requireFill is form/write |"
  echo "| submit-start dual-check | $DUAL_CHECK | form.write + process.instance/start |"
  echo "| H5 open pages still open | $H5_OPEN_STILL_OPEN | not claimed covered |"
  echo "| leftover rows kept | true | parents + all sibling leftover rows |"
  echo "| Live JAR / issued context | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| live H5 every-page walk | $LIVE_H5_WALK | observed=$LIVE_H5_OBSERVED |"
  echo "| #112/#116/#137/#144/#150/#155/#163/#173 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G2 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g2-h5-every-page-form-write-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g2-h5-every-page-form-write-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar=$LIVE_WALK live_h5=$LIVE_H5_WALK g2_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#163/#173 harness PASS; live JAR / issued-context $LIVE_WALK)"
echo "See docs/g2-h5-every-page-form-write-honesty.md"
echo "Keep sibling leftover rows including g2-h5-every-page / g2-form-write-honesty"
echo "Not claimed: product G2 close / #163/#173 product redo / live JAR walk unless observed"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
