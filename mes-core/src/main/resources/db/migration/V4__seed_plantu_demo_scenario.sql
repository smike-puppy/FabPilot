INSERT INTO factory (code, name, description)
VALUES ('FAB-01', 'FabPilot 示范工厂', 'PlantU 风格的轻量 MES 演示工厂');

INSERT INTO production_line (factory_id, code, name)
SELECT id, 'LINE-OLED-01', 'OLED 面板示范产线'
FROM factory WHERE code = 'FAB-01';

INSERT INTO product (code, name, description)
VALUES ('OLED-PANEL-A', 'OLED 面板 A 型号', 'FabPilot 异常诊断演示产品');

INSERT INTO operation (code, name, standard_cycle_seconds)
VALUES ('CLEAN', '清洗', 300), ('ETCH', '刻蚀', 600), ('INSPECT', '检测', 240);

INSERT INTO equipment_group (code, name, group_type, description)
VALUES
    ('EQP-GRP-CLEAN', '清洗设备能力组', 'PROCESS', '可执行 CLEAN Step 的设备'),
    ('EQP-GRP-ETCH', '刻蚀设备能力组', 'PROCESS', '可执行 ETCH Step 的设备'),
    ('EQP-GRP-INSPECT', '检测设备能力组', 'PROCESS', '可执行 INSPECT Step 的设备');

INSERT INTO equipment (
    production_line_id, code, name, equipment_type, status,
    up_down_status, primary_status, last_event_code, last_event_at, version
)
SELECT line.id, seed.code, seed.name, seed.equipment_type, seed.status,
       seed.up_down_status, seed.primary_status, seed.last_event_code,
       seed.last_event_at, seed.version
FROM production_line line
JOIN (
    SELECT 'CLEAN-01' AS code, '清洗机 01' AS name, 'CLEANER' AS equipment_type,
           'IDLE' AS status, 'U' AS up_down_status, 'IDLE' AS primary_status,
           NULL AS last_event_code, NULL AS last_event_at, 0 AS version
    UNION ALL SELECT 'ETCH-01', '刻蚀机 01', 'ETCHER', 'DOWN', 'D', 'DOWN',
           'VACUUM_LOW', TIMESTAMP('2026-08-06 09:25:00'), 2
    UNION ALL SELECT 'ETCH-02', '刻蚀机 02', 'ETCHER', 'IDLE', 'U', 'IDLE',
           NULL, NULL, 0
    UNION ALL SELECT 'INSPECT-01', '检测机 01', 'INSPECTOR', 'IDLE', 'U', 'IDLE',
           NULL, NULL, 0
) seed ON 1 = 1
WHERE line.code = 'LINE-OLED-01';

INSERT INTO equipment_group_member (equipment_group_id, equipment_id)
SELECT grp.id, eqp.id
FROM equipment_group grp
JOIN equipment eqp ON (
    (grp.code = 'EQP-GRP-CLEAN' AND eqp.code = 'CLEAN-01') OR
    (grp.code = 'EQP-GRP-ETCH' AND eqp.code IN ('ETCH-01', 'ETCH-02')) OR
    (grp.code = 'EQP-GRP-INSPECT' AND eqp.code = 'INSPECT-01')
);

INSERT INTO route (product_id, code, name, revision, status, effective_from)
SELECT id, 'FLOW-OLED-A', 'OLED 面板 A 标准流程', 1, 'PUBLISHED', TIMESTAMP('2026-08-01 00:00:00')
FROM product WHERE code = 'OLED-PANEL-A';

INSERT INTO route_step (
    route_id, step_code, step_version, operation_id, required_equipment_group_id,
    sequence_no, name, standard_cycle_seconds
)
SELECT route.id, seed.step_code, 1, operation.id, equipment_group.id,
       seed.sequence_no, seed.name, seed.standard_cycle_seconds
FROM route
JOIN (
    SELECT 'STEP-CLEAN-010' AS step_code, 'CLEAN' AS operation_code,
           'EQP-GRP-CLEAN' AS equipment_group_code, 10 AS sequence_no,
           '清洗站' AS name, 300 AS standard_cycle_seconds
    UNION ALL SELECT 'STEP-ETCH-020', 'ETCH', 'EQP-GRP-ETCH', 20, '刻蚀站', 600
    UNION ALL SELECT 'STEP-INSPECT-030', 'INSPECT', 'EQP-GRP-INSPECT', 30, '检测站', 240
) seed ON 1 = 1
JOIN operation ON operation.code = seed.operation_code
JOIN equipment_group ON equipment_group.code = seed.equipment_group_code
WHERE route.code = 'FLOW-OLED-A' AND route.revision = 1;

INSERT INTO equipment_event_definition (
    event_code, name, event_category, from_up_down_status, from_primary_status,
    to_up_down_status, to_primary_status, requires_reason
)
VALUES
    ('TRACK_IN', 'Lot 上机', 'MANUFACTURING', 'U', 'IDLE', 'U', 'PROC', FALSE),
    ('TRACK_OUT', 'Lot 下机', 'MANUFACTURING', 'U', 'PROC', 'U', 'IDLE', FALSE),
    ('VACUUM_LOW', '真空不足报警', 'ENGINEERING', 'U', 'PROC', 'D', 'DOWN', TRUE),
    ('START_MAINTENANCE', '开始维修', 'ENGINEERING', 'D', 'DOWN', 'D', 'MAINTENANCE', TRUE),
    ('COMPLETE_MAINTENANCE', '维修完成', 'ENGINEERING', 'D', 'MAINTENANCE', 'U', 'IDLE', TRUE);

INSERT INTO work_order (code, product_id, plan_quantity, due_at, status, released_at, version)
SELECT 'WO-2026-008', id, 100, TIMESTAMP('2026-08-10 18:00:00'),
       'IN_PROGRESS', TIMESTAMP('2026-08-05 08:00:00'), 0
FROM product WHERE code = 'OLED-PANEL-A';

INSERT INTO lot (
    code, work_order_id, route_id, current_route_step_id, current_equipment_id,
    quantity, execution_status, hold_status, last_transaction_code,
    last_transaction_at, last_operator_id, version, started_at
)
SELECT 'LOT-013', work_order.id, route.id, step.id, equipment.id,
       20, 'RUNNING', 'HELD', 'HOLD', TIMESTAMP('2026-08-06 09:25:00'),
       'ENG-001', 4, TIMESTAMP('2026-08-06 08:00:00')
FROM work_order
JOIN route ON route.code = 'FLOW-OLED-A' AND route.revision = 1
JOIN route_step step ON step.route_id = route.id AND step.step_code = 'STEP-ETCH-020'
JOIN equipment ON equipment.code = 'ETCH-01'
WHERE work_order.code = 'WO-2026-008';

INSERT INTO lot (
    code, work_order_id, route_id, current_route_step_id, quantity,
    execution_status, hold_status, last_transaction_code,
    last_transaction_at, last_operator_id, version
)
SELECT 'LOT-014', work_order.id, route.id, step.id, 20,
       'READY', 'RELEASED', 'TRACK_OUT', TIMESTAMP('2026-08-06 09:10:00'),
       'OP-001', 2
FROM work_order
JOIN route ON route.code = 'FLOW-OLED-A' AND route.revision = 1
JOIN route_step step ON step.route_id = route.id AND step.step_code = 'STEP-ETCH-020'
WHERE work_order.code = 'WO-2026-008';

INSERT INTO lot_transaction (
    lot_id, transaction_type, route_step_id, operation_id, equipment_id,
    execution_status_before, execution_status_after, hold_status_before, hold_status_after,
    operator_type, operator_id, reason_code, reason_text, idempotency_key,
    lot_version_before, lot_version_after, correlation_id, occurred_at
)
SELECT lot.id, seed.transaction_type, step.id, operation.id, equipment.id,
       seed.execution_status_before, seed.execution_status_after,
       seed.hold_status_before, seed.hold_status_after,
       seed.operator_type, seed.operator_id, seed.reason_code, seed.reason_text,
       seed.idempotency_key, seed.lot_version_before, seed.lot_version_after,
       'TRACE-LOT-013-20260806', seed.occurred_at
FROM lot
JOIN route_step step ON step.id = lot.current_route_step_id
JOIN operation ON operation.id = step.operation_id
LEFT JOIN equipment ON equipment.code = 'ETCH-01'
JOIN (
    SELECT 'CREATE' AS transaction_type, NULL AS execution_status_before, 'CREATED' AS execution_status_after,
           NULL AS hold_status_before, 'RELEASED' AS hold_status_after,
           'USER' AS operator_type, 'OP-001' AS operator_id, NULL AS reason_code,
           NULL AS reason_text, 'IDEMP-LOT-013-CREATE' AS idempotency_key,
           NULL AS lot_version_before, 0 AS lot_version_after,
           TIMESTAMP('2026-08-05 08:10:00') AS occurred_at
    UNION ALL
    SELECT 'RELEASE', 'CREATED', 'READY', 'RELEASED', 'RELEASED', 'USER', 'OP-001',
           NULL, NULL, 'IDEMP-LOT-013-RELEASE', 0, 1, TIMESTAMP('2026-08-05 08:15:00')
    UNION ALL
    SELECT 'TRACK_IN', 'READY', 'RUNNING', 'RELEASED', 'RELEASED', 'USER', 'OP-001',
           NULL, NULL, 'IDEMP-LOT-013-TRACK-IN-ETCH', 1, 2, TIMESTAMP('2026-08-06 09:00:00')
    UNION ALL
    SELECT 'HOLD', 'RUNNING', 'RUNNING', 'RELEASED', 'HELD', 'SYSTEM', 'ENG-001',
           'VACUUM_LOW', 'ETCH-01 发生真空不足报警，自动 Hold Lot',
           'IDEMP-LOT-013-HOLD-VACUUM', 2, 4, TIMESTAMP('2026-08-06 09:25:00')
) seed ON 1 = 1
WHERE lot.code = 'LOT-013';

INSERT INTO equipment_history (
    equipment_id, event_code, up_down_status_before, up_down_status_after,
    primary_status_before, primary_status_after, operator_type, operator_id,
    operator_role, reason_code, reason_text, idempotency_key,
    equipment_version_before, equipment_version_after, correlation_id, occurred_at
)
SELECT equipment.id, seed.event_code, seed.up_down_status_before, seed.up_down_status_after,
       seed.primary_status_before, seed.primary_status_after, seed.operator_type,
       seed.operator_id, seed.operator_role, seed.reason_code, seed.reason_text,
       seed.idempotency_key, seed.equipment_version_before, seed.equipment_version_after,
       'TRACE-LOT-013-20260806', seed.occurred_at
FROM equipment
JOIN (
    SELECT 'TRACK_IN' AS event_code, 'U' AS up_down_status_before, 'U' AS up_down_status_after,
           'IDLE' AS primary_status_before, 'PROC' AS primary_status_after,
           'USER' AS operator_type, 'OP-001' AS operator_id, 'MANUFACTURING' AS operator_role,
           NULL AS reason_code, NULL AS reason_text, 'IDEMP-EQP-ETCH-01-TRACK-IN' AS idempotency_key,
           0 AS equipment_version_before, 1 AS equipment_version_after,
           TIMESTAMP('2026-08-06 09:00:00') AS occurred_at
    UNION ALL
    SELECT 'VACUUM_LOW', 'U', 'D', 'PROC', 'DOWN', 'SYSTEM', 'ENG-001', 'ENGINEERING',
           'VACUUM_LOW', 'ETCH-01 真空值低于下限', 'IDEMP-EQP-ETCH-01-VACUUM',
           1, 2, TIMESTAMP('2026-08-06 09:25:00')
) seed ON 1 = 1
WHERE equipment.code = 'ETCH-01';
