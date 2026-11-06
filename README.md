# Industrial Application — 离散制造 MES

面向离散制造车间的生产管理系统，覆盖设备接入、物料与 BOM、工艺路线、生产计划、工单执行、质检与设备维保。系统定位、角色与目标能力见 [产品介绍](docs/PRODUCT-OVERVIEW.md)。

## 核心能力

- 设备通过 MQTT 上报状态与遥测，支持持久会话、断线重订阅与设备签名校验
- 生产计划下达为工单，工单按状态机流转：待生产 → 生产中 → 待质检 → 完工
- 开工前按 BOM 校验物料齐套，报工记录合格数与不良数
- 工艺发布审批、BOM 版本管理、设备维保记录、写操作审计
- Web 管理端（看板、主数据、计划、工单）与 uni-app 移动端（工单、报工、设备）

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Java | 17 |
| 后端 | Spring Boot | 3.3.5 |
| 后端 | Spring Security + JJWT | 6.x / 0.12.6 |
| 后端 | MyBatis-Plus | 3.5.7 |
| 后端 | Flyway | 随 Spring Boot 管理 |
| 数据库 | MySQL | 8.0+（compose 固定 8.0） |
| 缓存 | Redis | 7+（compose 固定 7-alpine） |
| 设备接入 | Eclipse Paho MQTT | 1.2.5 |
| 设备接入 | Eclipse Mosquitto | 2（compose） |
| 管理端 | Vue / TypeScript / Vite | 3.5 / 5.6 / 5.4 |
| 管理端 | Element Plus / ECharts | 2.9 / 5.5 |
| 移动端 | uni-app（Vue 3） | 3.x |
| 构建 | Maven / npm | 3.9+ / 10+ |

## 目录结构

```text
.
├── zhizao-xietong/                  # Spring Boot 后端
│   ├── src/main/java/com/zhizao/
│   │   ├── controller/              # REST 入口（10 个）
│   │   ├── service/                 # 业务编排、状态机、齐套、编号
│   │   ├── gateway/                 # MQTT 设备网关
│   │   ├── security/                # JWT、CORS、设备签名校验
│   │   ├── entity/ mapper/ dto/ enums/
│   │   └── common/ config/ aspect/
│   ├── src/main/resources/
│   │   ├── application.yml          # 配置，全部支持环境变量覆盖
│   │   └── db/migration/            # Flyway 迁移 V1–V5
│   └── src/test/java/               # 29 个单元测试
├── zhizao-xietong-web/              # Vue 3 管理端
│   ├── src/views/                   # 8 个业务页面
│   └── vite.config.ts               # 开发服务器 5173，代理 /api
├── mobile-app/                      # uni-app 移动端（3 个页面）
├── docs/                            # 工程文档
├── .githooks/                       # commit-msg 与 pre-commit 钩子
├── docker-compose.yml               # MySQL / Redis / Mosquitto / 后端 / 前端
├── mosquitto.conf
└── .env.example
```

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 后端编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 20+ | 管理端与移动端构建 |
| npm | 10+ | 依赖安装 |
| MySQL | 8.0+ | 业务库 `zhizao_xietong`，Flyway 自动建表 |
| Redis | 7+ | 缓存，默认 `localhost:6379` |
| Mosquitto | 2.x | 可选；不启用 MQTT 时设备接入口保持关闭 |
| Docker | 24+ | 仅在容器方式部署时需要 |

最低配置：2 核 CPU、4 GB 内存。本地同时运行 MySQL、Redis 与前后端即可。

## 快速开始（本地开发）

以下命令均已在本机执行验证，操作系统为 macOS，MySQL 8.4.8、Redis 8.10.1、Node 22.23.4、JDK 17.0.20。

### 1. 初始化配置

```bash
cp .env.example .env
# 编辑 .env，至少填写 DB_PASSWORD 与 JWT_SECRET（JWT_SECRET 至少 32 字节）
```

### 2. 创建数据库

```bash
set -a && source .env && set +a
mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS zhizao_xietong DEFAULT CHARSET utf8mb4;"
```

表结构由 Flyway 在后端启动时自动执行 `db/migration/V1`–`V5`，不需要手工建表。

### 3. 启动后端

```bash
set -a && source .env && set +a
cd zhizao-xietong && mvn spring-boot:run
```

- 端口：8080
- 健康检查：<http://localhost:8080/actuator/health>
- Swagger UI：<http://localhost:8080/swagger-ui/index.html>

首次启动时 `DataInitializer` 会创建默认账号：

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `zhangsan` | `123456` | USER |

生产环境必须替换默认密码。

### 4. 启动管理端

```bash
cd zhizao-xietong-web
npm ci
npm run dev
```

访问 <http://localhost:5173>，使用上表账号登录。Vite 将 `/api` 代理到 `http://localhost:8080`。

### 5. 启动移动端 H5（可选）

```bash
cd mobile-app
npm install
npm run dev:h5
```

已验证：`npm install` 与 `npm run build:h5` 均可完成（uni-app 编译器 4.75）。移动端构建尚未纳入 CI。

### 6. 启用 MQTT（可选）

```bash
docker compose up -d mqtt
MQTT_ENABLED=true MQTT_BROKER_URL=tcp://localhost:1883 mvn spring-boot:run
```

发布一条未签名上报（`DEVICE_AUTH_ENABLED=false` 时可用）：

```bash
docker exec zhizao-mqtt mosquitto_pub -h localhost \
  -t 'factory/devices/EQ-202609-0001/status' -q 1 \
  -m '{"deviceNo":"EQ-202609-0001","status":"RUNNING","temperature":45.2,"rpm":1500,"current":8.5}'
```

设备鉴权启用后，上报需携带 `keyId`、`timestamp`、`nonce`、`signature`，签名规则见 [接口契约](docs/API-CONTRACT.md) 4.1.1。

## 配置说明

所有配置均可通过环境变量覆盖，仓库不保存真实密码或密钥。

| 变量 | 作用 | 默认值 | 必填 | 示例 |
|---|---|---|---|---|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://localhost:3306/zhizao_xietong...` | 否 | `jdbc:mysql://db:3306/zhizao_xietong` |
| `DB_USERNAME` | 数据库账号 | `root` | 否 | `mes_app` |
| `DB_PASSWORD` | 数据库密码 | 无 | 是 | 由部署环境注入 |
| `JWT_SECRET` | JWT 签名密钥 | 无 | 是 | 至少 32 字节的随机串 |
| `JWT_EXPIRATION` | 令牌有效期（毫秒） | `86400000` | 否 | `3600000` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost` / `6379` | 否 | `redis` / `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 | 否 | 由部署环境注入 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端源，逗号分隔 | `http://localhost:5173,http://localhost:8081` | 否 | `https://mes.example.com` |
| `MQTT_ENABLED` | 是否启用 MQTT 网关 | `false` | 否 | `true` |
| `MQTT_BROKER_URL` | Broker 地址 | `tcp://localhost:1883` | 否 | `tcp://mqtt:1883` |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | Broker 账号 | 空 | 否 | 由部署环境注入 |
| `MQTT_TOPIC` | 设备上报主题 | `factory/devices/+/status` | 否 | 同上 |
| `MQTT_CLIENT_ID` | 客户端标识 | `zhizao-backend` | 否 | 多实例必须各不相同 |
| `MQTT_QOS` | 订阅与发布 QoS | `1` | 否 | `1` |
| `DEVICE_ONLINE_THRESHOLD_SECONDS` | 超过该时长未上报视为离线 | `90` | 否 | `120` |
| `DEVICE_SIMULATION_ENABLED` | 无上报时是否返回模拟遥测 | `false` | 否 | `false` |
| `DEVICE_AUTH_ENABLED` | 是否强制设备 HMAC 签名 | `false` | 否 | `true` |
| `DEVICE_SIGNATURE_TOLERANCE_SECONDS` | MQTT 时间戳容忍窗口 | `300` | 否 | `60` |
| `DEVICE_CREDENTIAL_KEY` | 设备密钥加密主密钥（32 字节 Base64） | 空 | 开启设备鉴权时必填 | `openssl rand -base64 32` 生成 |
| `DEVICE_IDEMPOTENCY_TTL_SECONDS` | 幂等键保留时长 | `600` | 否 | `600` |
| `DEVICE_IDEMPOTENCY_MAX_ENTRIES` | 幂等键最大条数 | `10000` | 否 | `10000` |

## 打包与部署

### 后端

```bash
cd zhizao-xietong
mvn -DskipTests package
java -jar target/zhizao-xietong-1.0.0.jar
```

已实测：打包产物为 `target/zhizao-xietong-1.0.0.jar`，使用环境变量启动后 `/actuator/health` 返回 `UP`。

生产环境注意：

- `server.port` 固定为 8080，如需改端口请在反向代理层处理，或修改 `application.yml`
- JWT 密钥、数据库密码、`DEVICE_CREDENTIAL_KEY` 必须由部署环境注入
- 日志输出到标准输出，容器部署用 `docker logs` 收集；文件日志与集中采集尚未接入（S7）

### 管理端

```bash
cd zhizao-xietong-web
npm ci
npm run build
```

产物在 `dist/`，由 Nginx 托管。仓库内的 `zhizao-xietong-web/nginx.conf` 已配置 history 路由回退与 `/api` 反向代理；容器构建见 `Dockerfile`。

### Docker Compose

`docker-compose.yml` 定义了 MySQL、Redis、Mosquitto、后端、前端五个服务，端口分别为 3306、6379、1883、8080、8081。

```bash
cp .env.example .env
# 填写 MYSQL_ROOT_PASSWORD 与 JWT_SECRET
docker compose up -d --build
```

已实测：`docker compose up -d mqtt` 可正常启动 Mosquitto。完整五服务栈未在当前机器实测，原因是本机 3306 与 6379 已被本地 MySQL/Redis 占用；在独立主机上执行上述命令前，请确认端口未被占用。

## 接口与文档

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- 健康检查：`http://localhost:8080/actuator/health`

工程文档：

- [产品介绍](docs/PRODUCT-OVERVIEW.md)：目标形态、角色、能力地图与端到端流程
- [工程设计与重构方案](docs/ENGINEERING-DESIGN.md)：现状盘点、目标架构、不变量与已决策事项
- [数据模型](docs/DATA-MODEL.md)：现有表与目标表设计
- [接口契约](docs/API-CONTRACT.md)：现有端点、目标端点与契约变更
- [重构分期](docs/REFACTOR-PLAN.md)：S0–S8 阶段、验收与风险
- [历史资料](docs/legacy/README.md)：早期 PRD 与开发文档，未随重构维护

## 测试与 CI

```bash
# 后端单元测试
cd zhizao-xietong && mvn test

# 管理端生产构建（同时覆盖 TypeScript 编译与打包）
cd zhizao-xietong-web && npm ci && npm run build

# 提交信息校验（仓库根目录）
npx --no -- commitlint --from HEAD~1 --to HEAD
```

CI 配置见 `.github/workflows/ci.yml`，每次 push 与 pull request 执行四个任务：

| 任务 | 内容 | 门禁 |
|---|---|---|
| Commit messages | 对推送范围运行 commitlint | 不通过则失败 |
| Secret scan | gitleaks 只扫描本次推送或 PR 引入的提交范围，不扫全历史 | 发现密钥则失败 |
| Backend tests | `mvn -B -ntp test` | 任一测试失败则失败 |
| Frontend build | `npm ci && npm run build` | 构建失败则失败 |

本地钩子：仓库使用 `.githooks/` 下的 `commit-msg` 与 `pre-commit`，克隆后执行一次：

```bash
git config core.hooksPath .githooks
```

`commit-msg` 校验提交信息；`pre-commit` 阻止 `target/`、`dist/`、`node_modules/`、`.env`、`.DS_Store` 等文件进入提交，并在检测到 `gitleaks` 时扫描暂存内容。

## 常见问题

**端口被占用**

现象：启动时报 `Port 8080 was already in use`。

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

结束占用进程，或修改 `application.yml` 的 `server.port`。管理端 5173、容器映射 8081 同理。

**数据库连接失败**

现象：启动日志出现 `Communications link failure` 或 `Access denied for user`。

确认 MySQL 已启动、`DB_URL` 指向正确主机与端口、`DB_USERNAME`/`DB_PASSWORD` 与数据库账号一致。容器部署时主机名是服务名 `mysql`，不是 `localhost`。

**Flyway 迁移失败**

现象：`Validate failed: Migration checksum mismatch`。

已执行过的 `V*.sql` 不允许修改；需要变更结构时新增 `V6__xxx.sql`。开发库可删除数据库后重新启动，由 Flyway 从头执行。

**前端接口 404 或跨域**

现象：页面请求 `/api/...` 返回 404，或浏览器提示 CORS 错误。

开发环境确认前端通过 Vite 启动（5173），由 `vite.config.ts` 代理 `/api` 到 8080；直接打开 `dist/index.html` 或换用其他端口会导致代理失效。生产环境由 Nginx 代理 `/api`，并把前端域名加入 `CORS_ALLOWED_ORIGINS`。

**MQTT 连不上**

现象：日志反复出现 `MQTT 连接失败`。

确认 Broker 已启动、1883 端口可达、`MQTT_BROKER_URL` 使用 `tcp://` 前缀。多实例部署时 `MQTT_CLIENT_ID` 必须各不相同，否则新连接会把旧连接的持久会话顶掉。

**设备上报被拒绝**

开启 `DEVICE_AUTH_ENABLED=true` 后：

- `401`：`keyId` 不存在、签名不匹配或 MQTT 时间戳超出窗口
- `403`：凭证已吊销
- 未配置 `DEVICE_CREDENTIAL_KEY`：凭证签发接口直接返回错误

REST 签名对象是原始请求体，必须与发送字节完全一致；MQTT 签名对象是接口契约中定义的规范化字段串。

**设备显示离线但仍在运行**

在线状态按 `last_seen_at` 与 `DEVICE_ONLINE_THRESHOLD_SECONDS` 计算。若设备上报间隔大于阈值，调大阈值；若设备根本没有上报，检查主题是否匹配 `MQTT_TOPIC`。

## 已知限制

- 遥测历史尚未落库，`eq_telemetry` 与历史查询接口在 S2 实现；当前实时遥测来自最近一次上报或显式开启的模拟数据
- 上报幂等当前为进程内有界缓存，重启后失效；持久化去重随 S2 的遥测表落地
- 工单并发控制、状态历史与报工数量约束在 S3 落地，当前仍可能出现并发覆盖
- 工艺只有主表，没有工序建模；BOM 版本发布后仍可修改（S4）
- 排产只是按优先级与日期排序，没有产能约束（S5）
- 权限仅 ADMIN/USER 两级，无车间数据域；登录限流为单机内存（S6）
- 只有 `/actuator/health`、`/actuator/info` 与标准输出日志，没有业务指标与告警（S7）
- 单元测试 29 个，没有数据库集成测试与并发用例；移动端构建未纳入 CI
- 完整 Docker Compose 五服务栈未在本机实测（本机端口被本地 MySQL/Redis 占用）
- AI 能力未实现，处于规划阶段（P2）

## 许可

内部工程代码，未授权不得外传。
