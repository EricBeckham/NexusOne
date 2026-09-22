package one.nexus.p3.dump;

/**
 * Honesty comment only. {@code pg_dump} ops stay off this engine.
 * Product dump uses AuthorizedExport.writeProjected.
 */
public final class CommentOnly {
    private CommentOnly() {}

    public static String path() {
        return "AuthorizedExport.writeProjected";
    }
}
