package one.nexus.crypto.tls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
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
 * Dual GM certificate chain for a local Java TLS connector: CA, server sign,
 * server encrypt, and mTLS client. Private keys stay on disk only under
 * {@code .local/} (gitignored).
 */
public final class GmTlsDualMaterial {
    public static final String CA_STORE = "ca.p12";
    public static final String SERVER_SIGN_STORE = "server-sign.p12";
    public static final String SERVER_ENCRYPT_STORE = "server-encrypt.p12";
    public static final String CLIENT_STORE = "client.p12";
    public static final String TRUST_STORE = "trust.p12";

    private final GmTlsHolder ca;
    private final GmTlsHolder serverSign;
    private final GmTlsHolder serverEncrypt;
    private final GmTlsHolder client;

    public GmTlsDualMaterial(GmTlsHolder ca, GmTlsHolder serverSign, GmTlsHolder serverEncrypt, GmTlsHolder client) {
        this.ca = Objects.requireNonNull(ca, "ca");
        this.serverSign = Objects.requireNonNull(serverSign, "serverSign");
        this.serverEncrypt = Objects.requireNonNull(serverEncrypt, "serverEncrypt");
        this.client = Objects.requireNonNull(client, "client");
    }

    public static GmTlsDualMaterial ephemeral() {
        KeyPair caKeys = GmTlsCertificates.sm2();
        X509Certificate caCert = GmTlsCertificates.issue(
                null,
                caKeys,
                caKeys,
                "CN=NexusOne G17 GM Test CA,O=NexusOne,C=CN",
                true,
                KeyUsage.keyCertSign | KeyUsage.cRLSign,
                null,
                false);
        KeyPair signKeys = GmTlsCertificates.sm2();
        X509Certificate signCert = GmTlsCertificates.issue(
                caCert,
                caKeys,
                signKeys,
                "CN=localhost,OU=gm-server-sign,O=NexusOne",
                false,
                KeyUsage.digitalSignature,
                KeyPurposeId.id_kp_serverAuth,
                true);
        KeyPair encryptKeys = GmTlsCertificates.sm2();
        X509Certificate encryptCert = GmTlsCertificates.issue(
                caCert,
                caKeys,
                encryptKeys,
                "CN=localhost,OU=gm-server-encrypt,O=NexusOne",
                false,
                KeyUsage.keyEncipherment | KeyUsage.keyAgreement,
                KeyPurposeId.id_kp_serverAuth,
                true);
        KeyPair clientKeys = GmTlsCertificates.sm2();
        X509Certificate clientCert = GmTlsCertificates.issue(
                caCert,
                caKeys,
                clientKeys,
                "CN=g17-mtls-client,OU=gm-client,O=NexusOne",
                false,
                KeyUsage.digitalSignature,
                KeyPurposeId.id_kp_clientAuth,
                false);
        GmTlsHolder ca = new GmTlsHolder(GmTlsRole.CA, "ca", caCert, caKeys.getPrivate(), new X509Certificate[] {caCert});
        return new GmTlsDualMaterial(
                ca,
                new GmTlsHolder(
                        GmTlsRole.SERVER_SIGN,
                        "server-sign",
                        signCert,
                        signKeys.getPrivate(),
                        new X509Certificate[] {signCert, caCert}),
                new GmTlsHolder(
                        GmTlsRole.SERVER_ENCRYPT,
                        "server-encrypt",
                        encryptCert,
                        encryptKeys.getPrivate(),
                        new X509Certificate[] {encryptCert, caCert}),
                new GmTlsHolder(
                        GmTlsRole.CLIENT,
                        "client",
                        clientCert,
                        clientKeys.getPrivate(),
                        new X509Certificate[] {clientCert, caCert}));
    }

    public GmTlsHolder ca() {
        return ca;
    }

    public GmTlsHolder serverSign() {
        return serverSign;
    }

    public GmTlsHolder serverEncrypt() {
        return serverEncrypt;
    }

    public GmTlsHolder client() {
        return client;
    }

    public boolean dualServerCertsDistinct() {
        return !serverSign.certificate().getSerialNumber().equals(serverEncrypt.certificate().getSerialNumber());
    }

    /** SM3 fingerprint of a public certificate via CryptoPort GM suite. */
    public static String sm3Fingerprint(X509Certificate certificate) {
        try {
            CryptoPort gm = JcaCryptoPort.ephemeral(CryptoSuite.GM);
            return Hex.encode(gm.digest(certificate.getEncoded()));
        } catch (Exception ex) {
            throw new IllegalStateException("unable to SM3-fingerprint certificate", ex);
        }
    }

    public void write(Path directory, char[] password) throws IOException {
        Files.createDirectories(directory.resolve("public"));
        writeKeyStore(directory.resolve(CA_STORE), ca, password);
        writeKeyStore(directory.resolve(SERVER_SIGN_STORE), serverSign, password);
        writeKeyStore(directory.resolve(SERVER_ENCRYPT_STORE), serverEncrypt, password);
        writeKeyStore(directory.resolve(CLIENT_STORE), client, password);
        writeTrustStore(directory.resolve(TRUST_STORE), ca.certificate(), password);
        writePem(directory.resolve("public/ca.pem"), ca.certificate());
        writePem(directory.resolve("public/server-sign.pem"), serverSign.certificate());
        writePem(directory.resolve("public/server-encrypt.pem"), serverEncrypt.certificate());
        writePem(directory.resolve("public/client.pem"), client.certificate());
        String fingerprints = """
                # Public SM3 fingerprints (CryptoPort GM). Safe to commit copies of this text.
                ca=%s
                server-sign=%s
                server-encrypt=%s
                client=%s
                %s
                """.formatted(
                        sm3Fingerprint(ca.certificate()),
                        sm3Fingerprint(serverSign.certificate()),
                        sm3Fingerprint(serverEncrypt.certificate()),
                        sm3Fingerprint(client.certificate()),
                        GmTlsVersions.summary());
        Files.writeString(directory.resolve("public/fingerprints.txt"), fingerprints, StandardCharsets.UTF_8);
        Files.writeString(
                directory.resolve("connector.hints.yaml"),
                connectorHints(),
                StandardCharsets.UTF_8);
    }

    public static GmTlsDualMaterial load(Path directory, char[] password) throws IOException {
        GmTlsHolder ca = readKeyStore(directory.resolve(CA_STORE), "ca", GmTlsRole.CA, password);
        GmTlsHolder sign = readKeyStore(directory.resolve(SERVER_SIGN_STORE), "server-sign", GmTlsRole.SERVER_SIGN, password);
        GmTlsHolder encrypt =
                readKeyStore(directory.resolve(SERVER_ENCRYPT_STORE), "server-encrypt", GmTlsRole.SERVER_ENCRYPT, password);
        GmTlsHolder clientHolder = readKeyStore(directory.resolve(CLIENT_STORE), "client", GmTlsRole.CLIENT, password);
        X509Certificate trust = readTrust(directory.resolve(TRUST_STORE), password);
        if (!trust.getSerialNumber().equals(ca.certificate().getSerialNumber())) {
            throw new IOException("trust store CA does not match ca.p12");
        }
        return new GmTlsDualMaterial(ca, sign, encrypt, clientHolder);
    }

    public static String connectorHints() {
        return """
                # Local Java HTTPS connector hints for G17 GM TLS dual-cert.
                # Password is NOT written here. Set NEXUS_GM_TLS_STORE_PASSWORD.
                # Product HTTP remains the default; this is opt-in and not GM/T 0024 Nginx.
                nexus:
                  tls:
                    gm:
                      enabled: false
                      dir: .local/g17-gm-tls
                      server-sign-store: server-sign.p12
                      server-encrypt-store: server-encrypt.p12
                      client-store: client.p12
                      trust-store: trust.p12
                # Tomcat / Jetty SSLHostConfig can point at the two server PKCS12 files:
                #   server-sign.p12     digitalSignature (RFC 8998 CertificateVerify)
                #   server-encrypt.p12  keyEncipherment + keyAgreement (GM dual-cert material)
                # Automated proof uses Bouncy Castle TLS 1.3 TLS_SM4_GCM_SM3, not SunJSSE.
                """;
    }

    private static void writeKeyStore(Path path, GmTlsHolder holder, char[] password) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12", CryptoSuite.GM.provider());
            store.load(null, null);
            store.setKeyEntry(holder.alias(), holder.privateKey(), password, holder.chain());
            try (OutputStream out = Files.newOutputStream(path)) {
                store.store(out, password);
            }
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to write " + path, ex);
        }
    }

    private static void writeTrustStore(Path path, X509Certificate caCert, char[] password) throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12", CryptoSuite.GM.provider());
            store.load(null, null);
            store.setCertificateEntry("ca", caCert);
            try (OutputStream out = Files.newOutputStream(path)) {
                store.store(out, password);
            }
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to write " + path, ex);
        }
    }

    private static GmTlsHolder readKeyStore(Path path, String alias, GmTlsRole role, char[] password) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore store = KeyStore.getInstance("PKCS12", CryptoSuite.GM.provider());
            store.load(in, password);
            PrivateKey key = (PrivateKey) store.getKey(alias, password);
            Certificate[] raw = store.getCertificateChain(alias);
            if (key == null || raw == null || raw.length == 0) {
                throw new IOException("missing key entry " + alias + " in " + path);
            }
            X509Certificate[] chain = new X509Certificate[raw.length];
            for (int i = 0; i < raw.length; i++) {
                chain[i] = (X509Certificate) raw[i];
            }
            return new GmTlsHolder(role, alias, chain[0], key, chain);
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to read " + path, ex);
        }
    }

    private static X509Certificate readTrust(Path path, char[] password) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore store = KeyStore.getInstance("PKCS12", CryptoSuite.GM.provider());
            store.load(in, password);
            return (X509Certificate) store.getCertificate("ca");
        } catch (GeneralSecurityException ex) {
            throw new IOException("unable to read " + path, ex);
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
}
