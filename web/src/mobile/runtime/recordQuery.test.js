import { describe, expect, it } from "vitest";
import { buildRecordQuery, isLedgerFieldCode, recordQueryString } from "./recordQuery.js";

describe("recordQuery", () => {
  it("keeps business field equals and paging", () => {
    expect(buildRecordQuery({ title: "alpha-two", leave_days: "3" }, { page: 0, size: 20 })).toEqual({
      title: "alpha-two",
      leave_days: "3",
      page: "0",
      size: "20",
    });
    expect(recordQueryString({ title: "alpha-two" })).toBe("title=alpha-two");
  });

  it("drops tenant identity and reserved system columns", () => {
    expect(isLedgerFieldCode("tenant_id")).toBe(false);
    expect(isLedgerFieldCode("title")).toBe(true);
    expect(buildRecordQuery({ tenant_id: "stolen", id: "x", title: "ok", "Title": "no" })).toEqual({
      title: "ok",
    });
  });
});
