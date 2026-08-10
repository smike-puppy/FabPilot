# FabPilot 项目记忆

> 新对话的首要恢复入口；每次实施、验证或作出重要决定后更新。

## 当前状态

- 阶段：MES Core 基础环境、V1-V4 数据模型及 MyBatis-Plus 只读诊断分层已完成。
- 目标：实现可演示的“轻量 MES + 生产异常诊断 Agent + 审批闭环”MVP。
- 当前可用接口：`GET /api/health`、`GET /api/lots/{lotCode}/diagnostic-context`。

## 已确认决策

- 数据库：MySQL 8.0，开发端口 `3307`。
- 后端：Java 21 + Spring Boot 3。
- 数据访问层：MyBatis-Plus，替代 Spring Data JPA。
- Agent：Python 3.11+ + FastAPI；只读、诊断、提议、验证，不直接写业务数据库。
- SOP：MVP 使用 MySQL FULLTEXT 与元数据过滤；向量检索第二阶段评估。
- Lot 使用 `execution_status` 与 `hold_status` 双状态；LotTransaction 只追加。
- MES Core 是业务唯一事实来源；所有状态写操作必须经过领域服务。

## 已完成

- 已建立设计基线与项目续接机制。
- 已初始化 `mes-core`（Java 21 + Spring Boot 3）和 MySQL 8.0 开发环境。
- Flyway V1-V4 已完成，包含 PlantU 对齐的核心表结构与 `LOT-013` 异常演示数据。
- `/api/health` 健康检查已完成。
- 已移除 Spring Data JPA 依赖及 Repository/持久化注解，改用 MyBatis-Plus `3.5.17`。
- 已按业务模块建立 `controller/service/mapper/model` 分层。
- 已为 Lot 与 Equipment 模型启用 `@Version`，并配置 MyBatis-Plus 乐观锁插件。
- 已实现 `GET /api/lots/{lotCode}/diagnostic-context`：
  - 聚合 Lot、工单、当前 Step/Operation、当前设备；
  - 返回最近 10 条 Lot 履历和设备履历；
  - Lot 不存在时保留 HTTP 404，并返回项目统一错误响应 `LOT_NOT_FOUND`。
- 已新增诊断 Service 单元测试和 Controller 接口测试。
- 已为本次新增的 MyBatis-Plus 配置、Controller、Service、Model 与 Mapper 补充关键业务语义中文注释。
- 已建立项目级 API 公共能力：统一泛型响应、错误码契约、业务异常、全局异常处理和 Trace ID 过滤器。
- 健康检查与 Lot 诊断接口均已接入统一响应；诊断业务 DTO 仍归属 `diagnostic` 模块，Service 不依赖 HTTP 响应信封。
- 已统一既有编码命名：数据库持久化模型按表名使用驼峰命名（如 `lot_transaction` 对应 `LotTransaction`），DTO 使用 `TO` 后缀（如 `LotDiagnosticContextTO`）；现有业务组件使用 `@Autowired` 自动注入。
- 已开始 Lot 写侧第一项 Release：新增 Release 请求/结果 TO、Controller、事务 Service 与模块错误码；事务内条件更新 Lot、追加 Release 履历，并校验版本与幂等键。尚待补齐专用单测和 Postman 命令请求后进行真实 MySQL 调用。

## 本次变更文件

- `mes-core/pom.xml`
- `mes-core/src/main/resources/application.yml`
- `mes-core/src/main/java/com/fabpilot/mescore/config/MybatisPlusConfig.java`
- `mes-core/src/main/java/com/fabpilot/mescore/{lot,equipment,process,workorder}/{model,mapper}/`
- `mes-core/src/main/java/com/fabpilot/mescore/diagnostic/{controller,model,service}/`
- `mes-core/src/test/java/com/fabpilot/mescore/diagnostic/`
- `mes-core/src/main/java/com/fabpilot/mescore/config/MybatisPlusConfig.java`
- `mes-core/src/main/java/com/fabpilot/mescore/{diagnostic,lot,equipment,process,workorder}/`（注释完善）
- `mes-core/src/main/java/com/fabpilot/mescore/common/{api,error,trace}/`
- `mes-core/src/main/java/com/fabpilot/mescore/health/{HealthController.java,dto/HealthStatus.java}`
- `mes-core/src/main/java/com/fabpilot/mescore/diagnostic/{controller,dto,exception,service}/`
- `mes-core/src/test/java/com/fabpilot/mescore/{health,diagnostic}/`
- `PROJECT_MEMORY.md`

## 验证结果

- 使用 Temurin JDK `21.0.12` 执行 `mvn test`：5 个测试全部通过，0 失败、0 错误。
- 注释完善后再次执行 `mvn test`：5 个测试全部通过，0 失败、0 错误。
- Docker 中 `fabpilot-mysql`（MySQL 8.4，宿主机端口 3307）状态为 healthy。
- 真实调用 `GET /api/lots/LOT-013/diagnostic-context` 成功，确认：
  - Lot 为 `RUNNING + HELD`，version = 4；
  - 当前 Step 为 `STEP-ETCH-020`；
  - 当前设备 `ETCH-01` 为 `D + DOWN`；
  - 最近事件为 `VACUUM_LOW`；
  - 返回 4 条 Lot 履历和 2 条设备履历。
- 真实链路验证使用的临时 MES Core 进程已关闭。
- 统一响应重构后再次使用 Temurin JDK `21.0.12` 执行 `mvn test`：5 个测试全部通过，0 失败、0 错误。
- 编码规范迁移后再次使用 Temurin JDK `21.0.12` 执行 `mvn test`：5 个测试全部通过，0 失败、0 错误；已验证 `@Autowired` 字段注入与重命名后的 MyBatis-Plus 泛型映射。
- 在端口 8081 临时启动新版本并连接 MySQL 3307，真实验证：
  - `GET /api/health` 返回 HTTP 200、`SUCCESS` 和强类型健康数据；
  - `GET /api/lots/LOT-013/diagnostic-context` 返回 HTTP 200、统一响应信封和完整诊断数据；
  - `GET /api/lots/LOT-404/diagnostic-context` 返回 HTTP 404 和 `LOT_NOT_FOUND`；
  - 三类响应的 `X-Trace-Id` 响应头均与响应体 `traceId` 一致。
- 临时 8081 进程及测试日志已清理；用户通过 IntelliJ 启动的原 8080 进程未改动。

## 下一步

1. 将后续新增接口持续接入统一 `ApiResponse`、模块错误码和 `BusinessException`，避免重复响应结构。
2. 实现 Lot 写侧应用服务和状态机：Release、Track In、Track Out、Hold、Release Hold、Scrap。
3. 在同一事务内完成 Lot 快照更新与 LotTransaction 追加。
4. 为写操作补齐乐观锁冲突、幂等键、设备能力/占用校验及集成测试。
5. 增加设备事件写侧服务，保证设备主表和 EquipmentHistory 同事务更新。
6. 在写侧稳定后实现 Agent Tool API 与审批闭环。

## 代码协作约定

- 每次编写代码前，先向用户说明本次实现的整体流程、涉及模块和验证方式。
- 代码为关键业务规则、状态机、事务边界、幂等/乐观锁校验和复杂查询提供简明中文注释；不添加重复代码含义的无效注释。
- Java 代码保持标准缩进、空行与换行：禁止把多个字段、注解、判断或语句压缩在同一行；避免通配符导入，长链式调用按语义换行，TO 使用类注释和 `@param` 说明字段业务含义。
- 采用“业务模块优先 + 公共能力集中”的结构：统一响应、错误码、异常处理、追踪等放入 `common`，业务 DTO 留在所属模块。
- 新接口使用统一泛型响应包装，禁止各模块重复定义 success/code/message/trace 等通用字段；同时保留强类型业务响应 DTO，避免退化为 Map。
- Spring 组件依赖使用 `@Autowired` 自动注入；DTO 目录中的传输对象统一使用 `TO` 后缀，避免与数据库持久化模型混淆。
- 数据库持久化模型的类名严格按数据库表名转换为大驼峰：如 `lot` → `Lot`、`lot_transaction` → `LotTransaction`，不使用 `Entity` 后缀。
- Postman Desktop 的工作区登记文件为 .postman/resources.yaml，可维护请求、环境和断言的项目资源统一放在根目录 postman/。后续每次新增或调整 API，应先同步完善对应 Postman 请求、环境变量与成功/失败断言，再进行接口实测；环境文件不得提交真实密码、Token 或其他敏感值。
- 已将现有 Health、Lot 诊断成功/404 Postman 请求断言迁移到统一 `ApiResponse` 信封；Lot 写侧命令请求将在接口契约落地时同步新增。

## 阻塞与注意项

- 当前系统默认 Maven 仍使用 JDK 10；运行项目或测试前需将 `JAVA_HOME` 和 `Path` 指向 `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`。
- Codex 文件编辑沙箱仍会出现 `helper_unknown_error`，且外部 `apply_patch` 被 Windows 拒绝执行；本次在用户授权下对明确目标文件进行了精确写入。后续会话应先重试补丁工具并核对 Git 差异。
- PowerShell 5 的 `Invoke-RestMethod | ConvertTo-Json` 终端显示中文时可能出现乱码；数据库和 HTTP 字段的业务值已正确返回，不影响本次结构验收。

### 2026-08-06：Release 状态字面量类型化

- 已将 Release 流程中的 Lot 执行状态、Hold 状态和交易类型分别提取为 `LotExecutionStatus`、`LotHoldStatus`、`LotTransactionType` 枚举。
- 跨业务模块复用的操作者类型提取为 `common.enums.OperatorType`，Release 服务不再使用本地字符串常量参与校验、更新和审计记录。
- 编码约定补充：领域专属值保留在所属业务模块；只有真正跨模块的稳定概念进入 `common`，避免形成臃肿的全局 `CommonConstants`。
- 变更文件：`lot/enums/`、`common/enums/OperatorType.java`、`lot/service/impl/LotCommandServiceImpl.java`。
- 验证结果：使用 JDK 21 执行 `mvn test`，5 个测试全部通过；`git diff --check HEAD` 通过。
- 下一步：为 Release 补充状态机、乐观锁冲突、幂等重放等专项测试和 Postman 请求。
### 2026-08-06：数据传输类统一改用 Lombok

- 根据项目编码偏好，已移除 `mes-core` 中全部 Java `record`，统一改为 Lombok `@Data` 普通类。
- 数据类同时使用 `@NoArgsConstructor` 和 `@AllArgsConstructor`，兼顾 Jackson 反序列化与现有强类型构造调用。
- 已迁移 `ApiResponse`、`HealthStatusTO`、Release 请求/结果 TO、`LotDiagnosticContextTO` 及其内部快照类，并将旧式 record 访问器改为 JavaBean getter。
- 编码约定补充：本项目后续 DTO/TO 和统一响应不使用 `record`，优先采用带清晰字段注释的 Lombok `@Data` 类。
- 验证结果：JDK 21 编译 42 个主源码文件成功；5 个测试全部通过；源码无 `record` 和旧式访问器残留；行宽及 `git diff --check HEAD` 检查通过。
- 下一步：继续补充 Release 专项单元测试和 Postman 请求。
### 2026-08-06：异常日志文件与 Trace ID 落地

- 新增 `logback-spring.xml`，日志同时输出到控制台和 `mes-core/logs/mes-core.log`，每行自动包含 MDC 中的 `traceId`。
- 文件日志按日期与 20MB 大小滚动，保留 30 天，总容量上限 1GB；可通过 `LOG_PATH` 环境变量修改目录。
- `GlobalExceptionHandler` 对业务异常记录 `WARN`（方法、路径、错误码、受控消息），对未知异常记录带完整堆栈的 `ERROR`；不记录请求体、凭据等敏感数据。
- 验证结果：JDK 21 编译 42 个主源码文件成功，5 个测试全部通过；测试已实际生成 `logs/mes-core.log`，并确认 `LOT_NOT_FOUND` 日志包含请求 Trace ID。
- 使用方式：从 API 响应头或响应体取得 `traceId`，在 `mes-core/logs/mes-core.log` 中全文搜索即可定位该请求。
### 2026-08-06：Release 接口真实链路测试

- 新增 Flyway `V5__seed_release_test_lot.sql`，追加专用测试 Lot `LOT-015`，初始状态为 `CREATED + RELEASED`、version=0，不修改既有 V1-V4。
- 新增 Postman `Lot Command` 下的 Release 命令与幂等重放请求，并补充 `lotCode_release`、`releaseExpectedVersion`、`releaseIdempotencyKey`、`operatorId` 环境变量和响应断言。
- 通过临时 8081 最新实例执行 Flyway V5，并经 MES Core 接口完成真实 Release；临时实例验证后已关闭，用户 IntelliJ 8080 进程未停止。
- 实测数据库变化：LOT-015 从 `CREATED` 变为 `READY`，保持 `RELEASED`，当前 Step 设为 `STEP-CLEAN-010`，version 从 0 变为 1，last_transaction_code=`RELEASE`。
- 履历验证：新增且仅新增一条 RELEASE LotTransaction，记录前后状态、USER/POSTMAN-USER、幂等键及 0→1 版本；重复调用返回 `idempotent=true`，履历总数仍为 1。
- 验证结果：JDK 21 构建成功，5 个测试通过；8080 实际请求成功；`git diff --check HEAD` 通过。Postman CLI 未安装，因此 YAML 由 Postman Desktop Local View 读取验证。
- 下一步：补齐 Release 状态非法、版本冲突、幂等键冲突的专项自动化测试，再进入 Track In。
### 2026-08-07：重建 Postman 本地开发环境

- 修复 `FabPilot-MES-Core.environment.yaml` 缺少 `$kind: environment` 导致 Postman Desktop 无法注册环境的问题。
- 环境重命名为 `FabPilot MES Core | Local Development`，变量按 `app_*`、`lot_query_*`、`lot_release_*`、`test_*` 四层组织。
- 同步更新 Health、Lot Diagnostic 和 Lot Command 全部请求变量引用，旧变量名已无残留。
- 验证结果：9 个环境变量与所有请求引用一一对应；`git diff --check HEAD` 通过；通过 `app_base_url` 对应的 8080 Health 接口真实返回 SUCCESS。
- Windows `computer-use` 因现有 `helper_unknown_error` 无法初始化，需用户在 Postman 中刷新 Local View 并选择新环境完成 UI 验收。
### 2026-08-07：修复 Postman 诊断断言连锁报错

- 确认后端 LOT-013 与 LOT-014 诊断响应结构正确；`undefined` 来自 Postman 旧请求/错误 Lot 响应仍执行成功场景嵌套断言。
- Held 与 Ready 诊断脚本改为先验证 HTTP 状态及统一响应信封，仅在 `response.data.lot` 存在时执行字段和数组断言，避免连续出现 `Cannot read properties of undefined`。
- 修复 Ready Lot 的字段路径：`json.currentEquipment` 改为 `json.data.currentEquipment`（脚本内为 `diagnostic.currentEquipment`）。
- 删除用户在 Postman 中误建且内容确认为 `values: []` 的空 `New Environment`，正式环境仅保留 `FabPilot MES Core | Local Development`。
- 验证结果：LOT-013/014 只读接口均返回 SUCCESS；环境目录仅保留正式环境；项目文件无 `baseUrl/lotCode_release` 等旧引用；`git diff --check HEAD` 通过。
- 注意：Postman History 中的旧请求不会随本地 YAML 更新，必须从 Collection 目录重新打开请求，不能重放含旧变量名的历史记录。
### 2026-08-07：修复 Postman Lot Command 文件夹未加载

- 确认 `Lot Command` 目录及两条 Release 请求文件均存在；Postman 跳过目录的原因是 `X-Trace-Id` 值以未加引号的 `{{test_trace_prefix}}` 开头，构成非法 YAML 映射语法。
- 两条请求的 Header 已改为带单引号的完整字符串：`'{{test_trace_prefix}}-LOT-015-RELEASE-*'`。
- 验证结果：两个文件均保留 `$kind: http-request`、正确 URL/Body/脚本，Header 文本检查通过，`git diff --check HEAD` 通过。
- 本机 Postman CLI、PyYAML 与 Node YAML 库均不可用，且 `computer-use` 受 `helper_unknown_error` 阻断；最终 UI 验收需在 Postman 刷新 Local View 后确认 `Lot Command` 重新出现。

### 2026-08-07：完成 Lot Track In 写侧闭环

- 新增 `POST /api/lots/{lotCode}/track-in`，请求携带 `expectedVersion`、`idempotencyKey`、`operatorId` 与 `equipmentCode`。
- 状态机约束：仅允许 `READY + RELEASED` 且未绑定设备的 Lot 上机；当前 Route Step 必须有效。
- 设备约束：设备必须存在、处于 `U + IDLE`、属于当前 Step 要求的设备能力组且未被其他 Lot 占用。
- 事务内使用 Lot 与 Equipment 双乐观锁条件更新：Lot 变为 `RUNNING` 并绑定设备，Equipment 变为 `PROC`；任一步失败时整体回滚。
- 同一事务追加 `LotTransaction(TRACK_IN)` 与 `EquipmentHistory(TRACK_IN)`，记录 Step、Operation、Equipment、前后状态、操作者、幂等键和版本。
- 幂等重放同时比较 Lot、命令类型与目标设备；相同命令直接返回当前结果，不重复更新快照或插入履历。
- 新增稳定错误码：`EQUIPMENT_NOT_FOUND`、`EQUIPMENT_STATE_INVALID`、`EQUIPMENT_CAPABILITY_MISMATCH`、`EQUIPMENT_OCCUPIED`。
- 修复请求体 `@Valid` 异常被误报为 500 的问题，现统一返回 HTTP 400 与 `VALIDATION_ERROR`。
- 新增 Flyway `V6__seed_track_in_test_lot.sql`，使用独立测试 Lot `LOT-016`；新增 Postman Track In 首执行与幂等重放请求及环境变量。
- 主要变更文件：
  - `mes-core/src/main/java/com/fabpilot/mescore/lot/{controller,dto,exception,model,service}/`
  - `mes-core/src/main/java/com/fabpilot/mescore/equipment/{mapper,model}/`
  - `mes-core/src/main/java/com/fabpilot/mescore/common/error/`
  - `mes-core/src/main/resources/db/migration/V6__seed_track_in_test_lot.sql`
  - `mes-core/src/test/java/com/fabpilot/mescore/lot/`
  - `postman/collections/FabPilot MES Core/Lot Command/`
  - `postman/environments/FabPilot-MES-Core.environment.yaml`
- 验证结果：
  - JDK 21 全量 `mvn test`：12 个测试全部通过，0 失败、0 错误。
  - `git diff --check HEAD` 通过。
  - MySQL 8.4 真实链路：首次 Track In 返回 `RUNNING/version=1/idempotent=false`；同键重放返回 `idempotent=true`。
  - 数据库核对：`LOT-016` 绑定 `ETCH-02`，Lot 为 `RUNNING + RELEASED`，设备为 `PROC`；LotTransaction 与 EquipmentHistory 各且仅新增 1 条。
  - 临时 8081 实例及验证日志已清理；用户 IntelliJ 8080 进程未改动。
- 下一步：实现 Track Out，复用当前命令支持与双快照/双履历事务模式，并处理下一 Step 与末工序完成分支。
- 阻塞项：无。
### 2026-08-07：完成 Lot Track Out 写侧闭环

- 新增 POST /api/lots/{lotCode}/track-out；设备由 Lot 当前绑定关系确定。
- 仅允许 RUNNING + RELEASED 且已绑定 Step 和 Equipment 的 Lot 下机。
- 普通工序推进到下一 Step 并变为 READY；末工序变为 COMPLETED 并写入 completed_at。
- 同一事务释放 Equipment 为 U + IDLE，并追加 LotTransaction 与 EquipmentHistory。
- 支持 Lot/Equipment 乐观锁与 Track Out 幂等重放。
- 新增 Service/Controller 自动化测试、静态 Postman 请求及 docs/TRACK_OUT_MANUAL_TEST.md；按用户要求未执行 Postman/HTTP 请求。
- 全量验证：JDK 21 mvn test 共 19 个测试，失败 0、错误 0、跳过 0；git diff --check HEAD 通过。
- 只读数据库核对：LOT-016 为 RUNNING + RELEASED、version=1、STEP-ETCH-020 / ETCH、绑定 ETCH-02；设备 U + PROC、version=1，completed_at=NULL；目标幂等键两类履历均为 0。
- 下一步：用户按人工测试文档执行 Track Out 并返回结果，再继续 Hold / Release Hold。
- 阻塞项：无。
### 2026-08-08：完成 Lot Hold 写侧闭环

- 新增 POST /api/lots/{lotCode}/hold，请求包含 expectedVersion、idempotencyKey、operatorId、reasonCode 与 reasonText。
- 仅允许 READY 或 RUNNING 且 hold_status=RELEASED、当前 Step 有效的 Lot 执行 Hold。
- Hold 保持 execution_status 和当前设备绑定不变，仅将 hold_status 从 RELEASED 更新为 HELD。
- 同一事务条件更新 Lot 并追加 LotTransaction(HOLD)，记录 Step、Operation、当前 Equipment、原因、操作者和版本。
- Hold 不更新 Equipment 或 EquipmentHistory；设备 Alarm/Down 由独立设备事件服务负责。
- 幂等重放严格比较 Lot、命令类型、reasonCode 和 reasonText，不同原因复用同一键返回 IDEMPOTENCY_CONFLICT。
- 新增 Service、Controller 和幂等支持专项测试；全量 JDK 21 mvn test 共 29 个测试，失败 0、错误 0、跳过 0。
- 新增静态 Postman 首执行/重放请求、环境变量和 docs/HOLD_MANUAL_TEST.md；按用户要求未执行 Postman/HTTP 请求。
- 只读数据库确认测试前 LOT-016 为 READY + RELEASED、version=2、STEP-INSPECT-030、无绑定设备。
- 下一步：用户按人工文档测试 Hold；随后实现 Release Hold。
- 阻塞项：无。
### 2026-08-08：完成 Lot Release Hold 写侧闭环

- 新增 `POST /api/lots/{lotCode}/release-hold`，请求包含 expectedVersion、idempotencyKey、operatorId、reasonCode 和 reasonText。
- 仅允许 `READY` 或 `RUNNING`、`hold_status=HELD` 且当前 Step 有效的 Lot 解除暂停。
- Release Hold 保持 execution_status、当前 Step 和设备绑定不变，仅将 hold_status 从 `HELD` 更新为 `RELEASED`。
- 同一事务使用版本及状态条件更新 Lot，并追加 `LotTransaction(RELEASE_HOLD)`；履历记录解除原因、操作人、Step、Operation、当前 Equipment 和版本。
- 不更新 Equipment 或 EquipmentHistory；幂等重放严格比较命令类型及两个原因字段。
- 新增 Service 6 个专项测试、Controller 成功/校验测试、静态 Postman 首次/重放请求及 `docs/RELEASE_HOLD_MANUAL_TEST.md`。
- 验证结果：JDK 21 完整 `mvn test` 共 37 个测试，失败 0、错误 0、跳过 0；`git diff --check` 通过。
- 按用户要求未执行 Postman/HTTP 请求，也未写业务数据库；只读确认测试前 `LOT-016 = READY + HELD`、version=3、`STEP-INSPECT-030`、无设备，Release Hold 目标履历为 0。
- 下一步：用户按人工测试文档执行 Release Hold 并核对数据库；之后实现 Scrap。
- 阻塞项：无。
### 2026-08-08：完成 Lot Scrap 写侧闭环

- 新增 `POST /api/lots/{lotCode}/scrap`，原因字段进入不可变 LotTransaction。
- 状态机遵循设计基线：任意非终态 Lot 可转为 `SCRAPPED + RELEASED`；`COMPLETED` 和 `SCRAPPED` 拒绝再次报废。
- Scrap 保留当前 Step 作为终态上下文，清空设备绑定，不设置 `completed_at`，并使用版本、原执行状态、原 Hold 状态及设备绑定作为条件更新。
- 无设备场景只更新 Lot 并追加 `LotTransaction(SCRAP)`；绑定 `PROC` 设备时，同事务将设备释放为 `IDLE` 并追加 `EquipmentHistory(SCRAP)`；已由独立异常流程进入其他状态的设备不会被 Scrap 覆盖。
- 幂等重放严格比较 Lot、命令类型、reasonCode 和 reasonText；同键不同原因返回冲突。
- 新增 5 个 Scrap Service 专项测试、2 个 Controller 测试、静态 Postman 首次/重放请求和 `docs/SCRAP_MANUAL_TEST.md`。
- 修复 Postman 环境中 Hold/Release Hold 中文原因值与 `enabled` 粘连的 YAML 格式问题，并追加 Scrap 变量。
- 验证结果：JDK 21 完整 `mvn test` 共 44 个测试，失败 0、错误 0、跳过 0；`git diff --check` 通过。
- 按用户要求未执行 Postman/HTTP 请求，也未写业务数据库；只读确认测试前 `LOT-016 = READY + RELEASED`、version=4、`STEP-INSPECT-030`、无设备，Scrap 目标履历为 0。
- 下一步：用户按人工测试文档执行 Scrap 并核对数据库；Lot MVP 写侧命令已完整，随后进入设备事件写侧服务。
- 阻塞项：无。
### 2026-08-08：补充 Lot 写侧业务规则注释

- 为 Release、Track In、Track Out、Hold、Release Hold、Scrap 六个命令入口补充“前置状态、执行步骤、成功结果”的流程注释。
- 为全部状态校验、工艺/设备校验、条件快照更新和 Lot/Equipment 履历方法补充规则依据及字段变化说明。
- 重写 Controller、LotCommandService、LotCommandSupport、公共版本请求 DTO、六个请求 DTO 和双状态枚举中的乱码注释，统一为 UTF-8 中文业务说明。
- 静态验证：`git diff --check` 通过，关键规则注释检索覆盖成功。
- 后续重构完成后已执行 JDK 21 完整 `mvn test`，最终 51 个测试全部通过，确认注释整理未改变业务行为。
- 下一步：后续新增代码继续使用相同的“业务规则、执行步骤、规则理由”注释方式。
- 阻塞项：无。
### 2026-08-08：人工测试文档增加“业务流程与编码依据”约定

- 后续每个 `*_MANUAL_TEST.md` 在测试前 SQL 和请求示例之前，必须增加“业务流程与编码依据”章节。
- 该章节使用中文按实际代码执行顺序描述：功能目标、允许的前置状态、幂等检查、版本校验、状态/资源校验、事务内快照更新、不可变履历、成功结果和失败边界。
- 每一项关键校验需要同时说明“校验什么”和“为什么校验”，让测试人员在阅读代码前先掌握实现大纲和编码方向。
- 对涉及 Lot 与 Equipment 的命令，还要明确两个聚合分别是否变化、为什么变化，以及失败时如何依靠事务回滚保持一致。
- 文档建议固定结构：1. 业务目标；2. 状态迁移；3. 执行顺序与理由；4. 事务和并发保护；5. 测试前数据；6. 请求；7. 测试后预期；8. 幂等重放与异常场景。
### 2026-08-08：统一改造现有人工测试文档

- 已重写 `TRACK_OUT_MANUAL_TEST.md`、`HOLD_MANUAL_TEST.md`、`RELEASE_HOLD_MANUAL_TEST.md`、`SCRAP_MANUAL_TEST.md`，修复原有中文乱码。
- 四份文档均增加“业务流程与编码依据”，包含业务目标、状态迁移、代码执行顺序、每项校验理由以及事务一致性说明。
- Track Out 文档明确普通工序与末工序分支；Scrap 文档明确无设备、PROC 设备及 DOWN/MAINTENANCE 设备三种资源处理分支。
- 保留并整理测试前 SQL、请求 JSON、首次响应、发送后 SQL、数据表预期、幂等重放和异常场景。
- 验证结果：四份文档的规定章节均存在，常见乱码扫描为 0，`git diff --check` 通过；未执行 Postman/HTTP 请求，未修改业务数据库。
- 下一步：后续新增人工测试文档继续使用相同模板。
- 阻塞项：无；代码注释整理已随公共逻辑重构完成 Maven 回归。
### 2026-08-08：完成 Lot 命令公共逻辑重构

- 重构目标：减少 Release、Track In、Track Out、Hold、Release Hold、Scrap 六个命令之间的重复代码，同时保持状态机、事务、乐观锁、幂等和审计语义不变。
- 新增 `LotStatePolicy`，集中声明六个命令的状态准入规则；Service 通过 `assertCanRelease/TrackIn/TrackOut/Hold/ReleaseHold/Scrap` 调用具名规则，代码审查时可以直接看到每个动作的业务前提。
- 新增 `LotTransactionFactory` 与 `LotTransactionRecordTO`，统一构造 LotTransaction 的 Lot、命令类型、工艺位置、设备、状态前后值、操作人、原因、幂等键、版本和发生时间等字段。
- 新增 `EquipmentHistoryFactory` 与 `EquipmentHistoryRecordTO`，统一构造 EquipmentHistory 的设备状态前后值、事件、操作人、角色、幂等键、版本和发生时间等字段。
- 各命令原有 `append...Transaction` / `append...EquipmentHistory` 方法保留为薄业务适配层：只声明该命令特有的状态变化和上下文，再交给公共工厂构造，避免公共字段在六个命令中重复维护。
- 新增 `LotCommandTestFixture`，统一创建 Lot、RouteStep 和 Equipment 的 Mockito 领域快照，减少测试准备代码重复。
- 新增 `LotStatePolicyTest`、`LotTransactionFactoryTest`、`EquipmentHistoryFactoryTest` 共 7 个专项测试，覆盖状态准入和公共履历字段映射。
- `LotCommandServiceImpl` 由约 1011 行降至 853 行；源码中已不存在分散的六组 `validate...State` 方法，也不存在直接 `new LotTransaction()` / `new EquipmentHistory()` 的重复构造。
- 按用户要求未重构 `ReasonedLotCommandRequestTO`，未统一时间来源，也未提取公共审计字段模型。
- Handler 拆分评估：去重后剩余代码主要是六个命令各自的单事务业务编排；当前不拆成六个仅搬运代码的 Handler，避免增加构造依赖和跨类跳转。后续单个命令继续增长或出现独立依赖边界时再拆分。
- 验证结果：新增专项测试 7/7 通过；JDK 21 完整 `mvn test` 共 51 个测试，失败 0、错误 0、跳过 0；`git diff --check` 通过。
- 验证边界：按用户要求未执行 Postman/HTTP 请求，未读取或写入业务数据库；本轮仅进行了代码编译、单元/Controller 测试和静态差异检查。
- 变更文件：`LotCommandServiceImpl.java`、`LotStatePolicy.java`、`LotTransactionFactory.java`、`LotTransactionRecordTO.java`、`EquipmentHistoryFactory.java`、`EquipmentHistoryRecordTO.java`、`LotCommandTestFixture.java` 及三个对应专项测试文件。
- 下一步：用户可按照现有人工测试文档自行执行 HTTP 测试并核对请求前后的表状态；代码主线可进入设备事件写侧服务。
- 阻塞项：无。
### 2026-08-08：完成设备故障事件写侧

- 业务场景：设备在加工中发生 `VACUUM_LOW` 等故障，MES 根据受控事件定义将设备从 `U + PROC` 更新为 `D + DOWN`，并追加不可变 EquipmentHistory；本接口不自动修改或 Hold 设备上绑定的 Lot。
- 新增 `POST /api/equipment-events/faults`。设备编号和事件编号统一放在请求体中，避免路径参数与业务参数分散。
- 请求包含 `equipmentCode`、`eventCode`、`expectedVersion`、`idempotencyKey`、`operatorId`、`reasonCode` 和 `reasonText`。
- 服务先检查幂等历史，再校验设备版本、ACTIVE 事件定义、故障目标状态和事件来源状态；相同设备、事件及原因的同键重放直接返回，不重复更新和写历史。
- 设备快照使用设备 ID、原版本、原 U/D 状态和原主状态作为条件更新；快照更新与 EquipmentHistory 插入在同一事务内，失败整体回滚。
- 故障事件只允许目标为 `D + DOWN`，不能通过该接口执行 Track In、Track Out、开始维修或完成维修。
- 扩展 EquipmentHistory 公共工厂，使其能够记录 U/D 状态变化、操作来源、业务角色和原因；保留 Lot 命令原有 `USER + MANUFACTURING` 默认语义。
- 新增 Service 5 个专项测试、Controller 2 个测试；连同公共履历工厂测试，设备故障专项测试 8/8 通过。
- 验证结果：JDK 21 完整 `mvn test` 共 58 个测试，失败 0、错误 0、跳过 0；`git diff --check` 通过。
- 新增 `docs/EQUIPMENT_FAULT_MANUAL_TEST.md`，说明业务流程、校验理由、请求前表状态和请求后预期状态。
- 按用户要求未执行 Postman/HTTP 请求，未启动服务，也未读取或写入业务数据库。
- 下一步：实现 Alarm 实体及告警生命周期，让设备故障事件关联一条可确认、可关闭的告警记录；随后实现维修单和 Agent 处置建议。
- 阻塞项：无。
### 2026-08-08：补充设备故障 Postman 测试集

- 在 `postman/collections/FabPilot MES Core/Equipment Event` 新增 8 个设备故障请求，按 `order=9000~16000` 排列，可按顺序覆盖完整测试流程。
- 测试场景包括：必填参数为空、设备版本过期、未知事件、非故障事件、首次故障成功、幂等重放、同键修改原因冲突、设备已 DOWN 后再次发起新故障。
- 每个请求包含 2 条 `afterResponse` 断言，共 16 条，校验 HTTP 状态、统一响应 code 和关键业务结果。
- 首次成功用例会把响应版本写入 `equipment_fault_result_version`，供最后一个“已 DOWN 状态不允许再次执行 U/PROC→D/DOWN”用例使用。
- Postman 环境新增设备编号、事件、初始版本、结果版本、幂等键、上报来源和原因等 13 个设备故障变量。
- 执行顺序要求：前四个失败用例不改数据；第五个成功用例要求目标设备测试前为 `U + PROC` 且主幂等键未使用；后续三个用例依赖第五个结果。
- 数据准备不允许直接 UPDATE 设备快照；若目标设备不是 `U + PROC`，应先通过正常 Track In 业务准备，或选择符合条件的设备。当前未发现需要额外 INSERT 的独立测试数据。
- 静态验证：8 个请求文件存在，16 条 Postman 断言存在，15 个变量引用均能在环境中找到定义，`git diff --check` 通过。
- 本次按用户要求只添加静态请求和测试脚本，未执行 Postman、未发送 HTTP 请求、未读写业务数据库。
- 下一步：用户按顺序运行 Equipment Event 文件夹并根据 `docs/EQUIPMENT_FAULT_MANUAL_TEST.md` 核对 equipment 与 equipment_history。
- 阻塞项：本地运行时没有 YAML 解析模块，因此未做第三方 YAML parser 校验；文件结构严格复用现有 Postman YAML 格式。

## 2026-08-08 设备事件通用状态切换更正

- 更正范围：此前的 `/api/equipment-events/faults` 故障专用实现与测试已被本节取代。
- 完成业务：改为 `POST /api/equipment-events`，按 ACTIVE `equipment_event_definition` 执行任意合法状态转换；支持 `USER/SYSTEM` 操作者来源、按定义校验原因、乐观锁、幂等和同事务历史审计。
- 专用数据：创建 `EQP-STATE-TEST-01`，当前已核验为 `D/DOWN v0`、无 Lot 绑定、无设备历史；V7 迁移保证环境重建后也能获得该测试设备。
- Postman：替换为开始维护、幂等重放、完成维护、非法来源状态四个请求；未执行请求。
- 变更文件：设备事件 Controller/Service/DTO/错误码/测试，`V7__seed_equipment_event_test_device.sql`，Postman Equipment Event 目录及环境，`docs/EQUIPMENT_EVENT_MANUAL_TEST.md`。
- 验证：JDK 21 执行 `mvn test` 成功，58 个测试全部通过（0 failures / 0 errors / 0 skipped）；未执行 Postman 请求。
- 下一步：用户按文档顺序手工发送 Postman，并核对最终设备快照和两条历史。
## 2026-08-08 Postman 请求取消 Environment 变量

- 按用户要求，将 `postman/collections` 下 21 个现有 YAML 请求中的全部 `{{...}}` 替换为请求内直接可见的固定值。
- 固定内容包括 `http://localhost:8080`、Lot/设备编码、版本、幂等键、操作人、原因和 Trace ID；响应断言中的占位符也已同步替换。
- Equipment Event 请求直接使用专用设备 `EQP-STATE-TEST-01`；现有 Environment 文件保留作历史配置，但所有请求均不再依赖它。
- 验证：collection 内 `{{...}}` 剩余 0 处，`pm.environment` 调用剩余 0 处；未执行任何 Postman 请求。
## 2026-08-08 告警生命周期第一步：故障自动建告警

- 业务场景：设备事件目标为 `D + DOWN` 时，除更新设备快照和追加 EquipmentHistory 外，同事务创建一条 `ACTIVE` 设备告警；维护开始/完成等非故障事件不创建告警。
- 新增 V8 `equipment_alarm` 表，状态预留 `ACTIVE -> ACKNOWLEDGED -> CLOSED`，包含严重度、来源事件、来源幂等键、消息、确认/关闭人员与时间、版本字段。
- 告警的 `source_idempotency_key` 唯一，并复用设备事件幂等键；相同事件重放在进入写流程前返回，因此不会重复建告警，数据库唯一约束作为并发下的最后保护。
- 告警创建失败会使设备快照和 EquipmentHistory 一并回滚，避免设备已 Down 但没有对应告警。
- 新增文件：`V8__create_equipment_alarm.sql`、`EquipmentAlarm.java`、`EquipmentAlarmMapper.java`；修改 `EquipmentEventServiceImpl` 和对应单元测试。
- 验证：设备事件定向测试通过；JDK 21 全量 `mvn test` 共 58 个测试，0 failures / 0 errors / 0 skipped；未执行 Postman/HTTP，未改变测试设备数据。
- 下一步：实现告警确认与关闭命令、告警动作历史；关闭前要求设备已经通过维修完成恢复为 `U + IDLE`。
- 阻塞项：无。
## 2026-08-08 告警确认与关闭闭环

- 新增 `POST /api/equipment-alarms/actions`：请求体使用 `alarmId`、`action`（`ACKNOWLEDGE`/`CLOSE`）、`expectedVersion`、`idempotencyKey` 和 `operatorId`，不依赖路径状态或隐式上下文。
- 状态规则：仅 `ACTIVE` 可确认；仅 `ACKNOWLEDGED` 可关闭；关闭前重新读取设备并要求为 `U + IDLE`，避免设备仍停机时提前结束异常。
- 新增 V9 `equipment_alarm_action_history`，每个确认/关闭动作记录操作者、幂等键、前后版本和发生时间；相同幂等键返回当前结果，复用不同动作/告警则拒绝。
- 告警快照条件更新同时比较 id、version、当前状态；更新和动作历史追加同事务，处理并发冲突时不留下半条审计记录。
- 验证：JDK 21 执行 `mvn test` 成功；未执行 Postman/HTTP，未写入业务测试数据。
- 下一步：为告警确认/关闭补充专用测试设备数据、Postman 请求与手工测试文档，并把 ACTIVE 告警纳入诊断上下文。
## 2026-08-10 告警测试闭环与代码可读性完善

- 重写 EquipmentAlarmServiceImpl 为可读的分步骤业务编排，补充确认/关闭规则、幂等顺序、版本校验、设备恢复依据、条件更新和事务回滚原因的中文注释。
- 修正幂等意图：同一幂等键除 alarmId 和 action 外，operatorId 也必须一致；不同操作人复用同一键返回 IDEMPOTENCY_CONFLICT。
- 提取稳定 AlarmCommandErrorCode，不再使用运行时匿名错误码。
- 新增 6 个告警 Service 专项测试，覆盖确认成功、设备未恢复拒绝关闭、恢复后关闭、幂等重放、不同操作人冲突和并发更新失败不写历史。
- 真实数据库检查发现旧状态测试设备已经是 U/IDLE v2，因此保留其结果，另建 EQP-ALARM-TEST-01 和 ACTIVE 告警；未重置或删除既有测试历史。
- 新增 V10 可重复种子迁移、docs/EQUIPMENT_ALARM_MANUAL_TEST.md 和 6 个固定值 Postman 请求；请求不使用 Environment，未执行 Postman/HTTP。
- 数据前置状态已确认：设备 ID 6 为 D/DOWN v0，告警 ID 1 为 ACTIVE v0。
- 验证：JDK 21 全量 mvn test 成功，共 64 个测试；git diff --check 通过。
- 下一步：把未关闭告警加入 Lot/Equipment 诊断上下文，使 Agent 能直接看见告警状态、严重度和持续时间。
## 2026-08-10 告警模块代码规则纠偏

- 重新读取并核对 PROJECT_MEMORY、DECISIONS、项目交接文档和 MES 后端开发规范；确认此前告警模块存在压缩语句、通配符导入、状态字符串散落和业务依据注释不足等问题，之前“可读性完善”的表述由本条记录纠正。
- 新增 AlarmAction、AlarmStatus，将 ACTIVE -> ACKNOWLEDGED -> CLOSED 的来源状态和目标状态集中定义，避免服务层重复硬编码状态字符串。
- 重写 EquipmentAlarmServiceImpl 的业务编排：幂等识别、版本校验、状态机校验、关闭前设备 U + IDLE 校验、条件更新和同事务审计均分步表达，并补充“做什么、为什么”的中文注释。
- 整理 EquipmentAlarmController、AlarmActionRequestTO、EquipmentAlarmActionHistory 的导入、缩进、字段说明和单语句换行，控制器继续只负责参数校验与统一响应包装。
- 变更文件：alarm/enums/AlarmAction.java、AlarmStatus.java，alarm/service/impl/EquipmentAlarmServiceImpl.java，alarm/controller/EquipmentAlarmController.java，alarm/dto/AlarmActionRequestTO.java，alarm/model/EquipmentAlarmActionHistory.java。
- 验证结果：Maven 回归已发起，但 Windows 沙箱 helper_unknown_error 和审批自动审查超时导致命令未真正完成，不能宣称测试通过；未执行 Postman/HTTP 请求，也未修改业务数据库。
- 下一步：执行 mvn test 与 git diff --check，并继续扫描告警模块其余 DTO、异常、Mapper 和测试文件的同类格式问题。
- 阻塞项：当前本地命令执行通道间歇性失败，不是用户业务授权不足。
## 2026-08-10 Lot 诊断告警聚合收尾与通用失败诊断设计

- Lot 诊断上下文新增 `activeAlarms`：只查询当前设备的 `ACTIVE`、`ACKNOWLEDGED` 告警，排除 `CLOSED`，按开启时间倒序最多10条。
- 返回告警码、严重度、状态、来源事件、消息、开启/确认信息、持续秒数和版本；整个接口保持只读。
- 新增 `docs/LOT_DIAGNOSTIC_ALARM_MANUAL_TEST.md`，规定请求前后 Lot、Equipment、Alarm 和相关履历必须不变。
- 修复纯 Mockito 测试的 EquipmentAlarm Lambda 元数据初始化，并新增当前设备未关闭告警测试。
- 验证：JDK 21 专项测试5个通过；全量 `mvn test` 共65个通过，0失败、0错误、0跳过；未执行 Postman，未访问或修改业务数据库。
- 新增 `docs/COMMAND_VALIDATION_AND_FAILURE_DESIGN.md` 和 ADR-004，确定只读预检查、脱敏失败现场、Policy复用、独立失败记录事务及Agent/页面接入方式。
- 下一步：定义 CommandType 和规则结果 TO；以 Track In 为首个 Validator；随后实现失败记录表、独立记录组件和 Trace ID 查询。
- 阻塞项：无。
## 2026-08-10：Track In `validate_command` 第一阶段完成

- 新增只读接口 `POST /api/command-validations`，第一阶段支持 `TRACK_IN + LOT`，请求使用强类型 TO，不使用任意 Map。
- Track In 预检查一次返回 11 项规则：Lot 存在、版本、READY、RELEASED、未绑定设备、当前 Step、设备存在、U、IDLE、能力组匹配、未被 Lot 占用；前置对象缺失时依赖规则返回 `evaluated=false`。
- 新增 `CommandValidator` 注册/路由结构和 `TrackInCommandValidator`；公共层只负责按 commandType 路由，Track In 具体规则仍归 Lot/Equipment 领域。
- 将 Lot Track In 状态拆成可复用命名规则，并新增 `TrackInPolicy`。正式 Track In 与预检查共用相同 Policy，避免两套 if/else 漂移。
- 新增 Controller 与 Validator 自动化测试，覆盖全部通过、一次返回多项失败、依赖规则未执行、HTTP 成功封装和缺少设备编码的 400 校验。
- 新增 `docs/TRACK_IN_COMMAND_VALIDATION_MANUAL_TEST.md`，包含中文业务流程、每项校验理由、测试前 SQL、固定请求和发送后数据库必须完全不变的验收标准。
- 验证结果：JDK 21 专项测试 5 个通过；JDK 21 全量 `mvn test` 共 70 个通过，0 failures / 0 errors / 0 skipped。
- 本阶段未执行 Postman/HTTP 请求，未连接或修改业务数据库，也未新增命令失败记录表。
- 下一步：由用户按手工文档验证只读接口；随后将同一 Validator/Policy 模式接入 Track Out，再逐项覆盖 Hold、Release Hold、Scrap、设备事件和告警动作。结构化 `command_failure_record` 在预检查稳定后另行实现。
## 2026-08-10：补齐 Track In validate_command Postman 测试集

- 新增 `postman/collections/FabPilot MES Core/Command Validation`，包含 5 个固定参数请求：允许执行、版本过期、Lot 不存在、设备不存在、缺少 equipmentCode。
- 所有请求直接使用 `http://localhost:8080`、固定 Lot/设备/版本和 Trace ID，不包含 `{{environmentVariable}}`。
- 每个请求都包含 afterResponse 自动断言，覆盖 HTTP envelope、allowed、observedVersion、11 项规则、稳定错误码、evaluated=false 依赖规则和 400 入参错误。
- 写入用例前只读核对本地 MySQL：LOT-016 已为 SCRAPPED/version 5，不再适合作为成功前置；当前及重建后的稳定种子组合为 LOT-014/version 2 + ETCH-02/U/IDLE，能力组匹配且设备未占用，因此 Postman 与手工文档改用该组合。
- 未执行任何 Postman/HTTP 请求，未修改业务数据库。
## 2026-08-10：一次性补齐其余命令 Validator

- 将只读 `POST /api/command-validations` 从仅支持 Track In 扩展为 9 类命令：RELEASE、TRACK_IN、TRACK_OUT、HOLD、RELEASE_HOLD、SCRAP、EXECUTE_EQUIPMENT_EVENT、ACKNOWLEDGE_ALARM、CLOSE_ALARM；目标类型覆盖 LOT、EQUIPMENT、ALARM。
- 新增 Lot、设备事件、告警动作 Validator，并通过抽象基类和 `CommandCheckFactory` 复用对象读取、依赖规则未执行结果和检查结果构造；公共模块只处理通用编排，具体业务规则仍保留在对应领域 Policy 中。
- 提取并复用 `LotStatePolicy`、`LotRoutePolicy`、`TrackOutPolicy`、`EquipmentEventPolicy`、`AlarmActionPolicy`。正式命令执行和预检查使用同一套状态、路线、事件定义及告警状态机判断，避免 Validator 与执行服务各写一套规则后逐渐不一致。
- 按既定编码规范补充中文业务注释，说明每一步检查“检查什么、为什么检查”；没有使用通配符导入、压缩式多语句或隐式 Map 请求。
- 新增 `RemainingCommandValidatorTest`，并同步调整 Track In Validator 与 Controller 测试；JDK 21 全量 `mvn test` 成功，共 79 个测试，0 failures、0 errors、0 skipped。
- 新增 `docs/ALL_COMMAND_VALIDATION_MANUAL_TEST.md`，按 9 类命令说明业务流程、规则依据、测试前 SQL 和发送后数据库必须保持不变的验收方式。
- Command Validation Postman 目录现有 13 个固定值请求：原 5 个 Track In 用例已适配 13 项规则，新增其余 8 类命令用例；全部直接写明 URL 和参数，不使用 `{{...}}`。
- 编写用例前只读核对本地 MySQL 的 Lot、设备、事件定义和告警状态；未修正、插入或更新任何业务数据。未执行 Postman/HTTP 请求。
- 静态检查：13 个 Postman 请求体均可解析为 JSON，目录不存在环境变量占位符；`git diff --check` 通过。
- 下一步：用户按照手工文档执行 13 个 Postman 用例并反馈结果；确认 Validator 行为后，再实现 `command_failure_record` 的独立事务记录和 Trace ID 查询，不让只读预检查产生业务写入。
- 阻塞项：无。