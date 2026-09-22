package one.nexus.p2.account;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;
import one.nexus.p2.support.Ids;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Reconciles a Keycloak (or stub) identity with the P2 global account. */
public final class AccountReconciler {
    private final IdentityRepository repository;
    private final Clock clock;

    public AccountReconciler(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Model.GlobalAccount reconcile(IdentityProviderPort.IdentityClaims claims) {
        if (claims.subject() == null || claims.subject().isBlank()) {
            throw new IllegalArgumentException("identity subject is required");
        }
        Instant now = clock.instant();
        return repository.findAccountByKeycloakUserId(claims.subject())
                .or(() -> claims.email() == null ? java.util.Optional.empty() : repository.findAccountByEmail(claims.email()))
                .map(existing -> update(existing, claims, now))
                .orElseGet(() -> create(claims, now));
    }

    private Model.GlobalAccount create(IdentityProviderPort.IdentityClaims claims, Instant now) {
        String email = Objects.requireNonNull(claims.email(), "email is required to create a global account");
        Model.GlobalAccount account = new Model.GlobalAccount(
                Ids.v7(clock),
                claims.subject(),
                email,
                claims.phone(),
                first(claims.username(), email),
                first(claims.displayName(), claims.username(), email),
                Model.AccountStatus.ACTIVE,
                now, now, null, 0);
        return repository.saveAccount(account);
    }

    private Model.GlobalAccount update(Model.GlobalAccount existing, IdentityProviderPort.IdentityClaims claims, Instant now) {
        Model.GlobalAccount updated = new Model.GlobalAccount(
                existing.id(),
                claims.subject(),
                first(claims.email(), existing.email()),
                first(claims.phone(), existing.phone()),
                first(claims.username(), existing.username()),
                first(claims.displayName(), existing.displayName()),
                existing.status(),
                existing.createdAt(),
                now,
                existing.deletedAt(),
                existing.version() + 1);
        return repository.saveAccount(updated);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
