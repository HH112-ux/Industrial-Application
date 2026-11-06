# 重构分期

本文档把重构拆成可独立交付的阶段。阶段编号用 `S` 前缀表示执行顺序，优先级单独用 `P0/P1/P2` 标注，避免与 AI 阶段的「P2」混淆。每个阶段单独分支、单独 PR，单个 PR 控制在 400 行以内；阶段之间不互相阻塞，允许调整顺序。

标注说明：`[代码实证]` 表示缺口已从当前代码确认；`[工程判断]` 表示实施建议；`[待确认]` 表示需要用户拍板。

---

## 1. 总体约束

| 约束 | 内容 |
|---|---|
| 迁移方式 | Flyway 前向迁移，不改写已执行脚本 |
| 兼容策略 | 新增字段与端点优先；破坏性变更走 `/api/v2` |
| 开关 | 新能力默认关闭，通过配置逐项开启 |
| 测试 | 每个阶段的关键路径必须有单测；涉及 SQL 的阶段补 Testcontainers 集成测试 |
| 回滚 | 代码回滚到上一版本；数据库保留新增列与表，不做破坏性回滚 |
| 交付纪律 | 每阶段完成即编译、跑测试、提交、发 PR；不攒提交 |

---

## 2. 阶段总览

| 阶段 | 优先级 | 主题 | 规模 | 依赖 | 可并行 |
|---|---|---|---|---|---|
| S0 | P0 | 文档与基线 | S | 无 | 与所有阶段并行 |
| S1 | P0 | 设备接入可靠性 | M | S0 | 与 S2、S3 并行 |
| S2 | P0 | 遥测时序存储 | M | S1 的写入点 | 与 S3 并行 |
| S3 | P0 | 工单执行一致性 | M | S0 | 与 S1、S2 并行 |
| S4 | P1 | 工艺路线与 BOM | L | S3 的工单工艺版本字段 | 与 S5 并行 |
| S5 | P1 | 计划与排产 | M | S4 的工序模型 | 与 S6 并行 |
| S6 | P1 | 权限与数据域 | M | S3 的历史与 S4 的车间字段 | 与 S5 并行 |
| S7 | P1 | 可观测性与前端 | M | S2、S3 | 最后联调 |
| S8 | P2 | AI 能力（可选） | L | S1、S2、S3、S6 | 最后阶段，可整体取消 |

规模是改动体量估计（S 小、M 中、L 大），不是工期。

---

## S0 文档与基线

目标：让仓库具备可评审的工程文档与自动验证入口，不改变业务行为。

涉及文件：

- 新增 `docs/ENGINEERING-DESIGN.md`、`docs/DATA-MODEL.md`、`docs/API-CONTRACT.md`、`docs/REFACTOR-PLAN.md`。
- 新增 `.github/workflows/ci.yml`：后端 `mvn -B test`，前端 `npm ci && npm run build`。
- 根 `README.md` 补充文档索引与验证命令。

验收：

- `mvn -o test` 通过，19 个现有用例不回归。
- `npm run build` 通过，产物可生成。
- CI 在 push 与 PR 上运行，后端与前端两个 job 均为绿。
- 文档中的端点数量、表数量、文件位置与代码一致。

依赖与风险：无。风险是文档与代码漂移，处理方式是把「接口清单」和「表清单」作为 PR 检查项。

---

## S1 设备接入可靠性

目标：设备断线后可恢复订阅，设备上报可鉴权、可去重；解决现状中 `cleanSession(true)` 导致重连后订阅丢失、首次连接重试有限的问题。`[代码实证]`

涉及文件：

- `gateway/MqttDeviceGateway.java`：持久会话、连接完成回调重订阅、指数退避、优雅关闭。
- `service/DeviceDataService.java`：写入 `last_seen_at`、幂等键处理。
- 新增 `security/DeviceSignatureVerifier.java`、`controller/DeviceCredentialController.java`。
- 新增迁移 `V4__device_online.sql`、`V5__device_credential.sql`。
- 配置：`mqtt.client-id`、`mqtt.qos`、`device.online-threshold-seconds`。

验收：

- 停止 Mosquitto 30 秒后重启，网关在 60 秒内重连，并继续收到 `factory/devices/+/status` 消息。
- 同一 `X-Idempotency-Key` 重复上报只写一条遥测。
- 错误签名返回 401，停用设备返回 403。
- 单测覆盖：退避序列 `1s→2s→4s→…→60s`、签名校验成功/失败、重复键短路。
- 不引入真实设备，使用本地 Mosquitto 与 `mosquitto_pub` 做手工验证。

依赖与风险：依赖 MQTT Broker 可用；持久会话要求 `clientId` 稳定且各实例唯一，多实例部署时需要配置化。风险是 Broker 不支持会话保留，处理方式是启动时检测 `sessionPresent`，为 false 时强制重订阅。

---

## S2 遥测时序存储

目标：把遥测从进程内 `ConcurrentHashMap` 落到数据库，支持按设备与时间范围查询。`[代码实证]`

涉及文件：

- 新增 `entity/DeviceTelemetryRecord.java`、`mapper/DeviceTelemetryMapper.java`。
- `service/DeviceDataService.java`：写入时序表，新增 `history` 查询。
- `controller/DeviceDataController.java`：新增 `GET /api/devices/{id}/telemetry/history`。
- 新增定时清理任务 `job/TelemetryRetentionJob.java`（保留 90 天）。
- 新增迁移 `V6__device_telemetry.sql` 中的表与索引。

验收：

- MQTT 上报一条后，`eq_telemetry` 新增一行，`reported_at` 与 `received_at` 均有值。
- 查询默认返回最近 24 小时，按时间升序，最多 1000 条；`from > to` 返回 400。
- 清理任务在测试中可注入固定时间，删除 90 天前数据，不误删新数据。
- 单测覆盖：写入、时间范围过滤、上限截断、非法时间；集成测试覆盖索引命中。

依赖与风险：依赖 S1 的写入点；数据量增长需要保留策略。风险是单表过大，预留按 `reported_at` 月分区或归档到对象存储的方案，不在本阶段实现。

---

## S3 工单执行一致性

目标：解决并发重复流转、状态无留痕、报工数量无约束三个问题。`[代码实证]`

涉及文件：

- `entity/WorkOrder.java`：新增 `@Version`、累计数量字段。
- `common/config/MybatisPlusConfig.java`：注册乐观锁插件。
- `service/WorkOrderService.java`：更新返回 0 时抛冲突；写状态历史；报工数量校验；质检结论前置校验。
- 新增 `entity/WorkOrderStatusHistory.java`、`mapper/WorkOrderStatusHistoryMapper.java`。
- `controller/WorkOrderController.java`：新增 `GET /api/work-orders/{id}/history`；`start` 的 operator 改为从令牌取。
- 新增迁移 `V7__work_order_consistency.sql`。

验收：

- 两个并发请求同时开工，只有一个成功，另一个返回 409 且不产生第二条历史。
- 每次开工、报工、质检都写入一条历史，包含前后状态、操作人、备注。
- `qty <= 0`、`qualifiedQty + defectQty != qty`、累计报工超过工单数量均按 HTTP 200 + 业务码拒绝，且不落报工记录。
- 质检结论非法时不写质检记录。
- 单测覆盖：状态机合法/非法流转、并发冲突分支、数量边界、历史内容。

依赖与风险：与 S1、S2 无耦合；若 S4 引入工艺版本，工单表需要同时加 `process_version`，两个迁移可合并评审。风险是乐观锁失败后的重试策略，默认不自动重试，由前端提示刷新。

---

## S4 工艺路线与 BOM

目标：把工艺从「一个描述字段」升级为可排产的工序路线，并让 BOM 与工艺版本绑定。采用 A 方案：同一 `process_no` 的多行表示不同版本，BOM 通过 `process_id` 归属版本行，不再单独维护 BOM 版本。`[代码实证]`

涉及文件：

- 新增 `entity/ProcessOperation.java`、`mapper/ProcessOperationMapper.java`、`service/ProcessOperationService.java`。
- `service/ProcessService.java`：提交审批校验工序与 BOM；发布冻结版本；`createVersion` 复制工序与 BOM 生成下一版草稿。
- `service/BomItemService.java`：按 `process_id` 读取，不再按 version 过滤；同一版本内物料不可重复；已发布版本只读。
- `service/KitCheckService.java`：按工单锁定的工艺版本（`process_id`）读取 BOM，不再取全局最大版本。
- `controller/ProcessController.java`：工序 CRUD 与版本复制端点；`controller/BomItemController.java` 移除 BOM 版本端点。
- 新增迁移 `V9__process_operation.sql`。

验收：

- 无工序或无 BOM 的工艺无法提交审批，返回 HTTP 200 + 业务码，message 明确缺什么。
- 已发布工艺不可编辑，只能新建版本；新版本初始为草稿，工序与 BOM 已复制且可编辑。
- 工单下达时锁定工艺版本（`process_id` + `process_version`），后续发布新版本不影响在制工单的齐套结果。
- 齐套计算按 `process_id` 取数，同一工单重复计算结果一致。
- 同一工艺版本内重复物料、工序重复序号被拒绝。
- 单测覆盖：提交前校验、版本冻结、版本复制、按版本齐套、重复物料与重复序号。

依赖与风险：依赖 S3 的工单乐观锁。风险是历史数据没有工序，处理方式是为存量工艺生成一条默认工序「默认工序」，并在迁移脚本中显式写入。

---

## S5 计划与排产（已落地）

目标：下达工单时明确工艺与车间，排产从「只排序」升级为带设备时间窗的贪心排程。`[代码实证]`

涉及文件：

- `service/PlanService.java`：`dispatch` 接收 `processId/deviceId/workshop`，校验工艺已发布。
- `service/ScheduleService.java`：按交期、优先级排序后，依次为工序分配设备时间窗；冲突时给出业务码与原因。
- `entity/ScheduleEntry.java`、`mapper/ScheduleEntryMapper.java`：排程结果落库，记录设备类型、时间窗、队列位置与依据。
- `controller/ScheduleController.java`：排程重算与单工单排程查询。
- `controller/PlanController.java`：`dispatch` 请求体改为 JSON。
- 新增 `dto/DispatchRequest.java`、`dto/ScheduleRunResult.java`、`enums/ScheduleConflict.java`。
- 新增迁移 `V10__schedule_entry.sql`。

验收：

- 下达工单必须指定已发布工艺；不指定按 HTTP 200 + 业务码拒绝。
- 排程按交期升序、优先级降序、工单主键升序执行；同一设备时间窗不重叠。
- 同一批工单与同一份设备数据重复运行结果一致；只重排 `PENDING` 工单，不改变在产/待质检/已完工工单的排程。
- 产能刚好排满可落位；超出一点、无可用设备、设备类型缺失、交期排不进均返回明确的业务码与可读消息，不静默挪期或拉长工期。
- 单测覆盖：刚好排满、超出一点、同类型竞争、空产能、重排不改在产；另补幂等重跑、缺标准工时被拒与显式估算配置用例。
- 不实现 APS 求解器，只做有限产能的贪心排程。`[工程判断]`

依赖与风险：依赖 S4 的工序与标准工时。风险是标准工时缺失导致排程失真，处理方式是**缺失即拒绝排程**并返回 `STANDARD_HOURS_MISSING`，不读取计划总工时、不使用内置默认值；只有显式配置 `schedule.estimate-fallback-hours`（默认关闭）才按估算工时排程，并在结果与前端明确标记。

---

## S6 权限与数据域

目标：用户禁用立即生效，限流跨实例一致，按车间限制数据访问，设备凭证与用户令牌分离。`[代码实证]`

涉及文件：

- `service/AuthService.java`：登录校验 `enabled`；刷新校验 `token_version`。
- `security/JwtAuthenticationFilter.java`：校验令牌版本或改为每次请求查缓存。
- `common/LoginRateLimiter.java`：改为 Redis 计数，保留本地降级。
- `service/DeviceService.java`、`PlanService.java`、`WorkOrderService.java`：按 `workshop_scope` 过滤。
- `controller/AuditLogController.java`：审计查询。
- 新增迁移 `V11__identity_scope.sql`、`V12__audit_enhance.sql`。

验收：

- 禁用用户后，已签发令牌在下一次请求即失效。
- 修改密码后旧令牌失效。
- 两个后端实例共享登录失败计数；Redis 不可用时降级为本地限流并记录告警。
- USER 只能读写自己车间范围的工单与计划，跨车间返回 403。
- 单测覆盖：禁用用户、令牌版本、限流阈值、车间过滤。

依赖与风险：依赖 S3 的历史与 S4 的车间字段。风险是现有数据没有车间归属，迁移时按「一车间」回填并记录。

---

## S7 可观测性与前端适配

目标：让重构后的能力在管理端可见，并暴露最基本的运行指标。

涉及文件：

- 后端：`actuator` 指标、设备在线率、上报延迟、工单节拍、齐套率；结构化日志增加 `traceId`。
- 前端：设备详情页增加遥测历史折线图；工单详情页增加流转时间线；工艺页增加工序维护。
- 移动端：报工表单增加数量校验提示与幂等键。

验收：

- `/actuator/metrics` 可见设备在线数与上报计数。
- 设备详情可切换 1 小时/24 小时/7 天查看历史曲线。
- 工单详情可看到每次状态变更的操作人与时间。
- 页面在 1280px 与 375px 宽度下无横向滚动。

依赖与风险：依赖 S2、S3 的接口。风险是前端打包体积增长，图表按需引入。

---

## S8 AI 能力（可选，P2）

目标：在不影响主链路的前提下，提供三个旁路场景。完整设计见主文档「AI 能力」章节。

阶段位置：优先级 P2，排在最后，可整体取消；不阻塞 S0–S7 的任何交付。

涉及文件：

- 新增 `ai/` 包：`AiAssistant` 端口、`FakeAiAssistant`、外部模型适配器。
- 新增 `ai/tool/`：只读工具，复用现有 Service。
- 新增 `controller/AiController.java`。
- 新增迁移 `V13__ai_sidecar.sql`。
- 配置：`ai.enabled=false`、模型地址与密钥从环境变量读取。

验收：

- `ai.enabled=false` 时，AI 端点返回 503，下单、报工、状态流转不受影响。
- 三个场景各有至少 1 个离线假模型测试，CI 不依赖外部 key。
- 模型输出只进入建议表与响应体，不修改工单状态、数量、库存。
- Text-to-SQL 仅连接只读视图，超过超时或行数上限返回明确错误。

依赖与风险：依赖 S1–S3 的数据与 S6 的权限。风险是模型成本与数据外泄，处理方式是默认关闭、脱敏输入、限制调用频率；模型不可用时直接降级，不重试主链路。

---

## 3. 测试策略

| 层级 | 范围 | 工具 | 是否进 CI |
|---|---|---|---|
| 单元测试 | 状态机、数量校验、齐套口径、签名、退避、权限过滤 | JUnit 5 + Mockito | 是 |
| 集成测试 | Flyway 迁移、时序查询、乐观锁并发、Redis 限流 | Testcontainers（MySQL/Redis） | 是，标记 `integration` |
| 接口冒烟 | 登录、设备上报、开工、报工、质检 | MockMvc + Testcontainers | 是 |
| 前端构建 | 类型检查与打包 | `npm run build` | 是 |
| AI 用例 | 抽取、归因、SQL 白名单 | 假模型实现 | 是，不访问外网 |

当前 72 个 Mockito 单测不启动 Spring 上下文；另有 3 个 Testcontainers 集成用例（`WorkOrderConsistencyIntegrationTest`），覆盖 Flyway 从空库迁移、状态机合法/非法流转与真实并发开工。时序查询、Redis 限流与接口冒烟按 S2/S6 的实际改动继续追加，集成用例保持「只守最容易回归的路径」，不铺全量数据库测试；Docker 不可用时整类跳过。

---

## 4. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 持久会话与多实例 clientId 冲突 | 设备消息重复或丢失 | `clientId` 配置化，部署时每实例唯一 |
| 时序表增长过快 | 查询变慢、磁盘增长 | 90 天保留 + 月分区预案 |
| 乐观锁重试造成体验差 | 用户重复点击失败 | 前端提示刷新，服务端不自动重试 |
| 工艺历史数据无工序 | S4 无法直接上线 | 迁移生成默认工序并标记 |
| 车间数据域回填错误 | 用户看不到历史数据 | 迁移前导出核对，先灰度一个车间 |
| AI 模型不稳定或超时 | 旁路不可用 | 默认关闭、超时降级、不重试主链路 |
| 范围蔓延 | 重构周期失控 | 每阶段独立验收，未列入阶段的需求进入待办 |
