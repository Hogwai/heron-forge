package dev.hogwai.platform.examples.provider.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplyChainDataTest {

    @Test
    void createsRecordsAndReadsTypedValues() {
        Instant requiredAt = Instant.parse("2025-01-10T00:00:00Z");
        SchemaRecord record = SupplyChainData.schemaRecord(SupplyChainSchemas.orders(), Map.of(
                "orderId", "ORDER-001", "orderedQuantity", 12L,
                "requiredAt", requiredAt, "priority", "HIGH"));

        assertThat(record.value(new FieldId("orderId"))).isEqualTo("ORDER-001");
        assertThat(SupplyChainData.string(record, "orderId")).isEqualTo("ORDER-001");
        assertThat(SupplyChainData.longValue(record, "orderedQuantity")).isEqualTo(12L);
        assertThat(SupplyChainData.instant(record, "requiredAt")).isEqualTo(requiredAt);
    }

    @Test
    void createsDatasetsWithTheSourceLimitsAndMetadata() {
        SchemaRecord record = SupplyChainData.schemaRecord(SupplyChainSchemas.deliveries(), Map.of(
                "orderId", "ORDER-001", "deliveredQuantity", 12L,
                "deliveredAt", Instant.parse("2025-01-10T00:00:00Z")));

        MaterializedDataSet dataSet = SupplyChainData.dataSet(
                SupplyChainSchemas.deliveries(), "demo-deliveries", List.of(record));

        assertThat(dataSet.schema()).isSameAs(SupplyChainSchemas.deliveries());
        assertThat(dataSet.records()).containsExactly(record);
        assertThat(dataSet.rowCount()).isEqualTo(1);
        assertThat(dataSet.byteEstimate()).isEqualTo(256L);
        assertThat(dataSet.metadata().name()).isEqualTo("demo-deliveries");
        assertThat(dataSet.metadata().limits().maxRows()).isEqualTo(1_000);
        assertThat(dataSet.metadata().limits().maxBytes()).isEqualTo(1_000_000);
    }
}
