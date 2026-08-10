# 通用操作失败记录与 validate_command 设计

## 1. 要解决的问题

Track In、Track Out、Hold、Release Hold、Scrap、设备事件和告警动作都有确定性校验，但失败信息目前主要存在于即时响应和文本日志中。未来 Agent 需要两种公共能力：

1. `validate_command`：执行前进行只读预检查，返回允许与否、全部规则结果和建议。
2. `command_failure_record`：真实命令失败后保存脱敏的结构化现场，可按 Trace ID 查询。

两者都不能绕过领域服务。MES Core 仍是业务唯一事实来源，所有写操作仍由原领域服务执行。

## 2. 业务流程

### 2.1 执行前咨询

```text
用户询问某操作能否执行
  -> Agent 调用 validate_command
  -> MES Core 读取目标快照并执行全部只读规则
  -> 返回通过项、失败项、证据和建议
  -> Agent 用中文解释
```

### 2.2 真实操作失败

```text
用户或页面发送真实命令
  -> 领域服务重新校验全部规则
  -> 命令失败，业务事务回滚
  -> 独立记录组件保存 command_failure_record
  -> 响应返回 errorCode + traceId
  -> Agent 按 traceId 查询失败现场并提出方案
```

预检查通过不代表执行必然成功。预检查与执行之间数据可能变化，真实执行必须重新校验状态机、版本、幂等、权限和审批。

## 3. validate_command 接口

```http
POST /api/command-validations
```

第一版请求对象保持强类型，不使用任意 `Map`：

```json
{
  "commandType": "TRACK_IN",
  "targetType": "LOT",
  "targetCode": "LOT-100",
  "expectedVersion": 3,
  "equipmentCode": "ETCH-01",
  "eventCode": null,
  "alarmId": null,
  "alarmAction": null,
  "reasonCode": null,
  "reasonText": null
}
```

第一版覆盖现有命令：`RELEASE`、`TRACK_IN`、`TRACK_OUT`、`HOLD`、`RELEASE_HOLD`、`SCRAP`、`EXECUTE_EQUIPMENT_EVENT`、`ACKNOWLEDGE_ALARM`、`CLOSE_ALARM`。

响应示例：

```json
{
  "allowed": false,
  "commandType": "TRACK_IN",
  "targetType": "LOT",
  "targetCode": "LOT-100",
  "observedVersion": 3,
  "observedAt": "2026-08-10T16:30:00",
  "checks": [
    {
      "ruleCode": "LOT_READY",
      "passed": true,
      "message": "Lot 当前为 READY"
    },
    {
      "ruleCode": "EQUIPMENT_AVAILABLE",
      "passed": false,
      "errorCode": "EQUIPMENT_OCCUPIED",
      "message": "ETCH-01 已被 LOT-090 占用",
      "suggestedActionTypes": ["SELECT_ALTERNATIVE_EQUIPMENT", "WAIT_FOR_TRACK_OUT"]
    }
  ]
}
```

预检查应尽量返回全部规则结果，而不是遇到第一条失败就停止，让用户和 Agent 一次看到完整问题清单。

## 4. 避免维护两套规则

禁止在 `validate_command` 复制一套 if/else、真实命令服务再维护另一套。

```text
CommandValidationService
  -> CommandValidatorRegistry
      -> ReleaseCommandValidator
      -> TrackInCommandValidator
      -> TrackOutCommandValidator
      -> HoldCommandValidator
      -> EquipmentEventCommandValidator
      -> AlarmActionCommandValidator
```

每个 Validator 调用所属业务模块的 Policy。Policy 返回结构化规则结果；真实执行服务对同一结果执行断言并抛出稳定业务异常。公共注册中心只负责按 `commandType` 路由，不承载 Lot 或 Equipment 的具体业务规则。

## 5. command_failure_record 数据设计

建议字段：

| 字段 | 作用 |
|---|---|
| `id` | 主键 |
| `trace_id` | 关联响应和日志，建立索引 |
| `command_type` | TRACK_IN、TRACK_OUT 等 |
| `target_type` | LOT、EQUIPMENT、ALARM |
| `target_code` | 目标业务编码；告警可保存字符串形式ID |
| `secondary_target_code` | 例如 Track In 的目标设备 |
| `operator_id` | 发起人 |
| `error_code` | 稳定业务错误码 |
| `error_message` | 当时返回的受控消息 |
| `failed_rule_code` | 对应的确定性规则 |
| `retryable` | 数据变化后是否可能重试 |
| `safe_request_json` | 白名单后的业务参数 |
| `state_snapshot_json` | 失败时的关键状态与版本 |
| `occurred_at` | 失败时间 |
| `resolved_trace_id` | 后续成功处理的 Trace ID，可为空 |
| `resolved_at` | 解决时间，可为空 |

### 5.1 事务边界

真实命令失败时，Lot、Equipment 和业务履历必须整体回滚，但失败现场不能一起消失。因此由独立 Spring Bean 使用 `REQUIRES_NEW` 保存失败记录。不得在原失败事务内直接插入记录。

### 5.2 安全边界

- 只记录真实写命令失败；普通查询404和请求格式错误不进入该表。
- 不保存密码、Token、数据库连接、完整请求体或异常堆栈。
- `safe_request_json` 由各命令构造白名单字段，不能直接序列化任意请求对象。
- 未知异常只保存通用错误码和 Trace ID，堆栈仍留在文件日志。

## 6. 失败记录查询

```http
GET /api/command-failures/{traceId}
```

返回命令、目标、错误码、失败规则、安全参数、状态快照、发生时间和解决状态。Agent Tool `get_command_failure_context` 调用该接口，不读取服务器日志文件，也不直接查询数据库。

## 7. 与 Agent 和审批的关系

Agent 第一版只需要两个相关 Tool：

- `validate_command`：执行前回答“能不能做、为什么不能做”。
- `get_command_failure_context`：操作已经失败时按 Trace ID 还原现场。

预检查结果不是执行授权。写操作仍遵循：

```text
诊断 -> propose_action -> 用户审批 -> execute_approved_action
     -> 领域服务重新校验 -> 执行 -> 重新查询验证
```

## 8. 页面衔接

- 命令按钮可先调用 `validate_command`。
- 全部通过时展示预计状态变化，确认后再发送真实命令。
- 存在失败时展示失败规则、当前事实和建议，不发送真实命令。
- 真实命令仍失败时显示 Trace ID，并提供“让 Agent 分析”入口。
- 审批中心和 Agent 工作台复用同一套规则检查展示组件。

## 9. 实现顺序

1. 定义 `CommandType`、规则结果、请求和响应 TO。
2. 先为 Track In 实现 Validator，将现有校验提取为可返回结果的 Policy。
3. 增加 `/api/command-validations` 和只读专项测试。
4. 逐项接入其他现有命令。
5. 增加失败记录迁移、独立记录组件和查询接口。
6. 在真实命令编排处接入失败记录，验证业务事务回滚而失败记录保留。
7. 最后封装为 Agent Tool 并接入页面。

## 10. 第一阶段验收标准

- Track In 预检查一次返回全部关键规则结果。
- 预检查不修改业务表，也不新增业务履历。
- Track In 真实失败后能按 Trace ID 查询结构化现场。
- Lot、Equipment 和履历随失败事务回滚，失败记录仍存在。
- 记录不包含凭证和异常堆栈。
- 预检查与真实执行复用同一份 Policy 规则测试。
