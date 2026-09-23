package one.nexus.crypto.tls;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateEntry;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClientProtocol;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto;
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider;

/**
 * Localhost dual-stack TLS: international TLS 1.3 (AES-GCM / P-256) and GM TLS
 * 1.3 (RFC 8998 {@code TLS_SM4_GCM_SM3} / SM2) on two loopback ports at once.
 * Cross-talk (intl client → GM port, GM client → intl port) is rejected.
 *
 * <p>This is the runnable-without-Tongsuo leftover. It is not Nginx/Tongsuo,
 * not GM/T 0024 encryption-cert negotiation, and not 等保 / 密评 / HSM.
 */
public final class DualStackTlsProbe {
    public static final String INTL_SERVER_PAYLOAD = "G17-INTL-OK";
    public static final String INTL_CLIENT_PAYLOAD = "G17-INTL-PING";
    public static final String GM_SERVER_PAYLOAD = GmTlsHandshakeProbe.SERVER_PAYLOAD;
    public static final String GM_CLIENT_PAYLOAD = GmTlsHandshakeProbe.CLIENT_PAYLOAD;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private DualStackTlsProbe() {}

    public record StackResult(
            boolean succeeded,
            String stack,
            String protocol,
            String cipherSuite,
            int cipherSuiteId,
            String serverSubject,
            String payload) {}

    public record Result(
            boolean succeeded,
            boolean simultaneous,
            StackResult international,
            StackResult gm,
            boolean crossTalkRejected,
            boolean dualServerCertsDistinct,
            String javaRuntime,
            String bcProvider,
            String bcTls) {}

    public static Result localhostSimultaneous() {
        return localhostSimultaneous(DualStackTlsMaterial.ephemeral());
    }

    public static Result localhostSimultaneous(DualStackTlsMaterial material) {
        JcaTlsCrypto crypto = new JcaTlsCryptoProvider()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .create(new SecureRandom());
        try (ServerSocket intlListen = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                ServerSocket gmListen = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int intlPort = intlListen.getLocalPort();
            int gmPort = gmListen.getLocalPort();
            CompletableFuture<StackResult> intlServer =
                    CompletableFuture.supplyAsync(() -> accept(crypto, intlListen, material, true));
            CompletableFuture<StackResult> gmServer =
                    CompletableFuture.supplyAsync(() -> accept(crypto, gmListen, material, false));
            StackResult intlClient = connect(crypto, intlPort, material, true);
            StackResult gmClient = connect(crypto, gmPort, material, false);
            intlServer.get(15, TimeUnit.SECONDS);
            gmServer.get(15, TimeUnit.SECONDS);
            boolean crossTalkRejected = crossTalkRejected(crypto, material);
            return new Result(
                    intlClient.succeeded() && gmClient.succeeded() && crossTalkRejected,
                    true,
                    intlClient,
                    gmClient,
                    crossTalkRejected,
                    material.gm().dualServerCertsDistinct(),
                    GmTlsVersions.javaRuntime(),
                    GmTlsVersions.bcProvider(),
                    GmTlsVersions.bcTls());
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    static boolean crossTalkRejected(JcaTlsCrypto crypto, DualStackTlsMaterial material) {
        boolean intlToGm = handshakeFails(() -> {
            try (ServerSocket gmListen = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                int port = gmListen.getLocalPort();
                CompletableFuture<?> server =
                        CompletableFuture.supplyAsync(() -> accept(crypto, gmListen, material, false));
                try {
                    connect(crypto, port, material, true);
                } finally {
                    server.cancel(true);
                }
            }
        });
        boolean gmToIntl = handshakeFails(() -> {
            try (ServerSocket intlListen = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                int port = intlListen.getLocalPort();
                CompletableFuture<?> server =
                        CompletableFuture.supplyAsync(() -> accept(crypto, intlListen, material, true));
                try {
                    connect(crypto, port, material, false);
                } finally {
                    server.cancel(true);
                }
            }
        });
        return intlToGm && gmToIntl;
    }

    private static boolean handshakeFails(ThrowingRunnable action) {
        try {
            action.run();
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private static StackResult accept(
            JcaTlsCrypto crypto, ServerSocket serverSocket, DualStackTlsMaterial material, boolean international) {
        try (Socket accepted = serverSocket.accept()) {
            TlsServerProtocol protocol = new TlsServerProtocol(accepted.getInputStream(), accepted.getOutputStream());
            PeerServer server = international
                    ? new IntlServer(crypto, material)
                    : new GmServer(crypto, material);
            protocol.accept(server);
            byte[] ping = new byte[32];
            int read = protocol.getInputStream().read(ping);
            String reply = international ? INTL_SERVER_PAYLOAD : GM_SERVER_PAYLOAD;
            protocol.getOutputStream().write(reply.getBytes());
            protocol.getOutputStream().flush();
            return new StackResult(
                    true,
                    international ? "intl" : "gm",
                    server.protocol,
                    cipherName(server.cipherSuite),
                    server.cipherSuite,
                    international
                            ? material.intlServer().certificate().getSubjectX500Principal().getName()
                            : material.gm().serverSign().certificate().getSubjectX500Principal().getName(),
                    read > 0 ? new String(ping, 0, read) : "");
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    private static StackResult connect(
            JcaTlsCrypto crypto, int port, DualStackTlsMaterial material, boolean international) {
        try (Socket clientSocket = new Socket("127.0.0.1", port)) {
            TlsClientProtocol protocol =
                    new TlsClientProtocol(clientSocket.getInputStream(), clientSocket.getOutputStream());
            PeerClient client = international
                    ? new IntlClient(crypto, material)
                    : new GmClient(crypto, material);
            protocol.connect(client);
            String ping = international ? INTL_CLIENT_PAYLOAD : GM_CLIENT_PAYLOAD;
            protocol.getOutputStream().write(ping.getBytes());
            protocol.getOutputStream().flush();
            byte[] reply = new byte[32];
            int n = protocol.getInputStream().read(reply);
            String payload = n > 0 ? new String(reply, 0, n) : "";
            return new StackResult(
                    true,
                    international ? "intl" : "gm",
                    String.valueOf(client.protocol),
                    cipherName(client.cipherSuite),
                    client.cipherSuite,
                    international
                            ? material.intlServer().certificate().getSubjectX500Principal().getName()
                            : material.gm().serverSign().certificate().getSubjectX500Principal().getName(),
                    payload);
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    private static String cipherName(int id) {
        if (id == CipherSuite.TLS_SM4_GCM_SM3) {
            return GmTlsOids.TLS_SM4_GCM_SM3_NAME;
        }
        if (id == CipherSuite.TLS_SM4_CCM_SM3) {
            return "TLS_SM4_CCM_SM3";
        }
        if (id == CipherSuite.TLS_AES_128_GCM_SHA256) {
            return "TLS_AES_128_GCM_SHA256";
        }
        if (id == CipherSuite.TLS_AES_256_GCM_SHA384) {
            return "TLS_AES_256_GCM_SHA384";
        }
        return "0x" + Integer.toHexString(id);
    }

    private static Certificate tlsChain(JcaTlsCrypto crypto, X509Certificate... chain) throws IOException {
        CertificateEntry[] entries = new CertificateEntry[chain.length];
        for (int i = 0; i < chain.length; i++) {
            try {
                TlsCertificate tls = crypto.createCertificate(chain[i].getEncoded());
                entries[i] = new CertificateEntry(tls, null);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
        return new Certificate(TlsUtils.EMPTY_BYTES, entries);
    }

    private static JcaDefaultTlsCredentialedSigner signer(
            JcaTlsCrypto crypto,
            org.bouncycastle.tls.TlsContext context,
            java.security.PrivateKey key,
            X509Certificate[] chain,
            SignatureAndHashAlgorithm algorithm)
            throws IOException {
        return new JcaDefaultTlsCredentialedSigner(
                new TlsCryptoParameters(context), crypto, key, tlsChain(crypto, chain), algorithm);
    }

    private static RuntimeException wrap(Throwable ex) {
        Throwable cause = ex;
        while (cause instanceof RuntimeException runtime && runtime.getCause() != null) {
            if (runtime.getCause() instanceof TlsFatalAlert) {
                cause = runtime.getCause();
                break;
            }
            if (runtime.getCause() == runtime) {
                break;
            }
            cause = runtime.getCause();
        }
        if (ex.getCause() instanceof TlsFatalAlert alert) {
            return new IllegalStateException("TLS handshake failed: " + alert.getAlertDescription(), alert);
        }
        if (ex instanceof TlsFatalAlert alert) {
            return new IllegalStateException("TLS handshake failed: " + alert.getAlertDescription(), alert);
        }
        if (ex instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("TLS handshake failed", ex);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private abstract static class PeerServer extends DefaultTlsServer {
        String protocol = "";
        int cipherSuite;

        PeerServer(JcaTlsCrypto crypto) {
            super(crypto);
        }

        @Override
        public void notifyHandshakeComplete() throws IOException {
            protocol = String.valueOf(context.getSecurityParameters().getNegotiatedVersion());
            cipherSuite = context.getSecurityParameters().getCipherSuite();
        }
    }

    private abstract static class PeerClient extends DefaultTlsClient {
        String protocol = "";
        int cipherSuite;

        PeerClient(JcaTlsCrypto crypto) {
            super(crypto);
        }

        @Override
        public void notifyHandshakeComplete() throws IOException {
            protocol = String.valueOf(context.getSecurityParameters().getNegotiatedVersion());
            cipherSuite = context.getSecurityParameters().getCipherSuite();
        }

        @Override
        public TlsAuthentication getAuthentication() {
            return new TlsAuthentication() {
                @Override
                public void notifyServerCertificate(TlsServerCertificate serverCertificate) {
                    // Local ephemeral CA; stack identity is asserted by the negotiated cipher.
                }

                @Override
                public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
                    return null;
                }
            };
        }
    }

    private static final class IntlServer extends PeerServer {
        private final JcaTlsCrypto crypto;
        private final DualStackTlsMaterial material;

        IntlServer(JcaTlsCrypto crypto, DualStackTlsMaterial material) {
            super(crypto);
            this.crypto = crypto;
            this.material = material;
        }

        @Override
        protected int[] getSupportedCipherSuites() {
            return new int[] {CipherSuite.TLS_AES_128_GCM_SHA256, CipherSuite.TLS_AES_256_GCM_SHA384};
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] {ProtocolVersion.TLSv13};
        }

        @Override
        public int[] getSupportedGroups() throws IOException {
            return new int[] {NamedGroup.secp256r1};
        }

        @Override
        public TlsCredentials getCredentials() throws IOException {
            return signer(
                    crypto,
                    context,
                    material.intlServer().privateKey(),
                    material.intlServer().chain(),
                    SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
        }
    }

    private static final class IntlClient extends PeerClient {
        IntlClient(JcaTlsCrypto crypto, DualStackTlsMaterial material) {
            super(crypto);
        }

        @Override
        protected int[] getSupportedCipherSuites() {
            return new int[] {CipherSuite.TLS_AES_128_GCM_SHA256, CipherSuite.TLS_AES_256_GCM_SHA384};
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] {ProtocolVersion.TLSv13};
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Vector getSupportedGroups(Vector namedGroupRoles) {
            Vector groups = new Vector();
            groups.add(NamedGroup.secp256r1);
            return groups;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Vector getSupportedSignatureAlgorithms() {
            Vector algs = new Vector();
            algs.add(SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa));
            return algs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Vector getEarlyKeyShareGroups() {
            Vector groups = new Vector();
            groups.add(NamedGroup.secp256r1);
            return groups;
        }
    }

    private static final class GmServer extends PeerServer {
        private final JcaTlsCrypto crypto;
        private final DualStackTlsMaterial material;

        GmServer(JcaTlsCrypto crypto, DualStackTlsMaterial material) {
            super(crypto);
            this.crypto = crypto;
            this.material = material;
        }

        @Override
        protected int[] getSupportedCipherSuites() {
            return new int[] {CipherSuite.TLS_SM4_GCM_SM3, CipherSuite.TLS_SM4_CCM_SM3};
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] {ProtocolVersion.TLSv13};
        }

        @Override
        public int[] getSupportedGroups() throws IOException {
            return new int[] {NamedGroup.curveSM2};
        }

        @Override
        public TlsCredentials getCredentials() throws IOException {
            return signer(
                    crypto,
                    context,
                    material.gm().serverSign().privateKey(),
                    material.gm().serverSign().chain(),
                    SignatureAndHashAlgorithm.sm2sig_sm3);
        }
    }

    private static final class GmClient extends PeerClient {
        GmClient(JcaTlsCrypto crypto, DualStackTlsMaterial material) {
            super(crypto);
        }

        @Override
        protected int[] getSupportedCipherSuites() {
            return new int[] {CipherSuite.TLS_SM4_GCM_SM3, CipherSuite.TLS_SM4_CCM_SM3};
        }

        @Override
        protected ProtocolVersion[] getSupportedVersions() {
            return new ProtocolVersion[] {ProtocolVersion.TLSv13};
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Vector getSupportedGroups(Vector namedGroupRoles) {
            Vector groups = new Vector();
            groups.add(NamedGroup.curveSM2);
            return groups;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Vector getSupportedSignatureAlgorithms() {
            Vector algs = new Vector();
            algs.add(SignatureAndHashAlgorithm.sm2sig_sm3);
            return algs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Vector getEarlyKeyShareGroups() {
            Vector groups = new Vector();
            groups.add(NamedGroup.curveSM2);
            return groups;
        }
    }
}
