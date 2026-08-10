-- 告警生命周期专用设备与告警，不复用已执行过状态切换测试的设备。
INSERT INTO equipment (production_line_id,code,name,equipment_type,status,up_down_status,primary_status,last_event_code,last_event_at,version)
SELECT id,'EQP-ALARM-TEST-01','告警生命周期专用测试机','ETCHER','DOWN','D','DOWN','VACUUM_LOW',CURRENT_TIMESTAMP(6),0
FROM production_line WHERE code='LINE-OLED-01' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQP-ALARM-TEST-01');
INSERT IGNORE INTO equipment_group_member(equipment_group_id,equipment_id)
SELECT g.id,e.id FROM equipment_group g JOIN equipment e ON e.code='EQP-ALARM-TEST-01' WHERE g.code='EQP-GRP-ETCH';
INSERT INTO equipment_alarm(equipment_id,alarm_code,severity,status,source_event_code,source_idempotency_key,message,version,opened_at)
SELECT e.id,'VACUUM_LOW','HIGH','ACTIVE','VACUUM_LOW','SEED-EQP-ALARM-TEST-01-001','专用测试设备真空值低于生产下限',0,CURRENT_TIMESTAMP(6)
FROM equipment e WHERE e.code='EQP-ALARM-TEST-01' AND NOT EXISTS(SELECT 1 FROM equipment_alarm WHERE source_idempotency_key='SEED-EQP-ALARM-TEST-01-001');