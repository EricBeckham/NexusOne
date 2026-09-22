package one.nexus.p3.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import org.junit.jupiter.api.Test;

class G3IdempotencyKeyTest {
    @Test
    void blankKeyIsAbsentAndTenantIsPrefixed() {
        assertNull(BusinessPathService.normalizeIdempotencyKey(null));
        assertNull(BusinessPathService.normalizeIdempotencyKey("  "));
        assertEquals("click-1", BusinessPathService.normalizeIdempotencyKey(" click-1 "));
        UUID tenant = UUID.fromString("77777777-7777-4777-8777-777777777777");
        assertEquals(tenant + ":click-1", BusinessPathService.storedIdempotencyKey(tenant, "click-1"));
    }

    @Test
    void overlongKeyIsRejected() {
        NexusException ex = assertThrows(
                NexusException.class,
                () -> BusinessPathService.normalizeIdempotencyKey("k".repeat(129)));
        assertEquals(ErrorCode.API_BAD_REQUEST, ex.errorCode());
    }
}
