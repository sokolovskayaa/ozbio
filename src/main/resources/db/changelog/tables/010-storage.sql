--liquibase formatted sql

--changeset ozbio:010-storage
CREATE TABLE storage (
    operation_id BIGINT NOT NULL,
    count INT NOT NULL,
    CONSTRAINT pk_storage PRIMARY KEY (operation_id),
    CONSTRAINT fk_storage_operation FOREIGN KEY (operation_id) REFERENCES operation (id)
);
COMMENT ON TABLE storage IS 'Склад промежуточных заготовок после операции';
COMMENT ON COLUMN storage.operation_id IS 'Операция (шаг маршрута), для которой учитывается остаток';
COMMENT ON COLUMN storage.count IS 'Количество заготовок на складе после операции';
