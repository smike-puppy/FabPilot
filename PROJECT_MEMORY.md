# FabPilot 项目记忆

> 新对话的首要恢复入口；每次实施、验证或作出重要决定后更新。

## 当前状态

- 阶段：架构设计完成，尚未初始化代码仓库。
- 目标：实现可演示的“轻量 MES + 生产异常诊断 Agent + 审批闭环”MVP。

## 已确认决策

- 数据库：MySQL 8.0。
- 后端：Java 21 + Spring Boot 3。
- Agent：Python 3.11+ + FastAPI；只读、诊断、提议、验证，不直接写业务数据库。
- SOP：MVP 使用 MySQL FULLTEXT 与元数据过滤；向量检索第二阶段评估。
- Lot 使用 `execution_status` 与 `hold_status` 双状态；LotTransaction 只追加。

## 已完成

- 已建立设计基线与项目续接机制。

## 下一步

1. 初始化 Git 单仓库与 `mes-core` Spring Boot 项目。
2. 配置 MySQL 8.0、Flyway、测试基础设施。
3. 建核心表和种子数据。
4. 实现并测试 Lot 状态机。

## 未决项

- 数据访问层：Spring Data JPA 或 MyBatis-Plus（建议 JPA）。
