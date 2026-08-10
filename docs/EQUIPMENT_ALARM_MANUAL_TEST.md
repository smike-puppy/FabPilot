# 设备告警生命周期手工测试

## 业务流程与编码依据

告警用于追踪“设备异常有没有人接手、是否真正处理完成”，状态依次为 `ACTIVE → ACKNOWLEDGED → CLOSED`。

1. 确认告警前要求告警为 ACTIVE，因为已确认或已关闭的告警不能重复接手。
2. 每个动作先查幂等历史，再校验版本；网络重试可安全返回，而旧页面不能覆盖其他工程师的处理结果。
3. 关闭前要求告警为 ACKNOWLEDGED，并重新读取设备快照确认是 `U + IDLE`。告警状态不能代替设备事实；设备仍 DOWN 或 MAINTENANCE 时说明维修尚未完成。
4. 告警条件更新与动作历史追加位于同一事务。更新失败不写历史，历史失败则告警更新回滚。

测试使用独立设备 `EQP-ALARM-TEST-01` 和告警 ID `1`。当前数据库已确认设备为 `D/DOWN v0`、告警为 `ACTIVE v0`。若重建数据库后告警 ID 不同，请先执行下面 SQL，并把请求体中的 alarmId 改为查询结果；请求不使用 Environment 变量。

## 测试前 SQL

```sql
SELECT e.id,e.code,e.up_down_status,e.primary_status,e.version,
       a.id AS alarm_id,a.alarm_code,a.status,a.version AS alarm_version
FROM equipment e JOIN equipment_alarm a ON a.equipment_id=e.id
WHERE e.code='EQP-ALARM-TEST-01';

SELECT * FROM equipment_alarm_action_history
WHERE alarm_id=(SELECT id FROM equipment_alarm WHERE source_idempotency_key='SEED-EQP-ALARM-TEST-01-001');
```

预期：设备 `D/DOWN v0`，告警 `ACTIVE v0`，动作历史为空。

## 请求顺序和预期

1. `01 Acknowledge Alarm`：告警变为 `ACKNOWLEDGED v1`，新增 ACKNOWLEDGE 历史。
2. `02 Acknowledge Idempotent Replay`：返回 `idempotent=true`，版本仍为 1，历史不增加。
3. `03 Reject Close Before Recovery`：设备仍 DOWN，返回 409 `EQUIPMENT_NOT_RECOVERED`，告警不变化。
4. `04 Start Maintenance`：设备变为 `D/MAINTENANCE v1`。
5. `05 Complete Maintenance`：设备恢复为 `U/IDLE v2`。
6. `06 Close Alarm`：告警变为 `CLOSED v2`，新增 CLOSE 历史。

## 测试后 SQL 与预期

```sql
SELECT code,status,up_down_status,primary_status,last_event_code,version
FROM equipment WHERE code='EQP-ALARM-TEST-01';
SELECT id,status,acknowledged_by,acknowledged_at,closed_by,closed_at,version
FROM equipment_alarm WHERE source_idempotency_key='SEED-EQP-ALARM-TEST-01-001';
SELECT action,operator_id,idempotency_key,alarm_version_before,alarm_version_after
FROM equipment_alarm_action_history WHERE alarm_id=1 ORDER BY id;
```

最终设备应为 `IDLE/U/IDLE/COMPLETE_MAINTENANCE/v2`；告警应为 `CLOSED v2`；动作历史恰好两条：`ACKNOWLEDGE 0→1`、`CLOSE 1→2`。失败的提前关闭不能留下历史。Codex 不执行这些 Postman 请求。