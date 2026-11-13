# Industrial Application — 离散制造 MES

面向离散制造车间的生产管理系统，覆盖设备接入、物料与 BOM、工艺路线、生产计划、工单执行、质检与设备维保。系统定位、角色与目标能力见 [产品介绍](docs/PRODUCT-OVERVIEW.md)。

## 核心能力

- 设备通过 MQTT 上报状态与遥测，支持持久会话、断线重订阅、设备签名校验与遥测历史查询
- 生产计划下达为工单，工单按状态机流转：待生产 → 生产中 → 待质检 → 完工
- 开工前按 BOM 校验物料齐套，报工记录合格数与不良数
- 工艺版本化与工序路线、BOM 按工艺版本管理、设备维保记录、写操作审计
- 工单质检完工后生成待入库单，确认入库按「产品编码 + 车间」原子累加成品库存，取消需填写原因
- Web 管理端（看板、主数据、计划、工单、成品入库）与 uni-app 移动端（工单、报工、设备）

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
│   │   ├── controller/              # REST 入口（18 个）
│   │   ├── service/                 # 业务编排、状态机、齐套、编号
│   │   ├── gateway/                 # MQTT 设备网关
│   │   ├── security/                # JWT、CORS、设备签名校验
│   │   ├── entity/ mapper/ dto/ enums/
│   │   └── common/ config/ aspect/
│   ├── src/main/resources/
│   │   ├── application.yml          # 配置，全部支持环境变量覆盖
│   │   ├── db/migration/            # Flyway 迁移 V1–V33（V27、V28 为空号）
│   │   └── db/seed/                 # 演示种子数据，默认不加载
│   └── src/test/java/               # 385 个测试（6 个 Testcontainers 集成类）
├── zhizao-xietong-web/              # Vue 3 管理端
│   ├── src/views/                   # 15 个页面组件
│   └── vite.config.ts               # 开发服务器 5173，代理 /api
├── mobile-app/                      # uni-app 移动端（4 个页面）
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
# 本地启动：编辑 .env，至少填写 DB_PASSWORD 与 JWT_SECRET（JWT_SECRET 至少 32 字节）
# docker compose：还必须填写 MYSQL_ROOT_PASSWORD、JWT_SECRET、CORS_ALLOWED_ORIGINS、REDIS_PASSWORD
# 以及 MQTT_USERNAME / MQTT_PASSWORD，任一项缺失会直接启动失败
```

### 2. 创建数据库

```bash
set -a && source .env && set +a
mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS zhizao_xietong DEFAULT CHARSET utf8mb4;"
```

表结构由 Flyway 在后端启动时自动执行 `db/migration/V1`–`V33`，不需要手工建表。演示种子数据位于 `db/seed`，默认不加载，需要演示环境时显式设置 `APP_SEED_DEMO_ENABLED=true`（见「数据库迁移」）。

### 3. 启动后端

```bash
set -a && source .env && set +a
cd zhizao-xietong && mvn spring-boot:run
```

- 端口：8080
- 健康检查：<http://localhost:8080/actuator/health>
- 业务指标：<http://localhost:8080/actuator/metrics>（如 `zhizao.devices.online`、`zhizao.telemetry.reports`；仅 ADMIN 可读）
- Swagger UI：<http://localhost:8080/swagger-ui/index.html>（仅 `dev` profile 或显式设置 `API_DOCS_ENABLED=true` 时开放）

首次启动时 `DataInitializer` 会创建管理员账号 `admin`：

- 初始口令通过环境变量 `APP_BOOTSTRAP_ADMIN_PASSWORD` 注入；
- 非 `dev` 环境未配置时启动直接失败；`dev` 环境未配置时启动日志会打印一次性随机口令，请登录后立即修改；
- 演示账号 `zhangsan` 仅在 `dev` profile 下创建，口令来自 `DEMO_INIT_PASSWORD`；
- 演示账号的车间范围来自 `DEMO_INIT_WORKSHOP`，留空则不预设车间（该账号看不到车间业务数据）。

### 4. 启动管理端

```bash
cd zhizao-xietong-web
npm ci
npm run dev
```

访问 <http://localhost:5173>，使用管理员账号登录。Vite 将 `/api` 代理到 `http://localhost:8080`。

### 5. 启动移动端 H5（可选）

```bash
cd mobile-app
npm install
npm run dev:h5
```

已验证：`npm install` 与 `npm run build:h5` 均可完成（uni-app 编译器 4.75）。移动端构建尚未纳入 CI。

### 6. 启用 MQTT（可选）

Broker 已关闭匿名接入，必须先准备账号（compose 会用 `MQTT_USERNAME` / `MQTT_PASSWORD` 生成口令文件）：

```bash
docker compose up -d mqtt
MQTT_ENABLED=true MQTT_BROKER_URL=tcp://localhost:1883 \
  MQTT_USERNAME="$MQTT_USERNAME" MQTT_PASSWORD="$MQTT_PASSWORD" mvn spring-boot:run
```

发布一条未签名上报（`DEVICE_AUTH_ENABLED=false` 时可用）：

```bash
docker exec zhizao-mqtt mosquitto_pub -h localhost \
  -u "$MQTT_USERNAME" -P "$MQTT_PASSWORD" \
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
| `MYSQL_ROOT_PASSWORD` | compose 中 MySQL root 口令 | 空 | docker compose 必填 | 由部署环境注入 |
| `JWT_SECRET` | JWT 签名密钥 | 无 | 是 | 至少 32 字节的随机串 |
| `JWT_EXPIRATION` | 令牌有效期（毫秒） | `86400000` | 否 | `3600000` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost` / `6379` | 否 | `redis` / `6379` |
| `REDIS_PASSWORD` | Redis 密码（compose 中开启 `requirepass`） | 空 | docker compose 必填 | 由部署环境注入 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端源，逗号分隔 | `http://localhost:5173,http://localhost:8081` | docker compose 必填 | `https://mes.example.com` |
| `API_DOCS_ENABLED` | 是否开放 Swagger/OpenAPI | `false` | 否 | `true`（仅内部或 dev） |
| `TRUSTED_PROXIES` | 可信反向代理固定 IP，逗号分隔；留空则不采信 `X-Forwarded-For` | 空 | 否 | `10.0.0.10,10.0.0.11` |
| `SSO_COOKIE_SECURE` | SSO 浏览器标识 Cookie 是否带 `Secure` | `true` | 否 | HTTPS 部署保持 `true`，仅本机 http 调试可设 `false` |
| `MQTT_ENABLED` | 是否启用 MQTT 网关 | `false` | 否 | `true` |
| `MQTT_BROKER_URL` | Broker 地址 | `tcp://localhost:1883` | 否 | `tcp://mqtt:1883` |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | Broker 账号（compose 中关闭匿名，必填） | 空 | docker compose 必填 | 由部署环境注入 |
| `MQTT_TOPIC` | 设备上报主题 | `factory/devices/+/status` | 否 | 同上 |
| `MQTT_CLIENT_ID` | 客户端标识 | `zhizao-backend` | 否 | 多实例必须各不相同 |
| `MQTT_QOS` | 订阅与发布 QoS | `1` | 否 | `1` |
| `APP_SEED_DEMO_ENABLED` | 空库启动时是否加载 `db/seed` 演示种子 | `false` | 否 | `true`（仅本地/演示） |
| `DEVICE_ONLINE_THRESHOLD_SECONDS` | 超过该时长未上报视为离线 | `90` | 否 | `120` |
| `DEVICE_SIMULATION_ENABLED` | 无上报时是否返回模拟遥测 | `false` | 否 | `false` |
| `DEVICE_AUTH_ENABLED` | 是否强制设备 HMAC 签名；compose 已透传该变量 | `false` | 否 | `true`（存量设备改造完成后） |
| `DEVICE_SIGNATURE_TOLERANCE_SECONDS` | MQTT 时间戳容忍窗口 | `300` | 否 | `60` |
| `DEVICE_CREDENTIAL_KEY` | 设备密钥加密主密钥（32 字节 Base64）；compose 已透传该变量 | 空 | 开启设备鉴权时必填，缺失时后端启动失败 | `openssl rand -base64 32` 生成 |
| `DEVICE_IDEMPOTENCY_TTL_SECONDS` | 幂等键保留时长 | `600` | 否 | `600` |
| `DEVICE_IDEMPOTENCY_MAX_ENTRIES` | 幂等键最大条数 | `10000` | 否 | `10000` |
| `DEVICE_TELEMETRY_RETENTION_DAYS` | 遥测时序保留天数，过期数据每日清理 | `90` | 否 | `30` |
| `DEVICE_ALARM_WECOM_ENABLED` | 是否把设备报警推送到企业微信 | `false` | 否 | `true` |
| `DEVICE_ALARM_WECOM_CORP_ID` / `_AGENT_ID` / `_SECRET` | 报警通知的应用凭据，任一为空时静默跳过发送 | 空 | 开启通知时必填 | 由部署环境注入 |
| `AI_ENABLED` | 是否启用 AI 旁路；关闭时三个 AI 端点返回 503 | `false` | 否 | `true` |
| `AI_PROVIDER` | 模型实现：`fake` 离线假模型 / `external` OpenAI 兼容接口 | `fake` | 否 | `fake` |
| `AI_TIMEOUT_SECONDS` | 模型调用超时 | `5` | 否 | `5` |
| `AI_MODEL_URL` | 外部模型完整地址 | 空 | `AI_PROVIDER=external` 时必填 | `https://api.openai.com/v1/chat/completions` |
| `AI_API_KEY` | 外部模型密钥，只从环境变量读取，不写日志 | 空 | `AI_PROVIDER=external` 时必填 | 由部署环境注入 |
| `AI_MODEL` | 模型名称 | `gpt-4o-mini` | 否 | `gpt-4o-mini` |
| `AI_MAX_ROWS` | Text-to-SQL 返回行数上限 | `200` | 否 | `200` |
| `AI_QUERY_TIMEOUT_SECONDS` | Text-to-SQL 语句超时 | `3` | 否 | `3` |
| `AI_DATASOURCE_URL` | AI 专属只读数据源 JDBC 地址 | 空 | 否 | `jdbc:mysql://...` |
| `AI_DATASOURCE_USERNAME` | AI 只读账号 | 空 | 配置 url 时建议填写 | `ai_reader` |
| `AI_DATASOURCE_PASSWORD` | AI 只读账号口令 | 空 | 配置 url 时建议填写 | 由部署环境注入 |
| `SSO_ENABLED` | 是否启用企业微信扫码登录；关闭时三个 SSO 端点明确拒绝 | `false` | 否 | `true` |
| `SSO_JIT_ENABLED` | 未开通用户首次扫码是否自动建号；关闭时返回「账号未开通，请联系管理员」 | `false` | 否 | `true` |
| `SSO_WECOM_CORP_ID` / `_AGENT_ID` / `_SECRET` | 企微应用凭据 | 空 | 开启 SSO 时必填 | 由部署环境注入 |
| `SSO_WECOM_REDIRECT_URI` | 授权回调页，需为企微可信域名下的 HTTPS 地址 | 空 | 开启 SSO 时必填 | `https://mes.example.com/sso/callback` |

## 打包与部署

### docker compose 环境变量透传

`docker-compose.yml` 的 `backend.environment` 只透传有限几类变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`CORS_ALLOWED_ORIGINS`、`API_DOCS_ENABLED`、`TRUSTED_PROXIES`、`SSO_COOKIE_SECURE`、`APP_BOOTSTRAP_ADMIN_PASSWORD`、`APP_SEED_DEMO_ENABLED`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`MQTT_ENABLED`、`MQTT_BROKER_URL`、`MQTT_USERNAME`、`MQTT_PASSWORD`、`DEVICE_AUTH_ENABLED`、`DEVICE_CREDENTIAL_KEY` 与 `TZ`。`.env` 只参与 compose 的变量替换，**不会自动进入容器**。其余 `SSO_*` 变量同样不在透传清单内，开启 SSO 时需手工追加到 `backend.environment`。

因此除 `SSO_COOKIE_SECURE` 外的 `SSO_*`（如 `SSO_ENABLED`、`SSO_WECOM_*`）以及 `AI_*`、`DEVICE_ALARM_*` 即使写进 `.env`，容器内仍取 `application.yml` 的默认值（均为关闭）。需要启用时，手工把对应变量追加到 `docker-compose.yml` 的 `backend.environment`，示例：

```yaml
  backend:
    environment:
      # 其余已有项（DB_URL、DB_USERNAME、DB_PASSWORD、JWT_SECRET、CORS_ALLOWED_ORIGINS、REDIS_*、MQTT_*、TZ）保持不变
      # 追加：企业微信扫码登录
      SSO_ENABLED: ${SSO_ENABLED:-false}
      SSO_JIT_ENABLED: ${SSO_JIT_ENABLED:-false}
      SSO_WECOM_CORP_ID: ${SSO_WECOM_CORP_ID:-}
      SSO_WECOM_AGENT_ID: ${SSO_WECOM_AGENT_ID:-}
      SSO_WECOM_SECRET: ${SSO_WECOM_SECRET:-}
      SSO_WECOM_REDIRECT_URI: ${SSO_WECOM_REDIRECT_URI:-}
      # 追加：设备报警企业微信通知
      DEVICE_ALARM_WECOM_ENABLED: ${DEVICE_ALARM_WECOM_ENABLED:-false}
      DEVICE_ALARM_WECOM_CORP_ID: ${DEVICE_ALARM_WECOM_CORP_ID:-}
      DEVICE_ALARM_WECOM_AGENT_ID: ${DEVICE_ALARM_WECOM_AGENT_ID:-}
      DEVICE_ALARM_WECOM_SECRET: ${DEVICE_ALARM_WECOM_SECRET:-}
      # 追加：AI 旁路（按需）
      AI_ENABLED: ${AI_ENABLED:-false}
      AI_PROVIDER: ${AI_PROVIDER:-fake}
```

真实凭据仍只放在部署环境的 `.env` 或密钥管理里，不要写进 `docker-compose.yml` 本身；仓库中的 compose 必填项逻辑（`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`、`MQTT_USERNAME`、`MQTT_PASSWORD`、`JWT_SECRET`、`CORS_ALLOWED_ORIGINS`）不因本说明改变。

### 启用设备 HMAC 鉴权（分步）

默认 `DEVICE_AUTH_ENABLED=false`，存量设备继续按原有方式上报。由于 MQTT 仍是共享账号，关闭鉴权时任一客户端都能伪造其他设备编号的上报，生产环境应尽快按下面顺序开启。鉴权是全局开关，没有「单台设备灰度」的服务端开关，只能先在设备侧逐台改造，全部改造完成后再统一打开。

1. 生成主密钥并写入部署环境（不要提交仓库）：`openssl rand -base64 32`，填到 `.env` 的 `DEVICE_CREDENTIAL_KEY`。
2. 保持 `DEVICE_AUTH_ENABLED=false` 启动后端。compose 已透传这两个变量，只需改 `.env`，不需要改 `docker-compose.yml`。
3. 用管理员账号逐台签发凭证：`POST /api/devices/{id}/credential`，明文密钥只在响应中出现一次，写入设备侧安全存储。设备需支持 REST 的 `X-Device-Key` + `X-Device-Signature`，或 MQTT 消息体中的 `keyId` / `timestamp` / `nonce` / `signature`。
4. 逐台观察后端日志，确认设备已切换为签名上报（成功为 `MQTT 收到设备上报`）；此时鉴权尚未生效，未改造设备仍可上报。
5. 全部设备改造完成后，把 `DEVICE_AUTH_ENABLED` 改为 `true` 并重启 backend。重启后未带凭据、签名错误、MQTT 时间戳越界或凭证已吊销的上报会被拒绝：REST 返回 401/403，MQTT 丢弃并记录 WARN。开启鉴权但未配置 `DEVICE_CREDENTIAL_KEY` 时后端启动直接失败，不会带病运行。
6. 回退：把 `DEVICE_AUTH_ENABLED` 改回 `false` 重启即可恢复原行为；已签发凭证保留，不需要重新签发。注意轮换主密钥必须先为所有设备重签凭证，否则既有密文无法解密。

存量设备兼容性：鉴权开启后没有「未升级设备白名单」，只要还有设备未改造，它的上报就会被整体拒绝，因此不要提前打开。过渡期只能保持关闭，或接受未改造设备停止上报；设备改造完成后统一开启才能获得防伪造能力。

### 后端

```bash
cd zhizao-xietong
mvn -DskipTests package
java -jar target/zhizao-xietong-1.0.0.jar
```

已实测：打包产物为 `target/zhizao-xietong-1.0.0.jar`，使用环境变量启动后 `/actuator/health` 返回 `UP`。

生产环境注意：

- `server.port` 固定为 8080，如需改端口请在反向代理层处理，或修改 `application.yml`
- JWT 密钥、数据库密码、`DEVICE_CREDENTIAL_KEY` 必须由部署环境注入；启用外部模型时 `AI_API_KEY` 同样只能由环境变量注入，不得写入配置文件或日志
- 日志输出到标准输出，每行带 `traceId`，可用响应头 `X-Request-Id` 串联一次请求；文件日志与集中采集尚未接入
- 默认日志级别为 `INFO`，MyBatis SQL 与参数**只在 `dev` profile 下输出**（`SPRING_PROFILES_ACTIVE=dev`），生产不会把业务数据打进日志
- 容器方式运行时各服务已配置 `json-file` 日志轮转（`max-size=10m`、`max-file=5`），避免容器日志无限增长

### 数据库迁移

后端启动时由 Flyway 自动执行 `src/main/resources/db/migration`。升级前先备份；已执行过迁移的环境不要修改历史迁移文件，Flyway 会因 checksum 变化拒绝启动，修复只能追加新版本。

#### 演示种子数据

`V2__seed.sql` 提供演示设备、物料、工艺、计划与 BOM，位于 `src/main/resources/db/seed`，**默认不加载**。空库按默认配置启动后不会写入任何演示主数据。

| 场景 | 配置 | 行为 |
|---|---|---|
| 生产 / 默认 | `APP_SEED_DEMO_ENABLED=false`（默认） | 只执行 `db/migration`，不灌入演示数据 |
| 本地 / 演示空库 | `APP_SEED_DEMO_ENABLED=true` | 额外加载 `db/seed`，一键装出演示环境 |
| 已执行过 V2 的存量库 | 保持默认即可 | 种子关闭时由 `FlywaySeedConfig` 追加 `*:missing`，放行缺失的历史版本，不会报 checksum 或 missing migration |

开关只在空库首次迁移时生效。已升级到最新版本的存量库不要开启：V2 依赖 V1 的表结构，V11/V22 之后 `eq_device.workshop` 已无默认值，回放会失败；且此时 V2 是低于基线的待执行迁移，Flyway 会显式校验失败而不是静默跳过。演示环境请使用全新空库。checksum、失败迁移与乱序迁移校验保持开启，未被整体关闭。

**注意**：默认路径追加的 `*:missing` 是 Flyway 全局校验模式，它也会掩盖「已执行过的迁移文件被误删或漏拷」的问题。升级时如果发现某个历史迁移文件不见了，不要因为启动成功就认为升级正常，请先核对 `flyway_schema_history` 与 `db/migration` 文件清单。种子开启时不追加该模式，保持严格校验。

`V15__process_unique_version.sql` **非幂等**：内容是 `ALTER TABLE pr_process DROP INDEX uk_process_no`，而 MySQL 8 不支持 `DROP INDEX IF EXISTS`。若某环境曾手工删除该索引，V15 会以「Can't DROP 'uk_process_no'; check that column/key exists」失败，需二选一处理后重跑：

1. 先恢复同名索引再重跑迁移：`ALTER TABLE pr_process ADD UNIQUE KEY uk_process_no (process_no);`
2. 确认索引确已删除后，手工向 `flyway_schema_history` 补一条 V15 成功记录（`version='15'`、`success=1`，checksum 与迁移文件一致），再重启。手工补记录前务必备份该表。

### 管理端

```bash
cd zhizao-xietong-web
npm ci
npm run build
```

产物在 `dist/`，由 Nginx 托管。仓库内的 `zhizao-xietong-web/nginx.conf` 已配置 history 路由回退与 `/api` 反向代理；容器构建见 `Dockerfile`。

### Docker Compose

`docker-compose.yml` 定义了 MySQL、Redis、Mosquitto、后端、前端五个服务（另有一次性 `mqtt-init` 用于生成口令文件与 ACL）。宿主机端口：后端 `8080`、前端 `8081`、MQTT TLS `8883` 对内网发布，可用环境变量覆盖（`BACKEND_PORT`、`FRONTEND_PORT`、`MQTT_TLS_PORT`）；MQTT 明文 `1883`、MySQL `3306` 与 Redis `6379` **只绑 `127.0.0.1`** 供本机调试，不向内网发布，生产环境可删除这几条端口映射。8883 默认绑定所有网卡，若宿主机存在公网地址，生产环境应把该条映射改成具体内网 IP（如 `- "192.168.1.10:8883:8883"`）。

```bash
cp .env.example .env
# 填写 MYSQL_ROOT_PASSWORD、REDIS_PASSWORD、MQTT_USERNAME、MQTT_PASSWORD、JWT_SECRET 与 CORS_ALLOWED_ORIGINS
# CORS_ALLOWED_ORIGINS 必须包含浏览器实际访问前端的地址（协议+主机+端口），否则登录会被 403 拦截
# 首次部署先生成 MQTT TLS 证书，缺失时 mqtt 会启动失败
./scripts/gen-mqtt-certs.sh
docker compose up -d --build
```

已实测：五服务栈可完整拉起并跑通关键链路。一键验证脚本：

```bash
./scripts/verify.sh
# 成功后保留现场：./scripts/verify.sh --keep
```

脚本使用 `13306/16379/11883/18080/18081` 避开本机端口，依次执行：带浏览器 Origin 登录（校验 CORS 白名单）→ 建计划 → 下达工单 → 开工 → 部分报工（状态不变）→ 报满转待质检 → 质检 → 查状态历史 → 查遥测历史 → 查指标，每步输出 PASS/FAIL，失败时打印日志尾部并保留现场，成功后自动 `docker compose down -v` 清理。

> 注意：报工口径调整后，`scripts/verify.sh` 中「部分报工后状态不变」「累计数量」等断言已与当前实现不一致，脚本当前不可直接作为验收依据，需要单独修复；修复前请以接口与单元测试结果为准。

### 重启策略

常驻服务（`mysql`、`redis`、`mqtt`、`backend`、`frontend`）统一使用 `restart: unless-stopped`：进程异常退出或宿主机重启后自动拉起，人工 `docker compose stop` 后不会被重新拉起。一次性任务 `mqtt-init` 不设重启策略，避免反复重建口令文件。

### MQTT TLS 与 ACL

设备入口是 TLS `8883`；明文 `1883` 只发布到 `127.0.0.1` 供本机调试，后端在容器网络内继续用 `tcp://mqtt:1883`，因此应用配置与信任库都不需要改。

```bash
# 首次部署或更换证书时执行：CA 写入 deploy/mqtt/ca/，服务端证书写入 deploy/mqtt/certs/（均已 gitignore）
# 只把 certs/ 挂进 broker 容器，CA 私钥不进入容器
./scripts/gen-mqtt-certs.sh
# 设备通过 IP 或域名访问时，用 SAN 覆盖证书绑定名（默认 DNS:mqtt,DNS:localhost,IP:127.0.0.1）
MQTT_CERT_SAN="DNS:mqtt,IP:192.168.1.10" ./scripts/gen-mqtt-certs.sh
```

证书缺失时 `mqtt` 会启动失败并持续重启，这是刻意的 fail-fast：补证书后再 `docker compose up -d`，不会静默降级回明文。

`mqtt-init` 用 `MQTT_USERNAME` / `MQTT_PASSWORD` 生成口令文件，并按 `MQTT_ACL_TOPICS`（空格分隔，默认 `factory/devices/+/status`）生成 `acl_file`；未匹配的主题一律拒绝。读权限按消息逐条校验，因此即便订阅 `#`，也只能收到 ACL 允许的主题。修改 `MQTT_ACL_TOPICS` 或口令后，`mqtt-init` 只重建文件，broker 不会自动重载，需要执行 `docker compose up -d --force-recreate mqtt` 使其生效。

**设备侧改造代价**：设备需支持 TLS（多数 4G DTU / 工业网关原生支持）、导入 `deploy/mqtt/ca/ca.crt`、把连接端口从 `1883` 改为 `8883`；账号口令不变，也不需要客户端证书。不支持 TLS 的老设备需要升级固件或加装网口/串口网关；由于明文端口不再对内网发布，改造完成前该设备无法接入（回退方式是临时把 compose 中 1883 的绑定改回 `0.0.0.0`，属应急手段，需同时接受明文风险）。

**残留风险**：当前仍是“共享账号 + 主题级 ACL”，任一设备都能发布到其他设备编号的主题（如 `factory/devices/{其他编号}/status`）。彻底隔离需要一设备一账号，ACL 从设备清单批量生成成 `topic readwrite factory/devices/{deviceNo}/status`，这依赖设备台账与运维流程，留作后续改造。

### 备份与恢复（MySQL）

```bash
# 备份：默认写到仓库根目录 backups/，保留 14 天
./scripts/db-backup.sh
BACKUP_DIR=/srv/backup BACKUP_RETENTION_DAYS=30 ./scripts/db-backup.sh

# 恢复：会覆盖同名表，属破坏性操作，不带 --yes 直接拒绝
# 先停应用写入，恢复完成后再启动
docker compose stop backend
./scripts/db-restore.sh backups/zhizao_xietong-20261113-033000.sql.gz --yes
docker compose start backend
```

- 备份使用 `--single-transaction` 取一致性快照，包含表结构、数据、存储过程、触发器与事件，产物为 `.sql.gz` 加同名 `.sha256`。
- 写正式文件名前先做 gzip 完整性校验与 `CREATE TABLE` 存在性检查，导出失败不会留下半截文件。
- 恢复前校验 gzip 与 SHA-256；校验不通过不恢复，并自动对当前库再备份一次（可用 `RESTORE_PRE_BACKUP_DIR` 指定目录），误用旧备份时仍可回退。
- 恢复前会从备份识别目标库并与 `DB_NAME`（默认 `zhizao_xietong`）比对，跨库恢复需显式设置 `RESTORE_ALLOW_OTHER_DB=1`，避免拿其他库的备份静默覆盖当前库。
- 两个脚本都通过 `docker compose exec` 在容器内执行，口令取自 compose 注入的 `MYSQL_ROOT_PASSWORD` 并写入 `MYSQL_PWD`，不出现在命令行与进程列表里。

备份需要每天定时执行并异地留存，建议在独立环境用同一备份文件做恢复演练，核对表数量与 `flyway_schema_history` 行数：

```cron
30 3 * * * cd /opt/zhizao && BACKUP_DIR=/srv/backup BACKUP_RETENTION_DAYS=14 ./scripts/db-backup.sh >> /var/log/zhizao-backup.log 2>&1
```

## 接口与文档

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`（默认关闭，需 `API_DOCS_ENABLED=true` 或 `dev` profile）
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`（同上）
- 健康检查：`http://localhost:8080/actuator/health`（公开）；`/actuator/info` 公开；`/actuator/metrics` 仅 ADMIN

工程文档：

- [产品介绍](docs/PRODUCT-OVERVIEW.md)：目标形态、角色、能力地图与端到端流程
- [工程设计与重构方案](docs/ENGINEERING-DESIGN.md)：现状盘点、目标架构、不变量与已决策事项
- [数据模型](docs/DATA-MODEL.md)：现有表与目标表设计
- [接口契约](docs/API-CONTRACT.md)：现有端点、目标端点与契约变更
- [重构分期](docs/REFACTOR-PLAN.md)：S0–S8 阶段、验收与风险
- [历史资料](docs/legacy/README.md)：早期 PRD 与开发文档，未随重构维护

## 测试与 CI

```bash
# 后端单元测试（不含 Testcontainers 集成类，无需 Docker）
cd zhizao-xietong && mvn test

# 后端全量测试：单元测试 + 6 个 Testcontainers 集成类（21 个用例）
# 必须在 Docker 可用时执行，缺少 Docker 会直接构建失败；确需跳过用 -DskipITs
cd zhizao-xietong && mvn verify

# 管理端生产构建（同时覆盖 TypeScript 编译与打包）
cd zhizao-xietong-web && npm ci && npm run build

# 提交信息校验（仓库根目录）
npx --no -- commitlint --from HEAD~1 --to HEAD
```

集成测试使用 Testcontainers 启动 `mysql:8.4`，覆盖工单状态机合法/非法流转、真实并发冲突、工艺版本隔离、遥测入库、AI 只读会话、计划下达幂等，以及成品入库的条件更新、超额/重复确认与并发确认只累加一次库存。这些类由 maven-failsafe-plugin 在 `mvn verify` 阶段强制执行，缺少 Docker 时会直接构建失败，不再静默跳过；首次运行需拉取镜像，本机约 60–90 秒，CI 约 1 分钟。

本机使用 Colima 时，Testcontainers 不读取 colima 的 Docker context，且 virtiofs 不支持把 unix socket 挂载进 Ryuk 容器。跑集成测试需要显式指定 socket 并关闭 Ryuk：

```bash
cd zhizao-xietong
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
mvn verify
```

这是 Colima + virtiofs 的本地限制；CI 使用标准 Docker socket，不需要这两个变量。

CI 配置见 `.github/workflows/ci.yml`，每次 push 与 pull request 执行五个任务：

| 任务 | 内容 | 门禁 |
|---|---|---|
| Commit messages | 对推送范围运行 commitlint | 不通过则失败 |
| Secret scan | gitleaks 只扫描本次推送或 PR 引入的提交范围，不扫全历史 | 发现密钥则失败 |
| Backend tests | `mvn -B -ntp verify`（含 failsafe 集成测试） | 任一测试失败、集成用例未执行或被跳过则失败 |
| Frontend build | `npm ci && npm run build` | 构建失败则失败 |
| Mobile tests and build | 移动端依赖安装、测试与构建 | 失败则失败 |

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

开发环境确认前端通过 Vite 启动（5173），由 `vite.config.ts` 代理 `/api` 到 8080；直接打开 `dist/index.html` 或换用其他端口会导致代理失效。生产环境由 Nginx 代理 `/api`，并把前端域名加入 `CORS_ALLOWED_ORIGINS`；该变量在 docker compose 中为必填，未注入时 compose 直接启动失败，不会再静默回落 `localhost`。

**MQTT 连不上**

现象：日志反复出现 `MQTT 连接失败`。

确认 Broker 已启动、`MQTT_BROKER_URL` 使用 `tcp://` 前缀（后端在容器内走 `tcp://mqtt:1883`，宿主机明文端口只绑 `127.0.0.1`）；设备侧必须走 TLS `8883` 并信任 `deploy/mqtt/ca/ca.crt`，证书 SAN 要覆盖设备实际连接的主机名或 IP。Broker 已关闭匿名接入，`MQTT_USERNAME` / `MQTT_PASSWORD` 必须与 compose 生成的口令文件一致，否则会返回 `Connection Refused: not authorised`；发布/订阅到 `MQTT_ACL_TOPICS` 之外的主题会被拒绝。多实例部署时 `MQTT_CLIENT_ID` 必须各不相同，否则新连接会把旧连接的持久会话顶掉。

**设备上报被拒绝**

开启 `DEVICE_AUTH_ENABLED=true` 后：

- `401`：缺少 `keyId`、`keyId` 不存在、签名不匹配或 MQTT 时间戳超出窗口
- `403`：凭证已吊销
- 未配置 `DEVICE_CREDENTIAL_KEY`：开启鉴权时后端启动直接失败；未开启鉴权时凭证签发接口返回业务错误
- 拒绝痕迹：backend 日志中的 `设备鉴权拒绝` WARN 行，包含状态码、`keyId` 与原因；MQTT 侧另有 `MQTT 设备鉴权失败` 行，包含主题与报文。可用 `docker compose logs backend | grep 设备鉴权拒绝` 查看

REST 签名对象是原始请求体，必须与发送字节完全一致；MQTT 签名对象是接口契约中定义的规范化字段串。

**设备显示离线但仍在运行**

在线状态按 `last_seen_at` 与 `DEVICE_ONLINE_THRESHOLD_SECONDS` 计算。若设备上报间隔大于阈值，调大阈值；若设备根本没有上报，检查主题是否匹配 `MQTT_TOPIC`。

**集成测试因缺少 Docker 失败**

现象：`mvn verify` 在集成测试阶段失败，提示找不到 Docker 环境。

这是刻意的门禁行为：集成测试不再在缺少 Docker 时静默跳过，避免把「跳过 21 个用例」误判成全部通过。请先确认 Docker 可达，再重跑：

```bash
# 1. 确认 Docker 可达；Colima 需要显式指定 socket
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock docker info

# 2. 运行全量测试；Ryuk 在 Colima 下无法挂载 socket，必须关闭
cd zhizao-xietong
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
mvn verify
```

仅想跑不依赖 Docker 的单元测试时，用 `mvn test`；确需在无 Docker 环境中执行 `verify`，必须显式加 `-DskipITs` 并自行承担集成用例未执行的风险。

## 已知限制

- 遥测历史已落库（`eq_telemetry`，S2），单表保留 90 天；管理端设备详情可选 1 小时/24 小时/7 天查看，无数据时显示空态而不是补点（S7）
- 上报幂等由进程内缓存与 `eq_telemetry` 的 `uk_device_ingest_key (device_id, ingest_key)` 联合唯一索引共同保证；模拟数据不写入时序表
- 工单开工、报工、质检使用乐观锁与状态历史：并发重复流转会被拒绝，每次流转留痕（S3）
- 报工数量已做正数、合计与累计上限校验；报工幂等键已落地（`wo_report.idempotency_key`，同工单唯一，重试返回首次结果）
- 工艺已支持版本化与单级工序路线；BOM 不单独版本化，`pr_bom.version` 列已由 V21 删除（S4）
- 排产为有限产能贪心（S5）：按交期与优先级排队，使用运行中设备的时间窗；不做 APS 最优解。标准工时缺失时直接拒绝排程并返回 `STANDARD_HOURS_MISSING`，不生成假工时；只有显式开启 `schedule.estimate-fallback-hours` 才按估算工时排程并明确标记
- 权限为 `ADMIN`/`USER`/`WORKER` 三级取值，非管理员按 `workshop_scope` 过滤设备、计划、工单、成品入库单与成品库存；未分配车间时看不到任何车间数据（deny-by-default），由管理员在用户管理页显式分配；禁用用户与改密通过令牌版本在下一次请求即失效（S6）。工单写操作：开工/报工允许三种角色，质检仅 `ADMIN`/`USER`，计划派工与其余管理动作仅 `ADMIN`；成品入库确认/取消允许三种角色
- 成品入库单由工单质检完工时生成，确认使用条件更新同时校验「待入库」与「数量不超过 min(计划数, 合格数) − 已入库」，0 行时区分已处理（409）与超额（409）两种提示；库存用 `ON DUPLICATE KEY UPDATE` 原子累加，不回填历史库存，已取消单据不会重新生成
- 登录限流已抽成 `LoginRateLimiter` 端口，当前为单实例本地实现；多实例部署必须替换为共享存储实现（S6）
- 已有 `/actuator/health`、`/actuator/info`、`/actuator/metrics` 与带 `traceId` 的标准输出日志；业务指标已覆盖设备在线、上报、报工、节拍与齐套率，但没有告警阈值与集中采集
- 测试 385 个（6 个 Testcontainers 集成类）；集成用例覆盖状态机流转、真实并发冲突、工艺版本隔离、遥测入库、AI 只读会话、计划下达幂等与成品入库并发确认，由 `mvn verify` 强制执行，缺少 Docker 时构建失败；移动端构建未纳入 CI
- 完整 Docker Compose 五服务栈未在本机实测（本机端口被本地 MySQL/Redis 占用）
- `scripts/verify.sh` 已因报工口径变更失效：部分报工后的状态与累计数量断言与当前实现不一致，脚本需单独修复后才能作为部署验收依据
- MQTT 已收敛为 TLS `8883` + `acl_file`：明文 `1883` 只绑 `127.0.0.1`；但仍为共享账号 + 主题级 ACL，任一设备可发布到其他设备编号的主题，按设备隔离需一设备一账号（见上文「MQTT TLS 与 ACL」）
- 设备 HMAC 鉴权默认关闭（`DEVICE_AUTH_ENABLED=false`）以兼容存量设备，compose 已可透传开关与主密钥；开启后才是全局强制，没有按设备灰度，存量设备未改造前不能打开（见上文「启用设备 HMAC 鉴权（分步）」）
- AI 能力已落地（S8），默认关闭：`AI_ENABLED=false` 时三个 `/api/ai/**` 端点返回 HTTP 503 + 业务码 503，其余功能不受影响；开启后默认使用离线假模型，CI 不需要网络与密钥。AI 只调用只读工具、只返回建议文本，自然语言报工只产出待确认草稿；Text-to-SQL 仅 ADMIN 可用，只允许查询 5 个只读视图并强制行数上限。配置 `AI_DATASOURCE_URL` 后使用独立只读账号；未配置时回落主数据源，但查询强制只读会话并在启动时输出 WARN，不会假装已隔离。模型超时或返回非法内容时降级为「建议暂不可用」，不影响主链路

## 许可

本项目采用 [MIT License](LICENSE)，版权人 Caspian，年份 2026。
