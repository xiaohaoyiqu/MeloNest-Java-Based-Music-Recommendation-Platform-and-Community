#!/usr/bin/env python3
                                                                               

import argparse
import csv
import math
import os
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import unquote, urlparse


KEY_TO_NUMBER = {
    "C": 0, "B#": 0,
    "C#": 1, "Db": 1,
    "D": 2,
    "D#": 3, "Eb": 3,
    "E": 4, "Fb": 4,
    "F": 5, "E#": 5,
    "F#": 6, "Gb": 6,
    "G": 7,
    "G#": 8, "Ab": 8,
    "A": 9,
    "A#": 10, "Bb": 10,
    "B": 11, "Cb": 11,
}
OUTPUT_FIELDS = [
    "song_id", "status", "tempo", "audio_key", "mode",
    "tempo_confidence", "key_strength", "error",
]


def safe_media_path(url, media_root):
                                                                                   
    if not url:
        return None
    parsed = urlparse(url)
    raw_path = parsed.path if parsed.scheme else url.split("?", 1)[0]
    relative = unquote(raw_path).lstrip("/").replace("\\", "/")
    candidate = (Path(media_root).resolve() / relative).resolve()
    root = Path(media_root).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        return None
    return candidate


def choose_source(row, media_root):
    for field in ("url_hires", "url_lossless", "url_high", "url_standard"):
        candidate = safe_media_path(row.get(field, ""), media_root)
        if candidate and candidate.is_file():
            return candidate
                                                                              
                                                                              
                                                                        
        if candidate and "-" in candidate.stem:
            artist, title = candidate.stem.split("-", 1)
            swapped = candidate.with_name("%s - %s%s" % (
                title.strip(), artist.strip(), candidate.suffix
            ))
            if swapped.is_file():
                return swapped
    return None


def normalized_scale(scale):
    value = (scale or "").lower()
    if value == "major":
        return 1
    if value == "minor":
        return 0
    return None


def analyze_row(row, settings):
    song_id = row["song_id"]
    result = {field: "" for field in OUTPUT_FIELDS}
    result.update({"song_id": song_id, "status": "failed"})
    source = choose_source(row, settings["media_root"])
    if not source:
        result.update(status="missing_file", error="catalog audio is not present on node3")
        return result
    duration = float(row.get("duration") or 0)
    start_seconds = 0 if duration and duration < 75 else settings["start_seconds"]
    sample_seconds = duration if 0 < duration < settings["sample_seconds"] else settings["sample_seconds"]
    try:
        import essentia.standard as es

        with tempfile.TemporaryDirectory(prefix="haoran-audio-feature-") as temp_dir:
            wav_path = os.path.join(temp_dir, "sample.wav")
            command = [
                settings["ffmpeg"], "-nostdin", "-hide_banner", "-loglevel", "error",
                "-ss", str(start_seconds), "-t", str(max(10, sample_seconds)),
                "-i", str(source), "-map", "0:a:0", "-ac", "1", "-ar", "44100",
                "-c:a", "pcm_s16le", "-y", wav_path,
            ]
            completed = subprocess.run(
                command, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                timeout=settings["timeout_seconds"], check=False,
            )
            if completed.returncode != 0 or not os.path.isfile(wav_path):
                detail = completed.stderr.decode("utf-8", "replace").strip().splitlines()
                raise RuntimeError(detail[-1][:240] if detail else "ffmpeg decode failed")

            audio = es.MonoLoader(filename=wav_path, sampleRate=44100)()
            if len(audio) < 44100 * 10:
                raise RuntimeError("audio sample is shorter than 10 seconds")
            bpm, _, tempo_confidence, _, _ = es.RhythmExtractor2013(
                method="multifeature", minTempo=45, maxTempo=220
            )(audio)
            key_name, scale, key_strength = es.KeyExtractor(profileType="edma")(audio)

        key_number = KEY_TO_NUMBER.get(str(key_name))
        mode = normalized_scale(str(scale))
        tempo = float(bpm)
        tempo_confidence = float(tempo_confidence)
        key_strength = float(key_strength)
        if not math.isfinite(tempo) or not 40 <= tempo <= 240 or key_number is None or mode is None:
            raise RuntimeError("analyzer returned an invalid tempo or key")

        result.update(
            tempo="%.2f" % tempo,
            audio_key=str(key_number),
            mode=str(mode),
            tempo_confidence="%.4f" % tempo_confidence,
            key_strength="%.4f" % key_strength,
        )
        if (tempo_confidence < settings["min_tempo_confidence"]
                or key_strength < settings["min_key_strength"]):
            result.update(status="low_confidence", error="feature confidence is below write threshold")
        else:
            result["status"] = "ready"
    except Exception as exc:
        result["error"] = ("%s: %s" % (exc.__class__.__name__, exc))[:300]
    return result


def read_catalog(path, limit):
    with open(path, "r", encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle, delimiter="\t"))
    return rows[:limit] if limit else rows


def write_results(path, results):
    with open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_FIELDS, delimiter="\t", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(sorted(results, key=lambda item: int(item["song_id"])))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--media-root", default="/sdb1/haoranmusicData/Datas")
    parser.add_argument("--ffmpeg", default="/usr/local/soft/ffmpeg-6.1.1/bin/ffmpeg")
    parser.add_argument("--workers", type=int, default=2, choices=range(1, 5))
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--start-seconds", type=int, default=20)
    parser.add_argument("--sample-seconds", type=int, default=120)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--min-tempo-confidence", type=float, default=0.15)
    parser.add_argument("--min-key-strength", type=float, default=0.35)
    args = parser.parse_args()

    if not os.path.isfile(args.ffmpeg):
        parser.error("FFmpeg is not available at %s" % args.ffmpeg)
    rows = read_catalog(args.catalog, args.limit)
    settings = vars(args)
    results = []
    with ProcessPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(analyze_row, row, settings) for row in rows]
        for index, future in enumerate(as_completed(futures), 1):
            result = future.result()
            results.append(result)
            print("event=audio_feature_scan_progress completed=%d total=%d songId=%s status=%s" % (
                index, len(rows), result["song_id"], result["status"]
            ), flush=True)
    write_results(args.output, results)
    counts = {}
    for result in results:
        counts[result["status"]] = counts.get(result["status"], 0) + 1
    print("event=audio_feature_scan_completed total=%d counts=%s resultFile=%s" % (
        len(results), counts, os.path.basename(args.output)
    ))
    return 0 if counts.get("ready", 0) else 1


if __name__ == "__main__":
    sys.exit(main())
