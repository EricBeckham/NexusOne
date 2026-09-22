#!/usr/bin/env python3
"""G7 withdraw + reject dual-tenant HTTP walkthrough (evidence, not a new class).

Talks to the same product APIs the Vue shell uses:
  POST /api/v1/p3/path/submit-start
  POST /api/v1/p3/path/instances/{id}/withdraw
  POST /api/v1/p3/path/tasks/{id}/reject
  GET  /api/v1/audit/traces/{traceId}
  GET  /api/v1/notifications

Prefer travel-shaped ledger. If PR #69 catalog is present, provision via
POST /api/v1/p3/business-types/travel/provision; otherwise reuse M0-06/07
application + ledger + form + validate + publish. Does not change AuditPort.
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


def auth_headers(token: str, context_id: str | None = None, extra: dict[str, str] | None = None, trace: str | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if context_id:
        headers["X-Nexus-Context-Id"] = context_id
    if trace:
        headers["X-Trace-Id"] = trace
    if extra:
        headers.update(extra)
    return headers


def require(cond: bool, message: str) -> None:
    if not cond:
        raise AssertionError(message)


def event_types(body: Any) -> set[str]:
    events = pick(body, "events") or []
    return {pick(row, "event_type", "eventType") for row in events}


def run(base: str, bootstrap_token: str, actor: str) -> int:
    api = Api(base)
    suffix = time.strftime("%H%M%S")
    withdraw_trace = str(uuid.uuid4())
    reject_trace = str(uuid.uuid4())

    status, health = api.request("GET", "/actuator/health")
    require(status == 200 and pick(health, "status") == "UP", f"health {health}")
    record("G8", "actuator health", "PASS", "UP")

    if not bootstrap_token:
        record("G7", "P2 admin bootstrap", "FAIL", "NEXUS_PLATFORM_BOOTSTRAP_TOKEN unset")
        return 1

    p1h = platform_headers(actor, "g7-withdraw-walk", withdraw_trace)

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

    alpha = create_tenant(f"g7a_{suffix}", f"G7 Walk A {suffix}")
    beta = create_tenant(f"g7b_{suffix}", f"G7 Walk B {suffix}")
    alpha_id = pick(pick(alpha, "tenant"), "id")
    beta_id = pick(pick(beta, "tenant"), "id")
    require(alpha_id != beta_id, "two tenants must have distinct ids")
    record("G7", "create two business tenants", "PASS", f"{alpha_id} / {beta_id}")

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

    admin_a = bootstrap_admin(alpha_id, f"g7-a-{suffix}@example.com", "G7 Admin A")
    admin_b = bootstrap_admin(beta_id, f"g7-b-{suffix}@example.com", "G7 Admin B")
    record("G7", "P2 admin init per tenant", "PASS", f"{pick(admin_a, 'person_id')} / {pick(admin_b, 'person_id')}")

    def login(sub: str, email: str, name: str) -> tuple[str, dict]:
        token = stub_token(sub, email, email, name)
        _, view = api.request("POST", "/api/v1/auth/login", {"access_token": token}, expected=200)
        return token, view

    token_a, sess_a = login(pick(admin_a, "keycloak_user_id"), f"g7-a-{suffix}@example.com", "G7 Admin A")
    token_b, sess_b = login(pick(admin_b, "keycloak_user_id"), f"g7-b-{suffix}@example.com", "G7 Admin B")
    ctx_a = pick(sess_a, "context_id", "contextId")
    ctx_b = pick(sess_b, "context_id", "contextId")
    require(ctx_a and ctx_b, "admins must auto-issue context")
    headers_a = auth_headers(token_a, ctx_a)
    headers_b = auth_headers(token_b, ctx_b)
    record("G7", "login auto-issues context", "PASS", "admin A/B")

    reviewer_sub = f"g7-ra-{suffix}"
    reviewer_email = f"g7-ra-{suffix}@example.com"
    reviewer_token, reviewer_login = login(reviewer_sub, reviewer_email, "G7 Reviewer A")
    reviewer_account = pick(reviewer_login, "account_id", "accountId")
    api.request(
        "POST",
        "/api/v1/memberships",
        {"account_id": reviewer_account},
        headers=headers_a,
        expected=200,
    )
    _, reviewer_person = api.request(
        "POST",
        "/api/v1/persons",
        {
            "display_name": "G7 Reviewer A",
            "employee_no": f"RA-{suffix}",
            "email": reviewer_email,
            "account_id": reviewer_account,
        },
        headers=headers_a,
        expected=200,
    )
    reviewer_person_id = pick(reviewer_person, "id")
    _, reviewer_switch = api.request(
        "POST",
        "/api/v1/auth/tenant-switch",
        {"tenant_id": alpha_id},
        headers=auth_headers(reviewer_token),
        expected=200,
    )
    reviewer_ctx = pick(reviewer_switch, "context_id", "contextId")
    headers_reviewer = auth_headers(reviewer_token, reviewer_ctx)
    record("G7", "reviewer membership in tenant A", "PASS", str(reviewer_person_id))

    travel_a = provision_travel(api, headers_a, f"w{suffix}a")
    travel_b = provision_travel(api, headers_b, f"w{suffix}b")
    form_a = pick(travel_a, "form_id", "formId")
    ledger_a = pick(travel_a, "ledger_id", "ledgerId")
    require(form_a and ledger_a, travel_a)
    require(pick(travel_b, "form_id", "formId"), travel_b)
    record(
        "G7",
        "provision travel-shaped ledger/form",
        "PASS",
        f"source={travel_a.get('source')} table={pick(travel_a, 'physical_table', 'physicalTable')}",
    )

    _, started = api.request(
        "POST",
        "/api/v1/p3/path/submit-start",
        {
            "form_id": form_a,
            "process_key": "nexusSequentialApproval",
            "assignee_type": "PERSON",
            "assignee_ref": reviewer_person_id,
            "values": {
                "title": "上海出差撤回",
                "destination": "上海",
                "days": 2,
                "purpose": "客户拜访",
                "applicant": "alice",
            },
        },
        headers=auth_headers(token_a, ctx_a, trace=withdraw_trace),
        expected=200,
    )
    require(pick(started, "approval_status", "approvalStatus") == "IN_APPROVAL", started)
    instance_id = pick(pick(started, "process"), "id")
    record_id = pick(started, "record_id", "recordId")
    require(instance_id and record_id, started)
    record("G7", "submit-start travel (withdraw)", "PASS", f"instance={instance_id}")

    status, stolen = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(token_b, ctx_b, trace=withdraw_trace),
    )
    require(status == 404 and pick(stolen, "code") == "P3.FLOW.0002", stolen)
    record("G1", "tenant B cannot withdraw A's instance", "PASS", "P3.FLOW.0002")

    status, client_tenant = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(token_a, extra={"X-Tenant-Id": alpha_id}, trace=withdraw_trace),
    )
    require(status == 400 and pick(client_tenant, "code") in {"P3.FLOW.0006", "P2.IAM.CLIENT_TENANT_REJECTED"}, client_tenant)
    record("G1", "withdraw rejects client X-Tenant-Id", "PASS", pick(client_tenant, "code"))

    _, withdrawn = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(token_a, ctx_a, trace=withdraw_trace),
        expected=200,
    )
    require(pick(withdrawn, "approval_status", "approvalStatus") == "WITHDRAWN", withdrawn)
    require(pick(pick(withdrawn, "process"), "status") == "WITHDRAWN", withdrawn)
    _, withdrawn_again = api.request(
        "POST",
        f"/api/v1/p3/path/instances/{instance_id}/withdraw",
        {},
        headers=auth_headers(token_a, ctx_a, trace=withdraw_trace),
        expected=200,
    )
    require(pick(withdrawn_again, "approval_status", "approvalStatus") == "WITHDRAWN", withdrawn_again)
    _, row = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_a}/records/{record_id}",
        headers=auth_headers(token_a, ctx_a, trace=withdraw_trace),
        expected=200,
    )
    require(pick(row, "approval_status", "approvalStatus") == "WITHDRAWN", row)
    require(pick(row, "destination") == "上海", row)
    status, leak = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_a}/records/{record_id}",
        headers=auth_headers(token_b, ctx_b, trace=withdraw_trace),
    )
    require(status in (403, 404), leak)
    _, initiated = api.request("GET", "/api/v1/todos/initiated", headers=headers_a, expected=200)
    require(any(pick(item, "status") == "WITHDRAWN" for item in initiated), initiated)
    record("G7", "travel withdraw + query + idempotent replay", "PASS", f"record={record_id}")

    _, audit_w = api.request(
        "GET",
        f"/api/v1/audit/traces/{withdraw_trace}",
        headers=auth_headers(token_a, ctx_a, trace=withdraw_trace),
        expected=200,
    )
    types_w = event_types(audit_w)
    require("p3.path.submitted" in types_w, audit_w)
    require("p3.approval.withdrawn" in types_w, audit_w)
    _, audit_b = api.request(
        "GET",
        f"/api/v1/audit/traces/{withdraw_trace}",
        headers=auth_headers(token_b, ctx_b, trace=withdraw_trace),
        expected=200,
    )
    require("p3.approval.withdrawn" not in event_types(audit_b), audit_b)
    record("G7", "withdraw audit-by-trace stays on tenant A", "PASS", ",".join(sorted(types_w)))

    _, reject_started = api.request(
        "POST",
        "/api/v1/p3/path/submit-start",
        {
            "form_id": form_a,
            "process_key": "nexusSequentialApproval",
            "assignee_type": "PERSON",
            "assignee_ref": reviewer_person_id,
            "values": {
                "title": "北京出差驳回",
                "destination": "北京",
                "days": 1,
                "purpose": "培训",
                "applicant": "alice",
            },
        },
        headers=auth_headers(token_a, ctx_a, trace=reject_trace),
        expected=200,
    )
    reject_todo = pick(pick(reject_started, "todo"), "id")
    reject_record = pick(reject_started, "record_id", "recordId")
    require(reject_todo and reject_record, reject_started)

    status, steal_reject = api.request(
        "POST",
        f"/api/v1/p3/path/tasks/{reject_todo}/reject",
        {},
        headers=auth_headers(token_b, ctx_b, trace=reject_trace),
    )
    require(status == 404 and pick(steal_reject, "code") == "P3.FLOW.0002", steal_reject)

    _, rejected = api.request(
        "POST",
        f"/api/v1/p3/path/tasks/{reject_todo}/reject",
        {},
        headers=auth_headers(reviewer_token, reviewer_ctx, trace=reject_trace),
        expected=200,
    )
    require(pick(rejected, "approval_status", "approvalStatus") == "REJECTED", rejected)
    _, reject_row = api.request(
        "GET",
        f"/api/v1/p3/ledgers/{ledger_a}/records/{reject_record}",
        headers=auth_headers(token_a, ctx_a, trace=reject_trace),
        expected=200,
    )
    require(pick(reject_row, "approval_status", "approvalStatus") == "REJECTED", reject_row)
    require(pick(reject_row, "destination") == "北京", reject_row)
    _, audit_r = api.request(
        "GET",
        f"/api/v1/audit/traces/{reject_trace}",
        headers=auth_headers(token_a, ctx_a, trace=reject_trace),
        expected=200,
    )
    types_r = event_types(audit_r)
    require("p3.approval.rejected" in types_r, audit_r)
    _, notes = api.request("GET", "/api/v1/notifications", headers=headers_a, expected=200)
    require(isinstance(notes, list), notes)
    record("G7", "travel reject + audit + notify", "PASS", f"todo={reject_todo} events={sorted(types_r)}")
    record("G7", "not a new business class", "PASS", "existing path APIs; travel-shaped fields only")
    return FAIL


def provision_travel(api: Api, headers: dict[str, str], suffix: str) -> dict:
    status, catalog = api.request(
        "POST",
        "/api/v1/p3/business-types/travel/provision",
        {"publish": True, "suffix": suffix},
        headers=headers,
    )
    if status in (200, 201) and pick(catalog, "form_id", "formId"):
        catalog["source"] = "catalog"
        return catalog

    _, app = api.request(
        "POST",
        "/api/v1/p3/applications",
        {"app_code": f"hr{suffix}", "display_name": "差旅", "kind": "CUSTOM"},
        headers=headers,
        expected=(200, 201),
    )
    app_id = pick(app, "id")
    _, ledger = api.request(
        "POST",
        f"/api/v1/p3/applications/{app_id}/ledgers",
        {
            "ledger_code": "travel",
            "display_name": "出差申请",
            "fields": [
                {"field_code": "title", "display_name": "标题", "field_type": "TEXT", "required": True},
                {"field_code": "destination", "display_name": "目的地", "field_type": "TEXT", "required": True},
                {"field_code": "days", "display_name": "天数", "field_type": "INTEGER", "required": True},
                {"field_code": "purpose", "display_name": "事由", "field_type": "TEXT", "required": True},
                {"field_code": "applicant", "display_name": "申请人", "field_type": "TEXT", "required": True},
                {"field_code": "approval_status", "display_name": "审批状态", "field_type": "TEXT", "required": False},
            ],
        },
        headers=headers,
        expected=(200, 201),
    )
    _, form = api.request(
        "POST",
        "/api/v1/p3/forms",
        {
            "app_code": pick(app, "app_code", "appCode"),
            "form_code": f"travel_form{suffix}",
            "name": "出差申请",
            "bindings": [
                {"widget_code": "title", "ledger_code": "travel", "field_code": "title", "required": True},
                {"widget_code": "destination", "ledger_code": "travel", "field_code": "destination", "required": True},
                {"widget_code": "days", "ledger_code": "travel", "field_code": "days", "required": True, "widget_type": "NUMBER"},
                {"widget_code": "purpose", "ledger_code": "travel", "field_code": "purpose", "required": True, "widget_type": "TEXTAREA"},
                {"widget_code": "applicant", "ledger_code": "travel", "field_code": "applicant", "required": True, "widget_type": "PERSON"},
            ],
        },
        headers=headers,
        expected=(200, 201),
    )
    form_id = pick(form, "form_id", "formId", "id")
    api.request("POST", f"/api/v1/p3/forms/{form_id}/validate", {}, headers=headers, expected=200)
    api.request(
        "POST",
        f"/api/v1/p3/forms/{form_id}/publish",
        {"notes": "g7-walk"},
        headers=headers,
        expected=200,
    )
    return {
        "source": "m0-06-07",
        "form_id": form_id,
        "ledger_id": pick(ledger, "id"),
        "physical_table": pick(ledger, "physical_table", "physicalTable"),
        "type_code": "travel",
    }


def print_table() -> None:
    print("\n## G7 withdraw / reject walkthrough (this run)")
    print("| Gate | Item | Status | Detail |")
    print("|---|---|---|---|")
    for row in ROWS:
        detail = row["detail"].replace("|", "/")
        print(f"| {row['gate']} | {row['item']} | {row['status']} | {detail} |")
    print(f"\nPASS={PASS} FAIL={FAIL} SKIP={SKIP}")
    print("This is HTTP walkthrough evidence, not a new G7 business class.")


def main() -> int:
    parser = argparse.ArgumentParser(description="NexusOne G7 withdraw/reject dual-tenant HTTP walkthrough")
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
        record("G7", "script aborted", "FAIL", str(ex))
        failed = 1
    print_table()
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as fh:
            json.dump({"pass": PASS, "fail": FAIL, "skip": SKIP, "rows": ROWS}, fh, indent=2)
            fh.write("\n")
    return 1 if failed or FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
