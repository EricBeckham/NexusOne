#!/bin/sh
set -eu

fail() { echo "FAIL: $1" >&2; exit 1; }
expect() { [ "$1" = "$2" ] || fail "$3 (expected '$2', got '$1')"; }

run_id="$(date +%s)-$$"
tenant_a="11111111-1111-4111-8111-111111111111"
tenant_b="22222222-2222-4222-8222-222222222222"
prefix_a="nexus:v1:{$tenant_a}:cache:p0-05:$run_id"
prefix_b="nexus:v1:{$tenant_b}:cache:p0-05:$run_id"
ttl_key="$prefix_a:ttl"
counter_key="$prefix_a:counter"
lock_key="$prefix_a:lock"
tenant_a_key="$prefix_a:identity"
tenant_b_key="$prefix_b:identity"

cleanup() {
  redis-cli --raw DEL "$ttl_key" "$counter_key" "$lock_key" "$tenant_a_key" "$tenant_b_key" >/dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

expect "$(redis-cli --raw PING)" "PONG" "redis availability"
echo 'PASS: Redis PING'

expect "$(redis-cli --raw SET "$ttl_key" value EX 1)" "OK" "SET EX"
ttl="$(redis-cli --raw TTL "$ttl_key")"
[ "$ttl" -ge 0 ] && [ "$ttl" -le 1 ] || fail "TTL out of range: $ttl"
sleep 2
expect "$(redis-cli --raw EXISTS "$ttl_key")" "0" "expiration"
echo 'PASS: TTL and expiration'

expect "$(redis-cli --raw INCR "$counter_key")" "1" "first INCR"
expect "$(redis-cli --raw INCR "$counter_key")" "2" "second INCR"
expect "$(redis-cli --raw GET "$counter_key")" "2" "atomic counter"
echo 'PASS: atomic INCR'

token="$(cat /proc/sys/kernel/random/uuid)"
other_token="$(cat /proc/sys/kernel/random/uuid)"
expect "$(redis-cli --raw SET "$lock_key" "$token" NX PX 5000)" "OK" "lock acquisition"
expect "$(redis-cli --raw SET "$lock_key" "$other_token" NX PX 5000)" "" "contended lock acquisition"
release_script='if redis.call("GET", KEYS[1]) == ARGV[1] then return redis.call("DEL", KEYS[1]) else return 0 end'
expect "$(redis-cli --raw EVAL "$release_script" 1 "$lock_key" "$other_token")" "0" "wrong-token unlock"
expect "$(redis-cli --raw GET "$lock_key")" "$token" "lock survived wrong-token unlock"
expect "$(redis-cli --raw EVAL "$release_script" 1 "$lock_key" "$token")" "1" "owner unlock"
expect "$(redis-cli --raw EXISTS "$lock_key")" "0" "lock deletion"
echo 'PASS: NX/PX lock ownership and safe release'

expect "$(redis-cli --raw SET "$tenant_a_key" A EX 60)" "OK" "tenant A write"
expect "$(redis-cli --raw SET "$tenant_b_key" B EX 60)" "OK" "tenant B write"
expect "$(redis-cli --raw GET "$tenant_a_key")" "A" "tenant A value"
expect "$(redis-cli --raw GET "$tenant_b_key")" "B" "tenant B value"
expect "$(redis-cli --raw EXISTS "$prefix_b:identity-unwritten")" "0" "tenant B unwritten key"
echo 'PASS: distinct tenant-prefixed keys'
echo 'NOTE: Redis itself does not enforce tenant authorization; application CachePort must validate tenant context before building a key'
