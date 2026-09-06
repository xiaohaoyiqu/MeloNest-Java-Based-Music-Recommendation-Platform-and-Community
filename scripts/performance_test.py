#!/usr/bin/env python3
\
\
\
\
   

import argparse
import json
import math
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Endpoint:
    name: str
    path: str
    requires_auth: bool = False


@dataclass
class RequestResult:
    endpoint: str
    status: int
    latency_ms: float
    error: str = ""
    response_bytes: int = 0
    content_range: str = ""
    passed: object = None

    def __post_init__(self):
        if self.passed is None:
            self.passed = 200 <= int(self.status) < 400 and not self.error


def percentile(values, fraction):
    if not values:
        return 0.0
    ordered = sorted(float(value) for value in values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * fraction) - 1))
    return ordered[index]


def extract_token(payload):
    if not isinstance(payload, dict):
        return ""
    for candidate in (payload, payload.get("data"), payload.get("result")):
        if isinstance(candidate, dict) and candidate.get("token"):
            return str(candidate["token"])
    return ""


def parse_range_header(value):
    if not value or not value.startswith("bytes="):
        raise argparse.ArgumentTypeError("range must use bytes=start-end")
    try:
        start, end = value[6:].split("-", 1)
        start_number, end_number = int(start), int(end)
    except ValueError as error:
        raise argparse.ArgumentTypeError("range must contain integer bounds") from error
    if start_number < 0 or end_number < start_number:
        raise argparse.ArgumentTypeError("range bounds are invalid")
    return "bytes={}-{}".format(start_number, end_number)


def parse_custom_endpoint(value, auth_all=False):
    if "=" not in value:
        raise argparse.ArgumentTypeError("endpoint must use name=/path")
    name, path = value.split("=", 1)
    if not name.strip() or not path.startswith("/") or path.startswith("//"):
        raise argparse.ArgumentTypeError("endpoint path must be an absolute local path")
    return Endpoint(name.strip(), path, bool(auth_all))


def request_once(base_url, endpoint, token, timeout_seconds, range_header=None, referer=None, read_chunk_size=65536, read_chunk_delay_ms=0):
    parsed = urlparse(base_url)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ValueError("base_url must be an absolute HTTP(S) URL")
    headers = {"Accept": "application/json, */*"}
    if token:
        headers["Authorization"] = "Bearer {}".format(token)
    if range_header:
        headers["Range"] = parse_range_header(range_header)
    if referer:
        headers["Referer"] = referer
    request = Request(urljoin(base_url.rstrip("/") + "/", endpoint.path.lstrip("/")), headers=headers, method="GET")
    started = time.perf_counter()
    response_bytes = 0
    content_range = ""
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            status = int(response.getcode())
            content_range = response.headers.get("Content-Range", "")
            while True:
                chunk = response.read(read_chunk_size)
                if not chunk:
                    break
                response_bytes += len(chunk)
                if read_chunk_delay_ms:
                    time.sleep(float(read_chunk_delay_ms) / 1000.0)
            latency_ms = (time.perf_counter() - started) * 1000.0
            return RequestResult(endpoint.name, status, latency_ms, response_bytes=response_bytes, content_range=content_range)
    except HTTPError as error:
        return RequestResult(endpoint.name, error.code, (time.perf_counter() - started) * 1000.0, "http {}".format(error.code))
    except (URLError, OSError) as error:
        return RequestResult(endpoint.name, 0, (time.perf_counter() - started) * 1000.0, type(error).__name__)


def summarize_results(results, duration_seconds):
    statuses = {}
    for result in results:
        key = str(result.status)
        statuses[key] = statuses.get(key, 0) + 1
    successes = sum(1 for result in results if result.passed)
    requests = len(results)
    latencies = [result.latency_ms for result in results]
    return {
        "requests": requests,
        "successes": successes,
        "failures": requests - successes,
        "success_rate_percent": round((successes * 100.0 / requests) if requests else 0.0, 2),
        "statuses": statuses,
        "latency_ms": {
            "p50": percentile(latencies, 0.50),
            "p90": percentile(latencies, 0.90),
            "p95": percentile(latencies, 0.95),
            "p99": percentile(latencies, 0.99),
        },
        "duration_seconds": duration_seconds,
        "requests_per_second": round(requests / duration_seconds, 2) if duration_seconds > 0 else 0.0,
    }


def result_payload(result):
                                                                              
    return {
        "endpoint": result.endpoint,
        "status": result.status,
        "latency_ms": round(result.latency_ms, 3),
        "response_bytes": result.response_bytes,
        "content_range": result.content_range,
        "error": result.error,
        "passed": bool(result.passed),
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Run a bounded HTTP read test against an authorized target.")
    parser.add_argument("--base-url", default="http://127.0.0.1:9090/api")
    parser.add_argument("--scenario", choices=("public-read", "authenticated-read"), default="public-read")
    parser.add_argument("--token", default="")
    parser.add_argument("--endpoint", action="append", default=[])
    parser.add_argument("--requests", type=int, default=1)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--range", dest="range_header", type=parse_range_header)
    parser.add_argument("--referer")
    parser.add_argument("--output", help="write the aggregate and per-request metrics as JSON")
    args = parser.parse_args(argv)
    if args.scenario == "authenticated-read" and not args.token:
        print("performance_test.py: authenticated-read requires --token from an authorized test account", file=sys.stderr)
        return 2
    if args.requests < 1 or args.requests > 1000:
        parser.error("--requests must be between 1 and 1000")
    if args.concurrency < 1 or args.concurrency > 500:
        parser.error("--concurrency must be between 1 and 500")
    endpoints = [parse_custom_endpoint(value, args.scenario == "authenticated-read") for value in args.endpoint]
    if not endpoints:
        endpoints = [Endpoint("hot", "/song/hot?limit=1", False)]
    started = time.perf_counter()

    def run_request(index):
        return request_once(
            args.base_url,
            endpoints[index % len(endpoints)],
            args.token,
            args.timeout,
            args.range_header,
            args.referer,
        )

    with ThreadPoolExecutor(max_workers=min(args.concurrency, args.requests)) as executor:
        results = list(executor.map(run_request, range(args.requests)))
    summary = summarize_results(results, time.perf_counter() - started)
    report = {
        "scenario": args.scenario,
        "base_url": args.base_url,
        "requests": args.requests,
        "concurrency": args.concurrency,
        "timeout_seconds": args.timeout,
        "summary": summary,
        "samples": [result_payload(result) for result in results],
    }
    if args.output:
        with open(args.output, "w", encoding="utf-8") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
    print(json.dumps(report, ensure_ascii=False))
    return 0 if all(result.passed for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
