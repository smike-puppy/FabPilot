# Release Hold 人工测试说明

> 只通过 MES Core API 执行业务操作，不直接修改业务表。Codex 不代发请求。

## 1. 业务流程与编码依据

### 1.1 业务目标与状态迁移

Release Hold 用于解除暂停，使 Lot 恢复允许继续流转的状态；它不推进工艺、不负责上机或下机。

```text
READY   + HELD → READY   + RELEASED
RUNNING + HELD → RUNNING + RELEASED
```

### 1.2 执行顺序与理由

1. 查询 Lot；不存在则终止。
2. 先按幂等键查已有 `RELEASE_HOLD` 履历，并核对 Lot、命令类型和两个原因字段；网络重试不会重复解除，同键也不能更换解除理由。
3. 校验 `expectedVersion`，防止基于旧的暂停快照操作。
4. 校验 execution_status 为 `READY/RUNNING`：只有仍处于生产生命周期的 Lot 才能解除暂停。
5. 校验 hold_status 为 `HELD`：没有暂停就不存在解除动作；校验当前 Step 存在，保证恢复后仍有明确生产上下文。
6. 条件更新 Lot：只执行 `HELD → RELEASED`，execution、Step、Equipment 均保持不变。
7. 追加 `LotTransaction(RELEASE_HOLD)`，记录解除原因、前后状态、当前工艺上下文、操作人和版本。

### 1.3 事务与一致性

Lot 快照与履历在同一事务中更新。SQL 同时携带旧 version、旧 execution 和 `HELD` 条件，防止并发误解除。Release Hold 不更新 EquipmentHistory，因为设备状态没有变化。

## 2. 测试前数据

```sql
SELECT l.code, l.execution_status, l.hold_status, l.version,
       rs.step_code, l.current_equipment_id, l.last_transaction_code
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-016';

SELECT transaction_type, reason_code, reason_text,
       lot_version_before, lot_version_after
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-HOLD-001';

SELECT COUNT(*) AS release_hold_transaction_count
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-RELEASE-HOLD-001';

SELECT COUNT(*) AS release_hold_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-RELEASE-HOLD-001';
```

| 数据 | 期望值 |
|---|---|
| execution_status / hold_status | `READY / HELD` |
| Lot version | `3` |
| 当前 Step | `STEP-INSPECT-030` |
| current_equipment_id | `NULL` |
| 原 Hold 履历 | 1 条，版本 `2 → 3` |
| Release Hold 履历 / 设备履历 | `0 / 0` |

## 3. 请求与首次响应

```http
POST /api/lots/LOT-016/release-hold
Content-Type: application/json
```

```json
{
  "expectedVersion": 3,
  "idempotencyKey": "POSTMAN-LOT-016-RELEASE-HOLD-001",
  "operatorId": "POSTMAN-USER",
  "reasonCode": "QUALITY_CHECK_RESOLVED",
  "reasonText": "质量数据复核完成，允许继续检测"
}
```

```json
{
  "lotCode": "LOT-016",
  "transactionType": "RELEASE_HOLD",
  "executionStatus": "READY",
  "holdStatus": "RELEASED",
  "version": 4,
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
WHERE idempotency_key = 'POSTMAN-LOT-016-RELEASE-HOLD-001';

SELECT COUNT(*) AS release_hold_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-RELEASE-HOLD-001';
```

| 表 | 期望状态 |
|---|---|
| `lot` | `READY / RELEASED`，version `3 → 4` |
| `lot` | Step 仍为 `STEP-INSPECT-030`，设备仍为 `NULL` |
| `lot` | last_transaction_code 为 `RELEASE_HOLD` |
| `lot_transaction` | 仅新增 1 条，execution `READY → READY`，hold `HELD → RELEASED` |
| `lot_transaction` | 解除原因、操作人和版本 `3 → 4` 正确 |
| `equipment_history` | 不新增，数量仍为 0 |

## 5. 幂等与异常场景

原样重放期望 HTTP 200、version 仍为 4、`idempotent=true`，履历数量不增加。同键修改任一解除原因期望 HTTP 409。对 `RELEASED`、`CREATED` 或终态 Lot 执行 Release Hold 期望 `LOT_STATE_INVALID`；错误 version 期望版本冲突。