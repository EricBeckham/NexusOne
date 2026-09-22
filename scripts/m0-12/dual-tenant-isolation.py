#!/usr/bin/env python3
"""M0-12 dual-tenant acceptance at the HTTP/UI-API boundary.

Talks to merged M0-03 (P1), M0-04 (P2), M0-05 (/api/v1/permission),
M0-06/07/08 (/api/v1/p3/* + process), and M0-10 (/api/v1/notifications).
Does not claim G18 closed. Missing TenantContext must be 403 (not SKIP).
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Any

PASS = 0
FAIL = 0
SKIP = 0
ROWS: list[dict[str, str]] = []


def pick(data: Any, *names: str, default=None):
    if data is None:
        return default
    if isinstance(data, list):
        return data
    for name in names:
        if isinstance(data, dict) and name in data and data[name] is not None:
            return data[name]
    return default


def stub_token(sub: str, email: str, username: str, name: str) -> str:
    payload = (
        '{"sub":"%s","email":"%s","preferred_username":"%s","name":"%s"}'
        % (sub, email, username, name)
    )
    encoded = base64.urlsafe_b64encode(payload.encode("utf-8")).decode("ascii").rstrip("=")
    return "stub." + encoded


def record(gate: str, item: str, status: str, detail: str) -> None:
    global PASS, FAIL, SKIP
    ROWS.append({"gate": gate, "item": item, "status": status, "detail": detail})
    if status == "PASS":
        PASS += 1
    elif status == "FAIL":
        FAIL += 1
    else:
        SKIP += 1
    print(f"[{status}] {gate} {item}: {detail}")


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        headers: dict[str, str] | None = None,
        expected: int | None = None,
    ) -> tuple[int, Any]:
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            self.base + path,
            data=data,
            method=method,
            headers={"Accept": "application/json", **(headers or {})},
        )
        if body is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                raw = resp.read().decode("utf-8")
                status = resp.status
        except urllib.error.HTTPError as err:
            raw = err.read().decode("utf-8")
            status = err.code
        parsed: Any = None
        if raw:
            try:
                parsed = json.loads(raw)
            except json.JSONDecodeError:
                parsed = {"raw": raw}
        if expected is not None:
            allowed = expected if isinstance(expected, (list, tuple, set)) else (expected,)
            if status not in allowed:
                raise AssertionError(f"{method} {path} expected {allowed} got {status}: {parsed}")
        return status, parsed


def platform_headers(actor: str, reason: str, trace: str) -> dict[str, str]:
    return {
        "X-Nexus-Demo-Actor-Id": actor,
        "X-Nexus-Demo-Reason": reason,
        "X-Nexus-Trace-Id": trace,
    }


def auth_headers(token: str, context_id: str | None = None, extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if context_id:
        headers["X-Nexus-Context-Id"] = context_id
    if extra:
        headers.update(extra)
    return headers


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def run(base: str, bootstrap_token: str, actor: str) -> int:
    api = Api(base)
    suffix = time.strftime("%H%M%S")
    trace = f"m012-{suffix}"

    status, health = api.request("GET", "/actuator/health")
    require(status == 200 and pick(health, "status") == "UP", f"health {health}")
    record("G8", "actuator health", "PASS", "UP")

    _, runtime = api.request("GET", "/api/v1/meta/runtime")
    require(pick(runtime, "schema_manager") == "flyway_static_only", runtime)
    record("G8", "runtime meta / trace", "PASS", f"trace={pick(runtime, 'trace_id')}")

    # --- P1 tenant isolation (M0-03) ---
    p1h = platform_headers(actor, "m0-12-dual-tenant", trace)
    _, platform = api.request("GET", "/api/v1/p1/platform-tenant", headers=p1h, expected=200)
    platform_id = pick(platform, "id")
    require(pick(platform, "code") == "platform", platform)
    record("G18", "platform tenant exists", "PASS", str(platform_id))

    def create_tenant(code: str, name: str) -> dict:
        _, body = api.request(
            "POST",
            "/api/v1/p1/tenants",
            {
                "name": name,
                "code": code,
                "legal_name": f"{name} Ltd",
                "admin": {"display_name": f"Admin {code}", "email": f"{code}@example.com"},
                "config": {"locale": "zh-CN", "timezone": "Asia/Shanghai", "currency": "CNY"},
            },
            headers=p1h,
            expected=(200, 201),
        )
        return body

    alpha = create_tenant(f"alpha_{suffix}", f"Alpha {suffix}")
    beta = create_tenant(f"beta_{suffix}", f"Beta {suffix}")
    alpha_id = pick(pick(alpha, "tenant"), "id")
    beta_id = pick(pick(beta, "tenant"), "id")
    require(alpha_id != beta_id, "two tenants must have distinct ids")
    record("G18", "create two business tenants", "PASS", f"{alpha_id} / {beta_id}")

    _, listed = api.request("GET", "/api/v1/p1/tenants", headers=p1h, expected=200)
    ids = {pick(row, "id") for row in listed}
    require(alpha_id in ids and beta_id in ids, listed)
    record("G1", "P1 list contains both tenants, not a client-side filter", "PASS", f"n={len(listed)}")

    _, disable_platform = api.request(
        "POST",
        f"/api/v1/p1/tenants/{platform_id}/disable",
        {"confirm": True, "reason": "adversarial"},
        headers=p1h,
    )
    require(pick(disable_platform, "code") == "P1.TENANT.0004", disable_platform)
    record("G1", "platform tenant cannot be disabled", "PASS", "P1.TENANT.0004")

    _, a_quota = api.request(
        "POST",
        f"/api/v1/p1/tenants/{alpha_id}/quotas/consume",
        {"metric": "CUSTOM_LEDGER", "amount": 1, "idempotency_key": f"a-{suffix}"},
        headers=p1h,
        expected=200,
    )
    _, b_quotas = api.request("GET", f"/api/v1/p1/tenants/{beta_id}/quotas", headers=p1h, expected=200)
    beta_ledger = next(q for q in b_quotas if pick(q, "metric") == "CUSTOM_LEDGER")
    require(pick(beta_ledger, "used") == 0, beta_ledger)
    require(pick(a_quota, "used") == 1, a_quota)
    record("G1", "quota consume on A does not change B", "PASS", f"A.used={pick(a_quota, 'used')} B.used=0")

    # --- P2 IAM isolation (M0-04) ---
    if not bootstrap_token:
        record("G18", "P2 admin bootstrap", "SKIP", "NEXUS_PLATFORM_BOOTSTRAP_TOKEN unset")
        assert_live_without_context(api)
        return FAIL

    boot = {"X-Nexus-Bootstrap-Token": bootstrap_token}

    def bootstrap_admin(tenant_id: str, email: str, name: str) -> dict:
        _, body = api.request(
            "POST",
            f"/api/v1/internal/tenants/{tenant_id}/admin-users",
            {"display_name": name, "email": email, "username": email},
            headers=boot,
            expected=200,
        )
        return body

    admin_a = bootstrap_admin(alpha_id, f"admin-a-{suffix}@example.com", "Admin A")
    admin_b = bootstrap_admin(beta_id, f"admin-b-{suffix}@example.com", "Admin B")
    record("G18", "P2 admin init per tenant", "PASS", f"{pick(admin_a, 'person_id')} / {pick(admin_b, 'person_id')}")

    def login(sub: str, email: str, name: str) -> tuple[str, dict]:
        token = stub_token(sub, email, email, name)
        _, view = api.request("POST", "/api/v1/auth/login", {"access_token": token}, expected=200)
        return token, view

    admin_a_token, admin_a_sess = login(
        pick(admin_a, "keycloak_user_id"), f"admin-a-{suffix}@example.com", "Admin A"
    )
    admin_b_token, admin_b_sess = login(
        pick(admin_b, "keycloak_user_id"), f"admin-b-{suffix}@example.com", "Admin B"
    )
    require(pick(admin_a_sess, "tenant_id", "tenantId") == alpha_id, admin_a_sess)
    require(pick(admin_b_sess, "tenant_id", "tenantId") == beta_id, admin_b_sess)
    record("G18", "login auto-issues context for single membership", "PASS", "admin A/B")

    alice_sub = f"alice-{suffix}"
    alice_email = f"alice-{suffix}@example.com"
    alice_token, alice_login = login(alice_sub, alice_email, "Alice")
    require(pick(alice_login, "context_id", "contextId") in (None, ""), alice_login)
    alice_account = pick(alice_login, "account_id", "accountId")
    record("G1", "no TenantContext until membership exists", "PASS", "alice context_id is null")

    def add_member(admin_token: str, context_id: str, account_id: str, display: str, employee: str) -> dict:
        api.request(
            "POST",
            "/api/v1/memberships",
            {"account_id": account_id},
            headers=auth_headers(admin_token, context_id),
            expected=200,
        )
        _, person = api.request(
            "POST",
            "/api/v1/persons",
            {"display_name": display, "employee_no": employee, "email": alice_email, "account_id": account_id},
            headers=auth_headers(admin_token, context_id),
            expected=200,
        )
        return person

    person_a = add_member(
        admin_a_token, pick(admin_a_sess, "context_id", "contextId"), alice_account, "Alice A", f"A-{suffix}"
    )
    person_b = add_member(
        admin_b_token, pick(admin_b_sess, "context_id", "contextId"), alice_account, "Alice B", f"B-{suffix}"
    )
    record("G18", "same natural person, two tenant Person rows", "PASS", f"{pick(person_a, 'id')} / {pick(person_b, 'id')}")

    _, switched_a = api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": alpha_id},
        headers=auth_headers(alice_token),
        expected=200,
    )
    ctx_a = pick(switched_a, "context_id", "contextId")
    _, persons_a = api.request("GET", "/api/v1/persons", headers=auth_headers(alice_token, ctx_a), expected=200)
    names_a = {pick(p, "display_name", "displayName") for p in persons_a}
    require("Alice A" in names_a, persons_a)
    require("Alice B" not in names_a, persons_a)
    require(all(pick(p, "tenant_id", "tenantId") == alpha_id for p in persons_a), persons_a)
    record("G1", "list persons in A never returns B rows", "PASS", f"names={sorted(names_a)}")

    _, switched_b = api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": beta_id},
        headers=auth_headers(alice_token),
        expected=200,
    )
    ctx_b = pick(switched_b, "context_id", "contextId")
    _, persons_b = api.request("GET", "/api/v1/persons", headers=auth_headers(alice_token, ctx_b), expected=200)
    names_b = {pick(p, "display_name", "displayName") for p in persons_b}
    require("Alice B" in names_b and "Alice A" not in names_b, persons_b)
    record("G1", "list persons in B never returns A rows", "PASS", f"names={sorted(names_b)}")

    _, tree_b = api.request(
        "GET", "/api/v1/departments/tree", headers=auth_headers(alice_token, ctx_b), expected=200
    )
    # Admin bootstrap created ROOT in each tenant; codes can repeat but ids must stay in-tenant.
    def collect_ids(nodes):
        out = []
        for node in nodes or []:
            dept = pick(node, "department") or node
            out.append(pick(dept, "id"))
            out.extend(collect_ids(pick(node, "children") or []))
        return out

    _, tree_a = api.request(
        "GET", "/api/v1/departments/tree", headers=auth_headers(alice_token, ctx_a), expected=200
    )
    ids_a = set(collect_ids(tree_a))
    ids_b = set(collect_ids(tree_b))
    require(ids_a.isdisjoint(ids_b), f"department id leak {ids_a & ids_b}")
    record("G1", "department trees do not share ids across tenants", "PASS", f"|A|={len(ids_a)} |B|={len(ids_b)}")

    status, missing = api.request(
        "GET",
        f"/api/v1/persons/{pick(person_a, 'id')}",
        headers=auth_headers(alice_token, ctx_b),
    )
    require(status == 404 and pick(missing, "code") == "P2.IAM.NOT_FOUND", missing)
    record("G1", "get person from A while in B is not-found", "PASS", "P2.IAM.NOT_FOUND")

    status, rejected = api.request(
        "GET",
        "/api/v1/auth/session",
        headers=auth_headers(alice_token, extra={"X-Tenant-Id": beta_id}),
    )
    require(status == 400 and pick(rejected, "code") == "P2.IAM.CLIENT_TENANT_REJECTED", rejected)
    record("G1", "UI/API rejects client X-Tenant-Id (ARCH-024)", "PASS", "P2.IAM.CLIENT_TENANT_REJECTED")

    stranger = str(uuid.uuid4())
    status, forbidden = api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": stranger},
        headers=auth_headers(alice_token),
    )
    require(status in (403, 400) and pick(forbidden, "code") == "P2.IAM.FORBIDDEN", forbidden)
    record("G1", "tenant switch without membership is forbidden", "PASS", "P2.IAM.FORBIDDEN")

    bob_token, _ = login(f"bob-{suffix}", f"bob-{suffix}@example.com", "Bob")
    status, stolen = api.request(
        "GET",
        "/api/v1/persons",
        headers=auth_headers(bob_token, ctx_a),
    )
    require(status in (401, 403) and pick(stolen, "code") in {"P2.IAM.FORBIDDEN", "P2.IAM.UNAUTHORIZED"}, stolen)
    record("G1", "issued context is bound to IdP subject", "PASS", pick(stolen, "code"))

    _, positions_a = api.request(
        "GET", "/api/v1/positions", headers=auth_headers(alice_token, ctx_a), expected=200
    )
    _, positions_b = api.request(
        "GET", "/api/v1/positions", headers=auth_headers(alice_token, ctx_b), expected=200
    )
    pos_a = {pick(p, "id") for p in positions_a}
    pos_b = {pick(p, "id") for p in positions_b}
    require(pos_a.isdisjoint(pos_b), "position id leak")
    record("G1", "positions stay inside tenant", "PASS", f"|A|={len(pos_a)} |B|={len(pos_b)}")

    assert_live_without_context(api)
    assert_live_with_context(
        api,
        admin_a_token,
        pick(admin_a_sess, "context_id", "contextId"),
        pick(admin_a, "person_id"),
        admin_b_token,
        pick(admin_b_sess, "context_id", "contextId"),
        alice_token,
        ctx_a,
    )
    return FAIL


def assert_live_without_context(api: Api) -> None:
    """No server TenantContext → 403. Never SKIP-as-success on missing context."""
    status, body = api.request("GET", "/api/v1/notifications")
    require(status == 403, f"notify without context expected 403 got {status} {body}")
    record("G18", "in-app notifications require TenantContext", "PASS", f"403 {pick(body, 'code', default='')}")

    status, body = api.request("GET", "/api/v1/p3/ledgers")
    require(status == 403, f"ledgers without context expected 403 got {status} {body}")
    record("G18", "ledger list requires TenantContext", "PASS", f"403 {pick(body, 'code', default='')}")

    status, body = api.request("GET", "/api/v1/p3/forms")
    require(status == 403, f"forms without context expected 403 got {status} {body}")
    record("G5", "forms / publish require TenantContext", "PASS", f"403 {pick(body, 'code', default='')}")

    status, health = api.request("GET", "/api/v1/permission/health")
    require(status == 200, health)
    record("G2", "permission health /api/v1/permission/health", "PASS", pick(health, "module", default=""))

    status, snap = api.request("GET", "/api/v1/permission/snapshot")
    require(status in (401, 403), f"permission snapshot without context expected 401/403 got {status} {snap}")
    record("G9", "policy snapshot without context is denied", "PASS", f"{status} {pick(snap, 'code', default='')}")


def assert_live_with_context(
    api: Api,
    admin_a_token: str,
    ctx_a_admin: str,
    admin_a_person: str,
    admin_b_token: str,
    ctx_b_admin: str,
    alice_token: str,
    ctx_a: str,
) -> None:
    headers_a = auth_headers(alice_token, ctx_a)
    headers_admin_a = auth_headers(admin_a_token, ctx_a_admin)
    headers_admin_b = auth_headers(admin_b_token, ctx_b_admin)

    _, ledgers = api.request("GET", "/api/v1/p3/ledgers", headers=headers_a, expected=200)
    require(isinstance(ledgers, list), ledgers)
    record("G18", "ledger list with issued context", "PASS", f"n={len(ledgers)} (API live; not a G18 walkthrough)")

    _, forms = api.request("GET", "/api/v1/p3/forms", headers=headers_a, expected=200)
    require(isinstance(forms, list), forms)
    record("G5", "forms list with issued context", "PASS", f"n={len(forms)}")

    # Todos: missing context with a valid bearer must be 403 (FlowAuthFilter).
    status, missing_todo = api.request("GET", "/api/v1/p3/todos", headers=auth_headers(alice_token))
    require(status == 403, f"todos without context expected 403 got {status} {missing_todo}")
    record("G18", "todos require TenantContext", "PASS", f"403 {pick(missing_todo, 'code', default='')}")

    _, todos = api.request("GET", "/api/v1/p3/todos", headers=headers_a, expected=200)
    require(isinstance(todos, list), todos)
    record("G18", "todo inbox with issued context", "PASS", f"n={len(todos)}")

    _, snap = api.request("GET", "/api/v1/permission/snapshot", headers=headers_a, expected=200)
    require(pick(snap, "tenant_id", "tenantId") is not None, snap)
    record("G9", "policy snapshot /api/v1/permission/snapshot", "PASS", f"version={pick(snap, 'version')}")

    _, decision = api.request(
        "POST",
        "/api/v1/permission/check",
        {"resource": "ledger", "action": "read"},
        headers=headers_a,
        expected=200,
    )
    record(
        "G2",
        "permission check /api/v1/permission/check",
        "PASS",
        f"allowed={pick(decision, 'allowed')} reason={pick(decision, 'reason')}",
    )

    status, denied = api.request(
        "POST",
        "/api/v1/permission/check",
        {"resource": "ledger", "action": "read"},
        headers=auth_headers(alice_token),
    )
    require(status == 403, f"permission check without context expected 403 got {status} {denied}")
    record("G2", "permission check without TenantContext", "PASS", f"403 {pick(denied, 'code', default='')}")

    _, created = api.request(
        "POST",
        "/api/v1/notifications",
        {
            "template_code": "approval.task.assigned",
            "recipient_subject": str(admin_a_person),
            "variables": {"taskTitle": "m0-12-notify", "actor": "script"},
        },
        headers=headers_admin_a,
        expected=(200, 201),
    )
    note_id = pick(created, "notification_id", "notificationId", "id")
    _, mine = api.request("GET", "/api/v1/notifications", headers=headers_admin_a, expected=200)
    require(isinstance(mine, list), mine)
    ids = {pick(row, "notification_id", "notificationId", "id") for row in mine}
    require(note_id in ids, f"admin A should see enqueued notification {note_id} in {ids}")
    record("G18", "in-app notification list with issued context", "PASS", f"n={len(mine)} id={note_id}")

    _, other = api.request("GET", "/api/v1/notifications", headers=headers_admin_b, expected=200)
    require(isinstance(other, list), other)
    other_ids = {pick(row, "notification_id", "notificationId", "id") for row in other}
    require(note_id not in other_ids, f"tenant B listed A's notification {note_id}")
    record("G1", "in-app notifications do not leak across tenants", "PASS", f"B.n={len(other)}")


def print_table() -> None:
    print("\n## G1 / G18 checklist (this run)")
    print("| Gate | Item | Status | Detail |")
    print("|---|---|---|---|")
    for row in ROWS:
        detail = row["detail"].replace("|", "/")
        print(f"| {row['gate']} | {row['item']} | {row['status']} | {detail} |")
    print(f"\nPASS={PASS} FAIL={FAIL} SKIP={SKIP}")
    print("G18 is NOT closed: live list/query is not a real business walkthrough.")


def main() -> int:
    parser = argparse.ArgumentParser(description="NexusOne M0-12 dual-tenant HTTP acceptance")
    parser.add_argument("--base", default=os.environ.get("NEXUS_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument(
        "--bootstrap-token",
        default=os.environ.get("NEXUS_PLATFORM_BOOTSTRAP_TOKEN", ""),
    )
    parser.add_argument(
        "--actor",
        default=os.environ.get("NEXUS_P1_DEMO_ACTOR_ID", "01900000-0000-7000-8000-0000000000ac"),
    )
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()
    try:
        failed = run(args.base, args.bootstrap_token, args.actor)
    except Exception as ex:  # noqa: BLE001 — script must surface the assertion
        record("G1", "script aborted", "FAIL", str(ex))
        failed = 1
    print_table()
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump({"pass": PASS, "fail": FAIL, "skip": SKIP, "rows": ROWS}, fh, indent=2)
            fh.write("\n")
    return 1 if failed or FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
