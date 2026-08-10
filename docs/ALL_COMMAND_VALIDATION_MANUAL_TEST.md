# 全命令 Validator 业务流程与手工测试大纲

> `/api/command-validations` 只读取 MES Core 当前事实，不执行正式命令、不写快照、不追加履历。预检查通过不等于审批通过；真正执行时领域服务仍会重新校验状态、版本、幂等和事务条件。

## 1. 为什么按业务模块拆分

公共层只完成两件事：按 `commandType` 选择 Validator，以及统一封装 `evaluated/passed/errorCode/suggestedActionTypes`。Lot、设备事件和告警的规则分别保留在自己的 Policy 中，正式执行服务和 Validator 共用这些 Policy，避免维护两套判断。

当依赖对象不存在时，Validator 不伪造结论：对象存在规则返回失败，依赖它的后续规则返回 `evaluated=false`。其他彼此独立的规则仍继续检查，因此一次响应能展示完整问题清单。

## 2. 各命令的业务顺序、规则和理由

| 命令 | 预检查顺序 | 业务理由 |
|---|---|---|
| RELEASE | Lot/版本 → CREATED → RELEASED → 当前或路线首 Step 可确定 | 只有未投产、未暂停且有工艺入口的 Lot 才能 Release |
| TRACK_IN | Lot/版本 → READY/RELEASED/未绑设备 → 当前 Step → 设备 U/IDLE → 能力组 → 未占用 | 防止重复上机、错误设备上机和状态快照冲突 |
| TRACK_OUT | Lot/版本 → RUNNING/RELEASED → 当前 Step/设备 → 设备 U/PROC | 只允许真正加工中的 Lot 下机，并防止覆盖设备 Down/Maintenance 事件 |
| HOLD | Lot/版本 → READY 或 RUNNING → RELEASED → 当前 Step 有效 | 暂停只作用于活动生产 Lot，已暂停或无工艺位置的 Lot 不重复处理 |
| RELEASE_HOLD | Lot/版本 → READY 或 RUNNING → HELD → 当前 Step 有效 | 只解除真实存在的暂停，不推进工艺、不操作设备 |
| SCRAP | Lot/版本 → 非 COMPLETED/SCRAPPED → 可选 Step/设备引用可解析 | 报废允许发生在早期阶段，但已有审计引用不能悬空；终态不可重复报废 |
| EXECUTE_EQUIPMENT_EVENT | Equipment/版本 → ACTIVE 事件定义 → 必填原因 → 来源 U/D 与主状态 | 数据库事件定义决定合法迁移，原因要求和来源状态必须同时满足 |
| ACKNOWLEDGE_ALARM | Alarm/版本 → ACTIVE | 确认表示工程师接手，只能把 ACTIVE 变成 ACKNOWLEDGED |
| CLOSE_ALARM | Alarm/版本 → ACKNOWLEDGED → 关联设备 U/IDLE | 关闭代表异常真正结束，不能跳过确认，也不能在设备未恢复时关闭 |

## 3. 通用请求格式

```http
POST http://localhost:8080/api/command-validations
Content-Type: application/json
```

Lot 命令使用：

```json
{
  "commandType": "HOLD",
  "targetType": "LOT",
  "targetCode": "LOT-014",
  "expectedVersion": 2,
  "reasonCode": "QUALITY_CHECK",
  "reasonText": "只读验证暂停规则"
}
```

设备事件使用 `targetType=EQUIPMENT`、设备编码放在 `targetCode`、事件放在 `eventCode`。告警动作使用 `targetType=ALARM`，并同时提供字符串形式的 `targetCode` 和数值 `alarmId`。

## 4. 测试前必须查看的数据

```sql
SELECT code, execution_status, hold_status, version,
       current_route_step_id, current_equipment_id
FROM lot
WHERE code IN ('LOT-013', 'LOT-014', 'LOT-015', 'LOT-016');

SELECT code, up_down_status, primary_status, version
FROM equipment
WHERE code IN ('ETCH-01', 'ETCH-02', 'EQP-STATE-TEST-01');

SELECT event_code, from_up_down_status, from_primary_status,
       to_up_down_status, to_primary_status, requires_reason, status
FROM equipment_event_definition
ORDER BY event_code;

SELECT id, equipment_id, alarm_code, status, version
FROM equipment_alarm
ORDER BY id;

SELECT COUNT(*) AS lot_transaction_count FROM lot_transaction;
SELECT COUNT(*) AS equipment_history_count FROM equipment_history;
SELECT COUNT(*) AS alarm_action_history_count
FROM equipment_alarm_action_history;
```

Postman 当前固定用例依据的快照为：

- LOT-013：`RUNNING + HELD / version 4`，用于 Release Hold 允许和 Track Out 拒绝；
- LOT-014：`READY + RELEASED / version 2`，用于 Hold、Scrap 和 Track In 允许；
- EQP-STATE-TEST-01：`U + IDLE / version 2`，用于设备 TRACK_IN 事件允许；
- Alarm 1：`CLOSED / version 2`，用于确认和关闭的状态拒绝。

如果你的查询值不同，应先判断是哪一次正式命令改变了数据，再同步请求固定值；不要为了让预检查通过直接修改共享业务表。

## 5. 发送后期望的数据状态

无论 `allowed=true` 还是 `allowed=false`，重新执行第 4 节 SQL 后都必须满足：

- Lot、Equipment、Alarm 的状态与 version 不变；
- `lot_transaction` 数量不变；
- `equipment_history` 数量不变；
- `equipment_alarm_action_history` 数量不变；
- 不产生幂等记录、审批记录或任何业务履历。

如果任一数据发生变化，说明 Validator 错误地执行了写操作，应停止测试并排查。

## 6. Postman 用例位置

所有请求位于 `postman/collections/FabPilot MES Core/Command Validation`。请求直接写明 URL、目标编码、版本和 Trace ID，不使用 `{{environment}}`，并包含响应断言。Codex 不代发这些请求。