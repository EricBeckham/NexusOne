package one.nexus.crypto.tls;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Writes gitignored local dual-cert PKCS12 files. Password comes from
 * {@code NEXUS_GM_TLS_STORE_PASSWORD} or a generated file next to the stores.
 */
public final class GmTlsMaterialWriter {
    private GmTlsMaterialWriter() {}

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? ".local/g17-gm-tls" : args[0]);
        char[] password = password();
        GmTlsDualMaterial material = GmTlsDualMaterial.ephemeral();
        material.write(directory, password);
        System.out.println("wrote " + directory.toAbsolutePath());
        System.out.println(GmTlsVersions.summary());
        System.out.println("server-sign " + material.serverSign().certificate().getSubjectX500Principal());
        System.out.println("server-encrypt " + material.serverEncrypt().certificate().getSubjectX500Principal());
        System.out.println("dual=" + material.dualServerCertsDistinct());
        System.out.println("password-source=" + (System.getenv("NEXUS_GM_TLS_STORE_PASSWORD") == null ? "generated-file" : "env"));
    }

    static char[] password() throws Exception {
        String env = System.getenv("NEXUS_GM_TLS_STORE_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.toCharArray();
        }
        Path dir = Path.of(System.getenv().getOrDefault("NEXUS_GM_TLS_DIR", ".local/g17-gm-tls"));
        Files.createDirectories(dir);
        Path passwordFile = dir.resolve("password");
        if (Files.exists(passwordFile)) {
            return Files.readString(passwordFile, StandardCharsets.UTF_8).trim().toCharArray();
        }
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        String generated = java.util.HexFormat.of().formatHex(raw);
        Files.writeString(passwordFile, generated, StandardCharsets.UTF_8);
        return generated.toCharArray();
    }

    static void wipe(char[] password) {
        CharBuffer.wrap(password).put("\0".repeat(password.length));
    }
}
