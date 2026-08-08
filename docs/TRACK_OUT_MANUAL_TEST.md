# Track Out 人工测试说明

> 只通过 MES Core API 执行业务操作，不直接修改业务表。Codex 不代发请求。

## 1. 业务流程与编码依据

### 1.1 业务目标与状态迁移

Track Out 表示 Lot 完成当前设备上的加工并下机。服务端根据 Route 决定下一状态，调用方不能指定下一 Step。

```text
普通工序：RUNNING + RELEASED → 下一 Step 的 READY + RELEASED
末道工序：RUNNING + RELEASED → COMPLETED + RELEASED
设备：    U + PROC → U + IDLE
```

### 1.2 执行顺序与理由

1. 查询 Lot，并优先识别幂等重放；同一成功命令重复发送不会再次下机或追加履历。
2. 校验 `expectedVersion`，防止两个请求同时推进同一个 Lot。
3. 校验 Lot 必须为 `RUNNING + RELEASED`，且当前 Step、Equipment 都存在：只有真正上机且未 Hold 的 Lot 才能正常下机。
4. 读取当前 Step 和绑定设备；当前 Step 必须属于 Lot 的 Route，设备必须仍为 `U + PROC`，避免覆盖设备侧并发 Down/Maintenance 事件。
5. 按 Route 的 sequence_no 查询下一 Step：找到则进入下一 Step 的 READY；找不到说明当前是末工序，进入 COMPLETED 并写 `completed_at`。
6. 条件更新 Equipment 为 IDLE；再条件更新 Lot，清除设备绑定并推进 Step/终态。两个更新都带旧 version 和旧状态条件。
7. 追加 `LotTransaction(TRACK_OUT)` 和 `EquipmentHistory(TRACK_OUT)`。Lot 履历记录“刚完成的旧 Step”，而不是下一 Step，便于还原加工事实。

### 1.3 事务与一致性

Lot、Equipment 两个快照和两类履历在同一事务中。任何更新或履历插入失败都会整体回滚，避免出现“设备已空闲但 Lot 仍在加工”或相反的不一致状态。

## 2. 普通工序测试前数据

```sql
SELECT l.code AS lot_code, l.execution_status, l.hold_status,
       l.version AS lot_version, step.step_code,
       operation.code AS operation_code, e.code AS equipment_code,
       e.up_down_status, e.primary_status, e.version AS equipment_version,
       l.completed_at
FROM lot l
LEFT JOIN route_step step ON step.id = l.current_route_step_id
LEFT JOIN operation operation ON operation.id = step.operation_id
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

| 数据 | 期望值 |
|---|---|
| Lot 状态 | `RUNNING / RELEASED` |
| Lot version | `1` |
| 当前 Step / Operation | `STEP-ETCH-020 / ETCH` |
| 当前设备 | `ETCH-02`，`U / PROC`，version `1` |
| completed_at | `NULL` |
| 两类目标履历 | 均为 `0` |

任一关键状态不一致时停止测试，不要直接修改数据库。

## 3. 请求与首次响应

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

## 4. 发送后查询与预期

重新执行第 2 节 Lot 联表查询，再执行：

```sql
SELECT tx.transaction_type, tx.execution_status_before, tx.execution_status_after,
       tx.hold_status_before, tx.hold_status_after,
       tx.lot_version_before, tx.lot_version_after,
       step.step_code, operation.code AS operation_code,
       equipment.code AS equipment_code, tx.operator_id
FROM lot_transaction tx
LEFT JOIN route_step step ON step.id = tx.route_step_id
LEFT JOIN operation operation ON operation.id = tx.operation_id
LEFT JOIN equipment equipment ON equipment.id = tx.equipment_id
WHERE tx.idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';

SELECT event_code, up_down_status_before, up_down_status_after,
       primary_status_before, primary_status_after,
       equipment_version_before, equipment_version_after, operator_id
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-TRACK-OUT-001';
```

| 表 | 期望变化 |
|---|---|
| `lot` | `RUNNING → READY`，version `1 → 2` |
| `lot` | 当前 Step 变为 `STEP-INSPECT-030`，设备清空，completed_at 仍为 `NULL` |
| `equipment` | `ETCH-02` 从 `U/PROC → U/IDLE`，version `1 → 2` |
| `lot_transaction` | 仅 1 条，记录完成的 `STEP-ETCH-020 / ETCH / ETCH-02` |
| `equipment_history` | 仅 1 条，记录 `U/PROC → U/IDLE` |

## 5. 幂等与异常场景

原样重放期望 HTTP 200、Lot/Equipment version 不再增加、`idempotent=true`，两类履历仍各 1 条。对 HELD、READY、无设备的 Lot 执行期望 `LOT_STATE_INVALID`；绑定设备不再是 `U + PROC` 时期望设备状态错误；错误 version 期望版本冲突。

## 6. 末工序完成分支

普通 Track Out 后，先把 LOT-016 Track In 到 `INSPECT-01`（使用新幂等键和正确 version），再执行末工序 Track Out。预期：

- Lot 为 `COMPLETED + RELEASED`；
- current_equipment_id 为 `NULL`；
- 当前 Step 保留 `STEP-INSPECT-030`，用于追溯最后工序；
- completed_at 不为空；
- `INSPECT-01` 恢复 `U/IDLE`；
- LotTransaction 记录 `RUNNING → COMPLETED`；
- EquipmentHistory 记录 `PROC → IDLE`；
- 同键重放不会重复更新或追加履历。