# 模块清单与职责表

> 对应 `ARCHITECTURE.md` 的依赖方向约束。数据来源：本工程磁盘上的 15 个 POM 与 `src/main` 实际类，核对日期 2026-11-26。
> 本文是架构设计文档的第一部分；时序、事件/outbox、可观测性等章节后续补充。

## 1. 模块总览

按层级从下往上排列。**所有业务模块共用同一套源码**，单体与微服务只是装配差异。

| # | 模块 | 层级 | packaging | 职责一句话 |
|---|---|---|---|---|
| 1 | 根 `pom.xml` | 构建治理 | pom | Reactor 聚合、全局属性、enforcer 门禁（JDK 25 / Maven 3.9+ / 依赖收敛）、编译参数 `-parameters` |
| 2 | `xs-dependencies` | 构建治理 | pom | 版本唯一事实源（BOM）：Spring Boot 4.2.0、Cloud 2026.0.0、springdoc 3.2.1、pgjdbc 42.7.15、ArchUnit 1.5.0 + 全部内部模块坐标 |
| 3 | `xs-common-core` | 基础契约 | jar | 零框架依赖的通用内核：Result / ErrorCode / BusinessException / TenantContext / DomainEvent / Ids |
| 4 | `xs-api-user` | 领域契约 | jar | 用户域的纯 Java 端口与 DTO：`UserPort` / `CreateUserCommand` / `UserView` / `UserCreatedEvent` |
| 5 | `xs-api-tenant` | 领域契约 | jar | 租户域纯 Java 端口：`TenantPort` / `TenantStatus` / `TenantView` / 两个领域事件 |
| 6 | `xs-adapter-user-http` | 远程适配器 | jar | `UserPort` 的 HTTP 声明式实现：`@HttpExchange` + `RestClient` + 租户头传播，属性开关切换 |
| 7 | `xs-starter-web` | 能力 Starter | jar | Web 横切自动装配：统一响应、全局异常、租户上下文过滤器、OpenAPI 元信息 |
| 8 | `xs-starter-data` | 能力 Starter | jar | 数据横切自动装配：`BaseEntity`（租户私有）、`GlobalEntity`（控制面全局）、多租户连接提供器、PG RLS 数据源包装 |
| 9 | `xs-module-user` | 领域实现 | jar | 用户域垂直切片：Entity / Repository / Service / Controller + Flyway V1/V2 |
| 10 | `xs-module-tenant` | 领域实现 | jar | 租户注册表垂直切片：`GlobalEntity` 实体 / Repository / Service / Controller + Flyway V3/V4 |
| 11 | `xs-app-monolith` | 装配 | jar | 单体可执行包：装配 user + tenant + HTTP 适配器，H2/PG 双 profile，Dockerfile |
| 12 | `xs-app-user` | 装配 | jar | 用户服务可执行包：`xs-module-user` + HTTP 适配器，端口 18081，Dockerfile |
| 13 | `xs-app-tenant` | 装配 | jar | 租户服务可执行包：只装配 `xs-module-tenant`，端口 18082，Dockerfile |
| 14 | `xs-tests/xs-arch-tests` | 验证 | jar(test) | ArchUnit 边界规则：4 条，守护分层依赖方向 |
| 15 | `xs-tests/xs-integration-tests` | 验证 | jar(test) | H2 + MockMvc 集成测试：user/tenant HTTP 全链路 + `UserPort` 本地/远程选择 |

> 注：`xs-tests/` 只是目录，没有独立 POM；两个测试模块直接挂在根 Reactor 下。

## 2. 依赖关系矩阵

只列**直接依赖**；箭头方向为「依赖 →」。

| 模块 | 依赖内部模块 | 依赖外部关键构件 | 被谁依赖 |
|---|---|---|---|
| 根 `pom.xml` | 全部（聚合） | maven-enforcer-plugin、maven-compiler-plugin | — |
| `xs-dependencies` | 全部（仅 version 管理） | spring-boot-dependencies、spring-cloud-dependencies、springdoc-openapi-bom | 所有模块（隐式） |
| `xs-common-core` | — | — | api-user、api-tenant、starter-web、starter-data、module-user、module-tenant、arch-tests |
| `xs-api-user` | common-core | — | module-user、arch-tests |
| `xs-api-tenant` | common-core | — | module-tenant、arch-tests |
| `xs-adapter-user-http` | api-user | spring-boot-starter-webmvc | app-monolith、app-user、arch-tests、integration-tests |
| `xs-starter-web` | common-core | spring-boot-starter-webmvc、starter-validation、springdoc-openapi-starter-webmvc-api | module-user、module-tenant、arch-tests |
| `xs-starter-data` | common-core | spring-boot-starter-data-jpa | module-user、module-tenant、arch-tests |
| `xs-module-user` | common-core、api-user、starter-web、starter-data | — | app-monolith、app-user、arch-tests |
| `xs-module-tenant` | common-core、api-tenant、starter-web、starter-data | — | app-monolith、app-tenant、arch-tests |
| `xs-app-monolith` | module-user、module-tenant、adapter-user-http | spring-boot-starter-flyway、postgresql(runtime)、h2(runtime) | arch-tests、integration-tests |
| `xs-app-user` | module-user、adapter-user-http | 同上 | arch-tests |
| `xs-app-tenant` | module-tenant | 同上 | arch-tests |
| `xs-arch-tests` | 全部（test scope） | junit-jupiter、archunit-junit5 | — |
| `xs-integration-tests` | app-monolith（test scope） | spring-boot-starter-webmvc-test | — |

**锁死的边界**（由 ArchUnit 强制，违者构建失败）：

- `api.*` 不得依赖 Spring / Jakarta / JPA / 任何 starter
- `common` 不得依赖任何框架
- `module.*` 之间不得互相依赖
- `app.*` 之间不得互相依赖

## 3. 逐模块职责

### 3.1 根 `pom.xml` — 构建治理

- Reactor 聚合 10 个模块，钉死 JDK 25 / Maven 3.9+。
- `enforcer@ci-enforcer`：`requireJavaVersion [25,26)`、`requireMavenVersion [3.9,)`、依赖收敛；BOM 排除收敛检查（Boot BOM 有意固定 Jackson 版本）。
- 编译插件开 `-parameters`（Boot 4 的 `@PathVariable` 等参数名解析必需）。
- 两个 app 模块配置 `spring-boot-maven-plugin` repackage 为 `-boot.jar`。

### 3.2 `xs-dependencies` — 版本唯一事实源

- 内部模块坐标在此统一管理版本；任何新模块必须在此登记。
- 第三方版本只在此声明：Boot、Cloud、springdoc、pgjdbc、ArchUnit。
- 变更纪律：改版本 → 写 ADR → 本地更新依赖基线 → PR 同含基线 diff。

### 3.3 `xs-common-core` — 零框架内核

| 类 | 职责 |
|---|---|
| `Result<T>` | 统一响应模型（`success/code/message/data`）及 `ok/fail` 工厂 |
| `ErrorCode` | 错误码 record + 四个标准码：`NOT_FOUND`、`TENANT_REQUIRED`、`VALIDATION_ERROR`、`INTERNAL_ERROR` |
| `BusinessException` | 业务异常，携带 `ErrorCode`，供 Web starter 统一翻译 |
| `TenantContext` | ThreadLocal 租户上下文：`set/get/require/clear/call(tenantId, body)` |
| `DomainEvent` | 领域事件标记接口（outbox 实现的契约基础） |
| `Ids` | 36 位 UUID 与 16 位业务编码生成（如 `UC9F0FC34792C648`） |

**约束**：不引入任何框架依赖，保证可被 `api.*` 安全引用。

### 3.4 `xs-api-user` — 用户域端口

- `UserPort`：用户域对外唯一入口，`create(CreateUserCommand)` / `getById(String)`。
- `CreateUserCommand`、`UserView`：输入/输出 DTO，纯 record。
- `UserCreatedEvent`：用户创建事件，继承 `DomainEvent`。
- **约束**：纯 Java，无 Spring/Jakarta/JPA 注解。微服务拆分时，此模块即远程调用契约，实现从本地 Service 换成 HTTP 声明式适配器即可（ADR 0003 已拒绝 gRPC）。

### 3.5 `xs-api-tenant` — 租户域端口

- `TenantPort`：控制面注册表入口，`create` / `getById` / `list` / `changeStatus`。
- `TenantStatus`：`ACTIVE` / `SUSPENDED`；`TenantView` / `CreateTenantCommand` 为纯 record。
- `TenantCreatedEvent` / `TenantStatusChangedEvent`：继承 `DomainEvent`，携带 `tenantId` 与事件时点。
- **约束**：同样是纯 Java 契约，供本地 Service 与 HTTP 远程适配器共用。

### 3.6 `xs-adapter-user-http` — 远程适配器样例

- `UserHttpExchange`：`@HttpExchange("/api/v1/users")` 声明式客户端，`POST` / `GET /{userId}` 与 user Controller 路由一致。
- `RemoteUserPort implements UserPort`：解包 `RemoteResponse`，失败时抛 `BusinessException`；业务代码只见端口。
- `TenantHeaderInterceptor`：出站请求带当前 `X-Tenant-Id`，保证上游 RLS 判定正确。
- `UserHttpAdapterAutoConfiguration`：仅 `xs.adapters.user.mode=remote` 时装配；本地 `UserService` 用 `@ConditionalOnProperty(mode=local, matchIfMissing=true)` 退让，二者互斥。
- 详见 `docs/guides/HTTP_REMOTE_ADAPTER.md`；自动化验证 bean 切换，双进程实测跨服务调用与租户隔离。

### 3.7 `xs-starter-web` — Web 横切

| 类 | 职责 |
|---|---|
| `ApiResponse<T>` | HTTP 响应包装（`code/message/data/traceId`） |
| `GlobalExceptionHandler` | `@RestControllerAdvice`：业务异常与未知异常统一转 HTTP 状态 + `ApiResponse` |
| `TenantContextFilter` | `X-Tenant-Id` 请求头 → `TenantContext`；仅当 `xs.tenant.allow-header=true` 时装配（生产态关闭） |
| `WebAutoConfiguration` | 自动装配入口，通过 `AutoConfiguration.imports` 注册 |

### 3.8 `xs-starter-data` — 数据横切

| 类 | 职责 |
|---|---|
| `BaseEntity` | 租户私有实体基类：36 位 UUID 主键（VARCHAR(36)）、`@TenantId` 租户列、`@Version` 乐观锁、created/updated/deleted 审计、`@PrePersist/@PreUpdate`、`markDeleted()`；`onCreate` 要求 `TenantContext` |
| `GlobalEntity` | 控制面全局实体基类：同款 UUID/审计/乐观锁字段但**无 `@TenantId`、不要求租户上下文**；供 `st_tenant` 这类全局表使用 |
| `TenantIdResolver` | `CurrentTenantIdentifierResolver`，从 `TenantContext` 取租户；无租户返回哨兵 `__none__` |
| `SameDbConnectionProvider` | Hibernate 7 `AbstractMultiTenantConnectionProvider` 实现，同库多租户共享物理连接 |
| `TenantRlsDataSource` | `DelegatingDataSource` 包装，取连接时按租户设置 PG RLS session 变量；`xs.tenant.rls-enabled=true` 时由 BPP 注入 |
| `TenantDataAutoConfiguration` | 装配上述 Bean，条件注解控制 RLS 开关 |

**注意**：`@TenantId` 只覆盖 JPQL/Criteria/派生查询；原生 SQL 默认禁止，PG RLS 是第二层兜底。

### 3.9 `xs-module-user` — 用户域实现

- `UserEntity extends BaseEntity`：`st_user` 表，`@SQLRestriction("deleted_flag = false")` 软删过滤，16 位 `user_code`。
- `UserRepository extends JpaRepository<UserEntity, String>`。
- `UserService implements UserPort`：`@Transactional` 写方法、`readOnly=true` 读方法，域内不直接暴露 Entity。
- `UserController`：`/api/v1/users`，注入 `UserPort`（不是 Service），带完整 OpenAPI 注解（`operationId`/description/4xx/参数描述）。
- Flyway 分域迁移：`db/migration/common/V1__user_core.sql`（建表，H2/PG 通用）、`db/migration/pg/V2__user_rls.sql`（PG 专属 RLS 策略）。

### 3.10 `xs-module-tenant` — 租户域实现（控制面）

- `TenantEntity extends GlobalEntity`：`st_tenant` 表，`@SQLRestriction("deleted_flag = false")`，id 覆盖映射为 `tenant_id`（即 `X-Tenant-Id` 的值），`TN` 前缀 16 位 `tenant_code`，`status` 用 `@Enumerated(EnumType.STRING)`。
- **不是租户私有表**：注册表不加 `@TenantId`、不受租户 RLS 限制，管理台需跨租户 `list()`。
- `TenantRepository` / `TenantService implements TenantPort`：`create`（生成 `TN` 编码、查重）、`getById`、`list`、`changeStatus`；写方法 `@Transactional`，读方法 `readOnly=true`。
- `TenantController`：`/api/v1/tenants`，create/get/list/patch-status，注入 `TenantPort`，完整 OpenAPI 注解。
- Flyway：`common/V3__tenant_core.sql`（建表）、`pg/V4__tenant_registry.sql`（RLS FORCE + `xs_console` 角色策略；角色幂等创建，待 W04 真机验证）。

### 3.11 `xs-app-monolith` / `xs-app-user` / `xs-app-tenant` — 装配层

三个 app 是**同一组模块的不同装配**，源码只有启动类与配置的差异：

- `xs-app-monolith`：`@EntityScan("com.xsframework.module")`、`@EnableJpaRepositories("com.xsframework.module")`，装配 user + tenant 全部领域，端口 18080。
- `xs-app-user`：扫描范围收窄到 `com.xsframework.module.user`，端口 18081。
- `xs-app-tenant`：扫描范围收窄到 `com.xsframework.module.tenant`，端口 18082。
- 三者都有 `application.yml`（H2 开发态，`allow-header=true`）与 `application-pg.yml`（PG19 生产态，RLS 开启、header 关闭）及 Dockerfile。

### 3.12 验证模块

- `xs-arch-tests`：4 条 ArchUnit 规则，把分层约束变成可执行的构建门禁。
- `xs-integration-tests`：`UserApiIntegrationTest`、`TenantApiIntegrationTest`，以及 `UserPortLocalSelectionTest` / `UserPortRemoteSelectionTest`（端口实现切换）。

## 3.13 Flyway 版本分配

全局版本号在 `db/migration/common` 与 `db/migration/pg` 间共享，新增域必须续号，**不得复用**：

| 版本 | 域 | 文件 |
|---|---|---|
| V1 / V2 | user | `V1__user_core.sql` / `V2__user_rls.sql` |
| V3 / V4 | tenant | `V3__tenant_core.sql` / `V4__tenant_registry.sql` |
| V5+ | 后续域（如 order） | 依次分配 |

## 4. 新增领域的扩展口径

以新增「订单」域为例，按序执行：

1. **建契约模块** `xs-api-order`：纯 Java 的 `OrderPort` / `CreateOrderCommand` / `OrderView` / `OrderCreatedEvent`。
2. **建实现模块** `xs-module-order`：`OrderEntity extends BaseEntity`、`OrderRepository`、`OrderService implements OrderPort`、`web/OrderController`（注入 `OrderPort`）；Flyway 用**下一个可用版本号**（当前为 V5/V6），见 3.13 版本分配表。
3. **登记 BOM**：`xs-dependencies/pom.xml` 的 `dependencyManagement` 加两个内部模块坐标。
4. **登记 Reactor**：根 `pom.xml` 的 `<modules>` 加两行。
5. **装配**：`xs-app-monolith/pom.xml` 加 `xs-module-order` 依赖；若要独立部署，复制 `xs-app-user` 为 `xs-app-order`，收窄扫描包。
6. **规格三件套**：`docs/spec/order/*.spec.md`（GWT）、`docs/spec/openapi/order-api.yaml`、必要时 ADR。
7. **门禁**：ArchUnit 自动覆盖新包；本地跑 `bash scripts/ci/verify.sh`；因 dependencyManagement 变化，需更新依赖基线。

## 5. 与 CI 门禁的对应

| 模块相关约束 | 门禁 |
|---|---|
| JDK / Maven / 依赖收敛 | G01 / G02（enforcer） |
| 新增模块的版本与坐标 | G02 / G03（依赖基线） |
| 分层依赖方向 4 条规则 | G04（ArchUnit，`xs-arch-tests`） |
| HTTP 全链路 | G04（`xs-integration-tests`） |
| Controller 的 OpenAPI 注解 | G05 / G06 / G07（authored spec / runtime contract / drift） |
