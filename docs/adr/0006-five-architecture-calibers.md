# ADR 0006: Five architecture calibers

Status: accepted

## Context

`docs/spec/VISION.md` listed five open decisions that change module boundaries. They must be fixed before the `tenant` domain and the first business slice are coded, otherwise the same work would be redone. The HTTP-vs-gRPC decision is recorded separately in ADR 0003.

## Decision

| # | 口径 | 决定 | 理由 |
|---|---|---|---|
| 1 | 租户身份来源 | 开发态 `X-Tenant-Id` 头；生产态由内部 `auth` 域签发的 JWT claim 提供，外部 IdP 联邦留到 `auth` 域之后接 | 自包含、无外部依赖，和控制台的 `auth` 模块同属一个信任域；避免一开始就绑死企业微信/OIDC |
| 2 | 控制台范围 | 只含 `tenant`、`user`、`auth`、`authorization`、`audit`、`config`；注册中心、配置下发、网关规则不在本仓库 | 后者是平台基础设施，应由独立组件或 `api-*` 端口适配，不进业务基座 |
| 3 | 管理台 UI | 独立前端工程，通过 OpenAPI 消费后端；本仓库不放 `admin-ui` 模块 | 后端仓库保持纯后端；已有 authored/runtime OpenAPI 与漂移门禁可作为前后端契约 |
| 4 | 单体数据库拓扑 | 一个数据库、一个 schema、按 `st_` / `sv_` 前缀区分域；不做 schema-per-domain | 与现有 Flyway `common` + `pg` 分域迁移、`@TenantId` + RLS 方案一致；运维简单 |
| 5 | 首个业务域 | `order` | 有完整生命周期和状态流转，能验证 outbox、事件携带 `tenant_id` 和独立装配；比纯 CRUD 域更能压出基座缺口 |

## Consequences

- `tenant` 域按以上口径实现：注册表是控制面全局数据，不是租户私有数据；租户私有表继续走 `@TenantId` + PG RLS。
- 生产 profile 已关闭 header 信任，但在 `auth` 域落地前仍不可用于登录态；这不是遗漏，是顺序问题。
- 跨服务同步调用只做 HTTP/JSON 声明式客户端适配器（ADR 0003）；gRPC 不引入，避免 proto 契约与第二套门禁。
- 前端独立仓库消费 OpenAPI，因此 G05–G07 契约门禁是前后端集成的前置条件，不能降级为 warning。
