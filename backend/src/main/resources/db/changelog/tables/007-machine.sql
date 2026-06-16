--liquibase formatted sql

--changeset ozbio:007-machine
CREATE TABLE machine (
    id BIGSERIAL PRIMARY KEY,
    machine_type_id BIGINT NOT NULL,
    CONSTRAINT fk_machine_machine_type FOREIGN KEY (machine_type_id) REFERENCES machine_type (id)
);
COMMENT ON TABLE machine IS 'Конкретные единицы оборудования на производстве';
COMMENT ON COLUMN machine.id IS 'Уникальный идентификатор станка';
COMMENT ON COLUMN machine.machine_type_id IS 'Тип станка, к которому относится оборудование';
