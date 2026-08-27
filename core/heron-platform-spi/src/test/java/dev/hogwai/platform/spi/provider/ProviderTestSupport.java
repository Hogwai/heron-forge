package dev.hogwai.platform.spi.provider;

import dev.hogwai.platform.spi.PortId;
import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;

import java.util.List;
import java.util.Optional;

/**
 * Package-private helpers shared by the provider tests.
 */
final class ProviderTestSupport {

    private ProviderTestSupport() {
        // no instances
    }

    static Schema schema(String identifier) {
        return new Schema(identifier, 1, List.of(
                new Field(new FieldId("id"), "Identifier", new FieldType.StringType(), false, Optional.empty())),
                false);
    }

    static PortDescriptor port(String id) {
        return new PortDescriptor(new PortId(id), schema("schema-" + id), true);
    }
}
