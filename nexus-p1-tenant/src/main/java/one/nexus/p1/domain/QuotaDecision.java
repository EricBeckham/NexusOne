package one.nexus.p1.domain;

public sealed interface QuotaDecision {

    Quota quota();

    int requested();

    record Accepted(Quota quota, int requested) implements QuotaDecision {}

    record Rejected(Quota quota, int requested, String reason) implements QuotaDecision {}
}
