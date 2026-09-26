package one.nexus.p1.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p1.domain.RegistrationStatus;
import one.nexus.p1.domain.TenantRegistrationApplication;

public interface TenantRegistrationRepository {
    void insert(TenantRegistrationApplication application);

    void update(TenantRegistrationApplication application);

    Optional<TenantRegistrationApplication> findById(UUID id);

    List<TenantRegistrationApplication> list(RegistrationStatus status);

    boolean existsPendingEmail(String adminEmail);
}