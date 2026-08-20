package dev.hogwai.platform.examples.provider.orders;

import dev.hogwai.platform.spi.data.access.DataAccess;
import dev.hogwai.platform.spi.data.access.DataRow;
import dev.hogwai.platform.spi.data.access.QueryContext;
import dev.hogwai.platform.spi.data.access.QueryRequest;
import dev.hogwai.platform.examples.provider.model.SupplyChainData;
import dev.hogwai.platform.examples.provider.model.SupplyChainSchemas;
import dev.hogwai.platform.examples.provider.support.ExecutionSupport;
import dev.hogwai.platform.spi.data.MaterializedDataSet;
import dev.hogwai.platform.spi.data.SchemaRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Explicit reader for the orders source query. */
final class OrdersQuery {

    private static final String SQL = "SELECT order_id, ordered_quantity, required_at, priority "
            + "FROM orders ORDER BY order_id";

    private OrdersQuery() {
    }

    static MaterializedDataSet read(DataAccess dataAccess, QueryContext context) {
        QueryRequest<OrderRow> request = new QueryRequest<>("orders", SQL, Map.of(), (DataRow row) -> {
            ExecutionSupport.checkQuery(context);
            return new OrderRow(row.string("order_id"), row.longValue("ordered_quantity"),
                    row.instant("required_at"), row.string("priority"));
        });
        List<OrderRow> rows = dataAccess.query(request, context);
        ExecutionSupport.checkQuery(context);
        List<SchemaRecord> records = new ArrayList<>();
        for (OrderRow row : rows) {
            ExecutionSupport.checkQuery(context);
            records.add(SupplyChainData.schemaRecord(SupplyChainSchemas.orders(), Map.of(
                    "orderId", row.orderId(), "orderedQuantity", row.orderedQuantity(),
                    "requiredAt", row.requiredAt(), "priority", row.priority())));
        }
        return SupplyChainData.dataSet(SupplyChainSchemas.orders(), "demo-orders", records);
    }

    private record OrderRow(String orderId, long orderedQuantity, java.time.Instant requiredAt, String priority) {
    }
}
