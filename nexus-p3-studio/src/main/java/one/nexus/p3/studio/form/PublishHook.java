package one.nexus.p3.studio.form;

/** Invoked inside the publish transaction before the running pointer switches. */
@FunctionalInterface
public interface PublishHook {
    void beforeActivate(FormSnapshot snapshot);

    static PublishHook noop() {
        return snapshot -> {
        };
    }
}
