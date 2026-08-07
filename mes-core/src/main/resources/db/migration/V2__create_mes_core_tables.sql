CREATE TABLE factory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_factory_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE production_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_production_line_factory_code UNIQUE (factory_id, code),
    CONSTRAINT fk_production_line_factory
        FOREIGN KEY (factory_id) REFERENCES factory (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    standard_cycle_seconds INT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_operation_code UNIQUE (code),
    CONSTRAINT ck_operation_cycle_seconds
        CHECK (standard_cycle_seconds IS NULL OR standard_cycle_seconds > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equipment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_line_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    equipment_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_code UNIQUE (code),
    CONSTRAINT fk_equipment_production_line
        FOREIGN KEY (production_line_id) REFERENCES production_line (id),
    CONSTRAINT ck_equipment_status
        CHECK (status IN ('IDLE', 'RUN', 'DOWN', 'MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equipment_operation_capability (
    equipment_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (equipment_id, operation_id),
    CONSTRAINT fk_equipment_capability_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_equipment_capability_operation
        FOREIGN KEY (operation_id) REFERENCES operation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE route (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    effective_from DATETIME(6) NULL,
    effective_to DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_route_product_code_revision UNIQUE (product_id, code, revision),
    CONSTRAINT fk_route_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_route_revision CHECK (revision > 0),
    CONSTRAINT ck_route_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_route_effective_range
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE route_step (
    id BIGINT NOT NULL AUTO_INCREMENT,
    route_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    name VARCHAR(128) NULL,
    standard_cycle_seconds INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_route_step_sequence UNIQUE (route_id, sequence_no),
    CONSTRAINT fk_route_step_route
        FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT fk_route_step_operation
        FOREIGN KEY (operation_id) REFERENCES operation (id),
    CONSTRAINT ck_route_step_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_route_step_cycle_seconds
        CHECK (standard_cycle_seconds IS NULL OR standard_cycle_seconds > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    plan_quantity INT NOT NULL,
    due_at DATETIME(6) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    released_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_work_order_code UNIQUE (code),
    CONSTRAINT fk_work_order_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_work_order_plan_quantity CHECK (plan_quantity > 0),
    CONSTRAINT ck_work_order_status
        CHECK (status IN ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_work_order_status_due_at ON work_order (status, due_at);

CREATE TABLE lot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    work_order_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    current_route_step_id BIGINT NULL,
    current_equipment_id BIGINT NULL,
    quantity INT NOT NULL,
    execution_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    hold_status VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
    version BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lot_code UNIQUE (code),
    CONSTRAINT uk_lot_current_equipment UNIQUE (current_equipment_id),
    CONSTRAINT fk_lot_work_order
        FOREIGN KEY (work_order_id) REFERENCES work_order (id),
    CONSTRAINT fk_lot_route
        FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT fk_lot_current_route_step
        FOREIGN KEY (current_route_step_id) REFERENCES route_step (id),
    CONSTRAINT fk_lot_current_equipment
        FOREIGN KEY (current_equipment_id) REFERENCES equipment (id),
    CONSTRAINT ck_lot_quantity CHECK (quantity > 0),
    CONSTRAINT ck_lot_execution_status
        CHECK (execution_status IN ('CREATED', 'READY', 'RUNNING', 'COMPLETED', 'SCRAPPED')),
    CONSTRAINT ck_lot_hold_status
        CHECK (hold_status IN ('RELEASED', 'HELD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_lot_work_order_status
    ON lot (work_order_id, execution_status, hold_status);

CREATE TABLE lot_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lot_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    route_step_id BIGINT NULL,
    operation_id BIGINT NULL,
    equipment_id BIGINT NULL,
    execution_status_before VARCHAR(32) NULL,
    execution_status_after VARCHAR(32) NOT NULL,
    hold_status_before VARCHAR(32) NULL,
    hold_status_after VARCHAR(32) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    reason_text VARCHAR(500) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    lot_version_before BIGINT NULL,
    lot_version_after BIGINT NOT NULL,
    correlation_id VARCHAR(128) NULL,
    metadata_json JSON NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lot_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_lot_transaction_lot
        FOREIGN KEY (lot_id) REFERENCES lot (id),
    CONSTRAINT fk_lot_transaction_route_step
        FOREIGN KEY (route_step_id) REFERENCES route_step (id),
    CONSTRAINT fk_lot_transaction_operation
        FOREIGN KEY (operation_id) REFERENCES operation (id),
    CONSTRAINT fk_lot_transaction_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT ck_lot_transaction_type
        CHECK (transaction_type IN (
            'CREATE', 'RELEASE', 'TRACK_IN', 'TRACK_OUT',
            'HOLD', 'RELEASE_HOLD', 'FINISH', 'SCRAP'
        )),
    CONSTRAINT ck_lot_transaction_execution_status_before
        CHECK (execution_status_before IS NULL OR execution_status_before IN (
            'CREATED', 'READY', 'RUNNING', 'COMPLETED', 'SCRAPPED'
        )),
    CONSTRAINT ck_lot_transaction_execution_status_after
        CHECK (execution_status_after IN ('CREATED', 'READY', 'RUNNING', 'COMPLETED', 'SCRAPPED')),
    CONSTRAINT ck_lot_transaction_hold_status_before
        CHECK (hold_status_before IS NULL OR hold_status_before IN ('RELEASED', 'HELD')),
    CONSTRAINT ck_lot_transaction_hold_status_after
        CHECK (hold_status_after IN ('RELEASED', 'HELD')),
    CONSTRAINT ck_lot_transaction_operator_type
        CHECK (operator_type IN ('USER', 'AGENT_PROPOSAL', 'SYSTEM')),
    CONSTRAINT ck_lot_transaction_version_order
        CHECK (lot_version_before IS NULL OR lot_version_after > lot_version_before)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_lot_transaction_lot_occurred_at
    ON lot_transaction (lot_id, occurred_at DESC);

CREATE INDEX idx_lot_transaction_correlation_id
    ON lot_transaction (correlation_id);
