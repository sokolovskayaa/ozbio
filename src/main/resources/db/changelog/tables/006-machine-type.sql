--liquibase formatted sql

--changeset ozbio:006-machine-type
CREATE TABLE machine_type (
    id BIGSERIAL PRIMARY KEY,
    type_name TEXT NOT NULL
);
COMMENT ON TABLE machine_type IS 'Справочник типов производственного оборудования';
COMMENT ON COLUMN machine_type.id IS 'Уникальный идентификатор типа станка';
COMMENT ON COLUMN machine_type.type_name IS 'Наименование типа станка';
