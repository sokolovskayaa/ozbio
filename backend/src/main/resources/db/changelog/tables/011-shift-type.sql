--liquibase formatted sql

--changeset ozbio:011-shift-type
CREATE TABLE shift_type (
    id BIGSERIAL PRIMARY KEY,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT chk_shift_type_time CHECK (start_time <> end_time)
);
COMMENT ON TABLE shift_type IS 'Рабочие смены по дням недели (календарь производства)';
COMMENT ON COLUMN shift_type.id IS 'Уникальный идентификатор записи смены';
COMMENT ON COLUMN shift_type.day_of_week IS 'День недели по ISO-8601: 1 — понедельник, 7 — воскресенье';
COMMENT ON COLUMN shift_type.start_time IS 'Время начала смены (локальное время производства)';
COMMENT ON COLUMN shift_type.end_time IS 'Время окончания смены; если меньше start_time — смена переходит через полночь';

--changeset ozbio:011-shift-type-drop-machine-type
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'shift_type' AND column_name = 'machine_type_id'
ALTER TABLE shift_type DROP CONSTRAINT IF EXISTS fk_shift_type_machine_type;
ALTER TABLE shift_type DROP COLUMN machine_type_id;
