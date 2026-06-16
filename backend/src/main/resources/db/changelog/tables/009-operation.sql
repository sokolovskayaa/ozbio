--liquibase formatted sql

--changeset ozbio:009-operation
CREATE TABLE operation (
    id BIGSERIAL PRIMARY KEY,
    detail_id BIGINT NOT NULL,
    duration INTERVAL NOT NULL,
    machine_type_id BIGINT NOT NULL,
    step INT NOT NULL CHECK (step > 0),
    setup_duration INTERVAL NOT NULL DEFAULT '0',
    CONSTRAINT uq_operation_detail_step UNIQUE (detail_id, step),
    CONSTRAINT fk_operation_detail FOREIGN KEY (detail_id) REFERENCES detail (id),
    CONSTRAINT fk_operation_machine_type FOREIGN KEY (machine_type_id) REFERENCES machine_type (id)
);
COMMENT ON TABLE operation IS 'Последовательность операций изготовления детали на станках заданного типа';
COMMENT ON COLUMN operation.id IS 'Уникальный идентификатор операции';
COMMENT ON COLUMN operation.detail_id IS 'Деталь, для которой описана операция';
COMMENT ON COLUMN operation.duration IS 'Длительность обработки одной единицы детали на операции';
COMMENT ON COLUMN operation.machine_type_id IS 'Подходящий тип станка для выполнения операции';
COMMENT ON COLUMN operation.step IS 'Порядковый номер шага в маршруте (1, 2, 3, …)';
COMMENT ON COLUMN operation.setup_duration IS 'Длительность переналадки станка для начала операции';
