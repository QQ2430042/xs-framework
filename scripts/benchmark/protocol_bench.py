#!/usr/bin/env python3
"""公平对比 HTTP/JSON 与 gRPC 的协议开销。

两端跑完全相同的处理逻辑：校验租户、生成 UUID 与用户编码、返回同一组字段。
不包含数据库、Spring、JPA，因此测的是协议 + 序列化 + 连接管理的成本。
"""
from __future__ import annotations

import argparse
import gzip
import http.client
import json
import math
import os
import statistics
import sys
import tempfile
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    import grpc
    from grpc_tools import protoc
except ImportError as exc:  # pragma: no cover
    print(
        "缺少依赖，请先执行: python3 -m pip install -r scripts/benchmark/requirements.txt\n"
        f"原始错误: {exc}",
        file=sys.stderr,
    )
    sys.exit(2)


PROTO = """syntax = "proto3";
package xs.bench;

service UserService {
  rpc CreateUser (CreateUserRequest) returns (CreateUserReply);
}

message CreateUserRequest {
  string username = 1;
  string display_name = 2;
}

message CreateUserReply {
  string code = 1;
  string user_id = 2;
  string tenant_id = 3;
  string user_code = 4;
  string username = 5;
}
"""

TENANT = "tenant-bench"


def generate_stubs(destination: str):
    proto_path = os.path.join(destination, "bench.proto")
    with open(proto_path, "w", encoding="utf-8") as handle:
        handle.write(PROTO)
    result = protoc.main([
        "protoc",
        f"-I{destination}",
        f"--python_out={destination}",
        f"--grpc_python_out={destination}",
        proto_path,
    ])
    if result != 0:
        raise RuntimeError(f"protoc 生成失败，退出码 {result}")
    sys.path.insert(0, destination)
    import bench_pb2
    import bench_pb2_grpc
    return bench_pb2, bench_pb2_grpc


def make_result(tenant_id: str, username: str):
    if not tenant_id or not username:
        return None
    return {
        "code": "0",
        "user_id": str(uuid.uuid4()),
        "tenant_id": tenant_id,
        "user_code": "UC" + uuid.uuid4().hex[:14].upper(),
        "username": username,
    }


class HttpHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    gzip_enabled = False

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        if self.path != "/api/v1/users":
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            payload = {}

        result = make_result(self.headers.get("X-Tenant-Id", ""), payload.get("username", ""))
        if result is None:
            status = 400
            body = {"code": "TENANT_REQUIRED", "message": "tenant required", "data": None, "traceId": None}
        else:
            status = 200
            body = {"code": "0", "message": "OK", "data": result, "traceId": None}

        data = json.dumps(body, separators=(",", ":")).encode("utf-8")
        headers = []
        if self.gzip_enabled and "gzip" in self.headers.get("Accept-Encoding", ""):
            data = gzip.compress(data)
            headers.append(("Content-Encoding", "gzip"))
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for key, value in headers:
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(data)


class BenchHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = True
    request_queue_size = 256


def start_http_server(port: int, gzip_enabled: bool):
    HttpHandler.gzip_enabled = gzip_enabled
    server = BenchHTTPServer(("127.0.0.1", port), HttpHandler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def build_grpc_server(pb, pb_grpc, port: int, workers: int):
    class Service(pb_grpc.UserServiceServicer):
        def CreateUser(self, request, context):
            tenant_id = ""
            for key, value in context.invocation_metadata():
                if key.lower() == "x-tenant-id":
                    tenant_id = value
                    break
            result = make_result(tenant_id, request.username)
            if result is None:
                context.abort(grpc.StatusCode.INVALID_ARGUMENT, "tenant required")
            return pb.CreateUserReply(
                code=result["code"],
                user_id=result["user_id"],
                tenant_id=result["tenant_id"],
                user_code=result["user_code"],
                username=result["username"],
            )

    server = grpc.server(ThreadPoolExecutor(max_workers=workers))
    pb_grpc.add_UserServiceServicer_to_server(Service(), server)
    server.add_insecure_port(f"127.0.0.1:{port}")
    server.start()
    return server


def percentile(values, ratio: float) -> float:
    if not values:
        return 0.0
    position = (len(values) - 1) * ratio
    low = math.floor(position)
    high = math.ceil(position)
    if low == high:
        return values[int(position)]
    return values[low] * (high - position) + values[high] * (position - low)


def run_worker_pool(worker, concurrency: int, seconds: float):
    barrier = threading.Barrier(concurrency)
    latencies = []
    stats = {"errors": 0}
    lock = threading.Lock()
    deadline = time.perf_counter() + seconds

    def target():
        local = []
        local_errors = 0
        barrier.wait()
        try:
            local, local_errors = worker(deadline)
        finally:
            with lock:
                latencies.extend(local)
                stats["errors"] += local_errors

    threads = [threading.Thread(target=target, daemon=True) for _ in range(concurrency)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    return latencies, stats["errors"]


def http_worker(deadline, port: int, gzip_enabled: bool):
    local = []
    errors = 0
    body = json.dumps({"username": "bench-user", "displayName": "压测"}).encode("utf-8")
    headers = {"Content-Type": "application/json", "X-Tenant-Id": TENANT}
    if gzip_enabled:
        headers["Accept-Encoding"] = "gzip"

    def connect():
        return http.client.HTTPConnection("127.0.0.1", port, timeout=10)

    conn = connect()
    try:
        while time.perf_counter() < deadline:
            started = time.perf_counter()
            try:
                conn.request("POST", "/api/v1/users", body=body, headers=headers)
                response = conn.getresponse()
                payload = response.read()
                if gzip_enabled and response.getheader("Content-Encoding") == "gzip":
                    payload = gzip.decompress(payload)
                if response.status != 200 or not payload:
                    errors += 1
            except Exception:
                errors += 1
                try:
                    conn.close()
                except Exception:
                    pass
                conn = connect()
                continue
            local.append((time.perf_counter() - started) * 1000.0)
    finally:
        try:
            conn.close()
        except Exception:
            pass
    return local, errors


def grpc_worker(deadline, stub, request):
    local = []
    errors = 0
    metadata = [("x-tenant-id", TENANT)]
    while time.perf_counter() < deadline:
        started = time.perf_counter()
        try:
            response = stub.CreateUser(request, metadata=metadata, timeout=10)
            if response.code != "0":
                errors += 1
        except Exception:
            errors += 1
        local.append((time.perf_counter() - started) * 1000.0)
    return local, errors


def summarize(name: str, concurrency: int, latencies, errors: int, duration: float):
    ordered = sorted(latencies)
    total = len(ordered)
    return {
        "protocol": name,
        "concurrency": concurrency,
        "requests": total,
        "errors": errors,
        "throughput": total / duration if duration else 0.0,
        "p50_ms": percentile(ordered, 0.50),
        "p90_ms": percentile(ordered, 0.90),
        "p95_ms": percentile(ordered, 0.95),
        "p99_ms": percentile(ordered, 0.99),
        "mean_ms": statistics.fmean(ordered) if ordered else 0.0,
    }


def run_phase(name, worker_factory, concurrency, warmup, duration):
    warm_worker = worker_factory()
    run_worker_pool(warm_worker, concurrency, warmup)
    latencies, errors = run_worker_pool(worker_factory(), concurrency, duration)
    return summarize(name, concurrency, latencies, errors, duration)


def parse_args():
    parser = argparse.ArgumentParser(description="HTTP/JSON 与 gRPC 对等协议压测")
    parser.add_argument("--concurrency", default="4,16,64", help="并发梯度，逗号分隔，默认 4,16,64")
    parser.add_argument("--duration", type=float, default=5.0, help="每轮测量秒数，默认 5")
    parser.add_argument("--warmup", type=float, default=1.0, help="每轮预热秒数，默认 1")
    parser.add_argument("--port-http", type=int, default=18090)
    parser.add_argument("--port-grpc", type=int, default=18091)
    parser.add_argument("--grpc-workers", type=int, default=32)
    parser.add_argument("--http-gzip", action="store_true", help="HTTP 响应启用 gzip")
    parser.add_argument("--output-json", default="", help="把原始结果写入 JSON 文件")
    return parser.parse_args()


def main():
    args = parse_args()
    try:
        concurrency_levels = [int(item.strip()) for item in args.concurrency.split(",") if item.strip()]
    except ValueError:
        print("--concurrency 必须是逗号分隔的整数", file=sys.stderr)
        return 2
    if not concurrency_levels or any(value < 1 for value in concurrency_levels):
        print("--concurrency 至少要有一个正整数", file=sys.stderr)
        return 2

    with tempfile.TemporaryDirectory(prefix="xs-bench-") as workdir:
        pb, pb_grpc = generate_stubs(workdir)
        http_server = start_http_server(args.port_http, args.http_gzip)
        grpc_server = build_grpc_server(pb, pb_grpc, args.port_grpc, args.grpc_workers)
        channel = grpc.insecure_channel(f"127.0.0.1:{args.port_grpc}")
        stub = pb_grpc.UserServiceStub(channel)
        request = pb.CreateUserRequest(username="bench-user", display_name="压测")

        print(f"== 压测配置: duration={args.duration}s warmup={args.warmup}s "
              f"http_gzip={args.http_gzip} concurrency={concurrency_levels}")

        results = []
        try:
            for level in concurrency_levels:
                http_factory = lambda level=level: (
                    lambda deadline: http_worker(deadline, args.port_http, args.http_gzip)
                )
                grpc_factory = lambda level=level: (
                    lambda deadline: grpc_worker(deadline, stub, request)
                )
                results.append(run_phase("HTTP/JSON", http_factory, level, args.warmup, args.duration))
                results.append(run_phase("gRPC", grpc_factory, level, args.warmup, args.duration))
        finally:
            channel.close()
            grpc_server.stop(0).wait()
            http_server.shutdown()
            http_server.server_close()

        print()
        print(f"{'协议':<10} {'并发':>6} {'请求数':>10} {'错误':>6} "
              f"{'吞吐/s':>12} {'P50 ms':>9} {'P95 ms':>9} {'P99 ms':>9}")
        print("-" * 78)
        for item in results:
            print(f"{item['protocol']:<10} {item['concurrency']:>6} {item['requests']:>10} "
                  f"{item['errors']:>6} {item['throughput']:>12.1f} "
                  f"{item['p50_ms']:>9.3f} {item['p95_ms']:>9.3f} {item['p99_ms']:>9.3f}")

        print()
        print("== 对比 (gRPC / HTTP/JSON，>1 表示 gRPC 更好)")
        print(f"{'并发':>6} {'吞吐比':>12} {'P99 比':>12} {'结论':>10}")
        print("-" * 46)
        for index in range(0, len(results), 2):
            http_item = results[index]
            grpc_item = results[index + 1]
            tp_ratio = grpc_item["throughput"] / http_item["throughput"] if http_item["throughput"] else 0
            p99_ratio = http_item["p99_ms"] / grpc_item["p99_ms"] if grpc_item["p99_ms"] else 0
            verdict = "gRPC 优" if tp_ratio > 1.05 else ("HTTP 优" if tp_ratio < 0.95 else "接近")
            print(f"{http_item['concurrency']:>6} {tp_ratio:>12.2f} {p99_ratio:>12.2f} {verdict:>10}")

        if args.output_json:
            with open(args.output_json, "w", encoding="utf-8") as handle:
                json.dump(results, handle, ensure_ascii=False, indent=2)
            print(f"\n结果已写入 {args.output_json}")

        print("\n说明: 本脚本只测协议开销，不含数据库/JPA/Spring。请在同一台机器、空闲状态下多次运行。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
