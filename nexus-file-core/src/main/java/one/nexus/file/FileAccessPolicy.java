package one.nexus.file;

/** Resource permission checked again before a signed URL is minted (P0-06 second auth). */
public interface FileAccessPolicy {
    boolean mayRead(String subject, String resourceId);

    default boolean mayWrite(String subject, String resourceId) {
        return mayRead(subject, resourceId);
    }
}
