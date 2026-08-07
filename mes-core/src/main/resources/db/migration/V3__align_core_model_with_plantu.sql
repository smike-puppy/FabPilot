CREATE TABLE equipment_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    group_type VARCHAR(32) NOT NULL DEFAULT 'PROCESS',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_group_code UNIQUE (code),
    CONSTRAINT ck_equipment_group_type
        CHECK (group_type IN ('PROCESS', 'MATERIAL', 'GENERAL')),
    CONSTRAINT ck_equipment_group_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equipment_group_member (
    equipment_group_id BIGINT NOT NULL,
    equipment_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (equipment_group_id, equipment_id),
    CONSTRAINT fk_equipment_group_member_group
        FOREIGN KEY (equipment_group_id) REFERENCES equipment_group (id),
    CONSTRAINT fk_equipment_group_member_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE route_step
    ADD COLUMN step_code VARCHAR(64) NULL AFTER route_id,
    ADD COLUMN step_version INT NOT NULL DEFAULT 1 AFTER step_code,
    ADD COLUMN required_equipment_group_id BIGINT NULL AFTER operation_id;

UPDATE route_step
SET step_code = CONCAT('STEP-', LPAD(sequence_no, 4, '0'))
WHERE step_code IS NULL;

ALTER TABLE route_step
    MODIFY COLUMN step_code VARCHAR(64) NOT NULL,
    ADD CONSTRAINT uk_route_step_code UNIQUE (route_id, step_code),
    ADD CONSTRAINT fk_route_step_required_equipment_group
        FOREIGN KEY (required_equipment_group_id) REFERENCES equipment_group (id),
    ADD CONSTRAINT ck_route_step_step_version CHECK (step_version > 0);

ALTER TABLE equipment
    ADD COLUMN up_down_status VARCHAR(8) NOT NULL DEFAULT 'U' AFTER equipment_type,
    ADD COLUMN primary_status VARCHAR(32) NOT NULL DEFAULT 'IDLE' AFTER up_down_status,
    ADD COLUMN last_event_code VARCHAR(64) NULL AFTER status,
    ADD COLUMN last_event_at DATETIME(6) NULL AFTER last_event_code;

UPDATE equipment
SET up_down_status = CASE
        WHEN status IN ('DOWN', 'MAINTENANCE') THEN 'D'
        ELSE 'U'
    END,
    primary_status = CASE status
        WHEN 'RUN' THEN 'PROC'
        WHEN 'DOWN' THEN 'DOWN'
        WHEN 'MAINTENANCE' THEN 'MAINTENANCE'
        ELSE 'IDLE'
    END;

ALTER TABLE equipment
    ADD CONSTRAINT ck_equipment_up_down_status
        CHECK (up_down_status IN ('U', 'D')),
    ADD CONSTRAINT ck_equipment_primary_status
        CHECK (primary_status IN ('IDLE', 'WAIT', 'PROC', 'DOWN', 'MAINTENANCE'));

CREATE INDEX idx_equipment_availability
    ON equipment (production_line_id, up_down_status, primary_status);

ALTER TABLE equipment_operation_capability
    COMMENT = 'Deprecated after V3: use route_step.required_equipment_group_id and equipment_group_member';

CREATE TABLE equipment_event_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    event_category VARCHAR(32) NOT NULL,
    from_up_down_status VARCHAR(8) NULL,
    from_primary_status VARCHAR(32) NULL,
    to_up_down_status VARCHAR(8) NOT NULL,
    to_primary_status VARCHAR(32) NOT NULL,
    requires_reason BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_event_definition_code UNIQUE (event_code),
    CONSTRAINT ck_equipment_event_category
        CHECK (event_category IN ('MANUFACTURING', 'ENGINEERING', 'SYSTEM')),
    CONSTRAINT ck_equipment_event_from_up_down
        CHECK (from_up_down_status IS NULL OR from_up_down_status IN ('U', 'D')),
    CONSTRAINT ck_equipment_event_from_primary
        CHECK (from_primary_status IS NULL OR from_primary_status IN (
            'IDLE', 'WAIT', 'PROC', 'DOWN', 'MAINTENANCE'
        )),
    CONSTRAINT ck_equipment_event_to_up_down
        CHECK (to_up_down_status IN ('U', 'D')),
    CONSTRAINT ck_equipment_event_to_primary
        CHECK (to_primary_status IN ('IDLE', 'WAIT', 'PROC', 'DOWN', 'MAINTENANCE')),
    CONSTRAINT ck_equipment_event_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equipment_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    equipment_id BIGINT NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    up_down_status_before VARCHAR(8) NOT NULL,
    up_down_status_after VARCHAR(8) NOT NULL,
    primary_status_before VARCHAR(32) NOT NULL,
    primary_status_after VARCHAR(32) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(128) NOT NULL,
    operator_role VARCHAR(64) NULL,
    reason_code VARCHAR(64) NULL,
    reason_text VARCHAR(500) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    equipment_version_before BIGINT NOT NULL,
    equipment_version_after BIGINT NOT NULL,
    correlation_id VARCHAR(128) NULL,
    metadata_json JSON NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_history_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_equipment_history_equipment
        FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT fk_equipment_history_event
        FOREIGN KEY (event_code) REFERENCES equipment_event_definition (event_code),
    CONSTRAINT ck_equipment_history_up_down_before
        CHECK (up_down_status_before IN ('U', 'D')),
    CONSTRAINT ck_equipment_history_up_down_after
        CHECK (up_down_status_after IN ('U', 'D')),
    CONSTRAINT ck_equipment_history_primary_before
        CHECK (primary_status_before IN ('IDLE', 'WAIT', 'PROC', 'DOWN', 'MAINTENANCE')),
    CONSTRAINT ck_equipment_history_primary_after
        CHECK (primary_status_after IN ('IDLE', 'WAIT', 'PROC', 'DOWN', 'MAINTENANCE')),
    CONSTRAINT ck_equipment_history_operator_type
        CHECK (operator_type IN ('USER', 'AGENT_PROPOSAL', 'SYSTEM')),
    CONSTRAINT ck_equipment_history_version_order
        CHECK (equipment_version_after > equipment_version_before)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_equipment_history_equipment_occurred_at
    ON equipment_history (equipment_id, occurred_at DESC);

CREATE INDEX idx_equipment_history_correlation_id
    ON equipment_history (correlation_id);

ALTER TABLE lot
    ADD COLUMN last_transaction_code VARCHAR(32) NULL AFTER hold_status,
    ADD COLUMN last_transaction_at DATETIME(6) NULL AFTER last_transaction_code,
    ADD COLUMN last_operator_id VARCHAR(128) NULL AFTER last_transaction_at,
    ADD CONSTRAINT ck_lot_last_transaction_code
        CHECK (last_transaction_code IS NULL OR last_transaction_code IN (
            'CREATE', 'RELEASE', 'TRACK_IN', 'TRACK_OUT',
            'HOLD', 'RELEASE_HOLD', 'FINISH', 'SCRAP'
        ));

CREATE INDEX idx_lot_last_transaction_at
    ON lot (last_transaction_at);
