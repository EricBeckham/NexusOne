package one.nexus.crypto.tls;

/**
 * Prints one dual-stack probe line for the leftover collect script.
 */
public final class DualStackTlsProbeMain {
    private DualStackTlsProbeMain() {}

    public static void main(String[] args) {
        DualStackTlsProbe.Result result = DualStackTlsProbe.localhostSimultaneous();
        System.out.println(result);
        if (!result.succeeded()) {
            System.exit(1);
        }
    }
}
