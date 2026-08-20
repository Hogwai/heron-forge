package dev.hogwai.platform.examples.provider.deliveries;

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

/** Explicit reader for the deliveries source query. */
final class DeliveriesQuery {

    private static final String SQL = "SELECT order_id, delivered_quantity, delivered_at "
            + "FROM deliveries ORDER BY order_id, delivered_at, delivery_id";

    private DeliveriesQuery() {
    }

    static MaterializedDataSet read(DataAccess dataAccess, QueryContext context) {
        QueryRequest<DeliveryRow> request = new QueryRequest<>("deliveries", SQL, Map.of(), (DataRow row) -> {
            ExecutionSupport.checkQuery(context);
            return new DeliveryRow(row.string("order_id"), row.longValue("delivered_quantity"),
                    row.instant("delivered_at"));
        });
        List<DeliveryRow> rows = dataAccess.query(request, context);
        ExecutionSupport.checkQuery(context);
        List<SchemaRecord> records = new ArrayList<>();
        for (DeliveryRow row : rows) {
            ExecutionSupport.checkQuery(context);
            records.add(SupplyChainData.schemaRecord(SupplyChainSchemas.deliveries(), Map.of(
                    "orderId", row.orderId(), "deliveredQuantity", row.deliveredQuantity(),
                    "deliveredAt", row.deliveredAt())));
        }
        return SupplyChainData.dataSet(SupplyChainSchemas.deliveries(), "demo-deliveries", records);
    }

    private record DeliveryRow(String orderId, long deliveredQuantity, java.time.Instant deliveredAt) {
    }
}
