package one.nexus.bootstrap.permission.bypass;

import java.io.IOException;

/**
 * Intentional ArchUnit fixture: product-shaped code executes {@code pg_dump}.
 * Not a product {@code src/main} class.
 */
public final class RawOpsPgDumpFixture {
    private RawOpsPgDumpFixture() {}

    public static void dump() throws IOException {
        new ProcessBuilder("pg_dump", "--format=custom").start();
    }
}
