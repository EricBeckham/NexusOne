package one.nexus.common.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceIdsTest {
    @AfterEach
    void clear() {
        TraceIds.clearMdc();
    }

    @Test
    void prefersIncomingHeader() {
        String id = "0199a1c0-0000-7000-8000-000000000001";
        assertEquals(id, TraceIds.resolve(id, "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"));
    }

    @Test
    void extractsW3cTraceparent() {
        assertEquals(
                "0af7651916cd43dd8448eb211c80319c",
                TraceIds.fromTraceparent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01").orElseThrow());
    }

    @Test
    void issuesUuidWhenInboundMissing() {
        String id = TraceIds.resolve(null, null);
        assertTrue(TraceIds.isValid(id));
    }

    @Test
    void mdcRoundTrip() {
        TraceIds.putMdc("abc");
        assertEquals("abc", TraceIds.currentOrUnknown());
        assertEquals("abc", TraceIds.current().orElseThrow());
        assertEquals("abc", TraceIds.logFields().get("trace_id"));
        assertEquals("abc", TraceIds.logFields().get("traceId"));
        assertEquals("corr-1", TraceIds.logFields("abc", "corr-1").get("correlation_id"));
        TraceIds.clearMdc();
        assertEquals("missing", TraceIds.currentOrUnknown());
        assertTrue(TraceIds.current().isEmpty());
    }
}
