package one.nexus.p3.portal;

import java.util.Collection;
import java.util.List;

/**
 * PLX1 C5 filter. A supplies the real check. Until then every installed key stays visible.
 * The portal list is not itself an authorization decision.
 */
public interface EntryReadFilter {
    List<String> readable(Collection<String> candidateKeys);
}
