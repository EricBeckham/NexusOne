#!/bin/sh
# Read-only inventory; review output before attaching to an issue.
# Product Profile collect/verify (PASS/SKIP matrix, no false ARM close) lives in
# scripts/linux-arm-profile/collect-and-verify.sh — this file stays a version probe.
set -eu
printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf 'kernel=%s\n' "$(uname -sr)"
printf 'machine=%s\n' "$(uname -m)"
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  printf 'os=%s\n' "${PRETTY_NAME:-unknown}"
fi
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -n 1
  java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*os\.arch[[:space:]]*=/{print "java_os_arch="$3; exit}'
  java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.version[[:space:]]*=/{print "java_version="$3; exit}'
else
  printf 'java=not-installed\n'
fi
for command_name in mvn psql pg_config docker; do
  if command -v "$command_name" >/dev/null 2>&1; then
    case "$command_name" in
      mvn) mvn -B -version 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | head -n 1 ;;
      psql) psql --version ;;
      pg_config) pg_config --version ;;
      docker) docker --version ;;
    esac
  else
    printf '%s=not-installed\n' "$command_name"
  fi
done
if [ "$#" -gt 0 ]; then
  command -v sha256sum >/dev/null 2>&1 || { echo 'sha256sum not available' >&2; exit 1; }
  sha256sum "$@"
fi
