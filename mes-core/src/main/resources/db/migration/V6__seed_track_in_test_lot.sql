-- 为 Track In 接口提供独立的 READY 状态测试 Lot，避免修改诊断场景 LOT-014。
INSERT INTO lot (
    code, work_order_id, route_id, current_route_step_id, quantity,
    execution_status, hold_status, last_transaction_code,
    last_transaction_at, last_operator_id, version
)
SELECT 'LOT-016', work_order.id, route.id, step.id, 20,
       'READY', 'RELEASED', 'RELEASE',
       CURRENT_TIMESTAMP(6), 'SYSTEM-SEED', 0
FROM work_order
JOIN route ON route.code = 'FLOW-OLED-A' AND route.revision = 1
JOIN route_step step
    ON step.route_id = route.id
   AND step.step_code = 'STEP-ETCH-020'
WHERE work_order.code = 'WO-2026-008';