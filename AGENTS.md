# FabPilot 项目指令

每次处理本项目的请求，先读取 `PROJECT_MEMORY.md`、`docs/DECISIONS.md` 与 `codex对话进度/FabPilot-项目设计与开发交接文档.md`。

- MES Core 是业务唯一事实来源；Agent 不能直写业务数据库。
- 使用 MySQL 8.0；MVP 的 SOP 使用 FULLTEXT 与元数据检索，不引入 pgvector。
- 每次完成实际工作后，更新 `PROJECT_MEMORY.md`：完成项、变更文件、验证结果、下一步与阻塞项。
- 状态变更必须符合状态机、事务、乐观锁、幂等、审批和审计约束。
