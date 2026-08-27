package dev.hogwai.platform.examples.provider.orders;

import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.StreamingDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.QueryContext;

import java.util.Map;

/**
 * Reader for the orders source query.
 */
final class OrdersQuery {

    private static final String SQL = "SELECT order_id, ordered_quantity, required_at, priority "
            + "FROM orders ORDER BY order_id";
    private static final int STREAM_BATCH_SIZE = 256;

    private OrdersQuery() {
    }

    static MaterializedDataSet read(DataAccess dataAccess, QueryContext context, DataSetLimits limits) {
        return dataAccess.queryToDataSet(context, "orders", SQL, SupplyChainSchemas.orders(),
                COLUMNS, limits);
    }

    static StreamingDataSet stream(DataAccess dataAccess, QueryContext context, DataSetLimits limits) {
        return dataAccess.streamQuery(context, "orders", SQL, SupplyChainSchemas.orders(),
                COLUMNS, limits, STREAM_BATCH_SIZE);
    }

    private static final Map<String, String> COLUMNS = Map.of("orderId", "order_id",
            "orderedQuantity", "ordered_quantity", "requiredAt", "required_at", "priority", "priority");
}