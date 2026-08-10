-- 专用设备只用于设备事件手工测试，避免改变 ETCH-01/ETCH-02 等演示业务设备。
-- 初始 D + DOWN 对应 START_MAINTENANCE 的来源状态，版本 0 便于按顺序验证乐观锁。
INSERT INTO equipment (
    production_line_id, code, name, equipment_type, status,
    up_down_status, primary_status, last_event_code, last_event_at, version
)
SELECT pl.id, 'EQP-STATE-TEST-01', '设备状态切换专用测试机', 'ETCHER', 'DOWN',
       'D', 'DOWN', 'VACUUM_LOW', CURRENT_TIMESTAMP(6), 0
FROM production_line pl
WHERE pl.code = 'LINE-OLED-01'
  AND NOT EXISTS (SELECT 1 FROM equipment WHERE code = 'EQP-STATE-TEST-01');

-- 加入刻蚀能力组，使测试设备的数据关系与真实设备一致；重复迁移不会产生重复成员。
INSERT IGNORE INTO equipment_group_member (equipment_group_id, equipment_id)
SELECT eg.id, e.id
FROM equipment_group eg
JOIN equipment e ON e.code = 'EQP-STATE-TEST-01'
WHERE eg.code = 'EQP-GRP-ETCH';