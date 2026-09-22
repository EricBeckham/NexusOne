/**
 * Shared contracts used by every NexusOne module.
 *
 * <p>M0-01 owns the types in this package. Sibling work packages (M0-02 isolation,
 * M0-03 tenant, M0-04 identity, M0-11 crypto/audit) should depend on these
 * records rather than inventing a second context, error, or audit model.
 */
package one.nexus.common;
