package dev.hogwai.platform.runtime.config;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable model of a dashboard widget declaration.
 *
 * <p>Widgets compose presentation: each references an {@link EntrypointConfig}
 * by id ({@code target}) and carries display metadata consumed by the widget
 * surface tooling.
 */
public record WidgetConfig(String id, String type, String title, String target) {

    /** Allowed widget types. */
    public static final Set<String> ALLOWED_TYPES = Set.of("kpi", "table", "chart");

    /**
     * Rejects null and blank components.
     *
     * @throws NullPointerException   if a component is null
     * @throws IllegalArgumentException if a component is blank
     */
    public WidgetConfig {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (target.isBlank()) {
            throw new IllegalArgumentException("target must not be blank");
        }
    }
}
