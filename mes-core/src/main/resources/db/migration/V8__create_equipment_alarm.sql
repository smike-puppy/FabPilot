-- 设备告警是对异常处理过程的持续跟踪记录，不等同于一次性的 EquipmentHistory。
-- 本阶段先支持故障事件创建 ACTIVE 告警；后续确认、关闭都在同一记录上推进状态和版本。
CREATE TABLE equipment_alarm (
    id BIGINT NOT NULL AUTO_INCREMENT,
    equipment_id BIGINT NOT NULL,
    alarm_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source_event_code VARCHAR(64) NOT NULL,
    source_idempotency_key VARCHAR(128) NOT NULL,
    message VARCHAR(500) NOT NULL,
    acknowledged_by VARCHAR(128) NULL,
    acknowledged_at DATETIME(6) NULL,
    closed_by VARCHAR(128) NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_alarm_source_key UNIQUE (source_idempotency_key),
    CONSTRAINT fk_equipment_alarm_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
    CONSTRAINT ck_equipment_alarm_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_equipment_alarm_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_equipment_alarm_equipment_status
    ON equipment_alarm (equipment_id, status, opened_at DESC);