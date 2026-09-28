package one.nexus.p3.portal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(EntryReadFilter.class)
public class OpenEntryReadFilter implements EntryReadFilter {
    @Override
    public List<String> readable(Collection<String> candidateKeys) {
        return candidateKeys == null ? List.of() : List.copyOf(candidateKeys);
    }
}
