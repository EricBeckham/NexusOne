#!/bin/sh
# Read-only inventory; review output before attaching to an issue.
set -eu
printf 'timestamp_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf 'kernel=%s\n' "$(uname -sr)"
printf 'machine=%s\n' "$(uname -m)"
if [ -r /etc/os-release ]; then
  . /etc/os-release
  printf 'os=%s\n' "${PRETTY_NAME:-unknown}"
fi
for command_name in java psql pg_config docker; do
  if command -v "$command_name" >/dev/null 2>&1; then
    case "$command_name" in
      java) java -version 2>&1 | head -n 1 ;;
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
