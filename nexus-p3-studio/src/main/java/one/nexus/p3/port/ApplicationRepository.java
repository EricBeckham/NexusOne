package one.nexus.p3.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.Application;

public interface ApplicationRepository {
    void insert(Application application);

    Optional<Application> findById(UUID tenantId, UUID id);

    Optional<Application> findByCode(UUID tenantId, String appCode);

    List<Application> list(UUID tenantId);
}
