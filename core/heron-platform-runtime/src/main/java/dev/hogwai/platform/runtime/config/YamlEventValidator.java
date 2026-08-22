package dev.hogwai.platform.runtime.config;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.hogwai.platform.spi.Diagnostic;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentEndEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingEndEvent;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceEndEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.JsonSchema;

/**
 * Validates SnakeYAML Engine events and builds a plain Java map/list/scalar
 * tree. It is intentionally the single temporary helper for event policy and
 * preserves numeric lexical values until Jackson conversion.
 */
@SuppressWarnings("PMD.CyclomaticComplexity")
final class YamlEventValidator {

    private static final Pattern ENV_INTERPOLATION = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern CANONICAL_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final JsonSchema JSON_SCHEMA = new JsonSchema();

    private final YamlLimits limits;
    private final EventState state;

    YamlEventValidator(YamlLimits limits) {
        this.limits = limits;
        this.state = new EventState();
    }

    boolean validate(Iterable<Event> events, List<Diagnostic> diagnostics) {
        for (Event event : events) {
            if (!handleEvent(event, diagnostics)) {
                return false;
            }
        }
        if (state.documentCount != 1 || state.inDocument || !state.rootSet) {
            return fail(diagnostics, null, "configuration document is empty",
                    "provide a non-empty YAML document");
        }
        return true;
    }

    Object value() {
        return state.root;
    }

    private boolean handleEvent(Event event, List<Diagnostic> diagnostics) {
        if (event instanceof DocumentStartEvent documentStart) {
            return handleDocumentStart(documentStart, diagnostics);
        }
        if (event instanceof DocumentEndEvent) {
            return handleDocumentEnd(diagnostics);
        }
        if (event instanceof AliasEvent) {
            return fail(diagnostics, nodePath(), "YAML aliases are not allowed",
                    "replace the alias with its value");
        }
        if (event instanceof MappingStartEvent mapping) {
            return startCollection(mapping, true, diagnostics);
        }
        if (event instanceof SequenceStartEvent sequence) {
            return startCollection(sequence, false, diagnostics);
        }
        if (event instanceof ScalarEvent scalar) {
            return scalar(scalar, diagnostics);
        }
        if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
            return endCollection(diagnostics);
        }
        return true;
    }

    private boolean handleDocumentStart(DocumentStartEvent documentStart, List<Diagnostic> diagnostics) {
        if (state.documentCount++ > 0) {
            return fail(diagnostics, null, "multiple YAML documents are not supported",
                    "provide a single document");
        }
        if (!documentStart.getTags().isEmpty()) {
            return fail(diagnostics, null, "explicit YAML tag is not allowed", "remove the explicit YAML tag");
        }
        state.inDocument = true;
        return true;
    }

    private boolean handleDocumentEnd(List<Diagnostic> diagnostics) {
        if (!state.rootSet || !state.frames.isEmpty()) {
            return fail(diagnostics, null, "configuration document is empty",
                    "provide a non-empty YAML document");
        }
        state.inDocument = false;
        return true;
    }

    private boolean endCollection(List<Diagnostic> diagnostics) {
        if (state.frames.isEmpty()) {
            return fail(diagnostics, null, "configuration could not be parsed",
                    "provide well-formed YAML");
        }
        state.frames.pop();
        return true;
    }

    private boolean startCollection(CollectionStartEvent event, boolean mapping, List<Diagnostic> diagnostics) {
        if (isMappingKey()) {
            return fail(diagnostics, state.frames.peek().path, "mapping keys must be scalar",
                    "use a scalar mapping key");
        }
        String path = nodePath();
        if (!checkControls(event, path, diagnostics, false)) {
            return false;
        }
        if (!isRootAllowed(mapping, diagnostics)) {
            return false;
        }
        if (!withinDepthLimit(path, diagnostics)) {
            return false;
        }
        Object value = mapping ? new LinkedHashMap<String, Object>() : new ArrayList<>();
        acceptValue(value);
        state.frames.push(new Frame(mapping, path == null ? "" : path, value));
        return true;
    }

    private boolean isRootAllowed(boolean mapping, List<Diagnostic> diagnostics) {
        if (state.frames.isEmpty() && !mapping) {
            return fail(diagnostics, null, "configuration root must be a mapping",
                    "start the document with a mapping such as 'apiVersion: ...'");
        }
        return true;
    }

    private boolean withinDepthLimit(String path, List<Diagnostic> diagnostics) {
        if (isNodeCountInvalid(path, diagnostics) || state.frames.size() + 1 > limits.maxDepth()) {
            return fail(diagnostics, path, "configuration exceeds maximum nesting depth",
                    "reduce the nesting depth");
        }
        return true;
    }

    private boolean scalar(ScalarEvent scalar, List<Diagnostic> diagnostics) {
        boolean key = isMappingKey();
        String path = nodePath();
        if (!checkControls(scalar, path, diagnostics, key)) {
            return false;
        }
        if (state.frames.isEmpty()) {
            return fail(diagnostics, null, "configuration root must be a mapping",
                    "start the document with a mapping such as 'apiVersion: ...'");
        }
        if (key) {
            return mappingKey(scalar, diagnostics);
        }
        if (isNodeCountInvalid(path, diagnostics)) {
            return false;
        }
        String value = scalar.getValue();
        if (!checkScalarValue(value, path, diagnostics)) {
            return false;
        }
        Object converted = convertScalar(scalar, path, diagnostics);
        if (converted instanceof InvalidNumber) {
            return false;
        }
        acceptValue(converted);
        return true;
    }

    private boolean checkScalarValue(String value, String path, List<Diagnostic> diagnostics) {
        if (value.length() > limits.maxStringLength()) {
            return fail(diagnostics, path, "string value exceeds maximum length", "shorten the value");
        }
        if (hasForbiddenInterpolation(value)) {
            return fail(diagnostics, path, "value contains forbidden interpolation",
                    "use exactly ${VARIABLE} as the entire value");
        }
        return true;
    }

    private boolean mappingKey(ScalarEvent scalar, List<Diagnostic> diagnostics) {
        Frame frame = state.frames.peek();
        String key = scalar.getValue();
        if (key.length() > limits.maxStringLength()) {
            return fail(diagnostics, frame.path, "mapping key exceeds maximum length", "shorten the key");
        }
        if (hasInterpolation(key)) {
            return fail(diagnostics, frame.path, "mapping key contains forbidden interpolation",
                    "remove the ${...} expression");
        }
        String fieldPath = Diagnostics.childPath(frame.path, key);
        if (!frame.keys.add(key)) {
            return fail(diagnostics, fieldPath, "duplicate key in mapping", "remove the duplicate key");
        }
        frame.expectingKey = false;
        frame.pendingKey = key;
        frame.pendingPath = fieldPath;
        return true;
    }

    private Object convertScalar(ScalarEvent scalar, String path, List<Diagnostic> diagnostics) {
        String text = scalar.getValue();
        if (!scalar.isPlain() || !scalar.getImplicit().canOmitTagInPlainScalar()) {
            return text;
        }
        if (isUnsupportedDecimal(text)) {
            return invalidNumber(diagnostics, path, "decimal value is not supported",
                    "use a supported decimal value");
        }
        Tag tag = JSON_SCHEMA.getScalarResolver().resolve(text, true);
        try {
            if (Tag.INT.equals(tag)) {
                return Long.parseLong(text);
            }
            if (Tag.FLOAT.equals(tag)) {
                return new BigDecimal(text);
            }
            if (Tag.BOOL.equals(tag)) {
                return Boolean.parseBoolean(text);
            }
            if (Tag.NULL.equals(tag)) {
                return null;
            }
            return text;
        } catch (NumberFormatException _) {
            return invalidNumber(diagnostics, path, numberMessage(tag), numberRemediation(tag));
        }
    }

    private static boolean isUnsupportedDecimal(String text) {
        return text.equalsIgnoreCase(".nan") || text.equalsIgnoreCase(".inf")
                || text.equalsIgnoreCase("+.inf") || text.equalsIgnoreCase("-.inf");
    }

    private static String numberMessage(Tag tag) {
        return Tag.INT.equals(tag) ? "integer value exceeds supported range"
                : "decimal value is not supported";
    }

    private static String numberRemediation(Tag tag) {
        return Tag.INT.equals(tag) ? "use a value within the 64-bit signed range"
                : "use a supported decimal value";
    }

    private Object invalidNumber(List<Diagnostic> diagnostics, String path, String message, String remediation) {
        fail(diagnostics, path, message, remediation);
        return INVALID;
    }

    private boolean checkControls(NodeEvent event, String path, List<Diagnostic> diagnostics, boolean key) {
        if (event.getAnchor().isPresent()) {
            return fail(diagnostics, path, key ? "YAML anchor on key is not allowed" : "YAML anchors are not allowed",
                    "remove the anchor");
        }
        if (hasExplicitTag(event)) {
            return fail(diagnostics, path, key ? "explicit YAML tag on key is not allowed"
                    : "explicit YAML tag is not allowed", "remove the explicit YAML tag");
        }
        return true;
    }

    private static boolean hasExplicitTag(NodeEvent event) {
        return (event instanceof ScalarEvent scalar && scalar.getTag().isPresent())
                || (event instanceof CollectionStartEvent collection && collection.getTag().isPresent());
    }

    private boolean isNodeCountInvalid(String path, List<Diagnostic> diagnostics) {
        state.nodeCount++;
        return state.nodeCount > limits.maxNodes()
                && !fail(diagnostics, path, "configuration exceeds maximum node count",
                "reduce the number of configuration entries");
    }

    private void acceptValue(Object value) {
        if (state.frames.isEmpty()) {
            state.root = value;
            state.rootSet = true;
            return;
        }
        Frame parent = state.frames.peek();
        if (parent.mapping) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parent.value;
            map.put(parent.pendingKey, value);
            parent.expectingKey = true;
            parent.pendingPath = null;
            parent.pendingKey = null;
        } else {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) parent.value;
            list.add(value);
            parent.nextIndex++;
        }
    }

    private boolean isMappingKey() {
        return !state.frames.isEmpty() && state.frames.peek().mapping && state.frames.peek().expectingKey;
    }

    private String nodePath() {
        if (state.frames.isEmpty()) {
            return null;
        }
        Frame frame = state.frames.peek();
        if (frame.mapping) {
            return frame.expectingKey ? frame.path : frame.pendingPath;
        }
        return frame.path + "/" + frame.nextIndex;
    }

    private static boolean hasInterpolation(String value) {
        return ENV_INTERPOLATION.matcher(value).find();
    }

    /**
     * Returns whether the value contains an interpolation expression that the
     * environment-placeholder resolver will not accept. A single canonical
     * {@code ${VARIABLE}} spanning the entire value is allowed through this
     * lexical gate and resolved later; any other occurrence of {@code ${...}}
     * (partial, doubled or malformed) is rejected here so that a mistyped
     * reference can never silently survive as literal content.
     */
    private static boolean hasForbiddenInterpolation(String value) {
        return hasInterpolation(value) && !CANONICAL_PLACEHOLDER.matcher(value).matches();
    }

    private static boolean fail(List<Diagnostic> diagnostics, String path, String message, String remediation) {
        diagnostics.add(Diagnostics.parseError(path, message, remediation));
        return false;
    }

    private static final Object INVALID = new InvalidNumber();

    private static final class InvalidNumber {
        // Sentinel type used to keep conversion failure separate from null.
    }

    private static final class EventState {
        private final Deque<Frame> frames = new ArrayDeque<>();
        private int documentCount;
        private int nodeCount;
        private boolean inDocument;
        private boolean rootSet;
        private Object root;
    }

    private static final class Frame {
        private final boolean mapping;
        private final String path;
        private final Object value;
        private final Set<String> keys = new HashSet<>();
        private boolean expectingKey;
        private String pendingKey;
        private String pendingPath;
        private int nextIndex;

        private Frame(boolean mapping, String path, Object value) {
            this.mapping = mapping;
            this.path = path;
            this.value = value;
            this.expectingKey = mapping;
        }
    }
}