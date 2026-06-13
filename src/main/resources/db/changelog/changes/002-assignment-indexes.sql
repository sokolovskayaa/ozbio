--liquibase formatted sql

--changeset ozbio:002-assignment-order-id-idx
CREATE INDEX IF NOT EXISTS idx_assignment_order_id ON assignment (order_id);

--changeset ozbio:002-assignment-machine-planned-end-idx
CREATE INDEX IF NOT EXISTS idx_assignment_machine_planned_end ON assignment (machine_id, planned_end DESC);
