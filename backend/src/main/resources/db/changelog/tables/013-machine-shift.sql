--liquibase formatted sql

--changeset ozbio:013-machine-shift
CREATE TABLE machine_shift (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL,
    shift_type_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    closed_at TIMESTAMPTZ,
    CONSTRAINT uq_machine_shift_machine_shift_type_work_date UNIQUE (machine_id, shift_type_id, work_date),
    CONSTRAINT chk_machine_shift_status CHECK (
        status IN ('EXPECTED', 'OPEN', 'OVERDUE', 'CLOSED', 'CLOSED_EMPTY')
    ),
    CONSTRAINT chk_machine_shift_window CHECK (window_end > window_start),
    CONSTRAINT fk_machine_shift_machine FOREIGN KEY (machine_id) REFERENCES machine (id),
    CONSTRAINT fk_machine_shift_shift_type FOREIGN KEY (shift_type_id) REFERENCES shift_type (id)
);
COMMENT ON TABLE machine_shift IS 'Экземпляр смены на станке (календарь для закрытия и мониторинга)';
COMMENT ON COLUMN machine_shift.id IS 'Уникальный идентификатор смены на станке';
COMMENT ON COLUMN machine_shift.machine_id IS 'Станок, для которого запланирована смена';
COMMENT ON COLUMN machine_shift.shift_type_id IS 'Шаблон смены из справочника shift_type';
COMMENT ON COLUMN machine_shift.work_date IS 'Календарный день смены (локальная дата производства)';
COMMENT ON COLUMN machine_shift.window_start IS 'Начало рабочего окна смены';
COMMENT ON COLUMN machine_shift.window_end IS 'Окончание рабочего окна смены';
COMMENT ON COLUMN machine_shift.status IS 'EXPECTED, OPEN, OVERDUE, CLOSED, CLOSED_EMPTY';
COMMENT ON COLUMN machine_shift.closed_at IS 'Момент закрытия смены; NULL — смена ещё не закрыта';
