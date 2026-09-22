package one.nexus.crypto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;

/**
 * Environment-variable {@link SecretPort}. Read-only: {@code put}/{@code delete}
 * are rejected so plaintext is not written back into process state as if this
 * were a durable store.
 *
 * <p>Each {@code get}/{@code find} calls the env {@link Function} again; values
 * are not cached. A mutable map (or other live lookup) therefore rotates
 * without restart. {@link #system(CryptoSuite)} uses {@code System::getenv},
 * which on Java 21 is an unmodifiable process-start snapshot — an OS environ
 * rotate of a running JVM is not a supported API and is not observed until
 * process restart.
 *
 * <p>This is an S0 adapter (CORE-SEC-001 / ARCH-007). It does not call OpenBao
 * or any KMS.
 */
public final class EnvSecretAdapter implements SecretPort {
    static final String ENV_KEY_ID = "env";

    private final CryptoSuite suite;
    private final Map<String, String> nameToEnv;
    private final Function<String, String> env;

    public static EnvSecretAdapter system(CryptoSuite suite) {
        return new EnvSecretAdapter(suite, SecretNames.envBindings(), System::getenv);
    }

    public EnvSecretAdapter(CryptoSuite suite, Map<String, String> nameToEnv, Function<String, String> env) {
        this.suite = Objects.requireNonNull(suite, "suite");
        this.nameToEnv = Map.copyOf(nameToEnv);
        this.env = Objects.requireNonNull(env, "env");
    }

    @Override
    public SecretRef put(String name, byte[] plaintext) {
        SecretNames.requireName(name);
        throw readOnly("put");
    }

    @Override
    public byte[] get(String name) {
        return findValue(name)
                .orElseThrow(() -> new NexusException(ErrorCode.SECRET_NOT_FOUND, 404, "secret not found"));
    }

    @Override
    public Optional<SecretRef> find(String name) {
        return findValue(name).map(ignored -> ref(SecretNames.requireName(name)));
    }

    @Override
    public void delete(String name) {
        SecretNames.requireName(name);
        throw readOnly("delete");
    }

    @Override
    public CryptoSuite suite() {
        return suite;
    }

    @Override
    public String backend() {
        return "env";
    }

    private Optional<byte[]> findValue(String name) {
        String envName = resolveEnvName(SecretNames.requireName(name));
        if (envName == null) {
            return Optional.empty();
        }
        String value = env.apply(envName);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveEnvName(String name) {
        String mapped = nameToEnv.get(name);
        if (mapped != null) {
            return mapped;
        }
        if (nameToEnv.containsValue(name)) {
            return name;
        }
        if (name.equals(name.toUpperCase(Locale.ROOT)) && name.indexOf('_') >= 0) {
            return name;
        }
        return null;
    }

    private SecretRef ref(String name) {
        return new SecretRef(name, 1, new KeyRef(suite, ENV_KEY_ID, 1), Instant.EPOCH);
    }

    private static NexusException readOnly(String operation) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", operation);
        details.put("backend", "env");
        return new NexusException(
                ErrorCode.SECRET_BACKEND_READ_ONLY,
                400,
                "environment SecretPort is read-only",
                details);
    }
}
