package one.nexus.p3.instance;

import java.util.UUID;
import one.nexus.isolation.boundary.IsolatedEnvelope;
import one.nexus.event.OutboxHandler;
import org.springframework.stereotype.Component;

/** Subscribes to {@code p4.app_instance}. Tenant comes from the envelope. */
@Component
public class AppInstanceEventHandler implements OutboxHandler {
    static final String DESTINATION = "p4.app_instance";

    private final AppInstanceEventConsumer consumer;

    public AppInstanceEventHandler(AppInstanceEventConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean supports(String destination) {
        return DESTINATION.equals(destination);
    }

    @Override
    public void onDispatch(IsolatedEnvelope envelope) {
        consumer.accept(UUID.fromString(envelope.tenantId()), InstanceEvents.parse(envelope.payload()));
    }
}
