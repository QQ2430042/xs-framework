# HTTP/JSON 与 gRPC 协议压测

## 1. 这个脚本测什么

`scripts/benchmark/protocol_bench.py` 在同一进程内启动两个对等服务端：

| 服务端 | 协议 | 入口 |
|---|---|---|
| HTTP | JSON over HTTP/1.1，支持连接复用，可选 gzip | `POST /api/v1/users`，租户走 `X-Tenant-Id` 头 |
| gRPC | Protobuf over HTTP/2 | `UserService.CreateUser`，租户走 metadata |

两边的业务逻辑完全相同：校验租户 → 生成 UUID → 生成 `UC` 前缀用户编码 → 返回同名字段。

**它不测**：Spring、JPA、Flyway、PostgreSQL RLS、真实数据库的耗时。所以结论只能用于回答「协议/序列化/连接管理这一层的开销谁更大」，不能替代对真实微服务的端到端压测。

## 2. 安装依赖

```bash
python3 -m pip install -r scripts/benchmark/requirements.txt
```

如果默认镜像源没有 `grpcio`，改用官方源：

```bash
python3 -m pip install --index-url https://pypi.org/simple -r scripts/benchmark/requirements.txt
```

脚本在临时目录里用 `grpc_tools.protoc` 现场生成 stub，不在仓库里落生成代码，也不污染 Maven Reactor 和 CI 依赖基线。

## 3. 运行

```bash
# 快速冒烟
bash scripts/benchmark/run.sh --duration 2 --warmup 1 --concurrency 4,16

# 正式一点：3 个并发梯度，每组 10 秒，结果落 JSON
bash scripts/benchmark/run.sh \
  --concurrency 4,16,64 \
  --duration 10 \
  --warmup 2 \
  --output-json .tools/bench-result.json
```

参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `--concurrency` | `4,16,64` | 并发梯度，逗号分隔；脚本按梯度从低到高跑两轮协议 |
| `--duration` | `5` | 每个并发档位的测量秒数 |
| `--warmup` | `1` | 每个档位的预热秒数，不计入统计 |
| `--port-http` / `--port-grpc` | `18090` / `18091` | 两个服务端口，被占用时改掉 |
| `--grpc-workers` | `32` | gRPC 服务端线程池大小 |
| `--http-gzip` | 关闭 | HTTP 响应启用 gzip，用于看压缩对结论的影响 |
| `--output-json` | 空 | 把原始指标写成 JSON |

## 4. 输出怎么读

示例（本机 2 秒短测，仅供理解格式）：

```text
协议             并发        请求数     错误         吞吐/s    P50 ms    P95 ms    P99 ms
HTTP/JSON       4       9604      0       4802.0     0.773     1.471     1.835
gRPC            4      10883      0       5441.5     0.702     1.092     1.313
HTTP/JSON      16      13583      0       6791.5     1.839     3.674     4.684
gRPC           16      10862      0       5431.0     2.682     5.030     6.492
```

- **吞吐比 = gRPC 吞吐 / HTTP 吞吐**，大于 1 表示 gRPC 更高。
- **P99 比 = HTTP P99 / gRPC P99**，大于 1 表示 gRPC 尾部延迟更低。
- `errors` 非 0 时先看错误，不要直接看吞吐。

典型判读：

| 现象 | 可能的瓶颈 |
|---|---|
| 低并发 gRPC 明显领先，高并发被 HTTP 反超 | 不在协议本身，而在通道复用、HTTP/2 流控或服务端线程池；先调 `--grpc-workers` 再看 |
| gRPC 全程吞吐更高、P99 更低 | JSON 序列化/文本解析是主要成本，内部高频链路值得换 gRPC |
| HTTP 全程领先且 gRPC 延迟随并发陡增 | gRPC 客户端通道或服务端线程配置成为瓶颈，需要专项调优 |
| 两者吞吐都上不去、CPU 打满 | 客户端/服务端同进程抢 CPU 或 Python GIL 限制；换多进程或减少并发梯度 |

## 5. 重要限制

1. **Python 实现不能外推绝对值**：客户端和服务端同进程，受 GIL 影响。这里适合看趋势和相对差异；要拿生产数字，需要用 Java/Spring 栈做同样的对等 harness。
2. **必须空闲运行**：不要同时跑单体、Maven 构建或 CI；客户端和服务端在同一台机器上会抢 CPU。
3. **多跑几次取稳定值**：短测很容易被 GC、线程调度、TCP 慢启动带偏。建议至少跑 3 次。
4. **gzip 不是默认开启**：默认比的是裸 JSON 与裸 Protobuf。加 `--http-gzip` 可以看压缩后 HTTP 是否缩小差距。
5. **本脚本不接工程门禁**：放在 `scripts/benchmark/`，不在根 Reactor，不影响 G01–G07 与依赖基线。

## 6. 和选型决策的关系

**决策已定：不引入 gRPC（ADR 0003）。** 10 轮聚合显示 gRPC/HTTP 吞吐比为 0.78–0.82，P99 比均小于 1，gRPC 在本工作负载下既无吞吐优势也无尾延迟优势。工程统一采用 HTTP/JSON 声明式客户端，不引入 `.proto` 契约与第二套门禁。

脚本保留为决策证据与回归验证工具：

- 未来若讨论重启 gRPC，必须先在新 ADR 中给出 Java/Spring 栈的实测证据（本脚本是 Python 同进程对等负载，绝对值不外推）。
- 若要验证压缩对结论的影响，跑 `--http-gzip` 对比。
- 原始数据与聚合见 `.tools/bench-runs/run*.json` 与 `summary.json`。
