package dev.hogwai.platform.runtime.config.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import dev.hogwai.platform.runtime.config.Diagnostics;
import dev.hogwai.platform.runtime.config.WorkerConfig;
import dev.hogwai.platform.spi.Diagnostic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the optional {@code workers} list.
 */
final class WorkerMapper {

    private static final Set<String> WORKER_FIELDS = Set.of("id", "transport", "config");
    private static final Set<String> ALLOWED_TRANSPORTS = Set.of("http");

    private WorkerMapper() {
        // no instances
    }

    static List<WorkerConfig> mapWorkers(JsonNode workersNode, String path, List<Diagnostic> diagnostics) {
        if (workersNode == null) {
            return List.of();
        }
        if (!workersNode.isArray()) {
            diagnostics.add(Diagnostics.schemaError(path, "workers must be a list", "provide a list"));
            return List.of();
        }
        List<WorkerConfig> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < workersNode.size(); i++) {
            String workerPath = "%s/%d".formatted(path, i);
            WorkerConfig mapped = mapWorker(workersNode.get(i), workerPath, diagnostics);
            if (mapped == null) {
                continue;
            }
            if (!seenIds.add(mapped.id())) {
                diagnostics.add(Diagnostics.schemaError("%s/id".formatted(workerPath),
                        "duplicate worker id", "use a unique id"));
            }
            result.add(mapped);
        }
        return List.copyOf(result);
    }

    private static WorkerConfig mapWorker(JsonNode worker, String path, List<Diagnostic> diagnostics) {
        if (worker == null || !worker.isObject()) {
            diagnostics.add(Diagnostics.schemaError(path, "worker must be a mapping", "provide a mapping"));
            return null;
        }
        FieldChecks.rejectUnknownFields(worker, WORKER_FIELDS, path, diagnostics);

        String id = FieldChecks.requiredString(worker, "id", path, diagnostics);
        String transport = FieldChecks.requiredString(worker, "transport", path, diagnostics);
        Map<String, Object> config = ConfigMapper.mapConfig(worker.get("config"), "%s/config".formatted(path), diagnostics);

        if (id == null || transport == null) {
            return null;
        }
        if (!ALLOWED_TRANSPORTS.contains(transport)) {
            diagnostics.add(Diagnostics.schemaError("%s/transport".formatted(path),
                    "unknown worker transport", "use one of http"));
            return null;
        }
        return new WorkerConfig(id, transport, config);
    }
}
