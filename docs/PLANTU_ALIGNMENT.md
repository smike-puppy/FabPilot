# FabPilot 与 PlantU 的业务对齐原则

## 基线原则

FabPilot 使用简洁的表名、Spring Boot 和 MySQL 实现，但业务概念、实体关系、状态迁移和主要操作流程以赛美特 PlantU 为蓝本。为控制 MVP 范围，可以减少模块和字段，但不随意改变核心业务语义。

## 概念映射

| FabPilot | PlantU 业务概念 |
|---|---|
| `product` | Product |
| `route` | Product Flow / Route Version |
| `route_step` | Step |
| `operation` | 可复用 Operation 定义 |
| `work_order` | Production Order |
| `lot` | Lot Master |
| `lot_transaction` | Lot History / Transaction History |
| `equipment` | Equipment Master |
| `equipment_group` | Equipment Group / Capability Group |
| `equipment_event_definition` | Equipment Event Definition |
| `equipment_history` | Equipment History |

## 必须遵守的 PlantU 语义

1. Step 的 Capability 指向设备组，设备通过组成员关系获得执行资格。
2. 设备状态分为粗粒度 `U/D` 和细粒度 Primary Status。
3. Event 是设备状态变化的原因，状态是执行 Event 的结果。
4. Lot 和 Equipment 都采用“当前主表 + 不可变历史表”。
5. 业务规则由 MES Core 领域服务校验，数据库约束只做最后一道数据保护。
6. 主表保存 `last_transaction/event` 快照，完整事实以历史流水为准。

## MVP 简化

- 一个设备同一时刻最多处理一个 Lot。
- 暂不实现 SubEquipment、Carrier、Recipe、完整 BOM 和物料批次。
- 物料/BOM 模块后续按 Production Order BOM、Material Definition、Material Lot、Equipment Install/Consume/Detach 模型增加。
