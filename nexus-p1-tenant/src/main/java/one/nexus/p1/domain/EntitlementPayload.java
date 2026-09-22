package one.nexus.p1.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime projection payload. Schema id is stable so P4 can later write the same document
 * with {@code source=SUBSCRIPTION}. P1 does not define commercial SKUs.
 */
public record EntitlementPayload(String schema, Map<String, EntitlementItem> entitlements) {

    public static final String SCHEMA_V1 = "p4.runtime-entitlement.v1";

    public EntitlementPayload {
        if (!SCHEMA_V1.equals(schema)) {
            throw new DomainException(ErrorCode.QUOTA_INVALID, "unsupported entitlement schema: " + schema);
        }
        entitlements = Map.copyOf(entitlements);
    }

    public static EntitlementPayload defaultPack(int appLimit, int customLedgerLimit) {
        Map<String, EntitlementItem> items = new LinkedHashMap<>();
        items.put("apps", new EntitlementItem(true, appLimit, Map.of()));
        items.put("custom_ledgers", new EntitlementItem(true, customLedgerLimit, Map.of()));
        return new EntitlementPayload(SCHEMA_V1, items);
    }

    public record EntitlementItem(boolean enabled, Integer quota, Map<String, Object> attributes) {
        public EntitlementItem {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
