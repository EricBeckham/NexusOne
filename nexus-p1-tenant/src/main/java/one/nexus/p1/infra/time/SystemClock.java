package one.nexus.p1.infra.time;

import java.time.Instant;
import org.springframework.stereotype.Component;
import one.nexus.p1.port.ClockPort;

@Component
public class SystemClock implements ClockPort {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
