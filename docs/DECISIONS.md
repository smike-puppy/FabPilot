# FabPilot 架构决策记录

## ADR-001：MVP 使用 MySQL 8.0

- 状态：已接受
- 日期：2026-08-04
- 决策：业务、审批、审计与 Agent 运行数据统一保存于 MySQL 8.0。
- 影响：SOP MVP 使用 MySQL FULLTEXT 和元数据过滤；不使用 PostgreSQL 或 pgvector，向量检索第二阶段再评估。

## ADR-002：MES Core 是唯一业务事实来源

- 状态：已接受
- 决策：所有业务写操作由 Spring Boot MES Core 的领域服务执行；Agent 不得直写数据库。

## ADR-003：Lot 使用双状态与不可变履历

- 状态：已接受
- 决策：Lot 分别维护执行状态与 Hold 状态；每次状态变更追加 LotTransaction。

## ADR-004：通用命令预检查与失败现场记录

- 状态：已接受
- 日期：2026-08-10
- 决策：MES Core 提供统一 `validate_command` 只读预检查，并为真实写命令失败保存可按 Trace ID 查询的脱敏结构化现场。
- 规则来源：预检查与真实执行必须复用所属业务模块的同一 Policy，禁止维护两套规则。
- 执行边界：预检查不是授权；真实执行必须重新校验状态机、版本、幂等、权限和审批。
- 事务边界：失败现场由独立组件使用 `REQUIRES_NEW` 保存，业务事务回滚时记录仍保留。
- 安全边界：不保存凭证、完整请求体、数据库连接或异常堆栈；Agent 只通过 MES Core 受控接口查询。
- 详细设计：`docs/COMMAND_VALIDATION_AND_FAILURE_DESIGN.md`。