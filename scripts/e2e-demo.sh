#!/usr/bin/env bash
# End-to-end check of the consumer path:
# scaffold an app with `heron create`, add a DB-backed provider registered via
# @HeronService, boot the Helidon shell against the Compose PostgreSQL fixture
# and probe the HTTP endpoints.
#
# Prerequisites: `make publish cli db-up` (run automatically by `make e2e`).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${E2E_WORK:-${TMPDIR:-/tmp}/heron-e2e}"
APP_NAME="${E2E_APP_NAME:-e2e-app}"
BASE_PACKAGE="com.example.e2e"
BASE_URL="http://127.0.0.1:8080"

: "${HERON_DB_URL:?set HERON_DB_URL (make provides it)}"
: "${HERON_DB_USER:?set HERON_DB_USER}"
: "${HERON_DB_PASSWORD:?set HERON_DB_PASSWORD}"

HERON="$ROOT/bricks/heron-platform-cli/build/install/heron/bin/heron"

echo "[e2e] workdir: $WORK"
rm -rf "$WORK"
mkdir -p "$WORK"

echo "[e2e] heron create app $APP_NAME --package $BASE_PACKAGE"
( cd "$WORK" && "$HERON" create app "$APP_NAME" --package "$BASE_PACKAGE" )

APP_DIR="$WORK/$APP_NAME"

echo "[e2e] adding DB-backed provider OrdersProviderFactory"
mkdir -p "$APP_DIR/src/main/java/$(echo "$BASE_PACKAGE" | tr '.' '/')"
cat > "$APP_DIR/src/main/java/$(echo "$BASE_PACKAGE" | tr '.' '/')/OrdersProviderFactory.java" <<'JAVA'
package com.example.e2e;

import dev.hogwai.platform.spi.CapabilityKind;
import dev.hogwai.platform.spi.Diagnostic;
import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.ProviderId;
import dev.hogwai.platform.spi.ProviderVersion;
import dev.hogwai.platform.spi.SpiMajor;
import dev.hogwai.platform.spi.annotation.HeronService;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.Schema;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import dev.hogwai.platform.spi.error.Severity;
import dev.hogwai.platform.spi.provider.BuildContext;
import dev.hogwai.platform.spi.provider.CapabilityInstance;
import dev.hogwai.platform.spi.provider.ConfigurationSchema;
import dev.hogwai.platform.spi.provider.PortDescriptor;
import dev.hogwai.platform.spi.provider.ProviderDescriptor;
import dev.hogwai.platform.spi.provider.ProviderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** DB-backed source capability reading orders through the data brick. */
@HeronService(value = ProviderFactory.class, id = "com.example.e2e.orders")
public final class OrdersProviderFactory implements ProviderFactory {

    private static final Schema SCHEMA = new Schema("orders", 1,
            List.of(
                    new Field(new FieldId("orderId"), "orderId",
                            new FieldType.StringType(), false, Optional.empty()),
                    new Field(new FieldId("orderedQuantity"), "orderedQuantity",
                            new FieldType.Int64Type(), false, Optional.empty())),
            false);

    private static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            new ProviderId("com.example.e2e.orders"), ProviderVersion.parse("1.0.0"),
            CapabilityKind.SOURCE, SpiMajor.V1, Map.of(),
            Map.of(new PortId("records"), new PortDescriptor(new PortId("records"), SCHEMA, true)),
            new ConfigurationSchema(Set.of("url", "user", "password"), Set.of(),
                    Map.of("url", ConfigurationSchema.ScalarKind.STRING,
                            "user", ConfigurationSchema.ScalarKind.STRING,
                            "password", ConfigurationSchema.ScalarKind.STRING),
                    Map.of()));

    /** Creates the orders factory. */
    public OrdersProviderFactory() {
        // Default constructor
    }

    @Override
    public ProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<Diagnostic> validate(Map<String, Object> rawConfig) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (String field : List.of("url", "user", "password")) {
            if (!(rawConfig.get(field) instanceof String value) || value.isBlank()) {
                diagnostics.add(new Diagnostic(PlatformErrorCode.PROVIDER_CONFIG_ERROR, Severity.ERROR,
                        "/config/" + field, "missing required database configuration field",
                        "check the database configuration"));
            }
        }
        return List.copyOf(diagnostics);
    }

    @Override
    public CapabilityInstance create(Map<String, Object> rawConfig, BuildContext context) {
        var failure = validate(rawConfig);
        if (!failure.isEmpty()) {
            throw new PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, failure);
        }
        DataAccess dataAccess = context.dataAccessFactory().open(new DataAccessConfiguration(
                (String) rawConfig.get("url"),
                (String) rawConfig.get("user"),
                (String) rawConfig.get("password")));
        try {
            context.resourceTracker().register(dataAccess);
        } catch (RuntimeException registrationFailure) {
            try {
                dataAccess.close();
            } catch (RuntimeException closeFailure) {
                registrationFailure.addSuppressed(closeFailure);
            }
            throw registrationFailure;
        }
        DataSetLimits limits = new DataSetLimits(1000, 1_000_000);
        return (inputs, executionContext) -> dataAccess.queryToDataSet(
                new QueryContext(executionContext.deadline(),
                        executionContext.cancellationToken()::isCancellationRequested),
                "orders",
                "SELECT order_id, ordered_quantity FROM orders ORDER BY order_id",
                SCHEMA,
                Map.of("orderId", "order_id", "orderedQuantity", "ordered_quantity"),
                limits);
    }
}
JAVA

echo "[e2e] wiring capabilities and endpoints in application.yaml"
cat > "$APP_DIR/src/main/resources/application.yaml" <<'YAML'
apiVersion: heron.dev/v1
application: e2e-app
capabilities:
  - id: hello
    provider:
      id: com.example.e2e.hello
      version: 1.0.0
  - id: orders
    provider:
      id: com.example.e2e.orders
      version: 1.0.0
    config:
      url: ${HERON_DB_URL}
      user: ${HERON_DB_USER}
      password: ${HERON_DB_PASSWORD}
endpoints:
  - id: hello-api
    method: GET
    path: /hello
    target: hello
  - id: orders-api
    method: GET
    path: /orders
    target: orders
YAML

echo "[e2e] building distribution"
"$ROOT/gradlew" -p "$APP_DIR" installDist --no-daemon >"$WORK/build.log" 2>&1 \
    || { echo "[e2e] build failed:"; tail -30 "$WORK/build.log"; exit 1; }

RUN_LOG="$WORK/run.log"
echo "[e2e] starting shell"
setsid nohup env HERON_DB_URL="$HERON_DB_URL" HERON_DB_USER="$HERON_DB_USER" \
    HERON_DB_PASSWORD="$HERON_DB_PASSWORD" \
    "$APP_DIR/build/install/$APP_NAME/bin/$APP_NAME" start \
    --config "$APP_DIR/src/main/resources/application.yaml" </dev/null >"$RUN_LOG" 2>&1 &
SHELL_PID=$!
cleanup() {
    kill "$SHELL_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "[e2e] waiting for readiness"
ready=0
for _ in $(seq 1 30); do
    if curl --max-time 3 -sf "$BASE_URL/health/ready" >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 1
done
if [ "$ready" -ne 1 ]; then
    echo "[e2e] shell did not become ready:"; tail -30 "$RUN_LOG"; exit 1
fi

echo "[e2e] probing endpoints"
hello=$(curl --max-time 5 -sf "$BASE_URL/hello")
orders=$(curl --max-time 10 -sf "$BASE_URL/orders")

if ! echo "$hello" | grep -q '"message":"hello from e2e-app"'; then
    echo "[e2e] unexpected /hello body: $hello"; exit 1
fi
if ! echo "$orders" | grep -q '"rowCount":3' || ! echo "$orders" | grep -q 'LATE-001'; then
    echo "[e2e] unexpected /orders body: $orders"; exit 1
fi
echo "[e2e] PASS"
echo "[e2e]   /health/ready -> ready"
echo "[e2e]   /hello        -> static hello record"
echo "[e2e]   /orders       -> 3 rows from PostgreSQL ($HERON_DB_URL)"
