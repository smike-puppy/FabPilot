CREATE TABLE equipment_alarm_action_history (
 id BIGINT NOT NULL AUTO_INCREMENT, alarm_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL,
 operator_id VARCHAR(128) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
 alarm_version_before BIGINT NOT NULL, alarm_version_after BIGINT NOT NULL, occurred_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id), CONSTRAINT uk_equipment_alarm_action_idem UNIQUE (idempotency_key),
 CONSTRAINT fk_equipment_alarm_action_alarm FOREIGN KEY (alarm_id) REFERENCES equipment_alarm(id),
 CONSTRAINT ck_equipment_alarm_action CHECK (action IN ('ACKNOWLEDGE','CLOSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;