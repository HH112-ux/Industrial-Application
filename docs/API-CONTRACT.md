# 接口契约

现状接口以当前代码为准，共 87 个端点（按 Controller 注解逐条统计，基线 `main @ 6aedd20`）。表格编号沿用历史值、只增不复用：原 BOM 版本端点 #26、#27 已于 S4 移除，新补登记使用 #66–#73 与 #78–#87。

标注说明：`[代码实证]` 表示已在 Controller 中核对；`[工程判断]` 表示设计建议；`[待确认]` 表示需要用户拍板。

---

## 1. 通用约定

### 1.1 返回体

所有接口返回统一三段式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页数据放在 `data` 内：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 128,
    "records": []
  }
}
```

### 1.1.1 错误响应口径

从 S3 起，新增接口一律按下面的口径返回；已有接口保持现状，不做批量改造。

| 判定 | 返回 | 示例 |
|---|---|---|
| 请求本身不合法：参数缺失、格式错误、范围倒置（`from > to`）、分页越界、数量非正数 | HTTP 4xx | `400 Bad Request`，响应体 `code=400` |
| 请求合法但业务规则不满足：状态不允许、重复提交、累计数量超限、并发冲突 | HTTP 200 + 业务码 | `HTTP 200`，响应体 `code=409`，`message` 说明原因 |
| 未认证、无权限、资源不存在、限流 | HTTP 401 / 403 / 404 / 429 | 响应体 `code` 与 HTTP 一致 |
| AI 能力未启用 | HTTP 503 | 响应体 `code=503`，`message` 说明 `ai.enabled=false`，不是空数组也不是 500 |
| 未预期异常 | HTTP 500 | 响应体 `code=500` |

两条判定线的区别是责任方：客户端把请求写错了属于前者；请求格式正确但当前业务状态不接受，属于后者。业务拒绝使用 HTTP 200 是为了让前端统一按 `code` 提示，而不是被浏览器或网关当成传输错误。

已有接口维持现状：`BusinessException` 仍由全局处理器转成 HTTP 200 + `code=500`，参数错误也仍是 HTTP 200。这样避免一次性改动 87 个端点的响应语义、把现有 Web 与移动端打崩。新接口的新写法只对新代码生效。`[代码实证]`

### 1.2 认证

- 登录后返回 JWT，放在 `Authorization: Bearer <token>`。
- 白名单：`/api/auth/**`、`/actuator/health`、`/actuator/info`；设备鉴权开启时 `/api/integration/device-status` 也放行，由 HMAC 校验替代用户令牌；其余端点均需登录。Swagger 与 `/v3/api-docs/**` 默认不放行，仅在 `app.api-docs.enabled=true`（`API_DOCS_ENABLED`）时进入白名单；`/actuator/metrics` 仅 `ADMIN` 可读。`[代码实证]`
- 管理类写操作使用 `@PreAuthorize("hasRole('ADMIN')")`；现场操作中开工、报工、成品入库确认与取消允许 `ADMIN` / `USER` / `WORKER`，质检只允许 `ADMIN` / `USER`。`[代码实证]`
- 角色取值为 `ADMIN` / `USER` / `WORKER`；`USER` 与 `WORKER` 均非管理员，数据域仍由 `sys_user.workshop_scope` 决定。
- 报警规则（`/api/alarm-rules/**`）整个控制器仅 `ADMIN`；报警记录的列表、未读数、确认与恢复对登录用户开放，按设备所属车间过滤数据域。`[代码实证]`
- SSO 三个端点位于 `/api/auth/sso/**`，随 `/api/auth/**` 一起放行；真正的防重放依赖服务端一次性 `state` 与 `sso_browser` Cookie，见 §4.7。`app.sso.enabled=false` 时授权跳转与回调均返回 503，`/config` 返回 `false`。`[代码实证]`
- 目标增加两类主体：用户令牌（管理端/移动端）与设备凭证（设备上报）。设备上报不再复用用户令牌。`[工程判断]`

### 1.3 时间与命名

- 请求与响应时间使用 ISO-8601，例如 `2026-11-06T10:30:00`。
- 路径使用 kebab-case；查询参数使用 camelCase。
- 分页参数统一 `pageNum`、`pageSize`，默认 1 与 10。`pageSize` 上限 500，超过按 500 返回；`pageNum` 小于 1 按 1 处理。报警记录列表为历史行为，上限仍为 100。

### 1.4 错误码

| HTTP | `code` | 场景 |
|---|---|---|
| 200 | 0 | 成功 |
| 200 | 409 | 新接口的业务拒绝：并发冲突、重复提交、累计数量超限（见 §1.1.1） |
| 400 | 400 | 新接口的请求非法：参数校验失败、范围倒置、分页越界 |
| 401 | 401 | 令牌缺失、过期、用户被禁用 |
| 403 | 403 | 角色或数据域不允许 |
| 404 | 404 | 资源不存在 |
| 429 | 429 | 登录或设备上报限流 |
| 503 | 503 | AI 能力未启用（`ai.enabled=false`），三个 `/api/ai/**` 端点统一返回 |
| 500 | 500 | 未预期异常；业务规则拒绝（如工艺缺工序/BOM 不能提交审批）也使用 HTTP 200 + 此码 |

`code` 是给前端判断的业务码，HTTP 状态码是给网关与监控判断的传输语义。两者不一致只出现在「业务拒绝」这一类，且仅限按 §1.1.1 口径实现的新接口。

---

## 2. 现状端点清单

「处置」列的取值：保留表示签名与语义不变；改签名表示路径或请求体需要调整；改行为表示签名不变但校验或返回语义收紧；新增表示目标阶段补充。

### 2.1 认证

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 公开 | 登录并签发 JWT；SSO 账号走本地密码登录时返回「该账号需使用企业微信扫码登录」 | 已落地 | 限流按用户名（10 次/分钟）与客户端 IP（30 次/分钟）两个维度分别计数；SSO 账号提示不校验本地密码，但请求仍计数 |
| 2 | POST | `/api/auth/refresh` | 公开（需带有效令牌） | 用旧令牌换新令牌 | 已落地 | 校验 `enabled` 与 `token_version`，禁用用户立即失效；前端目前未调用 |
| 66 | POST | `/api/auth/logout` | 公开 | 统一登出入口 | 已落地 | 无状态 JWT，服务端不维护会话，由客户端丢弃令牌 |
| 67 | GET | `/api/auth/sso/config` | 公开 | 查询 SSO 灰度开关，登录页据此显隐入口 | 已落地 | 关闭时仍返回 200 + `enabled=false` |
| 68 | GET | `/api/auth/sso/wecom/authorize` | 公开 | 302 到企微扫码页，同时签发一次性 `state` 并下发 `sso_browser` Cookie | 已落地 | 已存在 Cookie 时复用，多标签页各自持有独立 `state`；开关关闭返回 503 |
| 69 | POST | `/api/auth/sso/wecom/login` | 公开（需 `state` + `sso_browser`） | 用回调 `code` 换取本系统 JWT | 已落地 | 见 §4.7；`state` 一次性、5 分钟有效、与 Cookie 绑定，校验失败一律拒绝 |

### 2.2 设备

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 3 | GET | `/api/devices` | 登录 | 设备分页 | 保留 | 增加 `workshop` 过滤 |
| 4 | GET | `/api/devices/{id}` | 登录 | 设备详情 | 保留 | 返回 `lastSeenAt`、`online` |
| 5 | POST | `/api/devices` | ADMIN | 新增设备 | 保留 | 写入 `workshop`、`protocol`；`workshop` 必填，不再缺省「一车间」 |
| 6 | PUT | `/api/devices/{id}` | ADMIN | 更新设备 | 保留 | 编号仍不可改 |
| 7 | DELETE | `/api/devices/{id}` | ADMIN | 逻辑删除 | 保留 | 有未完工工单时拒绝 |
| 8 | POST | `/api/devices/{id}/status` | ADMIN | 手工改状态 | 改行为 | 状态机校验：`SCRAPPED` 不可回退 |

### 2.3 设备数据与集成

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 9 | GET | `/api/devices/{id}/telemetry` | 登录 | 实时遥测 | 改行为 | 无真实上报时不再返回随机数；模拟数据由开关控制并标记 `source=SIMULATED` |
| 10 | POST | `/api/integration/device-status` | 登录；`DEVICE_AUTH_ENABLED=true` 时为 HMAC | REST 设备回传 | 已落地 | 设备鉴权开启后不走用户令牌，改校验 `X-Device-Key` + `X-Device-Signature`；`X-Idempotency-Key` 幂等 |
| 11 | POST | `/api/integration/mqtt/publish` | ADMIN | 发布测试消息 | 已落地 | S1 已收紧为仅 ADMIN，非生产环境启用 |
| 47 | GET | `/api/devices/{id}/telemetry/history?from=&to=&limit=` | 登录 | 设备遥测历史，默认最近 24 小时，最多 1000 条 | 已落地 | S2 已实现，见 §4.4 |

### 2.4 物料

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 12 | GET | `/api/materials` | 登录 | 物料分页 | 保留 | 支持低库存过滤 |
| 13 | GET | `/api/materials/{id}` | 登录 | 物料详情 | 保留 | 无 |
| 14 | POST | `/api/materials` | ADMIN | 新增物料 | 保留 | 库存变更走入库单，不允许直接改 `stock_qty` |
| 15 | PUT | `/api/materials/{id}` | ADMIN | 更新物料 | 改行为 | 同上，库存字段只读 |
| 16 | DELETE | `/api/materials/{id}` | ADMIN | 逻辑删除 | 保留 | 被 BOM 引用时拒绝 |

### 2.5 工艺

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 17 | GET | `/api/processes` | 登录 | 工艺分页 | 保留 | 返回 `version` 字段，按版本展示 |
| 18 | GET | `/api/processes/{id}` | 登录 | 工艺详情 | 保留 | 返回版本信息（含 `version`）；工序走独立端点 |
| 19 | POST | `/api/processes` | ADMIN | 新建工艺 | 保留 | 初始版本 `1`，状态 `DRAFT` |
| 20 | PUT | `/api/processes/{id}` | ADMIN | 更新工艺 | 改行为 | 已发布工艺不可改，只能新建版本 |
| 21 | DELETE | `/api/processes/{id}` | ADMIN | 删除工艺 | 保留 | 有工单引用时拒绝 |
| 22 | POST | `/api/processes/{id}/submit` | ADMIN | 提交审批 | 改行为 | 校验至少 1 道工序、BOM 至少 1 行；缺项返回 HTTP 200 + 业务码，message 说明缺什么 |
| 23 | POST | `/api/processes/{id}/approve` | ADMIN | 审批发布 | 改行为 | 发布时冻结版本，写 `published_at` |
| 24 | POST | `/api/processes/{id}/reject` | ADMIN | 驳回 | 保留 | 记录驳回原因 |
| 58 | POST | `/api/processes/{id}/versions` | ADMIN | 复制下一版草稿 | 已落地 | S4 已落地；复制基础信息、工序与 BOM，状态 `DRAFT` |
| 49 | GET | `/api/processes/{id}/operations` | 登录 | 工序列表 | 已落地 | S4 已落地，按 `seq` 升序返回 |
| 50 | POST/PUT/DELETE | `/api/processes/{id}/operations[/{operationId}]` | ADMIN | 工序维护（3 个端点合并登记） | 已落地 | S4 已落地；重复序号拒绝、已发布只读 |

### 2.6 工艺物料清单

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 25 | GET | `/api/processes/{processId}/bom` | 登录 | 读取 BOM | 改行为 | 按 `process_id` 读取该工艺版本的 BOM，不区分 `version` |
| 28 | POST | `/api/processes/{processId}/bom` | ADMIN | 新增 BOM 行 | 改行为 | 校验物料存在、用量大于 0、同一 `process_id` 下物料不重复 |
| 29 | PUT | `/api/processes/{processId}/bom/{id}` | ADMIN | 更新 BOM 行 | 改行为 | 已发布版本只读 |
| 30 | DELETE | `/api/processes/{processId}/bom/{id}` | ADMIN | 删除 BOM 行 | 改行为 | 已发布版本只读 |

原 #26 `GET /api/processes/{processId}/bom/versions` 与原 #27 `POST /api/processes/{processId}/bom/version` 已在 S4 移除，能力由 `POST /api/processes/{id}/versions`（#58）承担；编号不复用。`[代码实证]`

### 2.7 生产计划

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 31 | GET | `/api/plans` | 登录 | 计划分页 | 保留 | 增加 `workshop` 过滤 |
| 32 | GET | `/api/plans/{id}` | 登录 | 计划详情 | 保留 | 返回关联工单摘要 |
| 33 | POST | `/api/plans` | ADMIN | 新建计划 | 保留 | 校验 `planEnd >= planStart`、数量大于 0；`workshop` 必填，不再缺省「一车间」 |
| 34 | PUT | `/api/plans/{id}` | ADMIN | 更新计划 | 改行为 | 已下达计划不可改数量与日期 |
| 35 | DELETE | `/api/plans/{id}` | ADMIN | 删除计划 | 保留 | 有工单时拒绝 |
| 36 | POST | `/api/plans/{id}/dispatch` | ADMIN | 下达工单 | 已落地 | S5 改为 JSON 请求体，必填 `processId` 且必须为已发布工艺；`deviceId` 为空时取计划所需设备；`workshop` 为空时取计划所属车间。派工写入 `dispatch_token=PLAN-{planId}` 幂等键，同一计划重复派工返回 HTTP 200 + 业务码 409 |

### 2.8 生产工单

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 37 | GET | `/api/work-orders` | 登录 | 工单分页 | 保留 | 增加 `workshop`、`planId` 过滤 |
| 38 | GET | `/api/work-orders/{id}` | 登录 | 工单详情 | 保留 | 无 |
| 39 | POST | `/api/work-orders/{id}/start` | ADMIN / USER / WORKER | 开工 | 已落地 | S3 改为从令牌取操作人；并发冲突返回 HTTP 200 + 业务码 409 |
| 40 | POST | `/api/work-orders/{id}/report` | ADMIN / USER / WORKER | 报工 | 已落地 | S3 已实现数量校验、累计上限与幂等键；多次累计至报满后转「待质检」，未报满保持「生产中」 |
| 41 | POST | `/api/work-orders/{id}/qc` | ADMIN / USER | 质检 | 已落地 | S3 结论校验前置，非法结论不写质检记录 |
| 48 | GET | `/api/work-orders/{id}/history` | 登录 | 状态流转历史 | 已落地 | S3 已实现，按发生时间升序返回 |
| 73 | GET | `/api/work-orders/stats/status` | 登录 | 按状态统计工单数量，供工作台卡片使用 | 已落地 | 返回状态到数量的映射，空结果返回 0 而不是 null |

工单没有创建接口，也没有 `PUT` / `PATCH` 更新接口：工单由计划派工生成，状态只能由开工、报工、质检三条链路驱动；下达工单（`POST /api/plans/{id}/dispatch`）由管理员执行，重复派工由 `dispatch_token` 唯一索引兜底并返回业务码 409。`[代码实证]`

### 2.9 设备点检与维修

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 42 | GET | `/api/maintenance` | 登录 | 维保分页 | 保留 | 支持按设备与时间范围过滤 |
| 43 | GET | `/api/maintenance/{id}` | 登录 | 维保详情 | 保留 | 无 |
| 44 | POST | `/api/maintenance` | 登录 | 新增点检/维修 | 改行为 | 校验设备存在、`mtTime` 不晚于当前时间 |
| 45 | PUT | `/api/maintenance/{id}` | ADMIN | 更新记录 | 保留 | 无 |
| 46 | DELETE | `/api/maintenance/{id}` | ADMIN | 删除记录 | 保留 | 无 |

### 2.10 设备凭证

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 51 | POST | `/api/devices/{id}/credential` | ADMIN | 签发或轮换设备凭证，明文密钥仅返回一次 | 已落地 | S1 已实现 |
| 52 | POST | `/api/devices/{id}/credential/revoke` | ADMIN | 吊销设备凭证 | 已落地 | S1 已实现 |

### 2.11 计划排程

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 59 | POST | `/api/schedules/run` | ADMIN | 重算全部「待开工」工单的有限产能排程 | 已落地 | S5 已实现；幂等，只重排 `PENDING` 工单 |
| 60 | GET | `/api/schedules/work-orders/{workOrderId}` | 登录 | 查询单张工单的排程依据 | 已落地 | S5 已实现；返回设备类型、时间窗、队列位置与依据 |
| 72 | GET | `/api/schedules/devices?from=&to=` | 登录 | 设备维度排程时间轴 | 已落地 | 跨度最长 31 天；数据域内工单返回完整明细，其他车间只返回 `occupied=true` 的占用块 |

### 2.12 用户与审计

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 53 | GET | `/api/audit-logs` | ADMIN | 审计查询 | 已落地 | S6 已实现；支持操作人、路径、起止日期筛选，验证码与密码不入审计 |
| 61 | GET | `/api/users` | ADMIN | 用户分页 | 已落地 | S6 已实现；返回脱敏视图，不包含密码；`ssoOnly=true` 只返回仍是纯 SSO 的账号 |
| 86 | POST | `/api/users` | ADMIN | 创建本地账号 | 已落地 | 请求体 `{username, nickname, role, workshopScope, password}`；`role` 缺省 `WORKER`，`password` 留空时由服务端生成并只在本次响应返回一次；新账号 `auth_source=LOCAL`、`must_change_password=1`；用户名重复返回业务码 409 |
| 70 | GET | `/api/users/me` | 登录 | 查询当前登录用户自身信息 | 已落地 | 强制改密期间仍可访问，供前端判断 `mustChangePassword` 与角色 |
| 62 | POST | `/api/users/{id}/status` | ADMIN | 启用或禁用用户 | 已落地 | S6 已实现；同时递增令牌版本，旧令牌下一次请求即 401 |
| 87 | DELETE | `/api/users/{id}` | ADMIN | 软删除用户 | 已落地 | 禁用 + 递增令牌版本 + 逻辑删除，用户行保留、用户名仍被占用；不能删除自己，也不能删除最后一个启用的本地管理员（403 / 400） |
| 63 | POST | `/api/users/{id}/password` | ADMIN | 重置用户密码 | 已落地 | S6 已实现；递增令牌版本，该用户所有旧令牌立即失效；同时置 `must_change_password=1`，纯 SSO 账号（`auth_source=SSO`）转为 `BOTH`，扫码通道保留 |
| 71 | POST | `/api/users/{id}/role` | ADMIN | 调整用户角色 | 已落地 | 角色取值 `ADMIN` / `USER` / `WORKER`；变更写审计并递增令牌版本 |
| 64 | POST | `/api/users/me/password` | 登录 | 自助改密 | 已落地 | S6 已实现；校验原密码，成功后当前令牌立即失效 |
| 65 | POST | `/api/users/{id}/workshop` | ADMIN | 分配车间数据域 | 已落地 | S6 已实现；非管理员未分配时看不到任何车间数据，分配后下一次请求生效，不需要重新登录 |

S6 的即时失效口径：认证过滤器在**每次请求**读取数据库中的 `enabled` 与 `token_version`，不使用缓存；禁用、重置密码或自助改密后，旧令牌在下一次请求返回 HTTP 401。非管理员访问其他车间的设备、计划或工单详情返回 HTTP 403，相关列表（含成品入库单与成品库存）查询自动追加 `workshop` 条件。升级到 S6 前签发的令牌不含 `tv` 声明，升级后第一次请求即被判定失效，用户需要重新登录。`[代码实证]`

车间数据域采用 deny-by-default：V11 曾把非管理员统一回填为「一车间」，但用户表此前没有车间字段，该回填没有依据且等于批量授权；V13 已将其纠正为 `NULL`，未分配车间的非管理员看不到任何设备、计划、工单、成品入库单与库存，必须由管理员通过 `POST /api/users/{id}/workshop` 显式分配。管理员不受车间限制，分配请求会被拒绝。`[代码实证]`

### 2.13 AI 旁路

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 55 | POST | `/api/ai/device-issue/suggest` | 登录 | 设备异常归因建议 | 已落地 | S8 已实现；只读设备、遥测、维修与质检记录，返回建议文本，不修改设备状态 |
| 56 | POST | `/api/ai/report/draft` | 登录 | 自然语言报工草稿 | 已落地 | S8 已实现；只抽取字段，`requiresConfirmation` 恒为 true，不调用报工接口 |
| 57 | POST | `/api/ai/query` | ADMIN | 生产数据问答 | 已落地 | S8 已实现；只允许单条 SELECT 查询 5 个只读视图，强制 `LIMIT 200`、3 秒超时并写 `ai_query_audit` |

`ai.enabled` 默认 false，关闭时三个 AI 端点返回 HTTP 503 + 业务码 503，其他功能不受影响。模型超时、报错或返回非法内容时返回 `available=false` 与原因，不抛 500。所有 AI 测试使用离线假模型，CI 不联网、不需要密钥。`[代码实证]`

### 2.14 指标概览

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 54 | GET | `/api/metrics/overview` | ADMIN | 设备在线率、遥测上报、报工、工单节拍、齐套率 | 已落地 | S7 已实现；空分母返回 `value=null` 与 `reason`，不伪造 0 或 100% |

### 2.15 成品入库

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 74 | GET | `/api/finished-goods/inbounds?pageNum=&pageSize=&keyword=&status=&workshop=` | 登录 | 成品入库单分页，按车间数据域过滤 | 已落地 | 支持按产品编码/批次模糊搜索与状态过滤；状态取 `PENDING/INBOUND/CANCELLED` |
| 75 | POST | `/api/finished-goods/inbounds/{id}/confirm` | ADMIN / USER / WORKER | 确认入库，可选覆盖批次号 | 已落地 | 请求体 `{batch}` 可省略；条件更新仅放行「待入库」且数量不超过 `min(计划数, 合格数) − 已入库数`；命中 0 行返回业务码 409（已处理 / 超额两种 message） |
| 76 | POST | `/api/finished-goods/inbounds/{id}/cancel` | ADMIN / USER / WORKER | 取消待入库单 | 已落地 | 请求体 `{reason}` 必填且不超过 255 字；仅「待入库」可取消，已被处理时返回业务码 409 |
| 77 | GET | `/api/finished-goods/stocks?pageNum=&pageSize=&keyword=` | 登录 | 成品库存分页，按车间数据域过滤 | 已落地 | 按产品编码搜索；同一产品编码 + 车间一行，库存为累计合格入库数 |

成品入库单由工单质检通过并完工时自动生成，没有创建接口；一张工单最多一条入库单（`uk_inbound_work_order`），已取消的单据不会重新生成。非管理员只能看到 `workshop_scope` 范围内的单据与库存，未分配车间时返回空列表。`[代码实证]`

### 2.16 设备报警

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 78 | GET | `/api/alarm-rules?deviceId=` | ADMIN | 查询报警规则，可按设备过滤 | 已落地 | 控制器整体仅 ADMIN；`deviceId` 可选，不传返回全部规则 |
| 79 | POST | `/api/alarm-rules` | ADMIN | 新建报警规则 | 已落地 | 同一设备 + 规则类型 + 指标唯一（`uk_rule_device_type_metric`）；重复返回业务码 409 |
| 80 | PUT | `/api/alarm-rules/{id}` | ADMIN | 更新报警规则 | 已落地 | 阈值与连续次数可改；已有报警记录保留创建时的阈值快照，不受影响 |
| 81 | DELETE | `/api/alarm-rules/{id}` | ADMIN | 删除报警规则 | 已落地 | 只删规则，不级联删除历史报警记录 |
| 82 | GET | `/api/alarm-records?pageNum=&pageSize=&status=` | 登录 | 报警记录分页，按开始时间倒序 | 已落地 | `status` 可选，取 `OPEN/ACKNOWLEDGED/RECOVERED`；按设备所属车间过滤数据域，未分配车间返回空列表 |
| 83 | GET | `/api/alarm-records/unread-count` | 登录 | 未处理报警数量 | 已落地 | 统计数据域内 `status=OPEN` 的记录数，无记录返回 0 |
| 84 | POST | `/api/alarm-records/{id}/acknowledge` | 登录 | 确认报警 | 已落地 | 仅 `OPEN` 可确认，写入 `ack_user`、`ack_time`；已被他人处理时返回业务码 409 |
| 85 | POST | `/api/alarm-records/{id}/recover` | 登录 | 标记报警恢复 | 已落地 | 仅 `OPEN` / `ACKNOWLEDGED` 可恢复，写入 `recover_time` 并置 `active_flag=NULL`；已恢复时返回业务码 409 |

报警规则只有管理员能维护；报警记录对登录用户开放，但只能看到设备所属车间在自己 `workshop_scope` 内的记录，跨车间按 403 处理。阈值规则需连续越界 `consecutive_times` 次才触发，离线规则由 `device.alarm.offline-scan-ms` 周期扫描。`[代码实证]`

端点合计：87 个（按 Controller 注解读取），已全部在 §2 表内编号（#1–#87，其中 #26、#27 已移除）。工序维护的 POST/PUT/DELETE 合并为 #50 一行，故表格数据行为 85 行。`[代码实证]`

---

## 3. 阶段新增端点

S4–S8 计划新增的端点均已落地，并已并入 §2 的现状清单，不再单独重复列出：工序与版本复制见 §2.5（#49、#50、#58），设备凭证见 §2.10（#51、#52），排程见 §2.11（#59、#60、#72），指标概览见 §2.14（#54），AI 旁路见 §2.13（#55–#57），遥测历史见 §2.3（#47），工单历史见 §2.8（#48）。空分母指标返回 `value=null` 并附 `reason`，不返回伪造的 0 或 100%。`[代码实证]`

---

## 4. 关键契约变更

### 4.1 设备上报

现状：

```http
POST /api/integration/device-status
Authorization: Bearer <user-token>
Content-Type: application/json

{"deviceNo":"EQ-202609-0001","status":"RUNNING","temperature":45.2,"rpm":1500,"current":8.5}
```

目标：

```http
POST /api/integration/device-status
X-Device-Key: <keyId>
X-Device-Signature: HMAC-SHA256(rawBody, secret)
X-Idempotency-Key: <设备侧唯一键>
Content-Type: application/json

{"deviceNo":"EQ-202609-0001","status":"RUNNING","temperature":45.2,"rpm":1500,"current":8.5}
```

响应：首次写入返回 `200`；重复 `X-Idempotency-Key` 返回 `200` 且不重复写入；签名错误返回 `401`；设备停用返回 `403`。`[工程判断]`

### 4.1.1 MQTT 上报签名

鉴权开启时，MQTT 消息体在原有字段外增加 `keyId`、`timestamp`（毫秒）、`nonce`、`signature`，以及可选的 `messageId`。签名对象是规范化字段串：

```text
deviceNo|status|temperature|rpm|current|timestamp|nonce
```

数值使用去尾零的十进制表示（如 `45.20` 写作 `45.2`），空值用空字符串占位。`timestamp` 超出 `device.signature-tolerance-seconds`（默认 300 秒）即拒绝；`messageId` 用于设备重发去重。`[已决策]`（S1 已实现）

### 4.2 报工

目标请求体：

```json
{
  "qty": 50,
  "qualifiedQty": 48,
  "defectQty": 2,
  "deviceId": 1,
  "idempotencyKey": "7f0c1e9a-..."
}
```

校验顺序：幂等判重 → 工单状态为「生产中」→ `qty > 0` 且 `qualifiedQty + defectQty == qty` → 累计报工不超过「工单数量 + 可返工额度」→ 写入报工与状态历史 → 累计达到「工单数量 + 可返工额度」时转「待质检」，未报满则保持「生产中」。格式与数量非法返回 `400`；累计超限属于业务拒绝，返回 HTTP 200 + 业务码 `409`（见 §1.1.1）。`[代码实证]`

多次累计口径：

- 报工可在「生产中」重复提交并累加，每次报工写一条状态历史，未报满时前后状态均为「生产中」。
- 仅当剩余可报数量为 0（累计 = 工单数量 + 可返工额度）时才转「待质检」，报满前不可送检；质检不可跳过，PASS 才完工。
- 并发报工由工单乐观锁裁决：只有一个请求能累加成功，另一个命中 0 行返回业务码 `409`，其报工记录与历史随事务回滚，不会重复累计或转移两次。
- 质检 FAIL 按「本轮」释放返工额度：本轮指上一次质检之后、当前生产阶段内的全部有效报工，等于 `qtyDone − qtyRework`；单次报工时与本批报工数相等，释放后剩余可报数量回到计划数量，不会超过计划数。

幂等约定：

- **生成方式**：客户端为每次「提交报工」生成一个 UUID（建议 v4），放在请求体 `idempotencyKey`；重试必须沿用同一个键，新的一次报工必须换新键。
- **作用域**：同一工单内唯一，数据库唯一索引 `uk_order_idem (order_id, idempotency_key)` 兜底；不同工单可以使用相同键。
- **长度与缺省**：最长 64 字符，超长返回 `400`；为空表示调用方不要求幂等，行为与改造前一致。Web 管理端可不传，移动端弱网重试应始终携带。
- **重复请求**：直接返回 HTTP 200 + `code=0`，响应体为当前工单快照；不校验状态、不写报工记录、不累加数量、不写状态历史。若首次提交后工单已继续流转，快照反映的是读取时的最新状态，幂等保证的是「副作用只发生一次」。
- **并发同键**：两个请求同时到达时由唯一索引裁决，后到者按重复请求处理。
- **保留期**：幂等键随报工记录保存在 `wo_report`，当前不设过期与清理任务，保留期等同于报工记录；如后续引入归档，需同步更新本节。`[代码实证]`

### 4.3 计划下达（S5 已落地）

请求体：

```json
{
  "processId": 1,
  "deviceId": 1,
  "workshop": "一车间"
}
```

`processId` 必填且必须为已发布工艺，否则按 §1.1.1 返回 **HTTP 200 + 业务码**，`message` 为「下达工单必须指定已发布工艺」；`deviceId` 可为空，为空时取计划上的 `requiredDeviceId`；`workshop` 为空时取计划所属车间，计划也未配置车间时返回「工单必须指定车间」。不再自动挑选「第一个已发布工艺」，避免多工艺产品下错版本。`[代码实证]`

### 4.4 时序查询

```http
GET /api/devices/1/telemetry/history?from=2026-11-05T00:00:00&to=2026-11-06T00:00:00&limit=500
```

响应 `data` 为按 `reportedAt` 升序的数组，字段包含 `reportedAt`、`temperature`、`rpm`、`current`、`status`、`source`。`limit` 上限 1000，超出按 1000 处理；`from > to` 返回 `400`。`[工程判断]`

### 4.5 工艺版本与工序（S4 已落地）

工艺版本采用 A 方案：同一 `processNo` 的每一行 `pr_process` 是一个版本，`id` 即版本标识；`version` 字段用于展示与递增，同一 `processNo` 内唯一。

- `POST /api/processes/{id}/versions`：以指定版本为源复制出下一版草稿，同时复制工序与 BOM；新版本 `approvalStatus=DRAFT`，可继续编辑。
- `GET /api/processes/{id}/operations`：按 `seq` 升序返回该版本的工序。
- `POST/PUT/DELETE /api/processes/{id}/operations[/{operationId}]`：维护工序；`seq` 在同一版本内不可重复；已发布版本不可修改。
- `POST /api/processes/{id}/submit`：提交前校验至少 1 道工序与至少 1 行 BOM。缺项属于业务规则拒绝，按 §1.1.1 返回 **HTTP 200 + 业务码**，`message` 形如「提交审批前请先添加工序与BOM 物料」。
- `GET/POST/PUT/DELETE /api/processes/{processId}/bom`：BOM 直接归属工艺版本行，按 `process_id` 读取；`version` 列已废弃，接口不再接受 `version` 参数；同一版本内物料不可重复；已发布版本只读。

在制工单的齐套计算只读取工单 `processId` 指向的版本行，因此发布新版本不会改变既有工单的齐套结果。`[代码实证]`

### 4.6 有限产能排程（S5 已落地）

排程是**贪心 + 有限产能**，不是 APS：不做最优解、不引入求解器。一次运行的处理顺序为：交期升序 → 优先级降序 → 工单主键升序；每张工单按工序顺序、在运行中设备上寻找最早可用的工作日时间窗。工序允许跨天但不会越过计划完成日期；落不下就返回业务码与可读原因。

请求与响应：

```http
POST /api/schedules/run
```

```json
{
  "runAt": "2026-11-06T19:30:00",
  "scheduledWorkOrders": 1,
  "unscheduledWorkOrders": 1,
  "scheduled": [
    {"workOrderId": 101, "planId": 1, "operationSeq": 1, "deviceType": "加工设备",
     "deviceId": 1, "startTime": "2026-11-10T08:00:00", "endTime": "2026-11-10T16:00:00",
     "durationHours": 8.0, "estimated": 0, "hoursSource": "STANDARD", "queueRank": 1,
     "rankBasis": "交期 2026-11-10，优先级 HIGH(3)", "status": "SCHEDULED"}
  ],
  "unscheduled": [
    {"workOrderId": 102, "planId": 2, "operationSeq": 1, "deviceType": "加工设备",
     "queueRank": 2, "rankBasis": "交期 2026-11-10，优先级 LOW(1)", "status": "UNSCHEDULED",
     "conflictCode": "CAPACITY_OVERLOAD",
     "message": "设备类型「加工设备」在 2026-11-10 至 2026-11-10 的产能已被占满，工序无法在交期内排入"}
  ]
}
```

幂等与边界：

- 同一批待排工单、同一份设备数据重复运行，排序、设备选择与时间窗一致。
- 只重排 `PENDING`（待开工）工单；`IN_PRODUCTION`、`PENDING_QC`、`FINISHED` 工单的排程记录不删除、不改期，并作为已占用产能参与落位。
- 产能来自 `RUNNING` 设备；每台设备每日可用工时由 `schedule.daily-hours`（默认 8）与 `schedule.day-start-hour`（默认 8）配置。这两项是车间规则，允许有默认值。
- 工序标准工时属于单据数据，缺失时**不排程**，返回 `STANDARD_HOURS_MISSING` 并指出缺工时的工序；不读取计划总工时，也不使用内置默认值。
- 只有显式配置 `schedule.estimate-fallback-hours`（默认 0，即关闭）且大于 0 时，缺少标准工时的工序才按该估算值排程；结果中 `estimated=1`、`hoursSource=ESTIMATED`，消息写明「按估算工时」，前端同步提示估算工序数量。`hoursSource` 为 `STANDARD` 时表示按实际标准工时排程。
- 结果落在 `po_schedule_entry`，记录设备类型、设备、时间窗、工时、队列位置与位置依据；`GET /api/schedules/work-orders/{workOrderId}` 可查询单张工单的落位原因。

冲突业务码：

| 业务码 | 含义 |
|---|---|
| `PLAN_MISSING` | 工单关联的计划不存在 |
| `PROCESS_MISSING` | 工单未绑定工艺 |
| `OPERATION_MISSING` | 工艺没有可排产工序 |
| `DEVICE_TYPE_MISSING` | 工序未配置设备类型，且计划未指定可用设备 |
| `NO_CAPACITY` | 该设备类型没有运行中的设备 |
| `DURATION_INVALID` | 工单数量无效，无法计算工序工时 |
| `STANDARD_HOURS_MISSING` | 工序缺少标准工时，且未显式开启估算工时配置 |
| `DUE_DATE_MISSING` | 计划缺少开始或完成日期 |
| `DUE_DATE_MISSED` | 上一工序结束时间已晚于计划交期 |
| `CAPACITY_OVERLOAD` | 交期前该设备类型产能已被占满 |

冲突不改变工单状态，也不静默挪期或拉长工期；需要人工调整交期、增加设备或拆分批量后重新运行。`[代码实证]`

### 4.7 企业微信扫码登录（SSO）

三个端点都在 `/api/auth/**` 白名单内，匿名可访问；「所需角色」是登录成功后拿到的本地角色，登录动作本身不要求令牌。

| 端点 | 鉴权 | 关键约束 |
|---|---|---|
| `GET /api/auth/sso/config` | 公开 | 返回 `{enabled}`；`app.sso.enabled=false` 时仍返回 200，登录页据此隐藏入口 |
| `GET /api/auth/sso/wecom/authorize` | 公开 | 302 到企微扫码页；`Set-Cookie: sso_browser=<随机值>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1800`（`app.sso.cookie-secure=false` 时省略 `Secure`，仅用于本机 http 调试）；服务端同时签发一次性 `state`（5 分钟）并绑定该 Cookie；开关关闭返回 503 |
| `POST /api/auth/sso/wecom/login` | 公开，但需一次性 `state` + `sso_browser` Cookie | 请求体 `{code, state}`；`state` 一次性消费、5 分钟过期、与 Cookie 绑定。伪造、复用、跨浏览器、缺失 `state` 或 Cookie 一律拒绝并返回可读提示，不会降级为跳过校验继续登录 |

流程是前端拿 `code` 与 `state` 直接换本地 JWT，没有 `sso_ticket` 中间票据，也没有票据表；code 的一次性由企业微信保证。登录成功后仍签发本系统 JWT（`sub` / `role` / `tv`），后续鉴权与本地登录完全一致。首次登录 JIT 建号：`auth_source=SSO`、`role=WORKER`、`workshop_scope=NULL`、`must_change_password=0`。SSO 账号不能用本地密码登录，`POST /api/auth/login` 返回「该账号需使用企业微信扫码登录」；回调按 `sso:<ip>` 限流。

边界行为：

- 同时打开两个登录页：复用同一 `sso_browser` Cookie，各自持有独立 `state`，两个都可完成。
- 授权超时：超过 5 分钟的 `state` 被拒绝，提示重新扫码。
- 重复回调：`state` 已被消费，第二次返回 400，不会重复签发令牌。
- 直接访问回调页且无 `state`：前端在调用后端前提示「缺少登录会话标识，请从登录页重新扫码」。`[代码实证]`

### 4.8 成品入库确认与取消（已落地）

**生成规则**：工单质检通过并流转为「已完工」时，在同一事务内生成一条「待入库」单，数量为 `min(计划数, 合格数) − 已入库数`，结果不大于 0 时不生成空单；批次号默认「工单号-yyyyMMdd」，确认时可覆盖。一张工单最多一条入库单，重复触发由 `uk_inbound_work_order` 忽略，已取消的单据也不会重新生成。`[代码实证]`

**确认入库**：

```http
POST /api/finished-goods/inbounds/12/confirm
Content-Type: application/json

{"batch":"WO-202611-0001-A"}
```

请求体可省略；`batch` 去空格后最长 64 字，为空则沿用原批次。服务端用一条条件更新同时约束状态与数量：

```text
WHERE id = ? AND status = 'PENDING'
  AND qty <= min(工单计划数, 工单合格数) - 已入库数
```

命中 0 行时回读当前单据区分原因，两者都返回 HTTP 200 + 业务码 `409`，但 `message` 不同：

- 单据已不是「待入库」：「入库单已确认或已取消，请刷新后重试」
- 仍是「待入库」：数量超过计划数或合格数，「入库数量超过计划数或合格数，无法确认」

确认成功后在同一事务内以 `INSERT ... ON DUPLICATE KEY UPDATE qty = qty + #{qty}` 原子累加 `fg_finished_stock`，不做先查后写；两个并发确认请求只有一个能命中条件更新，库存只增加一次，集成用例 `confirmWritesStockOnlyOnceUnderConcurrentRequests` 覆盖该行为。`[代码实证]`

**取消入库**：

```http
POST /api/finished-goods/inbounds/12/cancel
Content-Type: application/json

{"reason":"批次标签打印错误，重新质检"}
```

`reason` 必填，去空格后 1–255 字，空值或超长返回 HTTP 400。取消同样使用 `WHERE status = 'PENDING'` 的条件更新，命中 0 行返回业务码 `409` +「入库单已确认或已取消，请刷新后重试」。操作人取自当前令牌，不读请求体。`[代码实证]`

---

## 5. 兼容策略

| 变更类型 | 策略 |
|---|---|
| 新增字段 | 直接追加，客户端忽略未知字段；不改变已有字段含义 |
| 新增端点 | 直接追加，不改旧路径 |
| 收紧校验 | 先在响应中返回明确错误码，至少保留一个小版本；当前仓库无外部客户端，可直接收紧 |
| 路径或请求体破坏性变更 | 新增 `/api/v2/**`，旧路径标记弃用并保留一个迭代；本轮只在文档中规划，不实现 |
| 状态枚举扩展 | 前端按字符串渲染，不在前端硬编码枚举值；后端返回 `label` 字段 |
| AI 端点 | 默认关闭；关闭时返回 `503`，不影响其他端点 |

版本管理建议：当前为单团队单仓库，采用「路径版本 + 弃用响应头」即可，不引入网关级版本路由。`[工程判断]`
