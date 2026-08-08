# Scrap 人工测试说明

> 只通过 MES Core API 执行业务操作，不直接修改业务表。Codex 不代发请求。

## 1. 业务流程与编码依据

### 1.1 业务目标与状态迁移

Scrap 将任意非终态 Lot 转为不可逆的报废终态。

```text
CREATED / READY / RUNNING + RELEASED或HELD → SCRAPPED + RELEASED
```

报废不是正常完工，因此不写 `completed_at`；当前 Step 保留用于追溯报废发生位置。

### 1.2 执行顺序与理由

1. 查询 Lot，并按 idempotencyKey、Lot、`SCRAP`、reasonCode、reasonText 识别重放；报废原因是业务意图的一部分，不能同键更换。
2. 校验 `expectedVersion`，防止基于旧状态执行不可逆报废。
3. 拒绝 `COMPLETED` 和 `SCRAPPED`：两者均为终态；其余 CREATED、READY、RUNNING 以及 HELD 组合均可按设计报废。
4. 保存当前 Step 和 Equipment 作为履历上下文；之后即使清除设备绑定，仍能追溯报废位置。
5. 如果绑定设备仍为 `PROC`，将其释放为 `IDLE` 并追加 EquipmentHistory；如果设备已因独立事件进入 `DOWN/MAINTENANCE`，只解除 Lot 绑定，不覆盖异常状态。
6. 条件更新 Lot 为 `SCRAPPED + RELEASED`，清空设备绑定，保留 Step，不写 completed_at。
7. 追加 `LotTransaction(SCRAP)`，保存报废前状态、原因、Step、Operation、原设备、操作人和版本。

### 1.3 事务与一致性

需要释放设备时，Equipment、Lot 和两类履历处于同一事务。任一步失败全部回滚。SQL 使用旧 version、旧状态和原设备绑定作为条件，防止并发请求把已经变化的 Lot 报废。

## 2. 测试前数据

```sql
SELECT l.code, l.execution_status, l.hold_status, l.version,
       rs.step_code, l.current_equipment_id, l.completed_at,
       l.last_transaction_code
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-016';

SELECT COUNT(*) AS scrap_transaction_count
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-SCRAP-001';

SELECT COUNT(*) AS scrap_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-SCRAP-001';
```

| 数据 | 期望值 |
|---|---|
| execution_status / hold_status | `READY / RELEASED` |
| Lot version | `4` |
| 当前 Step | `STEP-INSPECT-030` |
| current_equipment_id / completed_at | `NULL / NULL` |
| Scrap LotTransaction / EquipmentHistory | `0 / 0` |

状态不一致时停止，不要直接修改数据库造状态。

## 3. 请求与首次响应

```http
POST /api/lots/LOT-016/scrap
Content-Type: application/json
```

```json
{
  "expectedVersion": 4,
  "idempotencyKey": "POSTMAN-LOT-016-SCRAP-001",
  "operatorId": "POSTMAN-USER",
  "reasonCode": "INSPECTION_REJECT",
  "reasonText": "检测结果超出规格，确认报废"
}
```

```json
{
  "lotCode": "LOT-016",
  "transactionType": "SCRAP",
  "executionStatus": "SCRAPPED",
  "holdStatus": "RELEASED",
  "version": 5,
  "idempotent": false
}
```

## 4. 发送后查询与预期

```sql
SELECT l.code, l.execution_status, l.hold_status, l.version,
       rs.step_code, l.current_equipment_id, l.completed_at,
       l.last_transaction_code, l.last_operator_id
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-016';

SELECT transaction_type, execution_status_before, execution_status_after,
       hold_status_before, hold_status_after, reason_code, reason_text,
       route_step_id, equipment_id, lot_version_before, lot_version_after, operator_id
FROM lot_transaction
WHERE idempotency_key = 'POSTMAN-LOT-016-SCRAP-001';

SELECT COUNT(*) AS scrap_equipment_history_count
FROM equipment_history
WHERE idempotency_key = 'POSTMAN-LOT-016-SCRAP-001';
```

| 表 | 期望状态 |
|---|---|
| `lot` | `SCRAPPED / RELEASED`，version `4 → 5` |
| `lot` | Step 仍为 `STEP-INSPECT-030`，设备仍为 `NULL` |
| `lot` | completed_at 仍为 `NULL`，last_transaction_code 为 `SCRAP` |
| `lot_transaction` | 仅新增 1 条，execution `READY → SCRAPPED`，hold `RELEASED → RELEASED` |
| `lot_transaction` | 原因、操作人、Step 和版本 `4 → 5` 正确 |
| `equipment_history` | 本场景无绑定设备，因此数量仍为 0 |

## 5. 幂等与异常场景

原样重放期望 HTTP 200、version 仍为 5、`idempotent=true`，履历不增加。同键修改原因期望 HTTP 409。错误 version 期望版本冲突；对 COMPLETED/SCRAPPED Lot 执行期望 `LOT_STATE_INVALID`。

## 6. 有设备时的补充分支

- `RUNNING` 且绑定 `PROC` 设备：Lot 解除绑定，设备回到 `IDLE`，新增一条 `EquipmentHistory(SCRAP)`。
- 绑定设备已为 `DOWN/MAINTENANCE`：Lot 解除绑定，但设备异常状态和 version 不由 Scrap 覆盖，也不伪造 PROC→IDLE 履历。
- 两种分支的 LotTransaction 都必须保存报废前的 equipment_id，保证审计可追溯。