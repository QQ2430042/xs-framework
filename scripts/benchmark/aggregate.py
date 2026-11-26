#!/usr/bin/env python3
"""把多轮 protocol_bench.py 的 JSON 结果聚合成稳定比值。"""
from __future__ import annotations

import argparse
import glob
import json
import statistics
import sys


def load_runs(patterns):
    paths = []
    for pattern in patterns:
        paths.extend(glob.glob(pattern))
    paths = sorted(set(paths))
    if not paths:
        raise SystemExit(f"没有找到匹配的结果文件: {patterns}")

    runs = []
    for path in paths:
        with open(path, "r", encoding="utf-8") as handle:
            records = json.load(handle)
        grouped = {}
        for record in records:
            key = (record["concurrency"], record["protocol"])
            grouped[key] = record
        runs.append((path, grouped))
    return runs


def ratio_stats(values):
    mean = statistics.fmean(values)
    median = statistics.median(values)
    stdev = statistics.stdev(values) if len(values) > 1 else 0.0
    cv = stdev / mean if mean else 0.0
    return mean, median, stdev, cv, min(values), max(values)


def main():
    parser = argparse.ArgumentParser(description="聚合多轮 HTTP/gRPC 压测结果")
    parser.add_argument("patterns", nargs="+", help="JSON 文件或通配符，例如 .tools/bench-runs/run*.json")
    parser.add_argument("--output-json", default="", help="把聚合结果写入 JSON")
    args = parser.parse_args()

    runs = load_runs(args.patterns)
    print(f"== 聚合 {len(runs)} 轮结果")

    levels = sorted({key[0] for _, grouped in runs for key in grouped})
    summary = []

    for level in levels:
        samples = []
        http_tp = []
        grpc_tp = []
        for path, grouped in runs:
            http = grouped.get((level, "HTTP/JSON"))
            grpc = grouped.get((level, "gRPC"))
            if not http or not grpc:
                continue
            if http["errors"] or grpc["errors"]:
                print(f"警告: {path} 并发 {level} 有错误，已跳过", file=sys.stderr)
                continue
            if not http["throughput"] or not grpc["p99_ms"]:
                continue
            samples.append({
                "throughput_ratio": grpc["throughput"] / http["throughput"],
                "p99_ratio": http["p99_ms"] / grpc["p99_ms"],
            })
            http_tp.append(http["throughput"])
            grpc_tp.append(grpc["throughput"])

        if len(samples) < 2:
            print(f"并发 {level}: 有效样本不足 2 轮，跳过")
            continue

        tp = ratio_stats([item["throughput_ratio"] for item in samples])
        p99 = ratio_stats([item["p99_ratio"] for item in samples])
        verdict = "gRPC 优" if tp[0] > 1.05 else ("HTTP 优" if tp[0] < 0.95 else "接近")
        stable = "稳定" if tp[3] < 0.08 else ("轻微波动" if tp[3] < 0.15 else "仍不稳")

        item = {
            "concurrency": level,
            "samples": len(samples),
            "http_throughput_mean": statistics.fmean(http_tp),
            "grpc_throughput_mean": statistics.fmean(grpc_tp),
            "throughput_ratio_mean": tp[0],
            "throughput_ratio_median": tp[1],
            "throughput_ratio_stdev": tp[2],
            "throughput_ratio_cv": tp[3],
            "throughput_ratio_min": tp[4],
            "throughput_ratio_max": tp[5],
            "p99_ratio_mean": p99[0],
            "p99_ratio_median": p99[1],
            "verdict": verdict,
            "stability": stable,
        }
        summary.append(item)

    print()
    print(f"{'并发':>6} {'样本':>5} {'HTTP/s':>12} {'gRPC/s':>12} "
          f"{'吞吐比均值':>12} {'±标准差':>10} {'CV':>8} {'范围':>18} {'P99比':>9} {'结论':>8} {'稳定性':>10}")
    print("-" * 124)
    for item in summary:
        print(f"{item['concurrency']:>6} {item['samples']:>5} "
              f"{item['http_throughput_mean']:>12.1f} {item['grpc_throughput_mean']:>12.1f} "
              f"{item['throughput_ratio_mean']:>12.3f} {item['throughput_ratio_stdev']:>10.3f} "
              f"{item['throughput_ratio_cv']:>8.3f} "
              f"{item['throughput_ratio_min']:.2f}~{item['throughput_ratio_max']:.2f}".rjust(18) +
              f" {item['p99_ratio_mean']:>9.3f} {item['verdict']:>8} {item['stability']:>10}")

    print()
    print("吞吐比 = gRPC / HTTP，>1 表示 gRPC 吞吐更高；P99 比 = HTTP / gRPC，>1 表示 gRPC 尾延迟更低。")
    print("CV < 0.08 视为稳定；0.08~0.15 轻微波动；>0.15 建议延长 --duration 或多跑几轮。")

    if args.output_json:
        with open(args.output_json, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, ensure_ascii=False, indent=2)
        print(f"\n聚合结果已写入 {args.output_json}")


if __name__ == "__main__":
    main()
