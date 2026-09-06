#!/usr/bin/env python3
                       

\
\
\
\
\
   

import argparse
import json
import math
import os
import re
import sys
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from datetime import datetime
from http.client import RemoteDisconnected
from typing import Dict, Iterable, List, Optional, Sequence, Tuple
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Endpoint:
    name: str
    path: str
    requires_auth: bool = False


@dataclass(frozen=True)
class RequestResult:
    endpoint: str
    status: int
    elapsed_ms: float
    error: str = ""
    response_bytes: int = 0
    content_range: str = ""

    @property
    def passed(self) -> bool:
        return 200 <= self.status < 300 and not self.error


DEFAULT_SCENARIOS = {
    "public-read": (
        Endpoint("song-page", "/song/page?page=1&size=10"),
        Endpoint("hot-songs", "/song/hot?limit=10"),
        Endpoint("new-songs", "/song/new?page=1&size=10"),
        Endpoint("playlist-page", "/playlist/page?page=1&size=10"),
        Endpoint("hot-playlists", "/playlist/hot?limit=10"),
        Endpoint("artist-page", "/artist/page?page=1&size=10"),
        Endpoint("hot-artists", "/artist/hot?limit=10"),
        Endpoint("album-page", "/album/page?page=1&size=10"),
        Endpoint("hot-albums", "/album/hot?limit=10"),
        Endpoint("mv-page", "/mv/page?page=1&size=10"),
        Endpoint("hot-mvs", "/mv/hot?limit=10"),
        Endpoint("new-mvs", "/mv/newest?limit=10"),
        Endpoint("song-search", "/search/songs?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("album-search", "/search/albums?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("artist-search", "/search/artists?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("playlist-search", "/search/playlists?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("mv-search", "/search/mvs?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("user-search", "/search/users?keyword=%E5%A4%9C&page=1&size=3"),
        Endpoint("search-suggest", "/search/suggest?keyword=%E5%A4%9C&limit=5"),
        Endpoint("hot-keywords", "/search/hot-keywords?limit=10"),
        Endpoint("curated-carousel", "/curated-content/carousel?scene=discover_top&limit=5"),
    ),
    "public-content": (
        Endpoint("playlist-square-featured", "/playlist-square/featured?limit=5"),
        Endpoint("playlist-square-overview", "/playlist-square/overview"),
        Endpoint("music-square-posts", "/music-square/posts?timeRange=all&type=recommend&page=1&size=10"),
        Endpoint("hot-topics", "/music-square/topics/hot?limit=10"),
    ),
    "authenticated-read": (
        Endpoint("daily-recommend", "/recommend/daily", True),
        Endpoint("personal-recommend", "/recommend/personal?limit=10", True),
        Endpoint("hybrid-recommend", "/hybrid/recommend?limit=10", True),
    ),
    "mixed-read": (
        Endpoint("song-list", "/song/list?page=1&size=10"),
        Endpoint("hot-songs", "/song/hot?limit=10"),
        Endpoint("new-songs", "/song/new?limit=10"),
        Endpoint("hot-playlists", "/playlist/hot?limit=10"),
        Endpoint("song-search", "/search/song?keyword=%E5%A4%9C&page=1"),
        Endpoint("daily-recommend", "/recommend/daily", True),
        Endpoint("personal-recommend", "/recommend/personal?limit=10", True),
        Endpoint("hybrid-recommend", "/hybrid/recommend?limit=10", True),
    ),
}


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def parse_range_header(value: str) -> str:
                                                                        
    candidate = value.strip()
    if not candidate:
        return ""
    match = re.fullmatch(r"bytes=(\d+)-(\d+)", candidate)
    if not match:
        raise argparse.ArgumentTypeError(
            "must use a bounded range such as bytes=0-65535"
        )
    start, end = (int(part) for part in match.groups())
    if start > end:
        raise argparse.ArgumentTypeError("range start must not exceed range end")
    return candidate


def percentile(values: Sequence[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * quantile) - 1))
    return ordered[index]


def parse_custom_endpoint(value: str, auth_all: bool = False) -> Endpoint:
                                                                                
    if "=" in value:
        name, path = value.split("=", 1)
        name = name.strip() or path
    else:
        path = value
        name = path
    path = path.strip()
    if not path.startswith("/"):
        path = "/" + path
    return Endpoint(name=name.strip(), path=path, requires_auth=auth_all)


def extract_token(payload: object) -> str:
    if isinstance(payload, dict):
        token = payload.get("token")
        if isinstance(token, str) and token:
            return token
        for key in ("data", "result"):
            nested = payload.get(key)
            found = extract_token(nested)
            if found:
                return found
    return ""


def login(base_url: str, username: str, password: str, timeout: float) -> str:
    payload = json.dumps({"username": username, "password": password}).encode("utf-8")
    request = Request(
        urljoin(base_url.rstrip("/") + "/", "user/login"),
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8")
    token = extract_token(json.loads(body))
    if not token:
        raise RuntimeError("login succeeded but no token was found in the response")
    return token


def request_once(
    base_url: str,
    endpoint: Endpoint,
    token: str,
    timeout: float,
    range_header: str = "",
    referer_header: str = "",
    read_chunk_size: int = 0,
    read_chunk_delay_ms: int = 0,
) -> RequestResult:
    headers = {
        "Accept": "application/json",
        "User-Agent": "haoran-performance-test/2026-08",
    }
    if token and endpoint.requires_auth:
        headers["Authorization"] = "Bearer " + token
    if range_header:
        headers["Range"] = range_header
    if referer_header:
        headers["Referer"] = referer_header

    request = Request(
        urljoin(base_url.rstrip("/") + "/", endpoint.path.lstrip("/")),
        headers=headers,
        method="GET",
    )
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=timeout) as response:
            if read_chunk_size > 0:
                chunks = []
                while True:
                    chunk = response.read(read_chunk_size)
                    if not chunk:
                        break
                    chunks.append(chunk)
                    if read_chunk_delay_ms > 0:
                        time.sleep(read_chunk_delay_ms / 1000.0)
                body = b"".join(chunks)
            else:
                body = response.read()
            status = response.getcode()
            response_headers = getattr(response, "headers", {})
            content_range = response_headers.get("Content-Range", "")
        return RequestResult(
            endpoint.name,
            status,
            elapsed_ms(started),
            response_bytes=len(body),
            content_range=content_range,
        )
    except HTTPError as error:
        return RequestResult(
            endpoint.name,
            error.code,
            elapsed_ms(started),
            "http {}".format(error.code),
        )
    except (URLError, TimeoutError, RemoteDisconnected, OSError) as error:
        return RequestResult(
            endpoint.name,
            0,
            elapsed_ms(started),
            "{}".format(error),
        )
    except Exception as error:                                               
        return RequestResult(
            endpoint.name,
            0,
            elapsed_ms(started),
            "{}".format(error),
        )


def elapsed_ms(started: float) -> float:
    return (time.perf_counter() - started) * 1000.0


def run_load(
    base_url: str,
    endpoints: Sequence[Endpoint],
    concurrency: int,
    request_count: int,
    timeout: float,
    token: str = "",
    range_header: str = "",
    referer_header: str = "",
    read_chunk_size: int = 0,
    read_chunk_delay_ms: int = 0,
) -> Tuple[List[RequestResult], float]:
    if not endpoints:
        raise ValueError("at least one endpoint is required")
    started = time.perf_counter()
    results: List[RequestResult] = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(
                request_once,
                base_url,
                endpoints[index % len(endpoints)],
                token,
                timeout,
                range_header,
                referer_header,
                read_chunk_size,
                read_chunk_delay_ms,
            )
            for index in range(request_count)
        ]
        for future in as_completed(futures):
            results.append(future.result())
    return results, elapsed_ms(started)


def summarize_results(results: Iterable[RequestResult], wall_time_ms: float) -> Dict:
    rows = list(results)
    latencies = [row.elapsed_ms for row in rows]
    statuses = Counter(str(row.status) for row in rows)
    content_ranges = Counter(
        row.content_range for row in rows if row.content_range
    )
    response_sizes = [row.response_bytes for row in rows if row.response_bytes]
    failures = [row for row in rows if not row.passed]
    return {
        "requests": len(rows),
        "successes": len(rows) - len(failures),
        "failures": len(failures),
        "success_rate_percent": (
            round((len(rows) - len(failures)) / len(rows) * 100.0, 2)
            if rows
            else 0.0
        ),
        "wall_time_ms": round(wall_time_ms, 2),
        "rps": round(len(rows) / (wall_time_ms / 1000.0), 2)
        if wall_time_ms > 0
        else 0.0,
        "latency_ms": {
            "min": round(min(latencies), 2) if latencies else 0.0,
            "avg": round(sum(latencies) / len(latencies), 2) if latencies else 0.0,
            "p50": round(percentile(latencies, 0.50), 2),
            "p95": round(percentile(latencies, 0.95), 2),
            "p99": round(percentile(latencies, 0.99), 2),
            "max": round(max(latencies), 2) if latencies else 0.0,
        },
        "statuses": dict(sorted(statuses.items())),
        "response_bytes": {
            "total": sum(response_sizes),
            "avg": round(sum(response_sizes) / len(response_sizes), 2)
            if response_sizes
            else 0.0,
            "min": min(response_sizes) if response_sizes else 0,
            "max": max(response_sizes) if response_sizes else 0,
        },
        "content_ranges": dict(sorted(content_ranges.items())),
        "errors": [
            {"endpoint": row.endpoint, "status": row.status, "error": row.error}
            for row in failures[:20]
        ],
    }


def report_by_endpoint(
    results: Sequence[RequestResult], wall_time_ms: float
) -> Dict[str, Dict]:
    grouped: Dict[str, List[RequestResult]] = {}
    for result in results:
        grouped.setdefault(result.endpoint, []).append(result)
    return {
        name: summarize_results(rows, wall_time_ms)
        for name, rows in sorted(grouped.items())
    }


def print_summary(summary: Dict, per_endpoint: Dict[str, Dict]) -> None:
    print(
        "requests={requests} success={successes} failures={failures} "
        "success_rate={success_rate_percent:.2f}% rps={rps:.2f} "
        "p50={p50:.2f}ms p95={p95:.2f}ms p99={p99:.2f}ms".format(
            p50=summary["latency_ms"]["p50"],
            p95=summary["latency_ms"]["p95"],
            p99=summary["latency_ms"]["p99"],
            **summary,
        )
    )
    print("endpoint | requests | failures | rps | p50 | p95 | p99")
    print("---|---:|---:|---:|---:|---:|---:")
    for name, row in per_endpoint.items():
        latency = row["latency_ms"]
        print(
            "{} | {} | {} | {:.2f} | {:.2f}ms | {:.2f}ms | {:.2f}ms".format(
                name,
                row["requests"],
                row["failures"],
                row["rps"],
                latency["p50"],
                latency["p95"],
                latency["p99"],
            )
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base-url",
        default=os.environ.get("HAORAN_API_BASE_URL", "http://127.0.0.1:9090/api"),
    )
    parser.add_argument(
        "--scenario",
        choices=sorted(DEFAULT_SCENARIOS),
        default="public-read",
    )
    parser.add_argument(
        "--endpoint",
        action="append",
        default=[],
        help="custom GET endpoint; use name=/path to label it",
    )
    parser.add_argument("--auth-all", action="store_true")
    parser.add_argument("--concurrency", type=positive_int, default=10)
    parser.add_argument("--requests", type=positive_int, default=100)
    parser.add_argument("--warmup", type=non_negative_int, default=0)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument(
        "--range",
        dest="range_header",
        type=parse_range_header,
        default="",
        help="bounded byte range for media requests, e.g. bytes=0-65535",
    )
    parser.add_argument(
        "--referer",
        default=os.environ.get("HAORAN_PERF_REFERER", ""),
        help="optional browser-like Referer header for crawler-protected reads",
    )
    parser.add_argument(
        "--read-chunk-size",
        type=non_negative_int,
        default=0,
        help="read responses in fixed chunks; combine with --read-chunk-delay-ms for slow-client tests",
    )
    parser.add_argument(
        "--read-chunk-delay-ms",
        type=non_negative_int,
        default=0,
        help="delay after each response chunk in milliseconds",
    )
    parser.add_argument("--token", default=os.environ.get("HAORAN_TEST_TOKEN", ""))
    parser.add_argument(
        "--username",
        default=os.environ.get("HAORAN_TEST_USERNAME", ""),
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("HAORAN_TEST_PASSWORD", ""),
    )
    parser.add_argument(
        "--output",
        default=os.environ.get("HAORAN_PERF_OUTPUT", ""),
        help="optional JSON output path",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    if args.timeout <= 0:
        raise SystemExit("--timeout must be greater than zero")

    endpoints = (
        [parse_custom_endpoint(value, args.auth_all) for value in args.endpoint]
        if args.endpoint
        else list(DEFAULT_SCENARIOS[args.scenario])
    )
    needs_auth = any(endpoint.requires_auth for endpoint in endpoints)
    token = args.token
    if args.username or args.password:
        if not args.username or not args.password:
            raise SystemExit("--username and --password must be provided together")
        try:
            token = login(args.base_url, args.username, args.password, args.timeout)
        except Exception as error:
            print("login failed: {}".format(error), file=sys.stderr)
            return 2
    if needs_auth and not token:
        print(
            "authenticated scenario requires HAORAN_TEST_TOKEN or username/password",
            file=sys.stderr,
        )
        return 2

    print("Haoran Music read-only performance test")
    print("time: {}".format(datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
    print("base_url: {}".format(args.base_url))
    print(
        "scenario: {} concurrency={} requests={} warmup={} timeout={}s".format(
            args.scenario,
            args.concurrency,
            args.requests,
            args.warmup,
            args.timeout,
        )
    )

    if args.warmup:
        run_load(
            args.base_url,
            endpoints,
            args.concurrency,
            args.warmup,
            args.timeout,
            token,
            args.range_header,
            args.referer,
            args.read_chunk_size,
            args.read_chunk_delay_ms,
        )

    results, wall_time_ms = run_load(
        args.base_url,
        endpoints,
        args.concurrency,
        args.requests,
        args.timeout,
        token,
        args.range_header,
        args.referer,
        args.read_chunk_size,
        args.read_chunk_delay_ms,
    )
    summary = summarize_results(results, wall_time_ms)
    per_endpoint = report_by_endpoint(results, wall_time_ms)
    print_summary(summary, per_endpoint)

    report = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "base_url": args.base_url,
        "scenario": "custom" if args.endpoint else args.scenario,
        "concurrency": args.concurrency,
        "request_count": args.requests,
        "warmup_count": args.warmup,
        "timeout_seconds": args.timeout,
        "range": args.range_header,
        "referer": args.referer,
        "read_chunk_size": args.read_chunk_size,
        "read_chunk_delay_ms": args.read_chunk_delay_ms,
        "endpoints": [asdict(endpoint) for endpoint in endpoints],
        "summary": summary,
        "per_endpoint": per_endpoint,
    }
    if args.output:
        output_dir = os.path.dirname(os.path.abspath(args.output))
        os.makedirs(output_dir, exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print("json report: {}".format(args.output))
    return 0 if summary["failures"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
