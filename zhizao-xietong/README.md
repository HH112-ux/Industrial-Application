# 智造协同工业 APP（后端）

面向离散制造车间的一体化生产管理工业 APP 后端，采用企业级自研技术栈：

- **Java 17** + **Spring Boot 3.3** + **MyBatis-Plus 3.5** + **MySQL 8**
- 覆盖五大模块：设备管理、物料管理、工艺管理、生产执行（工单）、计划与调度
- 核心业务：工单状态机、物料齐套校验、排产优先级、报工与质检闭环

## 目录结构

```
zhizao-xietong/
├── pom.xml
└── src/main/
    ├── java/com/zhizao/
    │   ├── ZhizaoApplication.java
    │   ├── common/         # Result / PageResult / 异常 / 全局处理 / 登录限流
    │   ├── enums/          # 工单/设备/工艺/计划/优先级 枚举
    │   ├── entity/         # 12 个业务实体
    │   ├── mapper/         # MyBatis-Plus Mapper
    │   ├── dto/            # 登录、报工、质检、设备上报等 DTO
    │   ├── gateway/        # MQTT 设备网关
    │   ├── security/       # JWT 认证与鉴权
    │   ├── service/        # 业务服务 + 状态机 + 齐套校验 + 排产 + 编号生成
    │   ├── controller/     # REST 接口
    │   ├── config/         # Redis / OpenAPI / 初始化数据
    │   └── aspect/         # 写操作审计
    └── resources/
        ├── application.yml
        ├── db/migration/   # Flyway 建表
        └── db/seed/        # 演示种子数据，默认不加载
```

## 快速开始

```bash
# 1. 准备 MySQL 8（本机需先建库 zhizao_xietong，或用默认账号 root/root）
# 2. 编译并启动（Flyway 自动建表；演示数据默认不灌入）
mvn spring-boot:run

# 3. 访问
#   应用启动于 http://localhost:8080
#   Swagger 接口文档：http://localhost:8080/swagger-ui/index.html
#   Actuator 健康检查：http://localhost:8080/actuator/health
```

> 表结构由 Flyway 管理（`src/main/resources/db/migration/`）。演示种子数据在 `db/seed/`，默认不加载；需要演示环境时用 `APP_SEED_DEMO_ENABLED=true mvn spring-boot:run`。首次接入需空库，已有旧库请先 `DROP DATABASE zhizao_xietong` 后重建。

## 运行测试

项目目标 Java 17，已升级 Mockito + ByteBuddy，兼容 JDK 17 与 JDK 26，直接运行：

```bash
mvn test
```

已覆盖：登录鉴权（AuthService）、齐套校验（KitCheckService）、编号生成（CodeGenerator）、工单状态机闭环（WorkOrderService）。

## Docker 部署

配合 backend 与 frontend 公共目录下的 `docker-compose.yml` 一键启动：

```bash
docker compose up -d --build
```

## 设备网关（MQTT）

设备数据通过 MQTT 网关实时接入。启用后网关订阅 `factory/devices/+/status` 主题，收到上报后更新设备状态并缓存最新遥测（`GET /api/devices/{id}/telemetry` 会优先返回真实数据）。

```bash
# 1. 启动 Broker（Docker Compose 已内置 mosquitto，或本机）
docker compose up -d mqtt

# 2. 启用网关（环境变量）
MQTT_ENABLED=true mvn spring-boot:run

# 3. 模拟一台设备上报
mosquitto_pub -h localhost -t 'factory/devices/EQ-202609-0001/status' \
  -m '{"deviceNo":"EQ-202609-0001","status":"RUNNING","temperature":45.2,"rpm":1500,"current":8.5}'
```

> 也可调用 `POST /api/integration/mqtt/publish` 发布测试上报（走 MQTT 全链路）。

## 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/devices | 设备分页列表（keyword/status）|
| POST | /api/devices | 新增设备（自动编号）|
| POST | /api/devices/{id}/status?status=RUNNING | 设备状态变更 |
| GET/POST/PUT/DELETE | /api/materials | 物料 CRUD（齐套状态自动计算）|
| GET/POST/PUT/DELETE | /api/processes | 工艺 CRUD |
| POST | /api/processes/{id}/submit | 提交审批 |
| POST | /api/processes/{id}/approve | 审批通过 |
| POST | /api/processes/{id}/reject | 审批驳回 |
| GET/POST/PUT/DELETE | /api/plans | 计划 CRUD |
| POST | /api/plans/{id}/dispatch | 下达工单（生成工单 + 匹配工艺）|
| GET | /api/work-orders | 工单分页列表 |
| POST | /api/work-orders/{id}/start | 开工（工艺校验 + 齐套校验 + 状态机）|
| POST | /api/work-orders/{id}/report | 报工 |
| POST | /api/work-orders/{id}/qc | 质检（PASS/FAIL）|

## 演示核心闭环

以下示例依赖演示种子数据，启动时需设置 `APP_SEED_DEMO_ENABLED=true`。

```bash
# 计划下达 → 工单
curl -X POST http://localhost:8080/api/plans/1/dispatch

# 开工（齐套 + 工艺校验通过）
curl -X POST "http://localhost:8080/api/work-orders/1/start?operator=zhangsan"

# 报工
curl -X POST http://localhost:8080/api/work-orders/1/report \
  -H 'Content-Type: application/json' \
  -d '{"qty":200,"qualifiedQty":198,"defectQty":2,"deviceId":1,"operator":"zhangsan"}'

# 质检通过 → 工单完工、计划完成
curl -X POST http://localhost:8080/api/work-orders/1/qc \
  -H 'Content-Type: application/json' \
  -d '{"result":"PASS","inspector":"lisi"}'
```

## 认证与权限

除 `/api/auth/**` 外，所有接口需携带 JWT：请求头 `Authorization: Bearer <token>`。登录接口 `POST /api/auth/login`。

首次启动自动创建管理员账号 `admin`，初始口令来自环境变量 `APP_BOOTSTRAP_ADMIN_PASSWORD`；
未配置时启动日志会打印一次性随机口令。演示账号 `zhangsan` 仅在 `dev` profile 下创建，
口令来自 `DEMO_INIT_PASSWORD`。登录后请立即修改初始口令。

管理类写接口（增删改、审批、下达、BOM 维护）标注 `@PreAuthorize("hasRole('ADMIN')")`，仅 ADMIN 可调用；USER 仅可查询与执行工单现场操作。

## 说明与后续项

- **编号生成**：基于数据库序列表 `sys_sequence`，按月分段、行级锁原子自增，并发安全、跨重启不重复。
- **物料齐套校验**：基于独立 BOM 表 `pr_bom`，按「单件用量 × 工单数量」核算所需物料，库存不足判缺料。
- **接口级鉴权**：已接入 Spring Security + JWT 与 `@PreAuthorize` 角色鉴权（ADMIN 管理 / USER 现场操作）。
- **BOM 管理**：`pr_bom` 目前通过演示种子数据初始化（需 `APP_SEED_DEMO_ENABLED=true`），BOM 的增删改查接口与页面可后续补充。
- **设备数据接入**：提供模拟遥测与状态回传接口，可替换为真实设备网关推送。
