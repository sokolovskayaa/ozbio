--liquibase formatted sql

--changeset ozbio:005-order-tool
CREATE TABLE order_tool (
    order_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    count INT NOT NULL CHECK (count > 0),
    CONSTRAINT pk_order_tool PRIMARY KEY (order_id, tool_id),
    CONSTRAINT fk_order_tool_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_tool_tool FOREIGN KEY (tool_id) REFERENCES tool (id)
);
COMMENT ON TABLE order_tool IS 'Количество инструментов, запрошенных в заказе';
COMMENT ON COLUMN order_tool.order_id IS 'Ссылка на заказ';
COMMENT ON COLUMN order_tool.tool_id IS 'Ссылка на инструмент';
COMMENT ON COLUMN order_tool.count IS 'Требуемое количество инструментов';
