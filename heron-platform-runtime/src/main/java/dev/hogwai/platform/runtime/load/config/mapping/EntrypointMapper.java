package dev.hogwai.platform.runtime.load.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.load.config.entrypoint.EntrypointConfig;
import dev.hogwai.platform.runtime.load.config.diagnostics.Diagnostics;
import dev.hogwai.platform.spi.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Maps the optional {@code spec.entrypoints} list and its declarations.
 * Package-private so the public mapping surface remains {@link ConfigMapper}.
 */
final class EntrypointMapper {

    private EntrypointMapper() {
        // no instances
    }

    /**
     * Maps entrypoints in YAML order.
     *
     * @param entrypointsNode the entrypoints node, or null when omitted
     * @param path the structural path of the entrypoints list
     * @param diagnostics the diagnostics collector
     * @return the mapped entrypoints
     */
    static List<EntrypointConfig> mapEntrypoints(JsonNode entrypointsNode, String path,
                                                  List<Diagnostic> diagnostics) {
        if (entrypointsNode == null) {
            return List.of();
        }
        if (!entrypointsNode.isArray()) {
            diagnostics.add(Diagnostics.schemaError(path,
                    "entrypoints must be a list", "provide a list"));
            return List.of();
        }

        List<EntrypointConfig> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();
        for (int i = 0; i < entrypointsNode.size(); i++) {
            String entrypointPath = path + "/" + i;
            EntrypointConfig mapped = EntrypointDeclarationMapper.map(
                    entrypointsNode.get(i), entrypointPath, diagnostics);
            if (mapped != null) {
                reportDuplicates(mapped, entrypointPath, seenIds, seenPaths, diagnostics);
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    private static void reportDuplicates(EntrypointConfig entrypoint, String path,
                                          Set<String> seenIds, Set<String> seenPaths,
                                          List<Diagnostic> diagnostics) {
        if (!seenIds.add(entrypoint.id())) {
            diagnostics.add(Diagnostics.schemaError(path + "/id",
                    "duplicate entrypoint id", "use a unique id"));
        }
        if (!seenPaths.add(entrypoint.path())) {
            diagnostics.add(Diagnostics.schemaError(path + "/path",
                    "duplicate entrypoint path", "use a unique path"));
        }
    }
}

/** Maps and validates one entrypoint declaration. */
final class EntrypointDeclarationMapper {

    private static final Set<String> ENTRYPOINT_FIELDS = Set.of("id", "method", "path", "target");

    private EntrypointDeclarationMapper() {
        // no instances
    }

    static EntrypointConfig map(JsonNode entrypoint, String path, List<Diagnostic> diagnostics) {
        if (entrypoint == null) {
            diagnostics.add(Diagnostics.schemaError(path,
                    "entrypoint must be a mapping", "provide a mapping"));
            return null;
        }
        if (!entrypoint.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path,
                    "entrypoint must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(entrypoint, ENTRYPOINT_FIELDS, path, diagnostics);

        String id = EntrypointFieldValidator.requiredString(entrypoint, "id", path, diagnostics);
        String method = EntrypointFieldValidator.requiredString(entrypoint, "method", path, diagnostics);
        String route = EntrypointFieldValidator.requiredString(entrypoint, "path", path, diagnostics);
        String target = EntrypointFieldValidator.requiredString(entrypoint, "target", path, diagnostics);

        if (EntrypointFieldValidator.hasMissingField(id, method, route, target)) {
            return null;
        }
        if (!EntrypointFieldValidator.validateMethod(method, path, diagnostics)) {
            return null;
        }
        if (!EntrypointFieldValidator.validatePath(route, path, diagnostics)) {
            return null;
        }
        return new EntrypointConfig(id, method, route, target);
    }
}

/** Validates scalar entrypoint fields and reports their structural paths. */
final class EntrypointFieldValidator {

    private EntrypointFieldValidator() {
        // no instances
    }

    static boolean hasMissingField(String id, String method, String route, String target) {
        return Stream.of(id, method, route, target).anyMatch(Objects::isNull);
    }

    static boolean validateMethod(String method, String path, List<Diagnostic> diagnostics) {
        if ("GET".equals(method)) {
            return true;
        }
        diagnostics.add(Diagnostics.schemaError(path + "/method",
                "entrypoint method must be GET", "use 'GET'"));
        return false;
    }

    static boolean validatePath(String route, String path, List<Diagnostic> diagnostics) {
        if (!route.startsWith("/")) {
            reportInvalidPath(path, diagnostics);
            return false;
        }
        if (route.indexOf('?') >= 0) {
            reportInvalidPath(path, diagnostics);
            return false;
        }
        if (route.indexOf('#') >= 0) {
            reportInvalidPath(path, diagnostics);
            return false;
        }
        return true;
    }

    private static void reportInvalidPath(String path, List<Diagnostic> diagnostics) {
        diagnostics.add(Diagnostics.schemaError(path + "/path",
                "entrypoint path is invalid", "use an absolute path without query or fragment"));
    }

    static String requiredString(JsonNode parent, String field, String path,
                                 List<Diagnostic> diagnostics) {
        JsonNode node = parent.get(field);
        String fieldPath = path + "/" + field;
        if (node == null) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "missing required member '" + field + "'", "provide a value for '" + field + "'"));
            return null;
        }
        if (!node.isTextual()) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "member '" + field + "' must be a string", "provide a string value"));
            return null;
        }
        String value = node.textValue();
        if (value.isBlank()) {
            diagnostics.add(Diagnostics.schemaError(fieldPath,
                    "member '" + field + "' must not be blank", "provide a non-blank value"));
            return null;
        }
        return value;
    }
}
