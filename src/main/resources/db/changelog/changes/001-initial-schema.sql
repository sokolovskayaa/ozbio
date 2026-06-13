--liquibase formatted sql

--changeset ozbio:001-factory-state
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'factory_state'
CREATE TABLE factory_state (
    id SMALLINT PRIMARY KEY,
    factory_started_at TIMESTAMPTZ NOT NULL
);

--changeset ozbio:001-machine-group
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'machine_group'
CREATE TABLE machine_group (
    group_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    setup_minutes INT NOT NULL
);

--changeset ozbio:001-machine
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'machine'
CREATE TABLE machine (
    machine_id VARCHAR(64) PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_machine_group FOREIGN KEY (group_id) REFERENCES machine_group (group_id)
);

--changeset ozbio:001-machine-capability
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'machine_capability'
CREATE TABLE machine_capability (
    machine_id VARCHAR(64) NOT NULL,
    capability VARCHAR(32) NOT NULL,
    CONSTRAINT fk_capability_machine FOREIGN KEY (machine_id) REFERENCES machine (machine_id),
    CONSTRAINT pk_machine_capability PRIMARY KEY (machine_id, capability)
);

--changeset ozbio:001-part-definition
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'part_definition'
CREATE TABLE part_definition (
    part_id VARCHAR(128) PRIMARY KEY,
    priority INT NOT NULL
);

--changeset ozbio:001-part-task
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'part_task'
CREATE TABLE part_task (
    part_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    sequence INT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    required_capability VARCHAR(32) NOT NULL,
    CONSTRAINT fk_part_task_definition FOREIGN KEY (part_id) REFERENCES part_definition (part_id),
    CONSTRAINT pk_part_task PRIMARY KEY (part_id, task_id)
);

--changeset ozbio:001-schedule-order
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'schedule_order'
CREATE TABLE schedule_order (
    order_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    priority INT NOT NULL
);

--changeset ozbio:001-order-part
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'order_part'
CREATE TABLE order_part (
    order_id VARCHAR(64) NOT NULL,
    part_id VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    CONSTRAINT fk_order_part_order FOREIGN KEY (order_id) REFERENCES schedule_order (order_id),
    CONSTRAINT pk_order_part PRIMARY KEY (order_id, part_id)
);

--changeset ozbio:001-order-part-task
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'order_part_task'
CREATE TABLE order_part_task (
    order_id VARCHAR(64) NOT NULL,
    part_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    sequence INT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    required_capability VARCHAR(32) NOT NULL,
    CONSTRAINT pk_order_part_task PRIMARY KEY (order_id, part_id, task_id),
    CONSTRAINT fk_order_part_task_order_part FOREIGN KEY (order_id, part_id)
        REFERENCES order_part (order_id, part_id)
);

--changeset ozbio:001-assignment
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'assignment'
CREATE TABLE assignment (
    assignment_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    part_id VARCHAR(128) NOT NULL,
    unit_index INT NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    sequence INT NOT NULL,
    machine_id VARCHAR(64) NOT NULL,
    planned_start TIMESTAMPTZ NOT NULL,
    planned_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    actual_start TIMESTAMPTZ,
    actual_end TIMESTAMPTZ,
    CONSTRAINT fk_assignment_order FOREIGN KEY (order_id) REFERENCES schedule_order (order_id),
    CONSTRAINT fk_assignment_machine FOREIGN KEY (machine_id) REFERENCES machine (machine_id)
);
