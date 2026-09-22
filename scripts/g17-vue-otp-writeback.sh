#!/usr/bin/env bash
# Public entry for the G17 Vue Keycloak otp write-back honesty leftover.
exec "$(cd "$(dirname "$0")" && pwd)/g17-vue-otp-writeback/run.sh" "$@"
