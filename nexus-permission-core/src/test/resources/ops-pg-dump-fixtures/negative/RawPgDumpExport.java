package one.nexus.p3.dump;

import java.io.IOException;

/** Intentional fixture: product dump surface executes pg_dump. */
public final class RawPgDumpExport {
    private RawPgDumpExport() {}

    public static void dump() throws IOException {
        new ProcessBuilder("pg_dump", "--format=custom").start();
    }
}
