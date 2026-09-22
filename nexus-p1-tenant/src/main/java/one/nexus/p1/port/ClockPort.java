package one.nexus.p1.port;

import java.time.Instant;
import java.util.UUID;

public interface ClockPort {
    Instant now();
}
