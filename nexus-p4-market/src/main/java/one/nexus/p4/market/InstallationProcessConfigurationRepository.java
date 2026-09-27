package one.nexus.p4.market;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface InstallationProcessConfigurationRepository {
    record Override(String key, int version) {}
    Optional<Override> find(UUID tenant, UUID installation);
    void save(UUID tenant, UUID installation, String key, int version, UUID person, Instant now);
    void delete(UUID tenant, UUID installation);
}
