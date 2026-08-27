package dev.hogwai.platform.examples.kotlinprovider

import dev.hogwai.platform.spi.CapabilityKind
import dev.hogwai.platform.spi.Diagnostic
import dev.hogwai.platform.spi.PortId
import dev.hogwai.platform.spi.ProviderId
import dev.hogwai.platform.spi.ProviderVersion
import dev.hogwai.platform.spi.SpiMajor
import dev.hogwai.platform.spi.annotation.HeronService
import dev.hogwai.platform.spi.data.DataSetLimits
import dev.hogwai.platform.spi.data.Field
import dev.hogwai.platform.spi.data.FieldId
import dev.hogwai.platform.spi.data.FieldType
import dev.hogwai.platform.spi.data.MaterializedDataSet
import dev.hogwai.platform.spi.data.Schema
import dev.hogwai.platform.spi.data.access.DataAccess
import dev.hogwai.platform.spi.data.access.DataAccessConfiguration
import dev.hogwai.platform.spi.data.access.QueryContext
import dev.hogwai.platform.spi.error.PlatformErrorCode
import dev.hogwai.platform.spi.error.PlatformException
import dev.hogwai.platform.spi.error.Severity
import dev.hogwai.platform.spi.provider.BuildContext
import dev.hogwai.platform.spi.provider.CapabilityInstance
import dev.hogwai.platform.spi.provider.ConfigurationSchema
import dev.hogwai.platform.spi.provider.PortDescriptor
import dev.hogwai.platform.spi.provider.ProviderDescriptor
import dev.hogwai.platform.spi.provider.ProviderFactory
import java.time.Instant
import java.util.Optional

private val SUMMARY_SCHEMA = Schema(
    "supply-chain.order-summary",
    1,
    listOf(
        Field(FieldId("orderId"), "orderId", FieldType.StringType(), false, Optional.empty()),
        Field(FieldId("orderedQuantity"), "orderedQuantity", FieldType.Int64Type(), false, Optional.empty()),
        Field(FieldId("deliveredQuantity"), "deliveredQuantity", FieldType.Int64Type(), false, Optional.empty()),
        Field(FieldId("deliveryPercent"), "deliveryPercent", FieldType.Int64Type(), false, Optional.empty()),
        Field(FieldId("status"), "status", FieldType.StringType(), false, Optional.empty())
    ),
    false
)

/** Kotlin provider exposing an aggregate delivery status rather than raw orders. */
@HeronService(value = ProviderFactory::class, id = "demo.kotlin.order-summary")
class KotlinOrderSummaryProviderFactory : ProviderFactory {

    override fun descriptor(): ProviderDescriptor = DESCRIPTOR

    override fun validate(rawConfig: Map<String, Any>): List<Diagnostic> {
        val diagnostics = rawConfig.keys
            .filterNot { it in CONFIGURATION_FIELDS }
            .map { diagnostic("/config/<key>", "unknown database configuration field") }
            .toMutableList()

        for ((key, value) in rawConfig) {
            if (key == "maxRows" || key == "maxBytes") {
                if (value !is Number || value.toLong() <= 0) {
                    diagnostics += diagnostic("/config/$key", "dataset limit must be a positive integer")
                }
            } else if (key in setOf("url", "user", "password") && (value !is String || value.isBlank())) {
                diagnostics += diagnostic("/config/$key", "database configuration field must be a non-blank string")
            }
        }

        for (field in REQUIRED_FIELDS) {
            if (rawConfig[field] == null) {
                diagnostics += diagnostic("/config/$field", "missing required database configuration field")
            }
        }
        return diagnostics
    }

    override fun create(rawConfig: Map<String, Any>, context: BuildContext): CapabilityInstance {
        val diagnostics = validate(rawConfig)
        if (diagnostics.isNotEmpty()) {
            throw PlatformException(PlatformErrorCode.PROVIDER_CONFIG_ERROR, diagnostics)
        }

        val dataAccess = context.dataAccessFactory().open(
            DataAccessConfiguration(
                requiredText(rawConfig, "url"),
                requiredText(rawConfig, "user"),
                requiredText(rawConfig, "password")
            )
        )
        try {
            context.resourceTracker().register(dataAccess)
        } catch (registrationFailure: RuntimeException) {
            try {
                dataAccess.close()
            } catch (closeFailure: RuntimeException) {
                registrationFailure.addSuppressed(closeFailure)
            }
            throw registrationFailure
        }

        val limits =
            DataSetLimits(positiveLong(rawConfig["maxRows"], 1_000), positiveLong(rawConfig["maxBytes"], 1_000_000))
        return CapabilityInstance { inputs, executionContext ->
            executionContext.cancellationToken().throwIfCancellationRequested()
            if (!Instant.now().isBefore(executionContext.deadline())) {
                throw PlatformException(PlatformErrorCode.DEADLINE_EXCEEDED, emptyList())
            }
            requireNotNull(inputs) { "inputs must not be null" }
            KotlinOrderSummaryQuery.read(
                dataAccess,
                QueryContext(
                    executionContext.deadline(),
                    executionContext.cancellationToken()::isCancellationRequested
                ),
                limits
            )
        }
    }

    private fun requiredText(rawConfig: Map<String, Any>, key: String): String {
        val configured = rawConfig[key]
        if (configured is String && configured.isNotBlank()) {
            return configured
        }
        // Unreachable through create(): validate() rejects a missing or blank
        // password before the configuration is decoded.
        throw IllegalArgumentException("missing required database configuration field: $key")
    }

    private fun positiveLong(value: Any?, fallback: Long): Long =
        (value as? Number)?.toLong()?.takeIf { it > 0 } ?: fallback

    private fun diagnostic(path: String, message: String): Diagnostic = Diagnostic(
        PlatformErrorCode.PROVIDER_CONFIG_ERROR,
        Severity.ERROR,
        path,
        message,
        "check the PostgreSQL configuration"
    )

    private companion object {
        val CONFIGURATION_FIELDS = setOf("url", "user", "password", "maxRows", "maxBytes")
        val REQUIRED_FIELDS = listOf("url", "user", "password")

        val DESCRIPTOR = ProviderDescriptor(
            ProviderId("demo.kotlin.order-summary"),
            ProviderVersion.parse("1.0.0"),
            CapabilityKind.SOURCE,
            SpiMajor.V1,
            emptyMap(),
            mapOf(PortId("records") to PortDescriptor(PortId("records"), SUMMARY_SCHEMA, true)),
            ConfigurationSchema(
                CONFIGURATION_FIELDS,
                emptySet(),
                mapOf(
                    "url" to ConfigurationSchema.ScalarKind.STRING,
                    "user" to ConfigurationSchema.ScalarKind.STRING,
                    "password" to ConfigurationSchema.ScalarKind.STRING,
                    "maxRows" to ConfigurationSchema.ScalarKind.INTEGER,
                    "maxBytes" to ConfigurationSchema.ScalarKind.INTEGER
                ),
                emptyMap()
            )
        )
    }
}

private object KotlinOrderSummaryQuery {
    private const val SQL = """
        SELECT o.order_id,
               o.ordered_quantity,
               COALESCE(SUM(d.delivered_quantity), 0) AS delivered_quantity,
               ROUND(COALESCE(SUM(d.delivered_quantity), 0)::numeric * 100 / o.ordered_quantity)::bigint AS delivery_percent,
               CASE
                   WHEN COALESCE(SUM(d.delivered_quantity), 0) >= o.ordered_quantity THEN 'COMPLETE'
                   WHEN COALESCE(SUM(d.delivered_quantity), 0) = 0 THEN 'PENDING'
                   ELSE 'PARTIAL'
               END AS status
        FROM orders o
        LEFT JOIN deliveries d ON d.order_id = o.order_id
        GROUP BY o.order_id, o.ordered_quantity
        ORDER BY o.order_id
    """

    fun read(dataAccess: DataAccess, context: QueryContext, limits: DataSetLimits): MaterializedDataSet =
        dataAccess.queryToDataSet(
            context,
            "kotlin-order-summary",
            SQL,
            SUMMARY_SCHEMA,
            mapOf(
                "orderId" to "order_id",
                "orderedQuantity" to "ordered_quantity",
                "deliveredQuantity" to "delivered_quantity",
                "deliveryPercent" to "delivery_percent",
                "status" to "status"
            ),
            limits
        )

}
