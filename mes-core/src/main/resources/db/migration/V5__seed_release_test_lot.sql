-- 为 Release 接口提供独立、可重复创建的 CREATED 状态测试 Lot。
INSERT INTO lot (
    code, work_order_id, route_id, quantity,
    execution_status, hold_status, last_transaction_code,
    last_transaction_at, last_operator_id, version
)
SELECT 'LOT-015', work_order.id, route.id, 20,
       'CREATED', 'RELEASED', 'CREATE',
       CURRENT_TIMESTAMP(6), 'SYSTEM-SEED', 0
FROM work_order
JOIN route ON route.code = 'FLOW-OLED-A' AND route.revision = 1
WHERE work_order.code = 'WO-2026-008';