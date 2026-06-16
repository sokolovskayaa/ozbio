--liquibase formatted sql

--changeset ozbio:000-order-status-enum
CREATE TYPE order_status AS ENUM ('CREATED', 'PLANNED', 'COMPLETED', 'CANCELLED');
COMMENT ON TYPE order_status IS 'Статус заказа в жизненном цикле планирования и производства';
