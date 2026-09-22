package one.nexus.common.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {
    @Test
    void generatesVersion7VariantRfc4122() {
        UUID id = UuidV7.generate();
        assertTrue(UuidV7.isUuidV7(id));
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }

    @Test
    void laterTimestampSortsAfterEarlier() {
        SecureRandom random = new SecureRandom(new byte[] {1, 2, 3, 4});
        UUID first = UuidV7.generate(1_700_000_000_000L, random);
        UUID second = UuidV7.generate(1_700_000_000_001L, random);
        assertTrue(first.compareTo(second) < 0);
        assertNotEquals(first, UuidV7.generate());
    }
}
