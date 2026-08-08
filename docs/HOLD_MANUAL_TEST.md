# Hold 人工测试说明

> 只通过 MES Core API 执行业务操作，不直接修改业务表。Codex 不代发请求。

## 1. 业务流程与编码依据

### 1.1 业务目标与状态迁移

Hold 用于暂停已经进入生产流程的 Lot，但暂停不等于下机或改变执行阶段。

```text
READY   + RELEASED → READY   + HELD
RUNNING + RELEASED → RUNNING + HELD
```

当前 Step 和 Equipment 绑定保持不变。

### 1.2 执行顺序与理由

1. 按 `lotCode` 查询 Lot；不存在立即返回 `LOT_NOT_FOUND`，后续不写数据。
2. 查询 `idempotencyKey`：同 Lot、同 `HOLD`、同原因视为安全重放；同键不同命令或原因返回 `IDEMPOTENCY_CONFLICT`，防止一个键表达两种业务意图。
3. 比较 `expectedVersion` 与当前 version；不一致说明调用方基于旧快照操作，避免覆盖其他请求的结果。
4. 校验 execution_status 为 `READY` 或 `RUNNING`：`CREATED` 尚未投产，`COMPLETED/SCRAPPED` 已是终态。
5. 校验 hold_status 为 `RELEASED`，防止重复 Hold；校验当前 Step 存在，确保暂停履历具有明确工艺上下文。
6. 条件更新 Lot：只执行 `RELEASED → HELD`，不修改 execution、Step、Equipment。
7. 追加 `LotTransaction(HOLD)`，保存前后状态、Step、Operation、Equipment、原因、操作人和版本。

### 1.3 事务与一致性

Lot 快照更新和 LotTransaction 插入位于同一事务，任一步失败都会整体回滚。Hold 不修改 Equipment/EquipmentHistory，因为设备 Down、Alarm 或 Maintenance 属于独立设备事件，不应被 Lot 暂停命令覆盖。

## 2. 测试前数据

```sql
SELECT l.code, l.execution_status, l.hold_status, l.version,
       rs.step_code, l.current_equipment_id, l.last_transaction_code
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-016';

SELECT COUNT(*) AS hold_transaction_count
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-HOLD-001';

SELECT COUNT(*) AS hold_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-HOLD-001';
```

| 数据 | 期望值 |
|---|---|
| execution_status / hold_status | `READY / RELEASED` |
| Lot version | `2` |
| 当前 Step | `STEP-INSPECT-030` |
| current_equipment_id | `NULL` |
| 目标 LotTransaction / EquipmentHistory | `0 / 0` |

状态不一致时停止测试，不要直接修改数据库匹配条件。

## 3. 请求与首次响应

```http
POST /api/lots/LOT-016/hold
Content-Type: application/json
```

```json
{
  "expectedVersion": 2,
  "idempotencyKey": "POSTMAN-LOT-016-HOLD-001",
  "operatorId": "POSTMAN-USER",
  "reasonCode": "QUALITY_CHECK",
  "reasonText": "检测工序前需要复核质量数据"
}
```

```json
{
  "lotCode": "LOT-016",
  "transactionType": "HOLD",
  "executionStatus": "READY",
  "holdStatus": "HELD",
  "version": 3,
  "idempotent": false
}
```

## 4. 发送后查询与预期

```sql
SELECT l.code, l.execution_status, l.hold_status, l.version,
       rs.step_code, l.current_equipment_id,
       l.last_transaction_code, l.last_operator_id
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-016';

SELECT transaction_type, execution_status_before, execution_status_after,
       hold_status_before, hold_status_after, reason_code, reason_text,
       route_step_id, equipment_id, lot_version_before, lot_version_after, operator_id
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-HOLD-001';

SELECT COUNT(*) AS hold_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-HOLD-001';
```

| 表 | 期望状态 |
|---|---|
| `lot` | execution 保持 `READY`，hold 为 `HELD`，version `2 → 3` |
| `lot` | Step 仍为 `STEP-INSPECT-030`，设备仍为 `NULL` |
| `lot` | last_transaction_code 为 `HOLD` |
| `lot_transaction` | 仅新增 1 条，execution `READY → READY`，hold `RELEASED → HELD` |
| `lot_transaction` | 原因、操作人、Step 和版本 `2 → 3` 正确 |
| `equipment_history` | 不新增，数量仍为 0 |

## 5. 幂等与异常场景

原样重放期望 HTTP 200、version 仍为 3、`idempotent=true`，履历数量不增加。同键修改任一原因字段期望 HTTP 409、`IDEMPOTENCY_CONFLICT`。错误 version 期望版本冲突；对已经 HELD 或终态 Lot 执行 Hold 期望 `LOT_STATE_INVALID`。