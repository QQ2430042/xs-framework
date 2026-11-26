# xs-framework
<<<<<<< HEAD
xs-framework 1.0
=======

基于 Spring Boot 4 / JDK 25 / JPA 的长期维护服务基座，采用 SDD（Spec-Driven Development）方式演进。同一套 `api-* / module-* / starter-* / app-*` 模块即可装配为**单体**，也可拆成**独立微服务**。

## 技术基线

| 项 | 版本 |
|---|---|
| JDK | 25（LTS） |
| Spring Boot | 4.2.0 |
| Spring Framework | 7.1.0 |
| Spring Cloud | 2026.0.0（BOM） |
| Hibernate ORM | 7.4.15.Final |
| PostgreSQL | 19（开发态 H2） |
| Flyway | 12.11.0 |

完整版本清单在 `xs-dependencies/pom.xml`；版本基线决策见 `docs/adr/0001-package-and-version-baseline.md`。

## 模块一览

- `xs-common-core` — 零框架内核：统一响应、错误码、租户上下文、ID 生成
- `xs-api-user` / `xs-api-tenant` — 领域端口与 DTO（纯 Java，无框架注解）
- `xs-starter-web` / `xs-starter-data` — Web / 数据横切（`BaseEntity` 租户私有、`GlobalEntity` 控制面全局）
- `xs-module-user` / `xs-module-tenant` — 领域垂直切片：Entity / Repository / Service / Controller + Flyway
- `xs-adapter-user-http` — HTTP 声明式远程适配器（`@HttpExchange` + `RestClient`），租户头透传
- `xs-app-monolith`（18080）、`xs-app-user`（18081）、`xs-app-tenant`（18082） — 三种装配
- `xs-tests/xs-arch-tests` / `xs-tests/xs-integration-tests` — ArchUnit 边界门禁 + HTTP 全链路测试

完整职责表：`docs/spec/MODULE_CATALOG.md`。

## 快速开始

```bash
# 门禁全套（enforcer → 依赖基线 → 编译测试 → OpenAPI lint/覆盖/漂移 → 密钥扫描）
bash scripts/ci/verify.sh

# 三种装配一键演示：构建 → 启动 → 冒烟 → 停止
bash scripts/deploy/demo.sh

# 手动启动单体并调用
bash scripts/deploy/start.sh monolith
curl -H 'X-Tenant-Id: tenant-1' -H 'Content-Type: application/json' \
  -d '{"username":"zhangsan","displayName":"张三"}' \
  http://127.0.0.1:18080/api/v1/users
bash scripts/deploy/stop.sh monolith
```

JDK 25 由部署脚本自动探测（`.tools/jdk25` → `JAVA_HOME` → `PATH`），无需手动设置。详细部署说明见 `docs/guides/DEPLOYMENT.md`。

## 远程适配器（微服务模式）

```bash
# 18081 以 remote 模式指向 18080，业务代码零改动
bash scripts/deploy/start.sh user -- \
  --xs.adapters.user.mode=remote \
  --xs.adapters.user.base-url=http://127.0.0.1:18080
```

远程调用、租户头传播与本地/远程 Bean 切换见 `docs/guides/HTTP_REMOTE_ADAPTER.md`。选型依据（不引入 gRPC）见 `docs/adr/0003-outbox-and-remote-adapters.md` 与 `docs/guides/PROTOCOL_BENCHMARK.md`。

## 架构与 SDD

- 架构总览：`docs/spec/ARCHITECTURE.md`
- 目标与已定口径：`docs/spec/VISION.md`、`docs/adr/0006-five-architecture-calibers.md`
- CI 门禁规则：`docs/spec/CI_GATES.md`
- GWT 用例：`docs/spec/user/create-user.spec.md`、`docs/spec/tenant/manage-tenant.spec.md`
- authored OpenAPI：`docs/spec/openapi/`，运行时契约：`docs/api/openapi.{yaml,json}`

## 已知边界

- PG RLS 六路径（W04）、Docker 冒烟（W05）、OWASP 扫描（W03）需要 Docker + PG19 环境，当前未实测。
- 认证授权尚未落地；开发态 `X-Tenant-Id` 头仅用于本地验证，生产 profile 不可直接用于登录态。
- V1–V4 迁移脚本在基线阶段被直接修改过；上线后任何变更必须走新增迁移。

## License

Apache License 2.0（见 `LICENSE`）。
>>>>>>> 39b3e3a8a4d99f50c1dfdcac5711edd48a11189f
