--liquibase formatted sql

--changeset ozbio:002-tool
CREATE TABLE tool (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    assemble_duration INTERVAL NOT NULL
);
COMMENT ON TABLE tool IS 'Справочник инструментов, собираемых из деталей';
COMMENT ON COLUMN tool.id IS 'Уникальный идентификатор инструмента';
COMMENT ON COLUMN tool.name IS 'Наименование инструмента';
COMMENT ON COLUMN tool.assemble_duration IS 'Длительность сборки одной единицы инструмента';
