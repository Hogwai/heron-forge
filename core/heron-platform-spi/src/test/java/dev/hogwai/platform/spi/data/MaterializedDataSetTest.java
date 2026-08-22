package dev.hogwai.platform.spi.data;

import dev.hogwai.platform.spi.error.PlatformErrorCode;
import dev.hogwai.platform.spi.error.PlatformException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterializedDataSetTest {

    private static final FieldId ID = new FieldId("id");

    private static Schema schema() {
        return new Schema("s", 1, List.of(
                new Field(ID, "Identifier", new FieldType.StringType(), false, Optional.empty())), false);
    }

    private static SchemaRecord schemaRecord(Schema schema, String value) {
        return SchemaRecord.of(schema, Map.of(ID, value));
    }

    private static DataSetMetadata metadata(long maxRows, long maxBytes) {
        return new DataSetMetadata("ds", new DataSetLimits(maxRows, maxBytes));
    }

    @Test
    void dataSetLimitsRejectNonPositiveValues() {
        assertThatThrownBy(() -> new DataSetLimits(0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataSetLimits(100, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataSetLimits(-1, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataSetLimits(100, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void materializedDataSetComputesRowCount() {
        Schema s = schema();
        MaterializedDataSet ds = new MaterializedDataSet(s, List.of(schemaRecord(s, "a"), schemaRecord(s, "b")),
                metadata(10, 1000), 100);
        assertThat(ds.rowCount()).isEqualTo(2);
        assertThat(ds.schema().identifier()).isEqualTo("s");
        assertThat(ds.byteEstimate()).isEqualTo(100);
        assertThat(ds.metadata().name()).isEqualTo("ds");
        assertThat(ds.records()).hasSize(2);
    }

    @Test
    void materializedDataSetRejectsNegativeByteEstimate() {
        Schema s = schema();
        List<SchemaRecord> noSchemaRecords = List.of();
        DataSetMetadata meta = metadata(10, 1000);
        assertThatThrownBy(() -> new MaterializedDataSet(s, noSchemaRecords, meta, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void materializedDataSetFailsWhenMaxRowsExceeded() {
        Schema s = schema();
        List<SchemaRecord> schemaRecords = List.of(schemaRecord(s, "a"), schemaRecord(s, "b"));
        DataSetMetadata meta = metadata(1, 1000);
        assertThatThrownBy(() -> new MaterializedDataSet(s, schemaRecords, meta, 100))
                .isInstanceOf(PlatformException.class)
                .hasFieldOrPropertyWithValue("code", PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
    }

    @Test
    void materializedDataSetFailsWhenMaxBytesExceeded() {
        Schema s = schema();
        List<SchemaRecord> schemaRecords = List.of(schemaRecord(s, "a"));
        DataSetMetadata meta = metadata(10, 50);
        assertThatThrownBy(() -> new MaterializedDataSet(s, schemaRecords, meta, 100))
                .isInstanceOf(PlatformException.class)
                .hasFieldOrPropertyWithValue("code", PlatformErrorCode.DATASET_LIMIT_EXCEEDED);
    }

    @Test
    void materializedDataSetRejectsRecordWithDistinctSchema() {
        Schema datasetSchema = schema();
        Schema otherSchema = schema();
        SchemaRecord schemaRecord = schemaRecord(otherSchema, "a");
        List<SchemaRecord> schemaRecords = List.of(schemaRecord);
        DataSetMetadata meta = metadata(10, 1000);
        assertThatThrownBy(() -> new MaterializedDataSet(datasetSchema, schemaRecords, meta, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void materializedDataSetRecordsAreImmutable() {
        Schema s = schema();
        MaterializedDataSet ds = new MaterializedDataSet(s, List.of(schemaRecord(s, "a")), metadata(10, 1000), 100);
        List<SchemaRecord> recordsView = ds.records();
        assertThatThrownBy(recordsView::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
