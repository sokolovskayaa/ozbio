--liquibase formatted sql

--changeset ozbio:003-orders
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    status order_status NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE orders IS 'Заказы на производство деталей и инструментов';
COMMENT ON COLUMN orders.id IS 'Уникальный идентификатор заказа';
COMMENT ON COLUMN orders.status IS 'Текущий статус заказа';
COMMENT ON COLUMN orders.created_at IS 'Дата и время создания заказа';
