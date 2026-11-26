# 双模打包部署指导手册

本手册对应 `docs/spec/VISION.md` 的目标：**同一套模块，单体与微服务两种装配**。当前有两个示例域：`user`（租户私有数据）与 `tenant`（控制面全局注册表）；三种装配跑的是同一份代码、同一组接口，差异只在装配范围与端口。

## 1. 前置条件

| 依赖 | 要求 | 说明 |
|---|---|---|
| JDK | 25 LTS | 脚本优先用工程自带 `.tools/jdk25/*`，其次 `JAVA_HOME`，逐个验版本；JDK 21 不会被误用 |
| Maven | 3.9+ | 在 `PATH` 中 |
| curl / python3 | 任意稳定版 | 冒烟脚本解析 JSON 用 |
| 数据库 | 开发态 H2 内存库，生产态 PG19 | 见第 5 节 |

脚本统一入口在 `scripts/deploy/`，均需在工程根目录 `xs-framework/` 下执行：

```bash
cd <repo>/xs-framework
bash scripts/deploy/<script>.sh <参数>
```

## 2. 三种最小命令

```bash
# 一键示例：构建三种模式 → 启动 → 冒烟 → 停止
bash scripts/deploy/demo.sh

# 示例完成后保留进程在后台
bash scripts/deploy/demo.sh --keep-running

# 查看运行状态
bash scripts/deploy/status.sh
```

`demo.sh` 是"两种打包部署"的完整示例，正常情况下约几十秒完成，输出中三段 `smoke OK` 分别对应单体、用户服务与租户服务。

## 3. 分步命令（日常使用）

### 3.1 构建

```bash
bash scripts/deploy/build.sh all       # 三个都构建
bash scripts/deploy/build.sh monolith  # 只构建单体
bash scripts/deploy/build.sh user      # 只构建用户服务
bash scripts/deploy/build.sh tenant    # 只构建租户服务
```

产物：

- `xs-app-monolith/target/xs-app-monolith-<version>-boot.jar`
- `xs-app-user/target/xs-app-user-<version>-boot.jar`
- `xs-app-tenant/target/xs-app-tenant-<version>-boot.jar`

均为 Spring Boot repackage 后的可执行包，包含各自需要的模块。

### 3.2 启动与停止

```bash
bash scripts/deploy/start.sh monolith          # 单体，端口 18080
bash scripts/deploy/start.sh user              # 用户服务，端口 18081
bash scripts/deploy/start.sh tenant            # 租户服务，端口 18082
bash scripts/deploy/start.sh monolith --profile pg   # 用 PG19 profile 启动
bash scripts/deploy/start.sh user --profile pg
bash scripts/deploy/start.sh tenant --profile pg

bash scripts/deploy/stop.sh monolith
bash scripts/deploy/stop.sh all
```

- PID 写在 `.tools/deploy/<mode>.pid`，日志在 `.tools/deploy/<mode>.log`。
- `start.sh` 会等待 `/v3/api-docs` 就绪（最多 60 秒）才返回，避免"启动即调用"的竞态。
- 端口可用环境变量覆盖：`MONOLITH_PORT=28080`、`USER_PORT=28081`、`TENANT_PORT=28082`。

### 3.3 冒烟验证

```bash
bash scripts/deploy/smoke.sh all       # 三个模式都验证
bash scripts/deploy/smoke.sh monolith
bash scripts/deploy/smoke.sh user
bash scripts/deploy/smoke.sh tenant
```

所有模式都先验证 `GET /v3/api-docs` 返回 200，再按装配范围检查对应域：

- **user 域**（monolith / user）：`POST /api/v1/users` 带 `X-Tenant-Id: tenant-1` 创建成功；`GET /api/v1/users/{userId}` 取回同一条；不带租户头创建返回 400（`TENANT_REQUIRED`）。
- **tenant 域**（monolith / tenant）：`POST /api/v1/tenants` 无需租户头创建成功；`GET /api/v1/tenants/{tenantId}` 与 `GET /api/v1/tenants` 可查；`PATCH .../status` 改为 `SUSPENDED` 生效。

**同一域在不同模式下的接口、状态码、响应结构完全一致**——这是"同包可拆"最直接的证据。

## 4. 三种装配对照

| 维度 | 单体 `monolith` | 微服务 `user` | 微服务 `tenant` |
|---|---|---|---|
| 装配范围 | `module.user` + `module.tenant` | 仅 `module.user` | 仅 `module.tenant` |
| 启动类 | `MonolithApplication` | `UserServiceApplication` | `TenantServiceApplication` |
| 端口 | 18080 | 18081 | 18082 |
| 应用名 | `xs-monolith` | `xs-user-service` | `xs-tenant-service` |
| H2 库名 | `xs_monolith` | `xs_user` | `xs_tenant` |
| 业务接口 | `/api/v1/users` + `/api/v1/tenants` | `/api/v1/users` | `/api/v1/tenants` |
| 拆分成本 | — | 新增 `xs-app-<domain>` + HTTP 远程适配器，业务代码不改 | 同左 |

单体聚合全部已落地域，两个微服务各自只装配一个域。域名增加时，只需扩 `xs-app-monolith` 的依赖并按需新增独立 app。

## 5. 配置与数据库

### 5.1 开发态（默认）

三个 app 的 `application.yml` 使用 H2 内存库，开启 `xs.tenant.allow-header=true`，方便本地用 `X-Tenant-Id` 头模拟租户。**此模式不可用于生产**。注意 `tenant` 域是控制面全局数据，其接口不需要也不校验租户头。

### 5.2 生产态（PG19）

```bash
export DB_URL='jdbc:postgresql://<host>:5432/<db>'
export DB_USER='xs_app'
export DB_PASSWORD='<secret>'
bash scripts/deploy/start.sh monolith --profile pg
```

`application-pg.yml` 的关键差异：

- datasource 走环境变量，应用角色不拥有表、无 `BYPASSRLS`；
- Flyway locations 增加 `classpath:db/migration/pg`（RLS 策略脚本：user 域 V2、tenant 域 V4）；
- `xs.tenant.rls-enabled=true`：取连接时设置租户 session 变量；
- `xs.tenant.allow-header=false`：不再信任客户端传来的租户头。

`st_tenant` 是控制面全局注册表，不受租户 RLS 限制（ADR 0006）；V4 创建了 `xs_console` 角色并授予其全表策略，部署时需把该角色授予管理台登录角色（待 W04 真机验证）。生产态租户身份应来自已验证的 JWT claim（待 `auth` 域落地后接入），在那之前 PG profile 仅用于 RLS 隔离验证。

### 5.3 容器镜像（可选）

三个 app 各带 Dockerfile，基于 `eclipse-temurin:25-jre`：

```bash
cd xs-app-monolith && docker build -t xs/monolith:0.1.0 .
cd ../xs-app-user && docker build -t xs/user-service:0.1.0 .
cd ../xs-app-tenant && docker build -t xs/tenant-service:0.1.0 .
```

镜像内端口分别为 18080 / 18081 / 18082，启动参数与裸机一致。当前环境未安装 Docker，镜像构建未实测。

## 5.4 远程适配器模式（跨服务调用）

`xs-app-user` 可切换为 HTTP 远程模式：本进程不再写库，而是把 `UserPort` 的调用通过 HTTP 委托给上游服务。

```bash
# 上游：单体（含本地 UserPort）
bash scripts/deploy/start.sh monolith

# 下游：用户服务切到 remote，指向上游
"$JAVA_HOME/bin/java" -jar xs-app-user/target/xs-app-user-*-boot.jar \
  --server.port=18081 \
  --xs.adapters.user.mode=remote \
  --xs.adapters.user.base-url=http://127.0.0.1:18080

# 调用下游，数据落在上游；租户头被透传
curl -X POST http://127.0.0.1:18081/api/v1/users \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-remote' \
  -d '{"username":"adapter-user","displayName":"Adapter"}'
```

详细说明与验证结果见 `docs/guides/HTTP_REMOTE_ADAPTER.md`。默认 `mode=local` 时使用本地 `UserService`，两者互斥。

## 6. 从单体切换到微服务

1. 确认新域已按 `MODULE_CATALOG.md` 第 4 节建立 `api-*` / `module-*`，并在 `xs-dependencies` 与根 `pom.xml` 登记。
2. 新增 `xs-app-<domain>`，参照 `xs-app-user`：`@EntityScan` / `@EnableJpaRepositories` 收窄到本域，依赖本域模块，其他域调用改为实现 `api-*` 端口的远程适配器。
3. `bash scripts/deploy/build.sh all` 重新构建。
4. 逐个 `start.sh <mode>` 启动，`status.sh` 确认端口无冲突。
5. `smoke.sh all` 验证接口契约未漂移。
6. 保持 `bash scripts/ci/verify.sh` 全绿（G05/G06/G07 会检查 OpenAPI 与运行时契约一致）。

## 7. 常见问题

| 现象 | 原因与处理 |
|---|---|
| `JDK 25 required, found major=21` | 本机 `JAVA_HOME` 指向旧 JDK；脚本会回退到 `.tools/jdk25`，若仍报错说明便携 JDK 缺失或损坏 |
| `boot jar not found` | 先执行 `build.sh`；或 `mvn -pl xs-app-monolith -am clean package` |
| 端口被占用 | 用 `MONOLITH_PORT` / `USER_PORT` / `TENANT_PORT` 覆盖，或 `stop.sh all` 清理旧进程 |
| `missing tenant returned 200` | 说明 `allow-header` 配置被改；检查 `application.yml`，生产态必须为 false |
| 启动后 `/v3/api-docs` 一直不通 | 看 `.tools/deploy/<mode>.log`；`start.sh` 在 APPLICATION FAILED TO START 时会直接打印日志尾部 |
| 生产态创建用户失败 | PG 未执行 RLS 脚本，或应用角色拥有表 / 带 BYPASSRLS；检查 `db/migration/pg/V2__user_rls.sql` |
| 生产态 `st_tenant` 查询为空 | 管理台登录角色没有被授予 `xs_console`，导致 V4 策略不匹配；见 W04 |

## 8. 脚本清单

| 脚本 | 用途 |
|---|---|
| `common.sh` | 公共库：JDK 探测、jar 定位、端口等待、进程判定（仅被 source） |
| `build.sh` | 构建 boot jar，参数 `monolith` / `user` / `tenant` / `all` |
| `start.sh` | 后台启动，参数 `<mode>` [`--profile pg`] |
| `stop.sh` | 停止，参数 `<mode>` / `all` |
| `smoke.sh` | 冒烟：OpenAPI + 装配内各域端点，参数 `<mode>` / `all` |
| `status.sh` | 查看三模式运行状态 |
| `demo.sh` | 一键示例：构建→启动→冒烟→停止，可选 `--keep-running` |
