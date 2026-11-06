# Industrial Application — 离散制造 MES

面向离散制造车间的一体化生产管理系统，覆盖设备接入、物料与 BOM、工艺路线、生产计划、工单执行、质检与设备维保。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3.5、Spring Security + JWT、MyBatis-Plus 3.5、Flyway |
| 数据 | MySQL 8、Redis 7 |
| 设备接入 | MQTT（Eclipse Paho），主题 `factory/devices/+/status` |
| 管理端 | Vue 3、TypeScript、Vite、Element Plus、ECharts |
| 移动端 | uni-app（Vue 3），H5 / App / 小程序 |
| 部署 | Docker Compose（MySQL、Redis、Mosquitto、后端、前端） |

## 目录结构

```
.
├── zhizao-xietong/          # Spring Boot 后端
│   ├── src/main/java/com/zhizao/
│   │   ├── controller/      # REST 接口
│   │   ├── service/         # 业务逻辑、状态机、齐套校验、编号生成
│   │   ├── gateway/         # MQTT 设备网关
│   │   ├── security/        # JWT 认证与鉴权
│   │   ├── entity/ mapper/ dto/ enums/
│   │   └── common/ config/ aspect/
│   └── src/main/resources/db/migration/   # Flyway 迁移
├── zhizao-xietong-web/      # 管理端前端
├── mobile-app/              # 车间移动端
├── docs/                    # 工程文档与历史资料
├── docker-compose.yml
├── mosquitto.conf
├── start.sh / stop.sh
└── .env.example
```

## 工程文档

- [工程设计与重构方案](docs/ENGINEERING-DESIGN.md)：现状盘点、目标架构、不变量、AI 旁路与已决策事项
- [数据模型](docs/DATA-MODEL.md)：现状 12 张表与目标表设计
- [接口契约](docs/API-CONTRACT.md)：46 个现状端点与目标端点
- [重构分期](docs/REFACTOR-PLAN.md)：S0–S8 阶段、验收与风险
- [历史资料](docs/legacy/README.md)：早期 PRD 与开发文档，未随重构维护

## 快速开始

### 方式一：Docker Compose

```bash
cp .env.example .env
# 填写 DB_PASSWORD、JWT_SECRET（JWT_SECRET 至少 32 字节）
docker compose up -d --build
```

- 管理端：http://localhost:8081
- 后端：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui/index.html
- 健康检查：http://localhost:8080/actuator/health

### 方式二：本地运行

前置条件：JDK 17、Maven 3.9+、MySQL 8、Redis 7（MQTT 可选）。

```bash
# 1. 创建数据库（表结构由 Flyway 自动创建）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS zhizao_xietong DEFAULT CHARSET utf8mb4;"

# 2. 配置环境变量
cp .env.example .env
set -a && source .env && set +a

# 3. 启动后端
cd zhizao-xietong && mvn spring-boot:run

# 4. 启动管理端
cd zhizao-xietong-web && npm install && npm run dev

# 5. 启动移动端 H5（可选）
cd mobile-app && npm install && npm run dev:h5
```

默认开发账号：`admin / admin123`（ADMIN）、`zhangsan / 123456`（USER），首次启动由 `DataInitializer` 创建。生产环境请通过环境变量或初始化脚本替换。

## 配置项

所有敏感配置均通过环境变量注入，仓库不保存真实密码或密钥。

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://localhost:3306/zhizao_xietong...` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `root` / 无（必填） |
| `JWT_SECRET` | JWT 签名密钥 | 无（必填） |
| `JWT_EXPIRATION` | 令牌有效期（毫秒） | `86400000` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 | `localhost` / `6379` / 空 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端源，逗号分隔 | `http://localhost:5173,http://localhost:8081` |
| `MQTT_ENABLED` | 是否启用 MQTT 网关 | `false` |
| `MQTT_BROKER_URL` / `MQTT_USERNAME` / `MQTT_PASSWORD` | MQTT Broker 连接 | `tcp://localhost:1883` / 空 |
| `MQTT_TOPIC` | 设备上报主题 | `factory/devices/+/status` |

## 设备接入

启用 MQTT 后，网关订阅设备主题，收到 JSON 上报后更新设备状态并记录遥测：

```bash
MQTT_ENABLED=true mvn spring-boot:run

mosquitto_pub -h localhost -t 'factory/devices/EQ-202609-0001/status' \
  -m '{"deviceNo":"EQ-202609-0001","status":"RUNNING","temperature":45.2,"rpm":1500,"current":8.5}'
```

也可通过 `POST /api/integration/device-status` 走 REST 回传，或 `POST /api/integration/mqtt/publish` 发布测试消息。

## 核心接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新令牌 |
| GET/POST/PUT/DELETE | `/api/devices` | 设备管理 |
| GET | `/api/devices/{id}/telemetry` | 实时遥测 |
| GET/POST/PUT/DELETE | `/api/materials` | 物料管理 |
| GET/POST/PUT/DELETE | `/api/processes` | 工艺管理 |
| POST | `/api/processes/{id}/submit` \| `/approve` \| `/reject` | 工艺审批流转 |
| GET/POST/PUT/DELETE | `/api/plans` | 生产计划 |
| POST | `/api/plans/{id}/dispatch` | 下达工单 |
| GET | `/api/work-orders` | 工单列表 |
| POST | `/api/work-orders/{id}/start` | 开工 |
| POST | `/api/work-orders/{id}/report` | 报工 |
| POST | `/api/work-orders/{id}/qc` | 质检 |

除 `/api/auth/**`、Swagger 与健康检查外，接口均需 `Authorization: Bearer <token>`。管理类写操作要求 ADMIN 角色。

## 验证

```bash
# 后端单元测试
cd zhizao-xietong && mvn test

# 管理端类型检查与生产构建
cd zhizao-xietong-web && npm ci && npm run build
```

CI 在每次 push 与 pull request 时执行上述两条命令，配置见 `.github/workflows/ci.yml`。

## 许可

内部工程代码，未授权不得外传。
