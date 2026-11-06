# 接口契约

现状接口以当前代码为准，共 46 个端点。目标接口为待评审设计，本轮不实现。

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

现状问题：`BusinessException` 由全局处理器转成 `code=500`，但 HTTP 状态码仍是 200；参数错误同样是 200。前端只能靠 `code` 判断，网关和监控无法按状态码统计失败。目标改为：业务拒绝返回 `400/409`，未认证 `401`，无权限 `403`，限流 `429`，服务异常 `500`，响应体保持 `code/message/data` 不变。`[代码实证]`

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
| 400 | 400 | 参数校验失败、非法状态流转 |
| 401 | 401 | 令牌缺失、过期、用户被禁用 |
| 403 | 403 | 角色或数据域不允许 |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 并发冲突、重复上报、数量超限 |
| 429 | 429 | 登录或设备上报限流 |
| 500 | 500 | 未预期异常 |

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
| 39 | POST | `/api/work-orders/{id}/start?operator=` | 登录 | 开工 | 改签名 | 操作人改为从令牌取；增加乐观锁冲突 409 |
| 40 | POST | `/api/work-orders/{id}/report` | 登录 | 报工 | 改行为 | 数量校验、累计上限、幂等键；报工后不再自动跳「待质检」以外的状态 |
| 41 | POST | `/api/work-orders/{id}/qc` | 登录 | 质检 | 改行为 | 结论校验前置；PASS 才完工；记录质检数量 |

### 2.9 设备点检与维修

| # | 方法 | 路径 | 现状鉴权 | 用途 | 处置 | 目标变更 |
|---|---|---|---|---|---|---|
| 42 | GET | `/api/maintenance` | 登录 | 维保分页 | 保留 | 支持按设备与时间范围过滤 |
| 43 | GET | `/api/maintenance/{id}` | 登录 | 维保详情 | 保留 | 无 |
| 44 | POST | `/api/maintenance` | 登录 | 新增点检/维修 | 改行为 | 校验设备存在、`mtTime` 不晚于当前时间 |
| 45 | PUT | `/api/maintenance/{id}` | ADMIN | 更新记录 | 保留 | 无 |
| 46 | DELETE | `/api/maintenance/{id}` | ADMIN | 删除记录 | 保留 | 无 |

端点合计：46。`[代码实证]`

---

## 3. 目标新增端点

| # | 方法 | 路径 | 鉴权 | 用途 | 阶段 |
|---|---|---|---|---|---|
| 47 | GET | `/api/devices/{id}/telemetry/history?from=&to=&limit=` | 登录 | 设备时序查询，默认最近 24 小时，最多 1000 条 | S2 |
| 48 | GET | `/api/work-orders/{id}/history` | 登录 | 状态流转历史 | S3 |
| 49 | GET | `/api/processes/{id}/operations` | 登录 | 工序列表 | S4 |
| 50 | POST/PUT/DELETE | `/api/processes/{id}/operations[/{opId}]` | ADMIN | 工序维护 | S4 |
| 51 | POST | `/api/devices/{id}/credential` | ADMIN | 签发设备凭证，返回一次明文密钥 | S1 |
| 52 | POST | `/api/devices/{id}/credential/revoke` | ADMIN | 吊销设备凭证 | S1 |
| 53 | GET | `/api/audit-logs` | ADMIN | 审计查询 | S6 |
| 54 | GET | `/api/metrics/overview` | ADMIN | 设备在线率、工单节拍、齐套率 | S7 |
| 55 | POST | `/api/ai/device-issue/suggest` | 登录 | AI 异常归因建议，只读 | S8（P2） |
| 56 | POST | `/api/ai/report/draft` | 登录 | 自然语言转结构化报工草稿，不提交 | S8（P2） |
| 57 | POST | `/api/ai/query` | ADMIN | 生产数据问答，只读视图 | S8（P2） |

新增端点均遵循同一返回体与错误码约定。

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

校验顺序：工单状态为「生产中」→ `qty > 0` → `qualifiedQty + defectQty == qty` → 累计报工不超过工单数量 → 写入报工与状态历史 → 工单状态转「待质检」。任一校验失败返回 `400`，累计超限返回 `409`。`[工程判断]`

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
