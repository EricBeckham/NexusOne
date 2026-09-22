package one.nexus.file.infra;

import one.nexus.file.FileAccessPolicy;

/** Default: any authenticated subject may read/write a referenced resource. Owner check is separate. */
public final class OwnerOnlyAccessPolicy implements FileAccessPolicy {
    @Override
    public boolean mayRead(String subject, String resourceId) {
        return subject != null && !subject.isBlank();
    }

    @Override
    public boolean mayWrite(String subject, String resourceId) {
        return subject != null && !subject.isBlank();
    }
}
