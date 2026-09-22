package one.nexus.p1.infra.uuid;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import one.nexus.p1.port.IdGenerator;

@Component
public class UuidV7Generator implements IdGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public UUID next() {
        return generate(Instant.now());
    }

    static UUID generate(Instant instant) {
        long epochMs = instant.toEpochMilli();
        long rand = new SecureRandom().nextLong();
        long msb = (epochMs << 16) | 0x7000L | (rand >>> 52);
        long lsb = (rand & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
