#!/usr/bin/env bash
# G2 leftover: ops pg_dump outside product SurfaceGuard honesty beyond #155.
#
# Extends docs/g2-ops-pg-dump.md. Does NOT redo #137/#144/#150/#155.
# Does NOT claim product G2 close. G18 stays closed. No Docker.
# Documented path + AuthorizedExport / ops runbook stay always-green.
# Live product-database pg_dump is PASS only when a dump was observed.
# Missing product DB is honest SKIP. #155 static gate is not a live dump.
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
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g2-ops-pg-dump-honesty.sh [--out DIR] [--skip-tests]
  scripts/g2-ops-pg-dump-honesty.sh --verify-report FILE

Always-green: ops pg_dump documented outside SurfaceGuard; product dump
uses AuthorizedExport.writeProjected; ops scripts stay G6 runbook.
Live product-database pg_dump is PASS only when observed. Missing
product DB is honest SKIP. Does not redo #137/#144/#150/#155.

  --out DIR           Output directory (default: .local/g2-ops-pg-dump-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
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
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used ops_pg_dump_outside_surfaceguard product_dump_uses_authorized_export ops_scripts_are_runbook ops_pg_dump_on_surfaceguard ops_scripts_claim_surfaceguard leftover_rows_kept static_gate_is_not_live_dump live_product_db_pg_dump live_product_db_pg_dump_observed live_product_db_skip_reason redone_137 redone_144 redone_150 redone_155 g2_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" redone_137)" = "false" ] || fail "redone_137 must stay false"
  [ "$(report_get "$file" redone_144)" = "false" ] || fail "redone_144 must stay false"
  [ "$(report_get "$file" redone_150)" = "false" ] || fail "redone_150 must stay false"
  [ "$(report_get "$file" redone_155)" = "false" ] || fail "redone_155 must stay false"
  [ "$(report_get "$file" g2_close)" = "NOT_CLAIMED" ] || fail "g2_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" ops_pg_dump_outside_surfaceguard)" = "PASS" ] || fail "ops_pg_dump_outside_surfaceguard must be PASS"
  [ "$(report_get "$file" product_dump_uses_authorized_export)" = "PASS" ] || fail "product_dump_uses_authorized_export must be PASS"
  [ "$(report_get "$file" ops_scripts_are_runbook)" = "PASS" ] || fail "ops_scripts_are_runbook must be PASS"
  [ "$(report_get "$file" ops_pg_dump_on_surfaceguard)" = "false" ] || fail "ops_pg_dump_on_surfaceguard must stay false"
  [ "$(report_get "$file" ops_scripts_claim_surfaceguard)" = "false" ] || fail "ops_scripts_claim_surfaceguard must stay false"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" static_gate_is_not_live_dump)" = "true" ] || fail "static_gate_is_not_live_dump must stay true"

  local live
  live="$(report_get "$file" live_product_db_pg_dump)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_product_db_pg_dump must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_product_db_pg_dump_observed)" = "true" ] || fail "live_product_db_pg_dump=PASS requires live_product_db_pg_dump_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_product_db_skip_reason)" ] || fail "live dump SKIP needs live_product_db_skip_reason"
    [ "$(report_get "$file" live_product_db_pg_dump_observed)" != "true" ] || fail "SKIP cannot claim live_product_db_pg_dump_observed=true"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g2-ops-pg-dump-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G2 leftover: ops pg_dump outside SurfaceGuard honesty beyond #155"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #137/#144/#150/#155. Not product G2 close."
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

README="$ROOT/docs/README.md"
LEFTOVER_ROWS=false
if grep -Fq '[g2-ops-pg-dump](./g2-ops-pg-dump.md)' "$README" \
  && grep -Fq '[g2-ops-pg-dump-honesty](./g2-ops-pg-dump-honesty.md)' "$README" \
  && grep -Fq '[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)' "$README" \
  && grep -Fq '[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)' "$README" \
  && grep -Fq '[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)' "$README" \
  && grep -Fq '[g15-wx-dt-native-bridge-honesty](./g15-wx-dt-native-bridge-honesty.md)' "$README" \
  && grep -Fq '[g1-queryast-boundsql-honesty](./g1-queryast-boundsql-honesty.md)' "$README" \
  && grep -Fq '[g2-h5-every-page-form-write-honesty](./g2-h5-every-page-form-write-honesty.md)' "$README" \
  && grep -Fq '[g4-export-wizard-deny-honesty](./g4-export-wizard-deny-honesty.md)' "$README" \
  && grep -Fq '[g10-leftover-honesty](./g10-leftover-honesty.md)' "$README" \
  && grep -Fq '[g5-mid-publish-failure-honesty](./g5-mid-publish-failure-honesty.md)' "$README" \
  && grep -Fq '[g3-outbox-consume-crash-honesty](./g3-outbox-consume-crash-honesty.md)' "$README" \
  && grep -Fq '[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)' "$README" \
  && grep -Fq '[g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md)' "$README" \
  && grep -Fq '[g17-mfa-recovery-vue-otp-honesty](./g17-mfa-recovery-vue-otp-honesty.md)' "$README" \
  && grep -Fq '[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)' "$README" \
  && grep -Fq '[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)' "$README" \
  && grep -Fq '[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)' "$README" \
  && grep -Fq '[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)' "$README" \
  && grep -Fq '[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)' "$README" \
  && grep -Fq '[g17-separation-honesty](./g17-separation-honesty.md)' "$README" \
  && grep -Fq '[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)' "$README" \
  && grep -Fq '[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)' "$README" \
  && grep -Fq '[g2-physical-dump-surface](./g2-physical-dump-surface.md)' "$README" \
  && grep -Fq '[g2-todo-http-surface](./g2-todo-http-surface.md)' "$README" \
  && grep -Fq '[g2-tenant-only-catalog](./g2-tenant-only-catalog.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep sibling leftover rows"

grep -Fq '产品级 G2 已关闭' "$README" && fail "docs/README.md must not claim product G2 closed"
grep -Fiq 'product g2 closed' "$README" && fail "docs/README.md must not claim product G2 closed"

CATALOG="$ROOT/nexus-permission-core/src/main/java/one/nexus/permission/surface/OpsPhysicalDumpCatalog.java"
DUMP="$ROOT/nexus-p3-studio/src/main/java/one/nexus/p3/dump/DumpController.java"
LEFTOVER="$ROOT/docs/g2-ops-pg-dump.md"
HONESTY="$ROOT/docs/g2-ops-pg-dump-honesty.md"
[ -f "$CATALOG" ] || fail "OpsPhysicalDumpCatalog missing"
[ -f "$DUMP" ] || fail "DumpController missing"
grep -Fq 'out of product SurfaceGuard' "$CATALOG" || fail "catalog must document ops dump out of SurfaceGuard"
grep -Fq 'AuthorizedExport.writeProjected' "$CATALOG" || fail "catalog must name AuthorizedExport.writeProjected"
grep -Fq 'AuthorizedExport.writeProjected' "$DUMP" || fail "DumpController must use AuthorizedExport.writeProjected"
grep -Fq 'ProcessBuilder' "$DUMP" && fail "DumpController must not construct ProcessBuilder"
grep -Fq 'not** that surface' "$LEFTOVER" || fail "#155 leftover must state ops pg_dump is not the product surface"
grep -Fq 'AuthorizedExport.writeProjected' "$LEFTOVER" || fail "#155 leftover must name AuthorizedExport"
grep -Fq 'outside' "$HONESTY" || fail "honesty leftover must state ops dump is outside SurfaceGuard"
grep -Fq 'NOT_CLAIMED' "$HONESTY" || fail "honesty leftover must keep g2_close=NOT_CLAIMED"

OUTSIDE=PASS
AUTHORIZED=PASS
RUNBOOK=PASS
ON_SURFACE=false
CLAIM_SURFACE=false
for rel in scripts/g6-recovery/run-drill.sh scripts/g6-recovery/run-single-tenant.sh scripts/linux-arm-profile/lifecycle.sh; do
  [ -f "$ROOT/$rel" ] || fail "ops script missing: $rel"
  grep -Fq 'pg_dump' "$ROOT/$rel" || fail "ops script must remain a pg_dump path: $rel"
  if grep -Eq 'SurfaceGuard|AuthorizedExport' "$ROOT/$rel"; then
    fail "ops script must not claim SurfaceGuard / AuthorizedExport: $rel"
  fi
done
for rel in docs/engineering/runbooks/postgres-whole-db-restore.md docs/engineering/runbooks/postgres-single-tenant-logical-restore.md; do
  [ -f "$ROOT/$rel" ] || fail "ops runbook missing: $rel"
done

LIVE_OBSERVED=false
if [ "${NEXUS_G2_LIVE_PRODUCT_DB_DUMP_OBSERVED:-}" = "1" ] || [ "${NEXUS_G2_LIVE_PRODUCT_DB_DUMP_OBSERVED:-}" = "true" ]; then
  LIVE_OBSERVED=true
fi
LIVE_WALK=SKIP
if [ "$LIVE_OBSERVED" = "true" ]; then
  LIVE_REASON="live dump listed but this leftover still does not claim product G2 close"
else
  LIVE_REASON="#155 static gate is not a live product-database pg_dump; this leftover does not dump haopan_platform / product :5432; Docker forbidden"
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  if command -v mvn >/dev/null 2>&1; then
    mvn -version | head -n 1 | tee -a "$OUT/collect.log"
    set +e
    mvn -pl nexus-permission-core -am test \
      -Dtest=G2OpsPgDumpPathHonestyTest \
      -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
    MVN_RC=${PIPESTATUS[0]}
    set -e
    [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
    TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  else
    log "mvn not on PATH; inventory + honesty only"
    TEST_SUMMARY="skipped (mvn not on PATH)"
  fi
else
  TEST_SUMMARY="skipped (--skip-tests)"
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
  kv ops_pg_dump_outside_surfaceguard "$OUTSIDE"
  kv product_dump_uses_authorized_export "$AUTHORIZED"
  kv ops_scripts_are_runbook "$RUNBOOK"
  kv ops_pg_dump_on_surfaceguard "$ON_SURFACE"
  kv ops_scripts_claim_surfaceguard "$CLAIM_SURFACE"
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv static_gate_is_not_live_dump true
  kv live_product_db_pg_dump "$LIVE_WALK"
  kv live_product_db_pg_dump_observed "$LIVE_OBSERVED"
  kv live_product_db_skip_reason "$LIVE_REASON"
  kv redone_137 false
  kv redone_144 false
  kv redone_150 false
  kv redone_155 false
  kv g2_close NOT_CLAIMED
  kv g2_close_reason "Honesty leftover beyond #155: ops pg_dump stays outside SurfaceGuard; must use AuthorizedExport or stay ops/G6 runbook. Live product-database dump SKIP when not observed. Not product G2 close."
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
  echo "| Ops pg_dump outside SurfaceGuard | $OUTSIDE | documented + catalog |"
  echo "| Product dump uses AuthorizedExport | $AUTHORIZED | #137 path, not rewritten |"
  echo "| Ops scripts stay G6 runbook | $RUNBOOK | must not claim SurfaceGuard |"
  echo "| leftover rows kept | $LEFTOVER_ROWS | #155 + this leftover + siblings |"
  echo "| Live product-database pg_dump | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| #137/#144/#150/#155 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G2 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g2-ops-pg-dump-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g2-ops-pg-dump-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_product_db_pg_dump=$LIVE_WALK g2_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live product-database pg_dump $LIVE_WALK)"
echo "See docs/g2-ops-pg-dump-honesty.md"
echo "Keep both leftover rows: docs/g2-ops-pg-dump.md and docs/g2-ops-pg-dump-honesty.md"
echo "Not claimed: product G2 close / ops pg_dump on SurfaceGuard / live product-database dump"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
