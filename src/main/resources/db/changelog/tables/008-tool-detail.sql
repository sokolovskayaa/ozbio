--liquibase formatted sql

--changeset ozbio:008-tool-detail
CREATE TABLE tool_detail (
    tool_id BIGINT NOT NULL,
    detail_id BIGINT NOT NULL,
    count INT NOT NULL CHECK (count > 0),
    CONSTRAINT pk_tool_detail PRIMARY KEY (tool_id, detail_id),
    CONSTRAINT fk_tool_detail_tool FOREIGN KEY (tool_id) REFERENCES tool (id),
    CONSTRAINT fk_tool_detail_detail FOREIGN KEY (detail_id) REFERENCES detail (id)
);
COMMENT ON TABLE tool_detail IS 'Спецификация: какие детали и в каком количестве нужны для сборки одного инструмента';
COMMENT ON COLUMN tool_detail.tool_id IS 'Ссылка на инструмент';
COMMENT ON COLUMN tool_detail.detail_id IS 'Ссылка на входящую деталь';
COMMENT ON COLUMN tool_detail.count IS 'Количество деталей на один инструмент';
