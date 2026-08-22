package dev.hogwai.platform.examples.provider.deliveries;

import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.spi.data.DataSetLimits;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.QueryContext;
import java.util.Map;

/** Reader for the deliveries source query. */
final class DeliveriesQuery {

    private static final String SQL = "SELECT order_id, delivered_quantity, delivered_at "
            + "FROM deliveries ORDER BY order_id, delivered_at, delivery_id";

    private DeliveriesQuery() {
    }

    static MaterializedDataSet read(DataAccess dataAccess, QueryContext context, DataSetLimits limits) {
        return dataAccess.queryToDataSet(context, "deliveries", SQL, SupplyChainSchemas.deliveries(),
                Map.of("orderId", "order_id", "deliveredQuantity", "delivered_quantity",
                        "deliveredAt", "delivered_at"), limits);
    }
}