import { describe, expect, it } from "vitest";
import {
  asRows,
  callLogFields,
  clientFields,
  deliveryFields,
  latestRun,
  show,
  webhookFields,
} from "./connectDisplay";

describe("P5 connect display", () => {
  it("reads snake_case records and drops secrets, payloads, and bodies", () => {
    expect(clientFields({
      id: "c1",
      name: "scheduler",
      key_prefix: "nx",
      scopes: ["integration:call"],
      rate_limit_per_minute: 60,
      active: true,
      secret: "must-not-map",
      secret_digest: "digest",
    })).toEqual({
      id: "c1",
      name: "scheduler",
      keyPrefix: "nx",
      scopes: ["integration:call"],
      rateLimit: 60,
      active: true,
      createdAt: null,
    });
    expect(deliveryFields({
      id: "d1",
      event_id: "event-1",
      event_type: "record.changed",
      status: "DELIVERED",
      attempts: 1,
      payload: { token: "sekret" },
    }).payload).toBeUndefined();
    expect(callLogFields({
      api_path: "/internal/call",
      http_method: "POST",
      trace_id: "trace-1",
      response: { body: "raw" },
      request: { body: "raw" },
    })).toMatchObject({ apiPath: "/internal/call", httpMethod: "POST", traceId: "trace-1" });
    expect(callLogFields({ response: { body: "raw" } }).response).toBeUndefined();
  });

  it("accepts camelCase and treats a missing list as unavailable", () => {
    expect(webhookFields({ eventTypes: ["record.changed"], endpoint: "https://hooks.example.test" }).eventTypes).toEqual(["record.changed"]);
    expect(asRows([])).toEqual([]);
    expect(asRows({ items: [] })).toBeNull();
    expect(show(0)).toBe("0");
    expect(show(null)).toBe("暂无");
    expect(latestRun([
      { connector_code: "health", started_at: "2026-09-24T00:00:00Z", status: "FAILED" },
      { connector_code: "health", started_at: "2026-09-24T01:00:00Z", status: "SUCCESS" },
    ], "health").status).toBe("SUCCESS");
  });
});
