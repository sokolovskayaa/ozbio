--liquibase formatted sql

--changeset ozbio:004-order-detail
CREATE TABLE order_detail (
    order_id BIGINT NOT NULL,
    detail_id BIGINT NOT NULL,
    count INT NOT NULL CHECK (count > 0),
    CONSTRAINT pk_order_detail PRIMARY KEY (order_id, detail_id),
    CONSTRAINT fk_order_detail_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_detail_detail FOREIGN KEY (detail_id) REFERENCES detail (id)
);
COMMENT ON TABLE order_detail IS 'Количество деталей, запрошенных в заказе';
COMMENT ON COLUMN order_detail.order_id IS 'Ссылка на заказ';
COMMENT ON COLUMN order_detail.detail_id IS 'Ссылка на деталь';
COMMENT ON COLUMN order_detail.count IS 'Требуемое количество деталей';
