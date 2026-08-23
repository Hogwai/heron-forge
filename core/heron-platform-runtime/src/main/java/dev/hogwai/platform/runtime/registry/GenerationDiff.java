package dev.hogwai.platform.runtime.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import dev.hogwai.platform.runtime.config.ApplicationConfig;
import dev.hogwai.platform.runtime.config.CapabilityConfig;
import dev.hogwai.platform.runtime.config.EntrypointConfig;
import dev.hogwai.platform.runtime.config.InputBindingConfig;
import dev.hogwai.platform.runtime.config.ParsedApplication;
import dev.hogwai.platform.runtime.config.SafeYamlParser;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.registry.GenerationRecord;

/**
 * Structural comparison of two sealed generation records by their raw YAML.
 *
 * <p>Both records are parsed through the standard configuration path:
 * {@link SafeYamlParser} for the secure lexical gate and the config mappers.
 * Only the declared capabilities (provider reference, safe config values, input bindings) and endpoints are compared.
 *
 * <p>The result is a deterministic list of {@link DiffEntry} records plus a readable text rendering.
 * Identical configurations yield an empty result.
 * An invalid document surfaces as the {@link PlatformException} of the existing parsing path.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
public final class GenerationDiff {

    public static final String CAPABILITY = "capability";
    public static final String CAPABILITY_CONFIG = "capability.config";
    public static final String CAPABILITY_INPUT = "capability.input";
    public static final String ENDPOINT = "endpoint";

    private GenerationDiff() {
        // no instances
    }

    /**
     * Compares two generation records.
     *
     * @param from the reference generation
     * @param to   the compared generation
     * @return the structured differences, empty when both parse to the same model
     * @throws NullPointerException if any argument is {@code null}
     * @throws PlatformException    if either raw YAML fails parsing or schema validation
     */
    public static DiffResult diff(GenerationRecord from, GenerationRecord to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        ApplicationConfig before = parse(from);
        ApplicationConfig after = parse(to);
        List<DiffEntry> entries = new ArrayList<>();
        diffCapabilities(before, after, entries);
        diffEndpoints(before, after, entries);
        return new DiffResult(entries);
    }

    private static ApplicationConfig parse(GenerationRecord generationRecord) {
        ParsedApplication parsed = new SafeYamlParser().parse(RegistrySupport.bytes(generationRecord.rawYaml()));
        if (!parsed.isValid()) {
            throw new PlatformException(RegistrySupport.firstErrorCode(parsed.diagnostics()),
                    parsed.diagnostics());
        }
        return parsed.application();
    }

    private static void diffCapabilities(ApplicationConfig before, ApplicationConfig after,
            List<DiffEntry> entries) {
        Map<String, CapabilityConfig> previous = byId(before.capabilities(), CapabilityConfig::id);
        Map<String, CapabilityConfig> current = byId(after.capabilities(), CapabilityConfig::id);
        for (String id : sortedUnion(previous.keySet(), current.keySet())) {
            CapabilityConfig was = previous.get(id);
            CapabilityConfig is = current.get(id);
            if (was == null) {
                entries.add(new DiffEntry(ChangeType.ADDED, CAPABILITY, id,
                        "provider '%s:%s'".formatted(is.providerId(), is.providerVersion())));
            } else if (is == null) {
                entries.add(new DiffEntry(ChangeType.REMOVED, CAPABILITY, id,
                        "provider '%s:%s'".formatted(was.providerId(), was.providerVersion())));
            } else {
                diffCapability(id, was, is, entries);
            }
        }
    }

    private static void diffCapability(String id, CapabilityConfig was, CapabilityConfig is,
            List<DiffEntry> entries) {
        if (!was.providerId().equals(is.providerId())
                || !was.providerVersion().equals(is.providerVersion())) {
            entries.add(new DiffEntry(ChangeType.MODIFIED, CAPABILITY, id,
                    "provider '%s:%s' -> '%s:%s'".formatted(was.providerId(), was.providerVersion(),
                            is.providerId(), is.providerVersion())));
        }
        diffCapabilityConfig(id, was.config(), is.config(), entries);
        diffCapabilityInputs(id, was.inputs(), is.inputs(), entries);
    }

    private static void diffCapabilityConfig(String id, Map<String, Object> was,
            Map<String, Object> is, List<DiffEntry> entries) {
        for (String key : sortedUnion(was.keySet(), is.keySet())) {
            Object previous = was.get(key);
            Object current = is.get(key);
            if (previous == null) {
                entries.add(new DiffEntry(ChangeType.ADDED, CAPABILITY_CONFIG, id,
                        "'%s' = %s".formatted(key, current)));
            } else if (current == null) {
                entries.add(new DiffEntry(ChangeType.REMOVED, CAPABILITY_CONFIG, id,
                        "'%s' (was %s)".formatted(key, previous)));
            } else if (!previous.equals(current)) {
                entries.add(new DiffEntry(ChangeType.MODIFIED, CAPABILITY_CONFIG, id,
                        "'%s': %s -> %s".formatted(key, previous, current)));
            }
        }
    }

    private static void diffCapabilityInputs(String id, List<InputBindingConfig> was,
            List<InputBindingConfig> is, List<DiffEntry> entries) {
        Map<String, InputBindingConfig> previous = byId(was, InputBindingConfig::inputPort);
        Map<String, InputBindingConfig> current = byId(is, InputBindingConfig::inputPort);
        for (String port : sortedUnion(previous.keySet(), current.keySet())) {
            InputBindingConfig before = previous.get(port);
            InputBindingConfig after = current.get(port);
            if (before == null) {
                entries.add(new DiffEntry(ChangeType.ADDED, CAPABILITY_INPUT, id,
                        "'%s' now binds %s".formatted(port, render(after))));
            } else if (after == null) {
                entries.add(new DiffEntry(ChangeType.REMOVED, CAPABILITY_INPUT, id,
                        "'%s' unbound (was %s)".formatted(port, render(before))));
            } else if (!before.capability().equals(after.capability())
                    || !before.port().equals(after.port())) {
                entries.add(new DiffEntry(ChangeType.MODIFIED, CAPABILITY_INPUT, id,
                        "'%s': %s -> %s".formatted(port, render(before), render(after))));
            }
        }
    }

    private static String render(InputBindingConfig binding) {
        return "%s.%s".formatted(binding.capability(), binding.port());
    }

    private static void diffEndpoints(ApplicationConfig before, ApplicationConfig after,
            List<DiffEntry> entries) {
        Map<String, EntrypointConfig> previous = byId(before.entrypoints(), EntrypointConfig::id);
        Map<String, EntrypointConfig> current = byId(after.entrypoints(), EntrypointConfig::id);
        for (String id : sortedUnion(previous.keySet(), current.keySet())) {
            EntrypointConfig was = previous.get(id);
            EntrypointConfig is = current.get(id);
            if (was == null) {
                entries.add(new DiffEntry(ChangeType.ADDED, ENDPOINT, id,
                        "%s %s -> %s".formatted(is.method(), is.path(), is.target())));
            } else if (is == null) {
                entries.add(new DiffEntry(ChangeType.REMOVED, ENDPOINT, id,
                        "%s %s -> %s".formatted(was.method(), was.path(), was.target())));
            } else {
                diffEndpoint(id, was, is, entries);
            }
        }
    }

    private static void diffEndpoint(String id, EntrypointConfig was, EntrypointConfig is,
            List<DiffEntry> entries) {
        List<String> changes = new ArrayList<>();
        if (!was.method().equals(is.method())) {
            changes.add("method %s -> %s".formatted(was.method(), is.method()));
        }
        if (!was.path().equals(is.path())) {
            changes.add("path %s -> %s".formatted(was.path(), is.path()));
        }
        if (!was.target().equals(is.target())) {
            changes.add("target %s -> %s".formatted(was.target(), is.target()));
        }
        if (!changes.isEmpty()) {
            entries.add(new DiffEntry(ChangeType.MODIFIED, ENDPOINT, id, String.join("; ", changes)));
        }
    }

    private static <T> Map<String, T> byId(List<T> items, Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        items.forEach(item -> map.put(key.apply(item), item));
        return map;
    }

    private static List<String> sortedUnion(Set<String> left, Set<String> right) {
        TreeSet<String> union = new TreeSet<>(left);
        union.addAll(right);
        return List.copyOf(union);
    }

    /** Kind of change carried by a {@link DiffEntry}. */
    public enum ChangeType {

        /** The item exists only in the {@code to} generation. */
        ADDED,

        /** The item exists only in the {@code from} generation. */
        REMOVED,

        /** The item exists in both generations with different content. */
        MODIFIED
    }

    /**
     * One atomic difference between two generations.
     *
     * @param kind    the kind of change
     * @param section the compared section ({@code capability},
     *                {@code capability.config}, {@code capability.input} or
     *                {@code endpoint})
     * @param id      identifier of the changed item within its section
     * @param detail  human-readable description of the change
     */
    public record DiffEntry(ChangeType kind, String section, String id, String detail) {
    }

    /**
     * Structured result of a comparison: the ordered entry list plus a readable
     * text rendering.
     *
     * @param entries the differences in deterministic order; empty when identical
     */
    public record DiffResult(List<DiffEntry> entries) {

        /**
         * Creates a diff result.
         *
         * @param entries the differences; copied defensively
         */
        public DiffResult {
            entries = List.copyOf(entries);
        }

        /**
         * @return {@code true} when both generations are structurally identical
         */
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /**
         * Renders the differences as one line per entry, prefixed with
         * {@code +} (added), {@code -} (removed) or {@code ~} (modified).
         *
         * @return the text rendering, or {@code no differences} when empty
         */
        public String render() {
            if (entries.isEmpty()) {
                return "no differences";
            }
            return String.join("\n", entries.stream().map(DiffResult::renderLine).toList());
        }

        private static String renderLine(DiffEntry entry) {
            String symbol = switch (entry.kind()) {
                case ADDED -> "+";
                case REMOVED -> "-";
                case MODIFIED -> "~";
            };
            return "%s %s '%s': %s".formatted(symbol, entry.section(), entry.id(), entry.detail());
        }
    }
}
