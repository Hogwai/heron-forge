package dev.hogwai.platform.examples.provider.model;

import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyChainDataTest {

    @Test
    void createsRecordsAndReadsTypedValues() {
        Instant requiredAt = Instant.parse("2025-01-10T00:00:00Z");
        SchemaRecord schemaRecord = SupplyChainData.schemaRecord(SupplyChainSchemas.orders(), Map.of(
                "orderId", "ORDER-001", "orderedQuantity", 12L,
                "requiredAt", requiredAt, "priority", "HIGH"));

        assertThat(schemaRecord.value(new FieldId("orderId"))).isEqualTo("ORDER-001");
        assertThat(SupplyChainData.string(schemaRecord, "orderId")).isEqualTo("ORDER-001");
        assertThat(SupplyChainData.longValue(schemaRecord, "orderedQuantity")).isEqualTo(12L);
        assertThat(SupplyChainData.instant(schemaRecord, "requiredAt")).isEqualTo(requiredAt);
    }

    @Test
    void createsDatasetsWithTheSourceLimitsAndMetadata() {
        SchemaRecord schemaRecord = SupplyChainData.schemaRecord(SupplyChainSchemas.deliveries(), Map.of(
                "orderId", "ORDER-001", "deliveredQuantity", 12L,
                "deliveredAt", Instant.parse("2025-01-10T00:00:00Z")));

        MaterializedDataSet dataSet = SupplyChainData.dataSet(
                SupplyChainSchemas.deliveries(), "demo-deliveries", List.of(schemaRecord));

        assertThat(dataSet.schema()).isSameAs(SupplyChainSchemas.deliveries());
        assertThat(dataSet.records()).containsExactly(schemaRecord);
        assertThat(dataSet.rowCount()).isEqualTo(1);
        assertThat(dataSet.byteEstimate()).isEqualTo(256L);
        assertThat(dataSet.metadata().name()).isEqualTo("demo-deliveries");
        assertThat(dataSet.metadata().limits().maxRows()).isEqualTo(1_000);
        assertThat(dataSet.metadata().limits().maxBytes()).isEqualTo(1_000_000);
    }
}
