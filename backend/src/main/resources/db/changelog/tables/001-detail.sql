--liquibase formatted sql

--changeset ozbio:001-detail
CREATE TABLE detail (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);
COMMENT ON TABLE detail IS 'Справочник деталей, производимых на заводе';
COMMENT ON COLUMN detail.id IS 'Уникальный идентификатор детали';
COMMENT ON COLUMN detail.name IS 'Наименование детали';
