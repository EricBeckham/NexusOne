#!/usr/bin/env python3
"""G7 leftover: expense + travel withdraw against a live product JAR.

Uses the #69 catalog provisioner already on main. Does not rewrite leave or
AuditPort. Talks HTTP only: issued Bearer + X-Nexus-Context-Id, never
X-Tenant-Id.

Requires a running NexusOne JAR (see scripts/g7/run-withdraw-expense-travel.sh).
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
ROWS: list[dict[str, str]] = []

TYPES = (
    {
        "code": "expense",
        "title": "费用报销",
        "values": {
            "title": "交通费",
            "amount": 128.5,
            "category": "交通",
            "payee": "张三",
            "remark": "g7-expense-withdraw",
        },
        "query": {"title": "交通费", "category": "交通", "payee": "张三", "remark": "g7-expense-withdraw"},
        "table_needle": "expense",
    },
    {
        "code": "travel",
        "title": "出差申请",
        "values": {
            "title": "客户拜访",
            "destination": "上海",
            "days": 3,
            "purpose": "g7-travel-withdraw",
            "applicant": "alice",
        },
        "query": {"title": "客户拜访", "destination": "上海", "purpose": "g7-travel-withdraw"},
        "table_needle": "travel",
    },
)


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


def record(item: str, status: str, detail: str) -> None:
    global PASS, FAIL
    ROWS.append({"item": item, "status": status, "detail": detail})
    if status == "PASS":
        PASS += 1
    else:
        FAIL += 1
    print(f"[{status}] {item}: {detail}")


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        headers: dict[str, str] | None = None,
        expected: int | tuple | list | None = None,
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
            with urllib.request.urlopen(req, timeout=30) as resp:
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


def auth_headers(token: str, context_id: str | None = None, trace: str | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if context_id:
        headers["X-Nexus-Context-Id"] = context_id
    if trace:
        headers["X-Trace-Id"] = trace
    return headers


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def walk_type(
    api: Api,
    spec: dict[str, Any],
    suffix: str,
    admin_token: str,
    admin_ctx: str,
    reviewer_token: str,
    reviewer_ctx: str,
    reviewer_person_id: str,
) -> dict[str, Any]:
    code = spec["code"]
    withdraw_trace = str(uuid.uuid4())
    iam = auth_headers(admin_token, admin_ctx, withdraw_trace)
    provisioned = api.request(
        "POST",
        f"/api/v1/p3/business-types/{code}/provision",
        {"publish": True, "suffix": f"{code}{suffix}"},
        headers=iam,
        expected=(200, 201),
    )[1]
    form_id = pick(provisioned, "form_id", "formId")
    ledger_id = pick(provisioned, "ledger_id", "ledgerId")
    physical = pick(provisioned, "physical_table", "physicalTable")
    require(form_id and ledger_id, provisioned)
    require(physical and spec["table_needle"] in physical, provisioned)
    record(f"{code} catalog provision", "PASS", f"{physical} {ledger_id}")

    started = api.request(
        "POST",
        "/api/v1/p3/path/submit-start",
        {
            "form_id": form_id,
            "process_key": "nexusSequentialApproval",
            "assignee_type": "PERSON",
            "assignee_ref": str(reviewer_person_id),
            "values": spec["values"],
        },
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(pick(started, "approval_status", "approvalStatus") == "IN_APPROVAL", started)
    require(pick(pick(started, "process"), "status") == "RUNNING", started)
    instance_id = pick(pick(started, "process"), "id")
    record_id = pick(started, "record_id", "recordId")
    record(f"{code} submit-start", "PASS", f"instance={instance_id} record={record_id}")

    initiated = api.request(
        "GET",
        "/api/v1/todos/initiated",
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(any(pick(row, "id") == instance_id for row in initiated), initiated)
    record(f"{code} 我发起 RUNNING", "PASS", str(instance_id))

    forbidden = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(reviewer_token, reviewer_ctx, withdraw_trace),
        expected=403,
    )[1]
    record(f"{code} non-initiator withdraw forbidden", "PASS", pick(forbidden, "code", default="403"))

    withdrawn = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(pick(withdrawn, "approval_status", "approvalStatus") == "WITHDRAWN", withdrawn)
    require(pick(pick(withdrawn, "process"), "status") == "WITHDRAWN", withdrawn)
    record(f"{code} initiator withdraw", "PASS", "approval_status=WITHDRAWN")

    replay = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(pick(replay, "approval_status", "approvalStatus") == "WITHDRAWN", replay)
    record(f"{code} withdraw idempotent", "PASS", "second POST stays WITHDRAWN")

    row = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_id}/records/{record_id}",
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(pick(row, "approval_status", "approvalStatus") == "WITHDRAWN", row)
    for field, expected in spec["query"].items():
        require(pick(row, field) == expected, f"{code} {field}={pick(row, field)} expected {expected}")
    record(f"{code} query row", "PASS", f"approval_status=WITHDRAWN table={physical}")

    after = api.request(
        "GET",
        "/api/v1/todos/initiated",
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    match = next(item for item in after if pick(item, "id") == instance_id)
    require(pick(match, "status") == "WITHDRAWN", match)
    record(f"{code} 我发起 WITHDRAWN", "PASS", str(instance_id))

    audit = api.request(
        "GET",
        f"/api/v1/audit/traces/{withdraw_trace}",
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    types = [pick(event, "event_type", "eventType") for event in pick(audit, "events") or []]
    require("p3.path.submitted" in types, types)
    require("p3.approval.started" in types, types)
    require("p3.approval.withdrawn" in types, types)
    require(types.count("p3.approval.withdrawn") == 1, types)
    record(f"{code} audit by withdraw trace", "PASS", ",".join(str(item) for item in types))

    notify = api.request(
        "GET",
        "/api/v1/notifications",
        headers=auth_headers(admin_token, admin_ctx, withdraw_trace),
        expected=200,
    )[1]
    require(isinstance(notify, list), notify)
    record(f"{code} notify accepts issued context", "PASS", f"n={len(notify)}")

    return {
        "type": code,
        "form_id": form_id,
        "ledger_id": ledger_id,
        "physical_table": physical,
        "record_id": record_id,
        "instance_id": instance_id,
        "withdraw_trace": withdraw_trace,
        "audit_event_types": types,
    }


def run(base: str, bootstrap_token: str, actor: str) -> dict[str, Any]:
    api = Api(base)
    suffix = time.strftime("%H%M%S")
    setup_trace = str(uuid.uuid4())

    status, health = api.request("GET", "/actuator/health")
    require(status == 200 and pick(health, "status") == "UP", f"health {health}")
    record("actuator health", "PASS", "UP")

    p1h = platform_headers(actor, "g7-expense-travel-withdraw", setup_trace)
    tenant = api.request(
        "POST",
        "/api/v1/p1/tenants",
        {
            "name": f"G7 Expense Travel {suffix}",
            "code": f"g7et{suffix}",
            "legal_name": f"G7 Expense Travel {suffix} Ltd",
            "admin": {"display_name": f"Admin g7et{suffix}", "email": f"g7et{suffix}@example.com"},
            "config": {"locale": "zh-CN", "timezone": "Asia/Shanghai", "currency": "CNY"},
        },
        headers=p1h,
        expected=(200, 201),
    )[1]
    tenant_id = pick(pick(tenant, "tenant"), "id") or pick(tenant, "id")
    tenant_code = pick(pick(tenant, "tenant"), "code") or pick(tenant, "code")
    require(tenant_id, tenant)
    record("P1 tenant", "PASS", f"{tenant_code} {tenant_id}")

    require(bootstrap_token, "NEXUS_PLATFORM_BOOTSTRAP_TOKEN unset")
    admin = api.request(
        "POST",
        f"/api/v1/internal/tenants/{tenant_id}/admin-users",
        {
            "display_name": "G7 Expense Travel Admin",
            "email": f"g7-et-admin-{suffix}@example.com",
            "username": f"g7-et-admin-{suffix}@example.com",
        },
        headers={"X-Nexus-Bootstrap-Token": bootstrap_token},
        expected=200,
    )[1]
    admin_email = f"g7-et-admin-{suffix}@example.com"
    admin_token = stub_token(pick(admin, "keycloak_user_id"), admin_email, admin_email, "G7 Expense Travel Admin")
    admin_sess = api.request("POST", "/api/v1/auth/login", {"access_token": admin_token}, expected=200)[1]
    admin_ctx = pick(admin_sess, "context_id", "contextId")
    require(admin_ctx, admin_sess)
    record("stub login + issued context", "PASS", str(admin_ctx))

    reviewer_email = f"g7-et-rev-{suffix}@example.com"
    reviewer_token = stub_token(f"g7-et-rev-{suffix}", reviewer_email, reviewer_email, "G7 Expense Travel Reviewer")
    reviewer_login = api.request(
        "POST", "/api/v1/auth/login", {"access_token": reviewer_token}, expected=200
    )[1]
    reviewer_account = pick(reviewer_login, "account_id", "accountId")
    api.request(
        "POST",
        "/api/v1/memberships",
        {"account_id": reviewer_account},
        headers=auth_headers(admin_token, admin_ctx),
        expected=200,
    )
    reviewer_person = api.request(
        "POST",
        "/api/v1/persons",
        {
            "display_name": "G7 Expense Travel Reviewer",
            "email": reviewer_email,
            "account_id": reviewer_account,
        },
        headers=auth_headers(admin_token, admin_ctx),
        expected=200,
    )[1]
    reviewer_person_id = pick(reviewer_person, "id")
    api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": tenant_id},
        headers=auth_headers(reviewer_token),
        expected=200,
    )
    reviewer_sess = api.request("POST", "/api/v1/auth/login", {"access_token": reviewer_token}, expected=200)[1]
    reviewer_ctx = pick(reviewer_sess, "context_id", "contextId")
    if not reviewer_ctx:
        switched = api.request(
            "POST",
            "/api/v1/auth/tenant-switch",
            {"tenant_id": tenant_id},
            headers=auth_headers(reviewer_token),
            expected=200,
        )[1]
        reviewer_ctx = pick(switched, "context_id", "contextId")
    require(reviewer_ctx, reviewer_sess)
    record("reviewer membership", "PASS", str(reviewer_person_id))

    walks = [
        walk_type(
            api,
            spec,
            suffix,
            admin_token,
            admin_ctx,
            reviewer_token,
            reviewer_ctx,
            str(reviewer_person_id),
        )
        for spec in TYPES
    ]
    return {
        "tenant_id": tenant_id,
        "tenant_code": tenant_code,
        "walks": walks,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="G7 expense + travel withdraw live HTTP evidence")
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
    evidence: dict[str, Any] = {}
    try:
        evidence = run(args.base, args.bootstrap_token, args.actor)
    except Exception as ex:  # noqa: BLE001
        record("script aborted", "FAIL", str(ex))
    print("\n## G7 expense + travel withdraw (live HTTP)")
    print("| Item | Status | Detail |")
    print("|---|---|---|")
    for row in ROWS:
        print(f"| {row['item']} | {row['status']} | {row['detail'].replace('|', '/')} |")
    print(f"\nPASS={PASS} FAIL={FAIL}")
    payload = {
        "pass": PASS,
        "fail": FAIL,
        "rows": ROWS,
        "evidence": evidence,
        "base": args.base,
        "business": ["expense", "travel"],
    }
    if args.json_out:
        os.makedirs(os.path.dirname(args.json_out) or ".", exist_ok=True)
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
