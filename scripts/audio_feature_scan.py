#!/usr/bin/env python3
\
\
\
\
   

import argparse
import csv
import json
import subprocess
import urllib.parse
from pathlib import Path


OUTPUT_FIELDS = [
    "song_id",
    "status",
    "source_path",
    "tempo",
    "audio_key",
    "mode",
    "duration_seconds",
    "confidence",
    "error",
]

SOURCE_FIELDS = ("url_lossless", "url_standard", "url_high", "url", "file_path")


def safe_media_path(value, media_root):
                                                                                 
    if not value:
        return None
    parsed = urllib.parse.urlparse(str(value))
    if parsed.scheme or parsed.netloc:
        return None
    decoded = urllib.parse.unquote(parsed.path).replace("\\", "/")
    relative = decoded.lstrip("/")
    if not relative or relative.startswith("../"):
        return None
    root = Path(media_root).resolve()
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        return None
    return candidate


def _legacy_candidates(path):
    stem = path.stem
    if "-" not in stem:
        return []
    artist, title = (part.strip() for part in stem.split("-", 1))
    if not artist or not title:
        return []
    return [path.with_name("{} - {}{}".format(title, artist, path.suffix))]


def choose_source(row, media_root):
                                                                                    
    for field in SOURCE_FIELDS:
        candidate = safe_media_path(row.get(field), media_root)
        if candidate and candidate.is_file():
            return candidate
        if candidate:
            for legacy in _legacy_candidates(candidate):
                if legacy.is_file():
                    return legacy.resolve()
    return None


def probe_features(source, ffprobe="ffprobe"):
                                                                                   
    command = [
        ffprobe,
        "-v", "error",
        "-show_entries", "format=duration:format_tags=TBPM,initial_key",
        "-of", "json",
        str(source),
    ]
    completed = subprocess.run(command, check=True, capture_output=True, text=True)
    payload = json.loads(completed.stdout or "{}")
    info = payload.get("format") or {}
    tags = {str(key).lower(): value for key, value in (info.get("tags") or {}).items()}
    return {
        "duration_seconds": info.get("duration") or "",
        "tempo": tags.get("tbpm", ""),
        "audio_key": tags.get("initial_key", ""),
        "mode": "",
        "confidence": "tag",
    }


def scan_rows(catalog_path, media_root, ffprobe="ffprobe"):
    with Path(catalog_path).open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        for row in reader:
            song_id = str(row.get("song_id") or row.get("id") or "").strip()
            source = choose_source(row, media_root)
            result = {field: "" for field in OUTPUT_FIELDS}
            result["song_id"] = song_id
            if not song_id or source is None:
                result.update(status="missing_source", error="approved local source not found")
                yield result
                continue
            result["source_path"] = str(source)
            try:
                result.update(probe_features(source, ffprobe))
                result["status"] = "ready" if result["tempo"] else "low_confidence"
            except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
                result.update(status="probe_failed", error=type(error).__name__)
            yield result


def write_scan(catalog_path, media_root, output_path, ffprobe="ffprobe"):
    rows = list(scan_rows(catalog_path, media_root, ffprobe))
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=OUTPUT_FIELDS, delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)
    return rows


def main(argv=None):
    parser = argparse.ArgumentParser(description="Scan approved local audio sources.")
    parser.add_argument("--catalog", required=True, help="TSV catalog containing song IDs and local media paths")
    parser.add_argument("--media-root", required=True, help="Root directory allowed for media reads")
    parser.add_argument("--output", required=True, help="TSV output path")
    parser.add_argument("--ffprobe", default="ffprobe")
    args = parser.parse_args(argv)
    rows = write_scan(args.catalog, args.media_root, args.output, args.ffprobe)
    print(json.dumps({"rows": len(rows), "ready": sum(row["status"] == "ready" for row in rows)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
