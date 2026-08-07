# Track Out 人工测试说明

> 本文只描述测试前置状态、请求和预期结果。请通过 MES Core API 执行业务操作，不要直接修改业务表。

## 一、普通工序 Track Out：ETCH → INSPECT

### 1. 发送请求前查询

```sql
SELECT
    l.code AS lot_code,
    l.execution_status,
    l.hold_status,
    l.version AS lot_version,
    current_step.step_code AS current_step_code,
    current_operation.code AS current_operation_code,
    e.code AS equipment_code,
    e.up_down_status,
    e.primary_status,
    e.version AS equipment_version,
    l.completed_at
FROM lot l
LEFT JOIN route_step current_step ON current_step.id = l.current_route_step_id
LEFT JOIN operation current_operation ON current_operation.id = current_step.operation_id
LEFT JOIN equipment e ON e.id = l.current_equipment_id
WHERE l.code = 'LOT-016';

SELECT step_code, sequence_no
FROM route_step
WHERE route_id = (SELECT route_id FROM lot WHERE code = 'LOT-016')
ORDER BY sequence_no;

SELECT COUNT(*) AS lot_transaction_count
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';

SELECT COUNT(*) AS equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';
```

发送前应满足：

| 数据 | 期望值 |
|---|---|
| Lot | `LOT-016` |
| execution_status / hold_status | `RUNNING / RELEASED` |
| Lot version | `1` |
| 当前 Step / Operation | `STEP-ETCH-020 / ETCH` |
| 当前设备 | `ETCH-02` |
| 设备状态 | `U / PROC` |
| 设备 version | `1` |
| completed_at | `NULL` |
| 两类目标幂等履历数 | 均为 `0` |

任一关键状态不一致时先停止测试，尤其不要为了匹配本文而直接更新数据库。

### 2. 请求

```http
POST /api/lots/LOT-016/track-out
Content-Type: application/json
```

```json
{
  "expectedVersion": 1,
  "idempotencyKey": "POSTMAN-LOT-016-TRACK-OUT-001",
  "operatorId": "POSTMAN-USER"
}
```

首次成功响应的业务数据应为：

```json
{
  "lotCode": "LOT-016",
  "transactionType": "TRACK_OUT",
  "executionStatus": "READY",
  "holdStatus": "RELEASED",
  "version": 2,
  "idempotent": false
}
```

如果此前已经成功执行过同一幂等键，响应会是相同状态，但 `idempotent` 为 `true`。

### 3. 发送后查询

重新执行第一节的 Lot 联表查询，再执行：

```sql
SELECT
    transaction_type,
    execution_status_before,
    execution_status_after,
    hold_status_before,
    hold_status_after,
    lot_version_before,
    lot_version_after,
    step.step_code,
    operation.code AS operation_code,
    equipment.code AS equipment_code,
    operator_type,
    operator_id,
    idempotency_key
FROM lot_transaction tx
LEFT JOIN route_step step ON step.id = tx.route_step_id
LEFT JOIN operation operation ON operation.id = tx.operation_id
LEFT JOIN equipment equipment ON equipment.id = tx.equipment_id
WHERE tx.idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';

SELECT
    event_code,
    up_down_status_before,
    up_down_status_after,
    primary_status_before,
    primary_status_after,
    equipment_version_before,
    equipment_version_after,
    operator_type,
    operator_id,
    idempotency_key
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';
```

发送后应满足：

| 表 | 期望变化 |
|---|---|
| `lot` | `RUNNING → READY`，version `1 → 2` |
| `lot` | 当前 Step 变为 `STEP-INSPECT-030` |
| `lot` | `current_equipment_id = NULL`，`completed_at = NULL` |
| `lot` | `last_transaction_code = TRACK_OUT`，操作者为 `POSTMAN-USER` |
| `equipment` | `ETCH-02` 从 `PROC → IDLE`，version `1 → 2` |
| `equipment` | `last_event_code = TRACK_OUT` |
| `lot_transaction` | 新增且仅新增一条 `TRACK_OUT` |
| `lot_transaction` | 记录刚完成的 `STEP-ETCH-020 / ETCH / ETCH-02` |
| `lot_transaction` | `RUNNING → READY`，Lot version `1 → 2` |
| `equipment_history` | 新增且仅新增一条 `TRACK_OUT` |
| `equipment_history` | `U/PROC → U/IDLE`，设备 version `1 → 2` |

## 二、幂等重放

原样再次发送相同请求。虽然请求中的 `expectedVersion` 仍为 `1`，服务会先识别已完成的相同幂等命令，响应应为：

- HTTP 200；
- `version = 2`；
- `idempotent = true`；
- Lot 和 Equipment 版本不再增加；
- 上述幂等键在 `lot_transaction` 与 `equipment_history` 中仍各只有一条。

## 三、末工序完成分支

普通 Track Out 成功后，`LOT-016` 已在 `STEP-INSPECT-030` 等待。可按以下顺序人工验证末工序：

1. Track In 到 `INSPECT-01`：`expectedVersion=2`，使用新的幂等键，例如 `MANUAL-LOT-016-TRACK-IN-INSPECT-001`。
2. 确认 Lot 为 `RUNNING`、version=3，设备 `INSPECT-01` 为 `U/PROC`。
3. Track Out：`expectedVersion=3`，使用新的幂等键，例如 `MANUAL-LOT-016-TRACK-OUT-INSPECT-001`。

末工序 Track Out 后应满足：

- Lot 为 `COMPLETED + RELEASED`，version=4；
- `current_equipment_id = NULL`；
- 当前 Step 保留 `STEP-INSPECT-030`，用于追溯最后完成工序；
- `completed_at` 不为空；
- `INSPECT-01` 恢复为 `U/IDLE`，设备 version 增加 1；
- LotTransaction 记录 `RUNNING → COMPLETED`；
- EquipmentHistory 记录 `PROC → IDLE`；
- 同幂等键重放不会再次更新或新增履历。
