# Track In 命令预检查手工测试说明

> 本接口只读取 MES Core 当前事实，不执行 Track In、不写历史、不修改数据库。Codex 不代发请求；由测试人员查看测试前数据、发送请求并核对测试后数据。

## 1. 业务流程、规则和理由

本功能用于在真正执行 Track In 前回答“LOT-014 现在能不能上 ETCH-02，如果不能，具体卡在哪些规则”。页面和未来 Agent 都可以调用它，但预检查通过不等于已经取得执行授权。

执行顺序如下：

1. 读取 Lot 和目标设备快照。对象不存在时仍返回完整规则清单，依赖该对象的规则标记为 `evaluated=false`。
2. 检查请求版本是否等于 Lot 当前版本，避免用户依据过期页面作决定。
3. 检查 Lot 为 `READY + RELEASED` 且未绑定设备。READY 表示已经 Release 到当前工序；RELEASED 表示未被 Hold；未绑定设备用于防止同一 Lot 重复上机。
4. 检查当前 Step 存在、属于 Lot 当前 Route，并配置了 required equipment group。否则无法安全判断设备是否能执行该工序。
5. 检查设备为 `U + IDLE`。U 表示设备可生产，IDLE 表示当前没有加工任务。
6. 检查设备恰好属于 Step 要求的设备组，并且没有任何 Lot 通过 `current_equipment_id` 占用它。
7. 一次返回全部规则，不在第一条失败处停止，方便用户或 Agent 一次看清所有问题。

正式 Track In 会重新执行同一套 Policy，因为预检查后数据可能被其他请求修改。预检查接口自身没有事务写入、幂等键和审批含义。

## 2. 发送前必须查看的数据

```sql
SELECT l.id, l.code, l.route_id, l.current_route_step_id,
       l.current_equipment_id, l.execution_status, l.hold_status, l.version,
       rs.step_code, rs.required_equipment_group_id
FROM lot l
LEFT JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE l.code = 'LOT-014';

SELECT e.id, e.code, e.up_down_status, e.primary_status, e.version
FROM equipment e
WHERE e.code = 'ETCH-02';

SELECT COUNT(*) AS capability_membership_count
FROM equipment_group_member egm
JOIN equipment e ON e.id = egm.equipment_id
JOIN lot l ON l.code = 'LOT-014'
JOIN route_step rs ON rs.id = l.current_route_step_id
WHERE e.code = 'ETCH-02'
  AND egm.equipment_group_id = rs.required_equipment_group_id;

SELECT code AS occupying_lot_code
FROM lot
WHERE current_equipment_id = (SELECT id FROM equipment WHERE code = 'ETCH-02');

SELECT COUNT(*) AS lot_transaction_count FROM lot_transaction;
SELECT COUNT(*) AS equipment_history_count FROM equipment_history;
```

允许场景的期望前置状态：

| 数据 | 期望 |
|---|---|
| LOT-014 | `READY / RELEASED`，`current_equipment_id IS NULL` |
| Lot version | 请求中的 `expectedVersion` 必须与查询值相同；种子初始值为 `2` |
| 当前 Step | 存在，route_id 与 Lot 一致，required_equipment_group_id 非空 |
| ETCH-02 | `U / IDLE` |
| 能力组条数 | `1` |
| 占用 Lot | 0 行 |

如果实际 version 已变化，应把下面 JSON 的 `expectedVersion` 改成查询值；不要为了匹配示例直接修改业务数据库。

## 3. 请求

```http
POST http://localhost:8080/api/command-validations
Content-Type: application/json
```

```json
{
  "commandType": "TRACK_IN",
  "targetType": "LOT",
  "targetCode": "LOT-014",
  "expectedVersion": 2,
  "equipmentCode": "ETCH-02"
}
```

请求中没有 `{{环境变量}}`。允许场景期望 HTTP 200、`data.allowed=true`、`observedVersion` 等于查询值，并且 11 条 checks 都为 `evaluated=true, passed=true`。

## 4. 发送后期望的数据状态

重新执行第 2 节全部 SQL。因为这是只读预检查，发送前后应完全一致：

- `lot` 的状态、Step、设备绑定和 version 不变；
- `equipment` 的 U/D、primary status 和 version 不变；
- `lot_transaction` 总数不变；
- `equipment_history` 总数不变；
- 不产生 Track In 业务事实，也不会占用设备。

若任何快照或历史数量发生变化，说明接口错误地产生了写操作，应停止后续测试并排查。

## 5. 失败场景怎么看

可以使用本来就不满足条件的专用测试数据验证失败清单，但不要直接修改生产或共享业务数据。典型结果：

- version 不一致：`LOT_VERSION_MATCH=false`，建议刷新 Lot；
- Lot 被 Hold：`LOT_RELEASED=false`，建议 Release Hold；
- 设备为 D 或 PROC：`EQUIPMENT_UP` 或 `EQUIPMENT_IDLE=false`；
- 能力不匹配：`EQUIPMENT_CAPABILITY_MATCH=false`；
- 设备已占用：`EQUIPMENT_NOT_OCCUPIED=false`；
- Lot 或设备不存在：对应 EXISTS 规则失败，依赖规则 `evaluated=false`。

无论 allowed 是 true 还是 false，数据库发送前后都必须保持不变。