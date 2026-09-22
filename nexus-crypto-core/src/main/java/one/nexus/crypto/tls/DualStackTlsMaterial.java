package one.nexus.crypto.tls;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;
import one.nexus.crypto.CryptoPort;
import one.nexus.crypto.CryptoSuite;
import one.nexus.crypto.Hex;
import one.nexus.crypto.JcaCryptoPort;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;

/**
 * Local dual-stack TLS material: existing GM sign+encrypt leftover plus a
 * P-256 international leaf. Used by the Java two-port probe and to render
 * Tongsuo nginx PEM paths under {@code .local/} (gitignored).
 */
public final class DualStackTlsMaterial {
    public static final String INTL_CA_STORE = "intl-ca.p12";
    public static final String INTL_SERVER_STORE = "intl-server.p12";
    public static final String INTL_CLIENT_STORE = "intl-client.p12";

    private final GmTlsDualMaterial gm;
    private final IntlTlsHolder intlCa;
    private final IntlTlsHolder intlServer;
    private final IntlTlsHolder intlClient;

    public DualStackTlsMaterial(
            GmTlsDualMaterial gm, IntlTlsHolder intlCa, IntlTlsHolder intlServer, IntlTlsHolder intlClient) {
        this.gm = Objects.requireNonNull(gm, "gm");
        this.intlCa = Objects.requireNonNull(intlCa, "intlCa");
        this.intlServer = Objects.requireNonNull(intlServer, "intlServer");
        this.intlClient = Objects.requireNonNull(intlClient, "intlClient");
    }

    public static DualStackTlsMaterial ephemeral() {
        GmTlsDualMaterial gm = GmTlsDualMaterial.ephemeral();
        KeyPair caKeys = IntlTlsCertificates.p256();
        X509Certificate caCert = IntlTlsCertificates.issue(
                null,
                caKeys,
                caKeys,
                "CN=NexusOne G17 Intl Test CA,O=NexusOne,C=CN",
                true,
                KeyUsage.keyCertSign | KeyUsage.cRLSign,
                null,
                false);
        KeyPair serverKeys = IntlTlsCertificates.p256();
        X509Certificate serverCert = IntlTlsCertificates.issue(
                caCert,
                caKeys,
                serverKeys,
                "CN=localhost,OU=intl-server,O=NexusOne",
                false,
                KeyUsage.digitalSignature,
                KeyPurposeId.id_kp_serverAuth,
                true);
        KeyPair clientKeys = IntlTlsCertificates.p256();
        X509Certificate clientCert = IntlTlsCertificates.issue(
                caCert,
                caKeys,
                clientKeys,
                "CN=g17-intl-client,OU=intl-client,O=NexusOne",
                false,
                KeyUsage.digitalSignature,
                KeyPurposeId.id_kp_clientAuth,
                false);
        return new DualStackTlsMaterial(
                gm,
                new IntlTlsHolder("intl-ca", caCert, caKeys.getPrivate(), new X509Certificate[] {caCert}),
                new IntlTlsHolder(
                        "intl-server",
                        serverCert,
                        serverKeys.getPrivate(),
                        new X509Certificate[] {serverCert, caCert}),
                new IntlTlsHolder(
                        "intl-client",
                        clientCert,
                        clientKeys.getPrivate(),
                        new X509Certificate[] {clientCert, caCert}));
    }

    public GmTlsDualMaterial gm() {
        return gm;
    }

    public IntlTlsHolder intlCa() {
        return intlCa;
    }

    public IntlTlsHolder intlServer() {
        return intlServer;
    }

    public IntlTlsHolder intlClient() {
        return intlClient;
    }

    public static String sha256Fingerprint(X509Certificate certificate) {
        try {
            CryptoPort intl = JcaCryptoPort.ephemeral(CryptoSuite.INTERNATIONAL);
            return Hex.encode(intl.digest(certificate.getEncoded()));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to SHA-256-fingerprint certificate", ex);
        }
    }

    /**
     * Writes GM leftover stores plus international PKCS12 / public PEMs and
     * private-key PEMs under {@code keys/}. Password is never written into
     * hints. Intended for gitignored {@code .local/} only.
     */
    public void write(Path directory, char[] password) throws IOException {
        gm.write(directory, password);
        Files.createDirectories(directory.resolve("public"));
        Files.createDirectories(directory.resolve("keys"));
        writeKeyStore(directory.resolve(INTL_CA_STORE), intlCa, password);
        writeKeyStore(directory.resolve(INTL_SERVER_STORE), intlServer, password);
        writeKeyStore(directory.resolve(INTL_CLIENT_STORE), intlClient, password);
        writePem(directory.resolve("public/intl-ca.pem"), intlCa.certificate());
        writePem(directory.resolve("public/intl-server.pem"), intlServer.certificate());
        writePem(directory.resolve("public/intl-client.pem"), intlClient.certificate());
        writeKeyPem(directory.resolve("keys/intl-server.key"), intlServer.privateKey());
        writeKeyPem(directory.resolve("keys/gm-server-sign.key"), gm.serverSign().privateKey());
        writeKeyPem(directory.resolve("keys/gm-server-encrypt.key"), gm.serverEncrypt().privateKey());
        String fingerprints = """
                # Public fingerprints. Safe to copy. Private keys stay under keys/.
                intl-ca-sha256=%s
                intl-server-sha256=%s
                gm-ca-sm3=%s
                gm-server-sign-sm3=%s
                gm-server-encrypt-sm3=%s
                %s
                """.formatted(
                sha256Fingerprint(intlCa.certificate()),
                sha256Fingerprint(intlServer.certificate()),
                GmTlsDualMaterial.sm3Fingerprint(gm.ca().certificate()),
                GmTlsDualMaterial.sm3Fingerprint(gm.serverSign().certificate()),
                GmTlsDualMaterial.sm3Fingerprint(gm.serverEncrypt().certificate()),
                GmTlsVersions.summary());
        Files.writeString(directory.resolve("public/dual-stack-fingerprints.txt"), fingerprints, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("dual-stack.hints.yaml"), dualStackHints(), StandardCharsets.UTF_8);
    }

    public static String dualStackHints() {
        return """
                # Local dual-stack TLS hints (international TLS 1.3 + GM TLS 1.3).
                # Password is NOT written here. Set NEXUS_GM_TLS_STORE_PASSWORD.
                # Product HTTP remains the default. This is not Tongsuo nginx,
                # not GM/T 0024 encryption-cert negotiation, not 等保/密评/HSM.
                nexus:
                  tls:
                    dual-stack:
                      enabled: false
                      dir: .local/g17-gm-tls-dual-stack
                      intl-server-store: intl-server.p12
                      gm-server-sign-store: server-sign.p12
                      gm-server-encrypt-store: server-encrypt.p12
                # Two loopback ports, two stacks (Java probe / Tongsuo nginx template):
                #   intl  TLS 1.3 TLS_AES_128_GCM_SHA256   (P-256 / SHA256withECDSA)
                #   gm    TLS 1.3 TLS_SM4_GCM_SM3          (SM2 sign cert; encrypt cert is material only)
                # Tongsuo nginx PEMs (gitignored keys/):
                #   public/intl-server.pem + keys/intl-server.key
                #   public/server-sign.pem + keys/gm-server-sign.key
                #   public/server-encrypt.pem + keys/gm-server-encrypt.key
                """;
    }

    private static void writeKeyStore(Path path, IntlTlsHolder holder, char[] password) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry(holder.alias(), holder.privateKey(), password, holder.chain());
            try (OutputStream out = Files.newOutputStream(path)) {
                store.store(out, password);
            }
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to write " + path, ex);
        }
    }

    private static void writePem(Path path, X509Certificate certificate) throws IOException {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(certificate.getEncoded());
            Files.writeString(
                    path,
                    "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n",
                    StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to write PEM " + path, ex);
        }
    }

    private static void writeKeyPem(Path path, java.security.PrivateKey key) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded());
        Files.writeString(
                path,
                "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n",
                StandardCharsets.US_ASCII);
    }
}
