--liquibase formatted sql

--changeset ozbio:014-shift-completion
CREATE TABLE shift_completion (
    machine_shift_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    count INT NOT NULL CHECK (count > 0),
    CONSTRAINT pk_shift_completion PRIMARY KEY (machine_shift_id, operation_id),
    CONSTRAINT fk_shift_completion_machine_shift FOREIGN KEY (machine_shift_id) REFERENCES machine_shift (id),
    CONSTRAINT fk_shift_completion_operation FOREIGN KEY (operation_id) REFERENCES operation (id)
);
COMMENT ON TABLE shift_completion IS 'Факт выполненных операций при закрытии смены';
COMMENT ON COLUMN shift_completion.machine_shift_id IS 'Смена на станке, в которой выполнена работа';
COMMENT ON COLUMN shift_completion.operation_id IS 'Операция маршрута детали';
COMMENT ON COLUMN shift_completion.count IS 'Количество выполненных единиц';
