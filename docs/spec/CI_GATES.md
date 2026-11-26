# CI 门禁规则

Version: 1.0.0
状态：accepted
适用仓库：`xs-framework`
唯一执行入口：`bash scripts/ci/verify.sh`
流水线定义：`.github/workflows/ci.yml`

## 1. 总则

1. 规格先于代码：用户用例（`docs/spec/<domain>/*.spec.md`）与接口契约（`docs/spec/openapi/*.yaml`）先改，代码后改。
2. 门禁只认机器可验证事实，不认口头说明；失败即阻断合并。
3. 依赖版本基线来自 `xs-dependencies`；任何第三方版本变化必须同步 `docs/ci/dependency-tree-baseline.txt` 并补 ADR。
4. 不允许通过 `skipTests`、`-Denforcer.skip`、`--no-verify` 绕过 P0 门禁。
5. 本地与 CI 使用同一脚本，避免“本地绿、线上红”。

## 2. 触发时机

| 事件 | 执行内容 |
|---|---|
| Pull Request（任意目标分支） | 全部 P0 门禁 |
| Push 到 `main` | 全部 P0 门禁 |
| `workflow_dispatch` | 全部 P0 门禁 |
| 依赖/流水线变更 | 全部 P0 门禁 + 人工复核 P1 报告 |

并发策略：同一 ref 的新 run 取消旧 run；`permissions: contents: read`。

## 3. P0 强门禁（必须绿）

| ID | 门禁 | 执行命令 | 失败条件 | 规则意图 |
|---|---|---|---|---|
| G01 | JDK 锁定 | Maven `requireJavaVersion [25,26)` | JDK 非 25，或 26+ 特性版 | 基线只跟 LTS，不让特性版污染生产口径 |
| G02 | Maven 与依赖收敛 | `requireMavenVersion [3.9,)` + `dependencyConvergence` | Maven < 3.9；同一 GA 出现多个版本 | 保证可重复构建，避免多版本共存 |
| G03 | 依赖基线与预发布版本 | `scripts/ci/check-dependencies.sh` | 依赖树与基线不一致；第三方出现 `SNAPSHOT/RC/M/alpha/beta` | 版本变更必须可审计；第三方只接 GA |
| G04 | 构建与测试 | `mvn -B -ntp clean verify` | 编译失败、单测失败、ArchUnit 4 条边界规则失败、H2/MockMvc 集成测试失败 | 验证代码、边界与垂直用例同时成立 |
| G05 | OpenAPI lint（全部规格 + 运行时） | Spectral 6.15.0 + `docs/ci/spectral.yaml` | 任一 authored spec 或运行时契约出现 error 级问题 | 所有契约都必须有 operationId、description、tags、4xx 响应 |
| G06 | 规格覆盖一致性 | `scripts/ci/check-openapi-coverage.sh` | 全部 authored spec 的并集与 committed runtime 的 path/method/operationId 不一致 | 规格与运行时契约不漂移；新域必须补 authored spec |
| G07 | 运行时契约漂移 | `scripts/ci/check-openapi-drift.sh` | 打包后的 jar 暴露的 OpenAPI 与 `docs/api/openapi.yaml` 不一致 | 防止改 Controller 不更新契约文件 |

### 3.1 规则细节

- **G01/G02** 写在根 `pom.xml` 的 `maven-enforcer-plugin` 中。`xs-dependencies` 是独立 BOM，不参与 enforcer CLI 调用；其余模块继承根 POM。
- **G03** 基线文件为 `docs/ci/dependency-tree-baseline.txt`。脚本把全部模块的 `dependency:tree` 汇总成 `.tools/dependency-tree/combined.txt`，统一换行后逐行 diff。
- **G04** 已覆盖的 ArchUnit 规则：`api` 纯 Java、`common` 无框架依赖、业务模块互不依赖、app 装配互不依赖。
- **G05** 同时 lint `docs/spec/openapi/user-api.yaml`、`docs/spec/openapi/tenant-api.yaml` 与 `docs/api/openapi.yaml`。当前 error 规则：`operation-description`、`operation-operationId`、`operation-operationId-unique`、`operation-tags`、`operation-4xx-response`。Spectral 6.15 没有内置 4xx 规则，规则集用 `schema` 函数补齐。关闭 `oas3-api-servers`、`info-contact`、`info-license`、`operation-tag-defined`，因为领域规格不承载部署元信息。运行时契约通过 Controller 的 `@Operation(description=...)`、`@ApiResponses`、`@Parameter(description=...)` 与 app 入口的 `@OpenAPIDefinition(info=@Info(...))` 满足规则。
- **G06** 通过正则解析 YAML 的 `paths` 区块，不依赖 PyYAML；调用方式为 `check-openapi-coverage.sh <spec1> [spec2 ...] <runtime>`，最后一个参数是运行时契约，其余全部为 authored spec。脚本先做并集（跨 spec 的路径/操作重复会直接失败），再要求双向路径/方法集合一致、同名操作 `operationId` 相同。
- **G07** 随机可用端口启动 `xs-app-monolith-*-boot.jar`，优先使用 `JAVA_HOME/bin/java` 避免本机多 JDK 误启动，拉取 `/v3/api-docs.yaml`，把 server URL 端口归一化为 `PORT` 后 diff；启动失败会打印应用日志。

## 4. P1 观察项（暂不阻断）

| ID | 项目 | 当前状态 | 升级为 P0 的条件 |
|---|---|---|---|
| W01 | `requireUpperBoundDeps` | 关闭 | Boot 4.2 BOM 有意把 Jackson 3.1.8 / jackson-annotations 2.21 钉在 springdoc 请求版本之下；手工覆盖会违反版本纪律。待五步依赖核查出具结论后再开启 |
| W02 | Secret scan / gitleaks | 已接入观察模式（2026-11-25） | 正则扫描已进 `verify.sh` 但只报告不阻断；仓库接入远端并运行一周零误报后，用 `STRICT_SECRETS=true` 转阻断 |
| W03 | OWASP dependency-check | 未接入 | 建立 NVD 数据源与误报豁免流程后转阻断 |
| W04 | PostgreSQL 19 RLS 六路径隔离测试 | 仅设计 | 有稳定 Testcontainers PG19 环境、六条路径全部通过后转阻断 |
| W05 | Docker 镜像构建与启动冒烟 | 未接入 | 镜像产物纳入发布流程时转阻断 |
| W06 | 运行时 OpenAPI 也跑 Spectral | 已升级为 G05（2026-11-25） | 已满足：运行时契约补齐 description/4xx/参数描述后纳入强门禁 |

### 4.1 W02 扫描口径

`scripts/ci/check-secrets.sh` 默认观察模式，命中只打印不失败；`STRICT_SECRETS=true` 时命中即失败。当前 pattern：AWS Access Key ID、RSA/EC/OPENSSH/DSA 私钥头、GitHub token、Slack token、JWT 三段式，以及 `password / passwd / secret / api[._-]?key / access[._-]?token / client[._-]?secret` 后接 8 位以上引号值。扫描排除 `.tools`、`target`、`.git`、`node_modules`、`.idea` 与脚本自身；证据落在 `.tools/secret-scan/hits.txt`。

这是正则基线，不替代 gitleaks 的熵检测；转正前仍需引入正式 secret scanning 工具并跑一周零误报。

## 5. 变更流程

### 5.1 普通功能变更

1. 新增或更新 `docs/spec/<domain>/*.spec.md`（Given/When/Then）。
2. 更新 `docs/spec/openapi/<domain>-api.yaml`。
3. 实现代码与测试。
4. 若 Controller 暴露变化，启动应用并重新导出：
   - `docs/api/openapi.yaml`
   - `docs/api/openapi.json`
5. 本地执行 `bash scripts/ci/verify.sh`，全绿后提 PR。

### 5.2 依赖变更

1. 先在 `xs-dependencies/pom.xml` 修改版本，根因写入新 ADR。
2. 本地执行 `UPDATE_DEPENDENCY_BASELINE=true bash scripts/ci/check-dependencies.sh` 更新基线。
3. PR 中必须包含：ADR、`xs-dependencies/pom.xml`、基线文件、`dependency:tree` 关键差异说明。
4. CI 环境禁止更新基线；脚本会主动拒绝。

### 5.3 架构边界变更

1. 先更新 `docs/spec/ARCHITECTURE.md` 与对应 ADR。
2. 再改 ArchUnit 规则或代码。
3. G04 必须全绿；不得以“临时放宽切片规则”合并。

## 6. 豁免规则

- P0 默认不可跳过。确需临时豁免时：PR 带 `gate-waiver` 标签、关联 ADR（写明失效日期，最长 7 天）、由另一名维护者 approve。
- 流水线本身不读取标签自动放行；豁免通过分支保护里的必需检查例外人工执行，并在 ADR 中留痕。
- 到期未关闭的豁免视为架构债，下一迭代第一个 PR 必须清理。

## 7. 本地复现

```bash
export JAVA_HOME=<JDK 25 home>
export PATH="$JAVA_HOME/bin:$PATH"
cd <repo>/xs-framework
bash scripts/ci/verify.sh
```

单条门禁可单独执行：

```bash
mvn -B -ntp -pl '!xs-dependencies' enforcer:enforce@ci-enforcer
bash scripts/ci/check-dependencies.sh
mvn -B -ntp clean verify
npx --yes @stoplight/spectral-cli@6.15.0 lint \
  docs/spec/openapi/user-api.yaml docs/spec/openapi/tenant-api.yaml docs/api/openapi.yaml \
  --ruleset docs/ci/spectral.yaml --fail-severity=error
bash scripts/ci/check-openapi-coverage.sh \
  docs/spec/openapi/user-api.yaml docs/spec/openapi/tenant-api.yaml docs/api/openapi.yaml
bash scripts/ci/check-openapi-drift.sh
bash scripts/ci/check-secrets.sh                  # observation
STRICT_SECRETS=true bash scripts/ci/check-secrets.sh  # blocking
```

## 8. 失败定位

| 现象 | 首要检查 |
|---|---|
| G01 失败 | `JAVA_HOME` 是否指向 JDK 25；不要用 21/26/27 跑 |
| G02 失败 | `mvn -version`；依赖树中同名 GA 是否多版本 |
| G03 失败 | `.tools/dependency-tree/dependency-diff.txt`；是否漏更新基线 |
| G04 失败 | 对应模块 `target/surefire-reports`；ArchUnit 违规信息 |
| G05 失败 | Spectral 输出的 rule id 与 JSON pointer；区分是 authored spec 还是 `docs/api/openapi.yaml` |
| G06 失败 | authored spec 与 `docs/api/openapi.yaml` 的 path/method/operationId 差异 |
| G07 失败 | `.tools/openapi-drift/diff.txt`、`.tools/openapi-drift/app.log` |
