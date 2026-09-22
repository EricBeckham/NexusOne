package one.nexus.p2.account;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.idp.IdentityProviderPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * G10 identity half: match P2 persons to Keycloak users and repair drift.
 * Does not create P2 persons from unmatched IdP users (P2 remains org master).
 * Does not sync Employment into Keycloak Organizations.
 */
public final class PersonIdentityReconciler {
    public static final String PLATFORM_PATH = "identity.reconcile";

    public enum Kind {
        MATCHED,
        DRIFT,
        MISSING_IDP,
        STALE_BINDING,
        UNMATCHED_IDP,
        CONFLICT
    }

    public enum Action {
        NONE,
        SYNC_P2,
        PROVISION_IDP,
        REBIND
    }

    public record Finding(
            Kind kind,
            UUID personId,
            UUID tenantId,
            UUID accountId,
            String p2Email,
            String idpSubject,
            String idpEmail,
            String detail
    ) {}

    public record Repair(
            Kind kind,
            Action action,
            UUID personId,
            UUID accountId,
            String idpSubject,
            String detail
    ) {}

    public record Report(
            boolean applied,
            String path,
            int matched,
            int drifted,
            int missingIdp,
            int staleBinding,
            int unmatchedIdp,
            int conflicts,
            int proposed,
            int repaired,
            List<Finding> findings,
            List<Repair> repairs
    ) {}

    private final IdentityRepository repository;
    private final IdentityProviderPort idp;
    private final AccountReconciler accounts;
    private final Clock clock;

    public PersonIdentityReconciler(
            IdentityRepository repository,
            IdentityProviderPort idp,
            AccountReconciler accounts,
            Clock clock
    ) {
        this.repository = repository;
        this.idp = idp;
        this.accounts = accounts;
        this.clock = clock;
    }

    public Report reconcile(boolean apply) {
        return reconcile(apply, null);
    }

    public Report reconcile(boolean apply, UUID tenantId) {
        List<IdentityProviderPort.DirectoryUser> directory = idp.listUsers();
        Map<String, IdentityProviderPort.DirectoryUser> bySubject = new HashMap<>();
        Map<String, IdentityProviderPort.DirectoryUser> byEmail = new HashMap<>();
        for (IdentityProviderPort.DirectoryUser user : directory) {
            if (user.subject() != null && !user.subject().isBlank()) {
                bySubject.put(user.subject(), user);
            }
            String emailKey = normalize(user.email());
            if (emailKey != null && !byEmail.containsKey(emailKey)) {
                byEmail.put(emailKey, user);
            }
        }

        List<Model.Person> persons = tenantId == null
                ? repository.listAllPersons()
                : repository.listPersons(tenantId);
        Set<String> boundSubjects = new HashSet<>();
        List<Finding> findings = new ArrayList<>();
        List<Repair> repairs = new ArrayList<>();

        for (Model.Person person : persons) {
            Model.GlobalAccount account = repository.findAccount(person.accountId()).orElse(null);
            if (account == null) {
                findings.add(new Finding(
                        Kind.CONFLICT, person.id(), person.tenantId(), person.accountId(),
                        person.email(), null, null, "person has no global account"));
                continue;
            }
            IdentityProviderPort.DirectoryUser bound = bySubject.get(account.keycloakUserId());
            if (bound != null) {
                boundSubjects.add(bound.subject());
                if (attributesMatch(person, account, bound)) {
                    findings.add(matched(person, account, bound));
                } else {
                    findings.add(finding(Kind.DRIFT, person, account, bound,
                            "email/phone/username drifted between person and Keycloak user"));
                    propose(repairs, Kind.DRIFT, Action.SYNC_P2, person, account, bound,
                            apply
                                    ? "synced P2 account/person identity fields from Keycloak"
                                    : "would sync P2 account/person identity fields from Keycloak");
                    if (apply) {
                        syncFromIdp(person, bound);
                    }
                }
                continue;
            }

            IdentityProviderPort.DirectoryUser emailMatch = firstPresent(
                    byEmail.get(normalize(account.email())),
                    byEmail.get(normalize(person.email())));
            if (emailMatch != null) {
                if (subjectTakenByOther(emailMatch.subject(), account.id())) {
                    findings.add(finding(Kind.CONFLICT, person, account, emailMatch,
                            "email matches a Keycloak user already bound to another P2 account"));
                    continue;
                }
                boundSubjects.add(emailMatch.subject());
                findings.add(finding(Kind.STALE_BINDING, person, account, emailMatch,
                        "Keycloak user id is stale; email matches " + emailMatch.subject()));
                propose(repairs, Kind.STALE_BINDING, Action.REBIND, person, account, emailMatch,
                        apply
                                ? "rebound P2 account to Keycloak user " + emailMatch.subject()
                                : "would rebind P2 account to Keycloak user " + emailMatch.subject());
                if (apply) {
                    syncFromIdp(person, emailMatch);
                }
                continue;
            }

            findings.add(new Finding(
                    Kind.MISSING_IDP, person.id(), person.tenantId(), account.id(),
                    firstNonBlank(account.email(), person.email()),
                    account.keycloakUserId(), null,
                    "no Keycloak user for subject or email"));
            if (!apply) {
                propose(repairs, Kind.MISSING_IDP, Action.PROVISION_IDP, person, account, null,
                        "would provision Keycloak user and bind P2 account");
                continue;
            }
            IdentityProviderPort.ProvisionedUser provisioned = idp.ensureUser(
                    firstNonBlank(account.email(), person.email()),
                    firstNonBlank(account.username(), account.email(), person.email()),
                    firstNonBlank(account.displayName(), person.displayName(), account.username()));
            IdentityProviderPort.DirectoryUser created = idp.findUser(provisioned.subject())
                    .orElseGet(() -> new IdentityProviderPort.DirectoryUser(
                            provisioned.subject(), provisioned.email(), provisioned.username(),
                            firstNonBlank(account.displayName(), person.displayName()),
                            firstNonBlank(account.phone(), person.phone()), true));
            if (subjectTakenByOther(created.subject(), account.id())) {
                findings.add(finding(Kind.CONFLICT, person, account, created,
                        "provisioned Keycloak user is already bound to another P2 account"));
                continue;
            }
            boundSubjects.add(created.subject());
            syncFromIdp(person, created);
            propose(repairs, Kind.MISSING_IDP, Action.PROVISION_IDP, person, account, created,
                    "provisioned Keycloak user and bound P2 account");
        }

        if (tenantId == null) {
            Set<UUID> accounted = new HashSet<>();
            for (Model.Person person : persons) {
                accounted.add(person.accountId());
            }
            for (Model.GlobalAccount account : repository.listAccounts()) {
                if (accounted.contains(account.id())) {
                    continue;
                }
                IdentityProviderPort.DirectoryUser bound = bySubject.get(account.keycloakUserId());
                if (bound != null) {
                    boundSubjects.add(bound.subject());
                    continue;
                }
                IdentityProviderPort.DirectoryUser emailMatch = byEmail.get(normalize(account.email()));
                if (emailMatch != null && !subjectTakenByOther(emailMatch.subject(), account.id())) {
                    boundSubjects.add(emailMatch.subject());
                    findings.add(new Finding(
                            Kind.STALE_BINDING, null, null, account.id(), account.email(),
                            emailMatch.subject(), emailMatch.email(),
                            "account has no person; rebound by email"));
                    repairs.add(new Repair(
                            Kind.STALE_BINDING, Action.REBIND, null, account.id(),
                            emailMatch.subject(),
                            apply ? "rebound account without person" : "would rebind account without person"));
                    if (apply) {
                        accounts.reconcile(emailMatch.toClaims());
                    }
                }
            }
            for (IdentityProviderPort.DirectoryUser user : directory) {
                if (user.subject() != null && boundSubjects.contains(user.subject())) {
                    continue;
                }
                if (repository.findAccountByKeycloakUserId(user.subject()).isPresent()) {
                    continue;
                }
                findings.add(new Finding(
                        Kind.UNMATCHED_IDP, null, null, null, null,
                        user.subject(), user.email(),
                        "Keycloak user has no P2 account; P2 is org master so no person was created"));
            }
        }

        return summarize(apply, findings, repairs);
    }

    private void syncFromIdp(Model.Person person, IdentityProviderPort.DirectoryUser user) {
        accounts.reconcile(user.toClaims());
        Instant now = clock.instant();
        String email = firstNonBlank(user.email(), person.email());
        String phone = firstNonBlank(user.phone(), person.phone());
        if (same(person.email(), email) && same(person.phone(), phone)) {
            return;
        }
        repository.savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                email, phone, person.hiredOn(), person.status(),
                person.createdAt(), now, person.deletedAt(), person.version() + 1));
    }

    private boolean subjectTakenByOther(String subject, UUID accountId) {
        return repository.findAccountByKeycloakUserId(subject)
                .filter(existing -> !existing.id().equals(accountId))
                .isPresent();
    }

    private static boolean attributesMatch(
            Model.Person person,
            Model.GlobalAccount account,
            IdentityProviderPort.DirectoryUser user
    ) {
        return same(account.email(), user.email())
                && same(firstNonBlank(person.email(), account.email()), firstNonBlank(user.email(), account.email()))
                && (user.phone() == null || user.phone().isBlank()
                    || same(firstNonBlank(account.phone(), person.phone()), user.phone()))
                && (user.username() == null || user.username().isBlank() || same(account.username(), user.username()));
    }

    private static Finding matched(
            Model.Person person,
            Model.GlobalAccount account,
            IdentityProviderPort.DirectoryUser user
    ) {
        return finding(Kind.MATCHED, person, account, user, "person matches Keycloak user");
    }

    private static Finding finding(
            Kind kind,
            Model.Person person,
            Model.GlobalAccount account,
            IdentityProviderPort.DirectoryUser user,
            String detail
    ) {
        return new Finding(
                kind, person.id(), person.tenantId(), account.id(),
                firstNonBlank(person.email(), account.email()),
                user == null ? account.keycloakUserId() : user.subject(),
                user == null ? null : user.email(),
                detail);
    }

    private static void propose(
            List<Repair> repairs,
            Kind kind,
            Action action,
            Model.Person person,
            Model.GlobalAccount account,
            IdentityProviderPort.DirectoryUser user,
            String detail
    ) {
        repairs.add(new Repair(kind, action, person.id(), account.id(),
                user == null ? account.keycloakUserId() : user.subject(), detail));
    }

    private static Report summarize(boolean apply, List<Finding> findings, List<Repair> repairs) {
        int matched = 0;
        int drifted = 0;
        int missing = 0;
        int stale = 0;
        int unmatched = 0;
        int conflicts = 0;
        for (Finding finding : findings) {
            switch (finding.kind()) {
                case MATCHED -> matched++;
                case DRIFT -> drifted++;
                case MISSING_IDP -> missing++;
                case STALE_BINDING -> stale++;
                case UNMATCHED_IDP -> unmatched++;
                case CONFLICT -> conflicts++;
            }
        }
        int proposed = repairs.size();
        return new Report(
                apply, PLATFORM_PATH, matched, drifted, missing, stale, unmatched, conflicts,
                proposed, apply ? proposed : 0, List.copyOf(findings), List.copyOf(repairs));
    }

    @SafeVarargs
    private static <T> T firstPresent(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean same(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
