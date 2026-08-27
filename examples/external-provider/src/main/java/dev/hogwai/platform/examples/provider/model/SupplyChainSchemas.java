package dev.hogwai.platform.examples.provider.model;

import dev.hogwai.platform.spi.data.Field;
import dev.hogwai.platform.spi.data.FieldId;
import dev.hogwai.platform.spi.data.FieldType;
import dev.hogwai.platform.spi.data.Schema;

import java.util.List;
import java.util.Optional;

/**
 * Schemas shared by the deterministic supply-chain example providers.
 */
public final class SupplyChainSchemas {

    private static final Schema ORDERS = createOrders();
    private static final Schema DELIVERIES = createDeliveries();
    private static final Schema EXCEPTIONS = createExceptions();
    public static final String ORDER_ID = "orderId";

    private SupplyChainSchemas() {
    }

    /**
     * Returns the orders records schema.
     *
     * @return the orders records schema
     */
    public static Schema orders() {
        return ORDERS;
    }

    private static Schema createOrders() {
        return new Schema("supply-chain.orders", 1, List.of(
                field(ORDER_ID, new FieldType.StringType()),
                field("orderedQuantity", new FieldType.Int64Type()),
                field("requiredAt", new FieldType.InstantType()),
                field("priority", new FieldType.StringType())), false);
    }

    /**
     * Returns the deliveries records schema.
     *
     * @return the deliveries records schema
     */
    public static Schema deliveries() {
        return DELIVERIES;
    }

    private static Schema createDeliveries() {
        return new Schema("supply-chain.deliveries", 1, List.of(
                field(ORDER_ID, new FieldType.StringType()),
                field("deliveredQuantity", new FieldType.Int64Type()),
                field("deliveredAt", new FieldType.InstantType())), false);
    }

    /**
     * Returns the exception records schema.
     *
     * @return the exception records schema
     */
    public static Schema exceptions() {
        return EXCEPTIONS;
    }

    private static Schema createExceptions() {
        return new Schema("supply-chain.exceptions", 1, List.of(
                field(ORDER_ID, new FieldType.StringType()),
                field("exceptionType", new FieldType.StringType()),
                field("severity", new FieldType.StringType()),
                field("reason", new FieldType.StringType()),
                field("recommendedAction", new FieldType.StringType())), false);
    }

    private static Field field(String id, FieldType type) {
        return new Field(new FieldId(id), id, type, false, Optional.empty());
    }
}
