# 接口契约

现状接口以当前代码为准，共 50 个端点（S0 基线 46 个，S1 新增设备凭证 2 个，S2 新增遥测历史 1 个，S3 新增工单历史 1 个）。目标接口尚未落地的部分仍为设计稿。

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
| 未预期异常 | HTTP 500 | 响应体 `code=500` |

两条判定线的区别是责任方：客户端把请求写错了属于前者；请求格式正确但当前业务状态不接受，属于后者。业务拒绝使用 HTTP 200 是为了让前端统一按 `code` 提示，而不是被浏览器或网关当成传输错误。

已有接口维持现状：`BusinessException` 仍由全局处理器转成 HTTP 200 + `code=500`，参数错误也仍是 HTTP 200。这样避免一次性改动 49 个端点的响应语义、把现有 Web 与移动端打崩。新接口的新写法只对新代码生效。`[代码实证]`

### 1.2 认证

- 登录后返回 JWT，放在 `Authorization: Bearer <token>`。
- 白名单：`/api/auth/**`、Swagger、`/actuator/health`、`/actuator/info`；其余端点均需登录。`[代码实证]`
- 管理类写操作使用 `@PreAuthorize("hasRole('ADMIN')")`；现场操作（开工、报工、质检、点检记录）允许 USER。
- 目标增加两类主体：用户令牌（管理端/移动端）与设备凭证（设备上报）。设备上报不再复用用户令牌。`[工程判断]`

### 1.3 时间与命名

- 请求与响应时间使用 ISO-8601，例如 `2026-11-06T10:30:00`。
- 路径使用 kebab-case；查询参数使用 camelCase。
- 分页参数统一 `pageNum`、`pageSize`，默认 1 与 10。

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
| 500 | 500 | 未预期异常；已有接口的业务拒绝也暂用此码（HTTP 200） |

`code` 是给前端判断的业务码，HTTP 状态码是给网关与监控判断的传输语义。两者不一致只出现在「业务拒绝」这一类，且仅限按 §1.1.1 口径实现的新接口。

---

## 2. 现状端点清单

「处置」列的取值：保留表示签名与语义不变；改签名表示路径或请求体需要调整；改行为表示签名不变但校验或返回语义收紧；新增表示目标阶段补充。

### 2.1 认证

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | 公开 | 登录并签发 JWT | 改行为 | 限流从单机内存迁到 Redis；记录失败次数与来源 |
| 2 | POST | `/api/auth/refresh` | 公开 | 用旧令牌换新令牌 | 改行为 | 校验 `sys_user.enabled` 与 `token_version`，禁用用户立即失效 |

### 2.2 设备

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 3 | GET | `/api/devices` | 登录 | 设备分页 | 保留 | 增加 `workshop` 过滤 |
| 4 | GET | `/api/devices/{id}` | 登录 | 设备详情 | 保留 | 返回 `lastSeenAt`、`online` |
| 5 | POST | `/api/devices` | ADMIN | 新增设备 | 保留 | 写入 `workshop`、`protocol` |
| 6 | PUT | `/api/devices/{id}` | ADMIN | 更新设备 | 保留 | 编号仍不可改 |
| 7 | DELETE | `/api/devices/{id}` | ADMIN | 逻辑删除 | 保留 | 有未完工工单时拒绝 |
| 8 | POST | `/api/devices/{id}/status` | ADMIN | 手工改状态 | 改行为 | 状态机校验：`SCRAPPED` 不可回退 |

### 2.3 设备数据与集成

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 9 | GET | `/api/devices/{id}/telemetry` | 登录 | 实时遥测 | 改行为 | 无真实上报时不再返回随机数；模拟数据由开关控制并标记 `source=SIMULATED` |
| 10 | POST | `/api/integration/device-status` | 登录 | REST 设备回传 | 改签名 | 改设备 HMAC 鉴权，增加 `ingestKey` 幂等 |
| 11 | POST | `/api/integration/mqtt/publish` | 登录 | 发布测试消息 | 改行为 | 仅 ADMIN，非生产环境启用 |
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
| 17 | GET | `/api/processes` | 登录 | 工艺分页 | 保留 | 增加 `version` 过滤 |
| 18 | GET | `/api/processes/{id}` | 登录 | 工艺详情 | 改行为 | 返回工序列表与当前版本 |
| 19 | POST | `/api/processes` | ADMIN | 新建工艺 | 保留 | 初始版本 `1`，状态 `DRAFT` |
| 20 | PUT | `/api/processes/{id}` | ADMIN | 更新工艺 | 改行为 | 已发布工艺不可改，只能新建版本 |
| 21 | DELETE | `/api/processes/{id}` | ADMIN | 删除工艺 | 保留 | 有工单引用时拒绝 |
| 22 | POST | `/api/processes/{id}/submit` | ADMIN | 提交审批 | 改行为 | 校验至少 1 道工序、BOM 至少 1 行 |
| 23 | POST | `/api/processes/{id}/approve` | ADMIN | 审批发布 | 改行为 | 发布时冻结版本，写 `published_at` |
| 24 | POST | `/api/processes/{id}/reject` | ADMIN | 驳回 | 保留 | 记录驳回原因 |

### 2.6 工艺物料清单

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 25 | GET | `/api/processes/{processId}/bom` | 登录 | 读取 BOM | 改行为 | `version` 省略时读当前发布版本，不再读全局最大版本 |
| 26 | GET | `/api/processes/{processId}/bom/versions` | 登录 | 版本列表 | 保留 | 返回版本与发布状态 |
| 27 | POST | `/api/processes/{processId}/bom/version` | ADMIN | 复制新版本 | 改行为 | 从指定版本复制，校验工艺未发布 |
| 28 | POST | `/api/processes/{processId}/bom` | ADMIN | 新增 BOM 行 | 改行为 | 校验物料存在、用量大于 0、同版本不重复 |
| 29 | PUT | `/api/processes/{processId}/bom/{id}` | ADMIN | 更新 BOM 行 | 改行为 | 已发布版本只读 |
| 30 | DELETE | `/api/processes/{processId}/bom/{id}` | ADMIN | 删除 BOM 行 | 改行为 | 已发布版本只读 |

### 2.7 生产计划

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 31 | GET | `/api/plans` | 登录 | 计划分页 | 保留 | 增加 `workshop` 过滤 |
| 32 | GET | `/api/plans/{id}` | 登录 | 计划详情 | 保留 | 返回关联工单摘要 |
| 33 | POST | `/api/plans` | ADMIN | 新建计划 | 保留 | 校验 `planEnd >= planStart`、数量大于 0 |
| 34 | PUT | `/api/plans/{id}` | ADMIN | 更新计划 | 改行为 | 已下达计划不可改数量与日期 |
| 35 | DELETE | `/api/plans/{id}` | ADMIN | 删除计划 | 保留 | 有工单时拒绝 |
| 36 | POST | `/api/plans/{id}/dispatch` | ADMIN | 下达工单 | 改签名 | 必填 `processId`；移除「自动取第一个已发布工艺」和固定「一车间」 |

### 2.8 生产工单

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 37 | GET | `/api/work-orders` | 登录 | 工单分页 | 保留 | 增加 `workshop`、`planId` 过滤 |
| 38 | GET | `/api/work-orders/{id}` | 登录 | 工单详情 | 保留 | 无 |
| 39 | POST | `/api/work-orders/{id}/start` | 登录 | 开工 | 已落地 | S3 改为从令牌取操作人；并发冲突返回 HTTP 200 + 业务码 409 |
| 40 | POST | `/api/work-orders/{id}/report` | 登录 | 报工 | 已落地 | S3 已实现数量校验、累计上限与幂等键；报工后转「待质检」 |
| 41 | POST | `/api/work-orders/{id}/qc` | 登录 | 质检 | 已落地 | S3 结论校验前置，非法结论不写质检记录 |
| 48 | GET | `/api/work-orders/{id}/history` | 登录 | 状态流转历史 | 已落地 | S3 已实现，按发生时间升序返回 |

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

端点合计：50（S0 基线 46 + S1 设备凭证 2 + S2 遥测历史 1 + S3 工单历史 1）。`[代码实证]`

---

## 3. 目标新增端点

| # | 方法 | 路径 | 鉴权 | 用途 | 阶段 |
|---|---|---|---|---|---|
| 49 | GET | `/api/processes/{id}/operations` | 登录 | 工序列表 | S4 |
| 50 | POST/PUT/DELETE | `/api/processes/{id}/operations[/{opId}]` | ADMIN | 工序维护 | S4 |
| 53 | GET | `/api/audit-logs` | ADMIN | 审计查询 | S6 |
| 54 | GET | `/api/metrics/overview` | ADMIN | 设备在线率、工单节拍、齐套率 | S7 |
| 55 | POST | `/api/ai/device-issue/suggest` | 登录 | AI 异常归因建议，只读 | S8（P2） |
| 56 | POST | `/api/ai/report/draft` | 登录 | 自然语言转结构化报工草稿，不提交 | S8（P2） |
| 57 | POST | `/api/ai/query` | ADMIN | 生产数据问答，只读视图 | S8（P2） |

新增端点均遵循同一返回体与错误码约定。设备凭证的 2 个端点已在 S1 落地（§2.10），遥测历史已在 S2 落地（§2.3 与 §4.4），工单历史已在 S3 落地（§2.8）。

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

校验顺序：幂等判重 → 工单状态为「生产中」→ `qty > 0` 且 `qualifiedQty + defectQty == qty` → 累计报工不超过工单数量 → 写入报工与状态历史 → 工单状态转「待质检」。任一校验失败返回 `400`；累计超限属于业务拒绝，返回 HTTP 200 + 业务码 `409`（见 §1.1.1）。`[代码实证]`

幂等约定：

- **生成方式**：客户端为每次「提交报工」生成一个 UUID（建议 v4），放在请求体 `idempotencyKey`；重试必须沿用同一个键，新的一次报工必须换新键。
- **作用域**：同一工单内唯一，数据库唯一索引 `uk_order_idem (order_id, idempotency_key)` 兜底；不同工单可以使用相同键。
- **长度与缺省**：最长 64 字符，超长返回 `400`；为空表示调用方不要求幂等，行为与改造前一致。Web 管理端可不传，移动端弱网重试应始终携带。
- **重复请求**：直接返回 HTTP 200 + `code=0`，响应体为当前工单快照；不校验状态、不写报工记录、不累加数量、不写状态历史。若首次提交后工单已继续流转，快照反映的是读取时的最新状态，幂等保证的是「副作用只发生一次」。
- **并发同键**：两个请求同时到达时由唯一索引裁决，后到者按重复请求处理。
- **保留期**：幂等键随报工记录保存在 `wo_report`，当前不设过期与清理任务，保留期等同于报工记录；如后续引入归档，需同步更新本节。`[代码实证]`

### 4.3 计划下达

目标请求体：

```json
{
  "processId": 1,
  "deviceId": 1,
  "workshop": "一车间"
}
```

`processId` 必填且必须为已发布工艺；`deviceId` 可为空，为空时由排产建议给出；`workshop` 为空时取计划默认车间。现状自动取第一个已发布工艺并硬编码车间，会让多工艺产品下错工艺。`[代码实证]`

### 4.4 时序查询

```http
GET /api/devices/1/telemetry/history?from=2026-11-05T00:00:00&to=2026-11-06T00:00:00&limit=500
```

响应 `data` 为按 `reportedAt` 升序的数组，字段包含 `reportedAt`、`temperature`、`rpm`、`current`、`status`、`source`。`limit` 上限 1000，超出按 1000 处理；`from > to` 返回 `400`。`[工程判断]`

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
