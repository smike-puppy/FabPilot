# 设备事件状态切换手工测试

## 业务流程和规则

本功能不是“设备故障测试接口”，而是按照 `equipment_event_definition` 执行通用设备状态切换。执行顺序如下：

1. 按设备编码读取当前快照，确认设备存在。
2. 先查幂等键：完全相同的请求重复发送只返回首次执行结果，不重复更新设备或写历史；同一键用于不同内容则拒绝。
3. 校验 `expectedVersion`，防止用户基于旧状态覆盖别人刚完成的操作。
4. 读取 ACTIVE 事件定义。定义中的来源状态决定“现在能不能做”，目标状态决定“成功后变成什么”。
5. 如果事件定义 `requires_reason = 1`，必须同时填写原因码和原因说明，保证后续能统计并能人工追溯。
6. 使用设备主键、原状态和原版本做条件更新，并在同一事务追加 `equipment_history`。任一步失败都会回滚。

本测试选用专用设备，避免修改 `ETCH-01` 上绑定的生产 Lot，也不影响 `ETCH-02` 等演示数据。状态链为：

`D + DOWN v0 --START_MAINTENANCE--> D + MAINTENANCE v1 --COMPLETE_MAINTENANCE--> U + IDLE v2`

这样设计的理由是：开始维护只能发生在已停机设备上；维修完成只能发生在维护中的设备上；恢复后设备进入可用但尚未加工的空闲状态。

## 发送请求前查询

```sql
SELECT e.id, e.code, e.status, e.up_down_status, e.primary_status,
       e.last_event_code, e.version, COUNT(l.id) AS bound_lots
FROM equipment e
LEFT JOIN lot l ON l.current_equipment_id = e.id
WHERE e.code = 'EQP-STATE-TEST-01'
GROUP BY e.id;

SELECT h.event_code, h.idempotency_key,
       h.equipment_version_before, h.equipment_version_after
FROM equipment_history h
JOIN equipment e ON e.id = h.equipment_id
WHERE e.code = 'EQP-STATE-TEST-01'
ORDER BY h.id;
```

开始测试前应为 `status=DOWN, up_down_status=D, primary_status=DOWN, version=0, bound_lots=0`，且历史查询无结果。当前数据库已由 Codex 读取并确认满足这些条件。

## Postman 顺序与期望结果

1. `01 Start Maintenance`：返回 `D/MAINTENANCE v1, idempotent=false`；设备旧 `status` 同步为 `MAINTENANCE`，新增一条 `0 -> 1` 历史。
2. `02 Start Maintenance Idempotent Replay`：返回当前 `D/MAINTENANCE v1, idempotent=true`；设备版本不增加，历史仍只有一条。
3. `03 Complete Maintenance`：返回 `U/IDLE v2, idempotent=false`；旧 `status=IDLE`，再新增一条 `1 -> 2` 历史。
4. `04 Reject Invalid Source State`：返回 HTTP 409 和 `EQUIPMENT_STATE_INVALID`；设备仍为 `U/IDLE v2`，不新增历史。

## 全部发送后的数据库期望

```sql
SELECT code, status, up_down_status, primary_status, last_event_code, version
FROM equipment WHERE code = 'EQP-STATE-TEST-01';

SELECT event_code, operator_type, operator_id, reason_code,
       idempotency_key, equipment_version_before, equipment_version_after
FROM equipment_history
WHERE equipment_id = (SELECT id FROM equipment WHERE code = 'EQP-STATE-TEST-01')
ORDER BY id;
```

最终设备应为 `IDLE / U / IDLE / COMPLETE_MAINTENANCE / v2`。历史应恰好两条：`START_MAINTENANCE 0→1` 和 `COMPLETE_MAINTENANCE 1→2`，两条均为 `operator_type=USER`。Codex 不执行这些 Postman 请求，测试结果由你发送后自行查看。