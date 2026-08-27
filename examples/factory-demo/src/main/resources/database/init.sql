CREATE TABLE orders
(
    order_id         VARCHAR(64) PRIMARY KEY,
    ordered_quantity BIGINT      NOT NULL CHECK (ordered_quantity > 0),
    required_at      TIMESTAMPTZ NOT NULL,
    priority         VARCHAR(16) NOT NULL CHECK (priority IN ('NORMAL', 'HIGH'))
);

CREATE TABLE deliveries
(
    delivery_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           VARCHAR(64) NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    delivered_quantity BIGINT      NOT NULL CHECK (delivered_quantity >= 0),
    delivered_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX orders_required_at_idx ON orders (required_at);
CREATE INDEX deliveries_order_delivered_at_idx ON deliveries (order_id, delivered_at);

INSERT INTO orders (order_id, ordered_quantity, required_at, priority)
VALUES ('LATE-001', 10, '2025-01-10T00:00:00Z', 'NORMAL'),
       ('SHORT-001', 100, '2025-01-20T00:00:00Z', 'HIGH'),
       ('OK-001', 20, '2025-01-15T00:00:00Z', 'NORMAL');

INSERT INTO deliveries (order_id, delivered_quantity, delivered_at)
VALUES ('LATE-001', 5, '2025-01-11T00:00:00Z'),
       ('LATE-001', 5, '2025-01-12T00:00:00Z'),
       ('SHORT-001', 20, '2025-01-18T00:00:00Z'),
       ('SHORT-001', 30, '2025-01-19T00:00:00Z'),
       ('OK-001', 20, '2025-01-14T00:00:00Z');
