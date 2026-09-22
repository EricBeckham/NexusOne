package one.nexus.notification;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TemplateCatalog {
    private final Map<String, NotificationTemplate> byCode;

    public TemplateCatalog() {
        this(BuiltinTemplates.all());
    }

    public TemplateCatalog(Iterable<NotificationTemplate> templates) {
        this.byCode = java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                .collect(Collectors.toUnmodifiableMap(NotificationTemplate::code, Function.identity()));
    }

    public Optional<NotificationTemplate> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }
}
