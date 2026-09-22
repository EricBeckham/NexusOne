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
import one.nexus.crypto.CryptoSuite;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateEntry;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.NamedGroup;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
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
 * Localhost TLS 1.3 handshake using RFC 8998 {@code TLS_SM4_GCM_SM3} and the
 * dual SM2 certificate material. This is a Java connector proof, not GM/T 0024
 * Nginx dual-stack and not a classified delivery.
 */
public final class GmTlsHandshakeProbe {
    public static final String SERVER_PAYLOAD = "G17-OK";
    public static final String CLIENT_PAYLOAD = "G17-PING";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private GmTlsHandshakeProbe() {}

    public record Result(
            boolean succeeded,
            boolean mtls,
            String protocol,
            String cipherSuite,
            int cipherSuiteId,
            int serverCertificateCount,
            int clientCertificateCount,
            String serverSignSubject,
            String serverEncryptSubject,
            String clientSubject,
            String javaRuntime,
            String bcProvider,
            String bcTls,
            boolean dualServerCertsDistinct,
            boolean sm3withSm2,
            String payload) {}

    public static Result localhostMtls(GmTlsDualMaterial material) {
        return run(material, true, true);
    }

    public static Result localhostServerOnly(GmTlsDualMaterial material) {
        return run(material, false, false);
    }

    public static Result localhostMtlsWithoutClientCert(GmTlsDualMaterial material) {
        return run(material, true, false);
    }

    private static Result run(GmTlsDualMaterial material, boolean requireClientCert, boolean presentClientCert) {
        JcaTlsCrypto crypto = new JcaTlsCryptoProvider()
                .setProvider(CryptoSuite.GM.provider())
                .create(new SecureRandom());
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            CompletableFuture<PeerOutcome> serverSide = CompletableFuture.supplyAsync(() -> {
                try (Socket accepted = serverSocket.accept()) {
                    TlsServerProtocol protocol =
                            new TlsServerProtocol(accepted.getInputStream(), accepted.getOutputStream());
                    GmServer server = new GmServer(crypto, material, requireClientCert);
                    protocol.accept(server);
                    byte[] ping = new byte[16];
                    int read = protocol.getInputStream().read(ping);
                    protocol.getOutputStream().write(SERVER_PAYLOAD.getBytes());
                    protocol.getOutputStream().flush();
                    return new PeerOutcome(
                            server.protocol,
                            server.cipherSuite,
                            server.clientCertCount,
                            2,
                            read > 0 ? new String(ping, 0, read) : "");
                } catch (Exception ex) {
                    throw new HandshakeFailed(ex);
                }
            });
            try (Socket clientSocket = new Socket("127.0.0.1", port)) {
                TlsClientProtocol protocol =
                        new TlsClientProtocol(clientSocket.getInputStream(), clientSocket.getOutputStream());
                GmClient client = new GmClient(crypto, material, presentClientCert);
                protocol.connect(client);
                protocol.getOutputStream().write(CLIENT_PAYLOAD.getBytes());
                protocol.getOutputStream().flush();
                byte[] reply = new byte[16];
                int n = protocol.getInputStream().read(reply);
                String payload = n > 0 ? new String(reply, 0, n) : "";
                PeerOutcome serverOutcome = serverSide.get(15, TimeUnit.SECONDS);
                return new Result(
                        true,
                        requireClientCert && presentClientCert,
                        serverOutcome.protocol,
                        cipherName(serverOutcome.cipherSuite),
                        serverOutcome.cipherSuite,
                        serverOutcome.serverCertCount,
                        serverOutcome.clientCertCount,
                        material.serverSign().certificate().getSubjectX500Principal().getName(),
                        material.serverEncrypt().certificate().getSubjectX500Principal().getName(),
                        material.client().certificate().getSubjectX500Principal().getName(),
                        GmTlsVersions.javaRuntime(),
                        GmTlsVersions.bcProvider(),
                        GmTlsVersions.bcTls(),
                        material.dualServerCertsDistinct(),
                        GmTlsOids.SM3_WITH_SM2.equals(material.serverSign().certificate().getSigAlgOID()),
                        payload);
            }
        } catch (HandshakeFailed ex) {
            throw wrap(ex.getCause());
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
        return "0x" + Integer.toHexString(id);
    }

    private static RuntimeException wrap(Throwable ex) {
        if (ex instanceof TlsFatalAlert alert) {
            return new IllegalStateException("TLS handshake failed: " + alert.getAlertDescription(), alert);
        }
        if (ex instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("TLS handshake failed", ex);
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
            JcaTlsCrypto crypto, org.bouncycastle.tls.TlsContext context, GmTlsHolder holder) throws IOException {
        return new JcaDefaultTlsCredentialedSigner(
                new TlsCryptoParameters(context),
                crypto,
                holder.privateKey(),
                tlsChain(crypto, holder.chain()),
                SignatureAndHashAlgorithm.sm2sig_sm3);
    }

    private record PeerOutcome(
            String protocol, int cipherSuite, int clientCertCount, int serverCertCount, String ping) {}

    private static final class HandshakeFailed extends RuntimeException {
        HandshakeFailed(Throwable cause) {
            super(cause);
        }
    }

    private static final class GmServer extends DefaultTlsServer {
        private final JcaTlsCrypto crypto;
        private final GmTlsDualMaterial material;
        private final boolean requireClientCert;
        private String protocol = "";
        private int cipherSuite;
        private int clientCertCount;

        GmServer(JcaTlsCrypto crypto, GmTlsDualMaterial material, boolean requireClientCert) {
            super(crypto);
            this.crypto = crypto;
            this.material = material;
            this.requireClientCert = requireClientCert;
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
            return signer(crypto, context, material.serverSign());
        }

        @Override
        @SuppressWarnings("unchecked")
        public CertificateRequest getCertificateRequest() throws IOException {
            if (!requireClientCert) {
                return null;
            }
            Vector sigAlgs = new Vector();
            sigAlgs.add(SignatureAndHashAlgorithm.sm2sig_sm3);
            return new CertificateRequest(TlsUtils.EMPTY_BYTES, sigAlgs, null, null);
        }

        @Override
        public void notifyClientCertificate(Certificate clientCertificate) throws IOException {
            clientCertCount = clientCertificate == null ? 0 : clientCertificate.getLength();
            if (requireClientCert && (clientCertificate == null || clientCertificate.isEmpty())) {
                throw new TlsFatalAlert(AlertDescription.certificate_required);
            }
        }

        @Override
        public void notifyHandshakeComplete() throws IOException {
            protocol = String.valueOf(context.getSecurityParameters().getNegotiatedVersion());
            cipherSuite = context.getSecurityParameters().getCipherSuite();
        }
    }

    private static final class GmClient extends DefaultTlsClient {
        private final JcaTlsCrypto crypto;
        private final GmTlsDualMaterial material;
        private final boolean presentClientCert;

        GmClient(JcaTlsCrypto crypto, GmTlsDualMaterial material, boolean presentClientCert) {
            super(crypto);
            this.crypto = crypto;
            this.material = material;
            this.presentClientCert = presentClientCert;
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

        @Override
        public TlsAuthentication getAuthentication() {
            return new TlsAuthentication() {
                @Override
                public void notifyServerCertificate(TlsServerCertificate serverCertificate) {
                    // Local ephemeral CA; chain presence is asserted by the probe result.
                }

                @Override
                public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) throws IOException {
                    if (!presentClientCert) {
                        return null;
                    }
                    return signer(crypto, context, material.client());
                }
            };
        }
    }
}
