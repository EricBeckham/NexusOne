package one.nexus.crypto.tls;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Writes gitignored local dual-stack PKCS12 / PEM files for the Java probe
 * and Tongsuo nginx template. Password comes from
 * {@code NEXUS_GM_TLS_STORE_PASSWORD} or a generated file.
 */
public final class DualStackMaterialWriter {
    private DualStackMaterialWriter() {}

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? ".local/g17-gm-tls-dual-stack" : args[0]);
        char[] password = password(directory);
        DualStackTlsMaterial material = DualStackTlsMaterial.ephemeral();
        material.write(directory, password);
        System.out.println("wrote " + directory.toAbsolutePath());
        System.out.println(GmTlsVersions.summary());
        System.out.println("intl-server " + material.intlServer().certificate().getSubjectX500Principal());
        System.out.println("gm-server-sign " + material.gm().serverSign().certificate().getSubjectX500Principal());
        System.out.println("gm-server-encrypt " + material.gm().serverEncrypt().certificate().getSubjectX500Principal());
        System.out.println("dual=" + material.gm().dualServerCertsDistinct());
        System.out.println("password-source="
                + (System.getenv("NEXUS_GM_TLS_STORE_PASSWORD") == null ? "generated-file" : "env"));
    }

    static char[] password(Path directory) throws Exception {
        String env = System.getenv("NEXUS_GM_TLS_STORE_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.toCharArray();
        }
        Files.createDirectories(directory);
        Path passwordFile = directory.resolve("password");
        if (Files.exists(passwordFile)) {
            return Files.readString(passwordFile, StandardCharsets.UTF_8).trim().toCharArray();
        }
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        String generated = java.util.HexFormat.of().formatHex(raw);
        Files.writeString(passwordFile, generated, StandardCharsets.UTF_8);
        return generated.toCharArray();
    }
}
