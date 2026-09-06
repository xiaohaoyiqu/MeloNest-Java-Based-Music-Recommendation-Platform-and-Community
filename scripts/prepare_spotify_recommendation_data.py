#!/usr/bin/env python3
\
\
\
\
   

import argparse
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


def normalize(value):
    return re.sub(r"\s+", " ", str(value or "").strip()).casefold()


def decode_hex(value):
    try:
        return bytes.fromhex(str(value or "")).decode("utf-8")
    except (TypeError, ValueError, UnicodeDecodeError):
        return ""


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_catalog(catalog_path):
    pairs = defaultdict(list)
    total = 0
    with Path(catalog_path).open(encoding="utf-8", newline="") as stream:
        for row in csv.DictReader(stream, delimiter="\t"):
            song_id = str(row.get("song_id") or "").strip()
            title = decode_hex(row.get("song_name_hex")) or row.get("song_name") or row.get("name")
            artist = decode_hex(row.get("artist_names_hex")) or row.get("artist_names") or row.get("artist")
            key = (normalize(title), normalize(artist))
            if song_id and all(key):
                pairs[key].append(song_id)
                total += 1
    unique = {key: values[0] for key, values in pairs.items() if len(values) == 1}
    ambiguous = sum(1 for values in pairs.values() if len(values) > 1)
    return total, unique, ambiguous


def write_csv(path, fields, rows):
    with Path(path).open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def prepare(spotify_json_path, playable_catalog_path, output_dir, minimum_interactions=1):
                                                                                
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    catalog_total, unique_catalog, ambiguous_catalog = load_catalog(playable_catalog_path)
    payload = json.loads(Path(spotify_json_path).read_text(encoding="utf-8"))
    raw = []
    external_to_site = {}
    ambiguous_external = set()
    for playlist in payload.get("playlists") or []:
        user_id = str(playlist.get("pid") or "").strip()
        if not user_id:
            continue
        for track in playlist.get("tracks") or []:
            external_id = str(track.get("track_uri") or "").strip()
            key = (normalize(track.get("track_name")), normalize(track.get("artist_name")))
            site_song_id = unique_catalog.get(key)
            if not external_id or not site_song_id:
                continue
            previous = external_to_site.get(external_id)
            if previous and previous != site_song_id:
                ambiguous_external.add(external_id)
                continue
            external_to_site[external_id] = site_song_id
            raw.append((user_id, external_id))
    counts = Counter(external_id for _, external_id in raw if external_id not in ambiguous_external)
    threshold = max(1, int(minimum_interactions))
    interactions = [
        {"userId": user_id, "id": external_id}
        for user_id, external_id in raw
        if external_id not in ambiguous_external and counts[external_id] >= threshold
    ]
    used_external = {row["id"] for row in interactions}
    mapping = [
        {"external_song_id": external_id, "song_id": external_to_site[external_id]}
        for external_id in sorted(used_external)
    ]
    interactions_path = output / "spotify_interactions.csv"
    mapping_path = output / "spotify_catalog_map.csv"
    write_csv(interactions_path, ["userId", "id"], interactions)
    write_csv(mapping_path, ["external_song_id", "song_id"], mapping)
    manifest = {
        "inputs": {
            "spotify_json": str(Path(spotify_json_path)),
            "playable_catalog": str(Path(playable_catalog_path)),
        },
        "outputs": {
            "interactions": {"path": interactions_path.name, "sha256": sha256_file(interactions_path)},
            "catalog_map": {"path": mapping_path.name, "sha256": sha256_file(mapping_path)},
        },
        "stats": {
            "playable_catalog_song_count": catalog_total,
            "ambiguous_catalog_pair_count": ambiguous_catalog,
            "ambiguous_external_song_count": len(ambiguous_external),
            "mapped_external_song_count": len(mapping),
            "mapped_interaction_count": len(interactions),
            "mapped_playlist_count": len({row["userId"] for row in interactions}),
            "minimum_interactions": threshold,
        },
    }
    manifest_path = output / "spotify_training_data_manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv=None):
    parser = argparse.ArgumentParser(description="Prepare uniquely mapped Spotify training interactions.")
    parser.add_argument("--spotify-json", required=True)
    parser.add_argument("--playable-catalog", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--minimum-interactions", type=int, default=1)
    args = parser.parse_args(argv)
    manifest = prepare(args.spotify_json, args.playable_catalog, args.output_dir, args.minimum_interactions)
    print(json.dumps(manifest["stats"], ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
