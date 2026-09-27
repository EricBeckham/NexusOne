package one.nexus.p3.domain;

import one.nexus.common.error.ErrorCode;

/** Stable P3 codes for M0-06 schema / ledger / record APIs. */
public final class P3ErrorCodes {
    public static final ErrorCode INVALID_IDENTIFIER = ErrorCode.of("P3", "SCHEMA", 1);
    public static final ErrorCode RESERVED_FIELD = ErrorCode.of("P3", "SCHEMA", 2);
    public static final ErrorCode ILLEGAL_EVOLUTION = ErrorCode.of("P3", "SCHEMA", 3);
    public static final ErrorCode TABLE_CONFLICT = ErrorCode.of("P3", "SCHEMA", 4);
    public static final ErrorCode APP_NOT_FOUND = ErrorCode.of("P3", "APP", 1);
    public static final ErrorCode APP_CONFLICT = ErrorCode.of("P3", "APP", 2);
    public static final ErrorCode LEDGER_NOT_FOUND = ErrorCode.of("P3", "LEDGER", 1);
    public static final ErrorCode LEDGER_CONFLICT = ErrorCode.of("P3", "LEDGER", 2);
    public static final ErrorCode LEDGER_NOT_READY = ErrorCode.of("P3", "LEDGER", 3);
    public static final ErrorCode RECORD_NOT_FOUND = ErrorCode.of("P3", "RECORD", 1);
    public static final ErrorCode RECORD_CONFLICT = ErrorCode.of("P3", "RECORD", 2);
    public static final ErrorCode QUERY_INVALID = ErrorCode.of("P3", "QUERY", 1);
    public static final ErrorCode MISSING_TENANT = ErrorCode.of("P3", "CTX", 1);
    /** FA3: post-completion action config is invalid (mapping / ledgers). */
    public static final ErrorCode ACTION_INVALID = ErrorCode.of("P3", "ACTION", 1);
    public static final ErrorCode ACTION_NOT_FOUND = ErrorCode.of("P3", "ACTION", 2);
    public static final ErrorCode ACTION_CONFLICT = ErrorCode.of("P3", "ACTION", 3);

    private P3ErrorCodes() {}
}
