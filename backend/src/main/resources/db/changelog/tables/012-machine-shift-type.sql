--liquibase formatted sql

--changeset ozbio:012-machine-shift-type
CREATE TABLE machine_shift_type (
    machine_id BIGINT NOT NULL,
    shift_type_id BIGINT NOT NULL,
    CONSTRAINT pk_machine_shift_type PRIMARY KEY (machine_id, shift_type_id),
    CONSTRAINT fk_machine_shift_type_machine FOREIGN KEY (machine_id) REFERENCES machine (id),
    CONSTRAINT fk_machine_shift_type_shift FOREIGN KEY (shift_type_id) REFERENCES shift_type (id)
);
COMMENT ON TABLE machine_shift_type IS 'Связь станков с типами смен (многие ко многим)';
COMMENT ON COLUMN machine_shift_type.machine_id IS 'Станок, для которого действует смена';
COMMENT ON COLUMN machine_shift_type.shift_type_id IS 'Тип смены из справочника shift_type';
