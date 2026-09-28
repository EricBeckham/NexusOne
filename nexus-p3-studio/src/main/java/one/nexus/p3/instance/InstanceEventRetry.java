package one.nexus.p3.instance;

/** Thrown when a revision gap cannot be filled yet. The outbox retries; the event is not marked seen. */
public class InstanceEventRetry extends RuntimeException {
    public InstanceEventRetry() {
        super("instance revision gap");
    }
}
