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
