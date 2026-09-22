package one.nexus.p2.idp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Local self-signed material for the G4 HTTPS PKCE leftover. Certs and
 * passwords stay under {@code .local/} or {@code /tmp} (not git).
 */
public final class LoopbackHttps {
    public record Material(
            Path directory,
            Path keyStore,
            Path trustStore,
            Path certificatePem,
            Path privateKeyPem,
            char[] password,
            SSLContext serverContext,
            SSLContext clientContext
    ) {}

    private LoopbackHttps() {}

    public static Material generatePkcs12(Path directory) throws Exception {
        Files.createDirectories(directory);
        char[] password = randomPassword();
        Path keyStore = directory.resolve("server.p12");
        Path trustStore = directory.resolve("truststore.p12");
        Path certPem = directory.resolve("server.crt");
        Path keyPem = directory.resolve("server.key");
        Files.deleteIfExists(keyStore);
        run(keytool(),
                "-genkeypair",
                "-alias", "loopback",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3",
                "-dname", "CN=127.0.0.1",
                "-ext", "SAN=IP:127.0.0.1,DNS:localhost",
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(password),
                "-keypass", new String(password));
        run(keytool(),
                "-exportcert",
                "-alias", "loopback",
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(password),
                "-rfc",
                "-file", certPem.toString());
        Files.deleteIfExists(trustStore);
        run(keytool(),
                "-importcert",
                "-noprompt",
                "-alias", "g4-loopback",
                "-file", certPem.toString(),
                "-keystore", trustStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(password));
        exportPrivateKeyPem(keyStore, password, keyPem);
        SSLContext server = serverContext(keyStore, password);
        SSLContext client = clientContext(trustStore, password);
        return new Material(directory, keyStore, trustStore, certPem, keyPem, password, server, client);
    }

    public static Material load(Path keyStore, Path trustStore, char[] password) throws Exception {
        Path directory = keyStore.getParent() == null ? Path.of(".") : keyStore.getParent();
        Path certPem = directory.resolve("server.crt");
        Path keyPem = directory.resolve("server.key");
        return new Material(
                directory,
                keyStore,
                trustStore,
                certPem,
                keyPem,
                password,
                serverContext(keyStore, password),
                clientContext(trustStore, password));
    }

    public static void installClientTrust(SSLContext client) {
        SSLContext.setDefault(client);
        HttpsURLConnection.setDefaultSSLSocketFactory(client.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((host, session) ->
                "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
    }

    public static SSLContext serverContext(Path keyStore, char[] password) throws Exception {
        KeyStore ks = loadStore(keyStore, password);
        javax.net.ssl.KeyManagerFactory kmf =
                javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, new SecureRandom());
        return ssl;
    }

    public static SSLContext clientContext(Path trustStore, char[] password) throws Exception {
        KeyStore ts = loadStore(trustStore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ssl;
    }

    public static SSLContext clientContextFromPem(Path certificatePem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (InputStream in = Files.newInputStream(certificatePem)) {
            cert = (X509Certificate) factory.generateCertificate(in);
        }
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("g4-loopback", cert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ssl;
    }

    private static KeyStore loadStore(Path path, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            store.load(in, password);
        }
        return store;
    }

    private static void exportPrivateKeyPem(Path keyStore, char[] password, Path keyPem) throws Exception {
        if (openssl() == null) {
            Files.writeString(keyPem, "", StandardCharsets.US_ASCII);
            return;
        }
        run(openssl(),
                "pkcs12",
                "-in", keyStore.toString(),
                "-nocerts",
                "-nodes",
                "-passin", "pass:" + new String(password),
                "-out", keyPem.toString());
    }

    private static void run(String executable, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command().add(executable);
        builder.command().addAll(java.util.List.of(args));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException(executable + " failed (" + code + "): " + output);
        }
    }

    private static String keytool() {
        Path bundled = Path.of(System.getProperty("java.home"), "bin", "keytool");
        if (Files.isExecutable(bundled)) {
            return bundled.toString();
        }
        return "keytool";
    }

    private static String openssl() {
        Path openssl = Path.of("/usr/bin/openssl");
        return Files.isExecutable(openssl) ? openssl.toString() : null;
    }

    private static char[] randomPassword() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).toCharArray();
    }

}
