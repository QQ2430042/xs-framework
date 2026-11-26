# HTTP 声明式远程适配器使用说明

ADR 0003 已决定：跨服务同步调用只用 HTTP/JSON 声明式客户端，**不引入 gRPC**。本文说明如何把某个 `api-*` 端口从本地实现切换到 HTTP 远程实现，业务代码零改动。

## 1. 已实现的样例

`xs-adapter-user-http` 为 `UserPort` 提供远程实现：

| 类 | 职责 |
|---|---|
| `UserHttpExchange` | `@HttpExchange("/api/v1/users")` 声明式客户端接口：`POST` 创建、`GET /{userId}` 查询 |
| `RemoteUserPort` | `implements UserPort`，把 `UserHttpExchange` 的响应解包成 `UserView`；失败时抛 `BusinessException` |
| `RemoteResponse<T>` | 对齐 `ApiResponse` 的线格式（`code/message/data/traceId`），避免依赖 `xs-starter-web` |
| `TenantHeaderInterceptor` | 每次出站请求带上当前 `TenantContext` 的 `X-Tenant-Id` |
| `UserHttpAdapterAutoConfiguration` | 仅在 `xs.adapters.user.mode=remote` 时注册上述 Bean |

切换机制：

- `UserService`（本地实现）标注 `@ConditionalOnProperty(prefix="xs.adapters.user", name="mode", havingValue="local", matchIfMissing=true)`
- 远程适配器标注 `havingValue="remote"`

两者互斥，因此注入 `UserPort` 的 Controller 无需任何改动。

## 2. 两种模式的配置

默认（本地，无需配置）：

```yaml
xs:
  adapters:
    user:
      mode: local   # 或省略
```

远程模式：

```yaml
xs:
  adapters:
    user:
      mode: remote
      base-url: http://127.0.0.1:18080   # 上游 user API 所在服务
```

`base-url` 未配置时默认 `http://127.0.0.1:18081`。

## 3. 端到端实测（已验证）

```bash
# 终端 1：单体作为上游（含本地 UserPort）
java -jar xs-app-monolith/target/xs-app-monolith-0.1.0-SNAPSHOT-boot.jar --server.port=18080

# 终端 2：用户服务切到 remote 模式，指向上游
java -jar xs-app-user/target/xs-app-user-0.1.0-SNAPSHOT-boot.jar \
  --server.port=18081 \
  --xs.adapters.user.mode=remote \
  --xs.adapters.user.base-url=http://127.0.0.1:18080

# 终端 3：调用 18081，请求实际经 HTTP 打到 18080
curl -X POST http://127.0.0.1:18081/api/v1/users \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-remote' \
  -d '{"username":"adapter-user","displayName":"Adapter"}'
```

实测结果：返回 `code=0`，`tenantId=tenant-remote`；随后直接用 `GET http://127.0.0.1:18080/api/v1/users/{id}` 带上同租户可查回，换租户返回 404。证明：

1. 端口实现切换生效（18081 自身没有写库，数据在上游）；
2. 租户头经 `TenantHeaderInterceptor` 传播到上游，RLS 判定正确；
3. 响应线格式与本地模式完全一致。

## 4. 自动化测试

`xs-tests/xs-integration-tests` 中：

- `UserPortLocalSelectionTest`：默认模式注入的是 `UserService`
- `UserPortRemoteSelectionTest`：`mode=remote` 时注入的是 `RemoteUserPort`

两者共用 `MonolithApplication`，只靠属性区分，证明切换是装配层决策。

## 5. 给新域加远程适配器

以 `order` 为例：

1. 新建 `xs-adapter-order-http`，依赖 `xs-api-order` + `spring-boot-starter-webmvc`。
2. 照 `UserHttpExchange` 写 `@HttpExchange` 接口（路径对齐该域 Controller 的 authored OpenAPI）。
3. 实现 `RemoteOrderPort implements OrderPort`，解包 `RemoteResponse`。
4. 写自动配置，条件为 `xs.adapters.order.mode=remote`，并注册到 `AutoConfiguration.imports`。
5. 给本地 `OrderService` 加相同的 `@ConditionalOnProperty(mode=local, matchIfMissing=true)`。
6. 在需要远程能力的 app pom 中加适配器依赖；补 bean 选择测试。

约束：

- 适配器**不得**被 `api.*` 引用（保持纯 Java 契约）。
- 契约变更必须同时更新 authored spec 与运行时契约，G05/G06/G07 会拦截漂移。
- 异步事件不走适配器，走 outbox（尚未实现）。
