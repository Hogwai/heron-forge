package dev.hogwai.platform.spi.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSetMetadataTest {

    private static final DataSetLimits LIMITS = new DataSetLimits(100, 1000);

    @Test
    void dataSetMetadataExposesAccessors() {
        DataSetMetadata metadata = new DataSetMetadata("ds", LIMITS);
        assertThat(metadata.name()).isEqualTo("ds");
        assertThat(metadata.limits()).isSameAs(LIMITS);
    }

    @Test
    void dataSetMetadataRejectsNullName() {
        assertThatThrownBy(() -> new DataSetMetadata(null, LIMITS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name must not be null");
    }

    @Test
    void dataSetMetadataRejectsBlankName() {
        assertThatThrownBy(() -> new DataSetMetadata(" ", LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }

    @Test
    void dataSetMetadataRejectsNullLimits() {
        assertThatThrownBy(() -> new DataSetMetadata("ds", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("limits must not be null");
    }

    @Test
    void dataSetMetadataEqualityHashCodeAndToString() {
        DataSetMetadata metadata = new DataSetMetadata("ds", LIMITS);
        DataSetMetadata same = new DataSetMetadata("ds", LIMITS);
        DataSetMetadata otherName = new DataSetMetadata("other", LIMITS);
        DataSetMetadata otherLimits = new DataSetMetadata("ds", new DataSetLimits(200, 2000));

        assertThat(metadata).isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(otherName)
                .isNotEqualTo(otherLimits)
                .hasToString("DataSetMetadata[name=ds, limits=" + LIMITS + "]");
    }
}