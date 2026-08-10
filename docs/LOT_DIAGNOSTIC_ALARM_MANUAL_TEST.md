# Lot 诊断上下文包含设备未关闭告警——手工测试说明

## 一、业务流程和规则

本功能用于让后续 Agent 查询某个 Lot 时，一次获得 Lot、工单、工序、当前设备、最近履历和当前设备尚未关闭的告警。

1. 根据 Lot Code 查询 Lot；不存在时返回 `LOT_NOT_FOUND`。
2. 查询工单、当前 Step、Operation、当前设备及最近履历。
3. Lot 没有绑定设备时返回 `activeAlarms: []`，不查询其他设备的告警。
4. Lot 已绑定设备时，只返回该设备的 `ACTIVE`、`ACKNOWLEDGED` 告警。
5. `CLOSED` 告警已经结束，不作为当前异常返回，避免误导 Agent。
6. 告警按开启时间倒序，最多返回10条；`openDurationSeconds` 表示异常已经持续的秒数。
7. 整个接口处于只读事务，不修改任何业务表。

## 二、请求前检查数据库

先用只读 SQL 寻找符合条件的 Lot：

```sql
SELECT l.code AS lot_code,
       l.current_equipment_id,
       e.code AS equipment_code,
       e.up_down_status,
       e.primary_status,
       a.id AS alarm_id,
       a.alarm_code,
       a.severity,
       a.status AS alarm_status,
       a.opened_at,
       a.version AS alarm_version
FROM lot l
JOIN equipment e ON e.id = l.current_equipment_id
JOIN equipment_alarm a ON a.equipment_id = e.id
WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED')
ORDER BY a.opened_at DESC;
```

确认 Lot 当前设备与告警设备相同，并记下 Lot、Equipment、Alarm 的状态和版本。若结果为空，不要直接修改既有数据；应通过正常设备事件和 Lot 流程准备专用测试场景。

## 三、发送请求

把 URL 中的 `LOT-CODE-FROM-SQL` 直接替换成上一步查到的 Lot Code：

```http
GET http://localhost:8080/api/lots/LOT-CODE-FROM-SQL/diagnostic-context
X-Trace-Id: POSTMAN-DIAGNOSTIC-ACTIVE-ALARM-01
```

## 四、期望响应

- HTTP 200，`success=true`，`code=SUCCESS`。
- `data.currentEquipment.code` 等于 SQL 查到的设备编码。
- `data.activeAlarms` 包含该设备尚未关闭的告警，不包含 `CLOSED` 告警。
- 告警包含 `id`、`alarmCode`、`severity`、`status`、`sourceEventCode`、`message`、`openedAt`、确认信息、`openDurationSeconds` 和 `version`。
- `openDurationSeconds` 大于等于0，并随时间增加。

## 五、请求后核对数据库

重新查询 `lot`、`equipment`、`equipment_alarm`，并检查相关履历数量。期望状态为：

- Lot 状态、当前设备和版本完全不变。
- Equipment 状态和版本完全不变。
- EquipmentAlarm 不新增、不更新、不关闭，状态和版本完全不变。
- `lot_transaction`、`equipment_history`、`equipment_alarm_action_history` 均不新增记录。

这是只读诊断功能；若任一业务数据发生变化，就属于实现错误。
