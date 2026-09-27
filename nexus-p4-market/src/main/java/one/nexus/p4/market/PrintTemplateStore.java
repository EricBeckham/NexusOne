package one.nexus.p4.market;

import java.util.List;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;

/** DPO-L4: stored print template copies ({@code market.print_template}). */
public interface PrintTemplateStore {
    List<PrintTemplate> copies();

    void insert(PrintTemplate template);
}
