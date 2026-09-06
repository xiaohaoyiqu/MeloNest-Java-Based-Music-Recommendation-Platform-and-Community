#!/usr/bin/env python3
                       
\
\
\
\
\
   

import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path


def normalize_text(value):
    normalized = unicodedata.normalize("NFKC", value or "").strip().casefold()
    return re.sub(r"\s+", " ", normalized)


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def decode_hex_text(value, field_name):
    try:
        return bytes.fromhex(value or "").decode("utf-8")
    except (ValueError, UnicodeDecodeError) as exc:
        raise ValueError("invalid UTF-8 hex in {}".format(field_name)) from exc


def read_playable_catalog(path):
    pair_to_song_ids = defaultdict(set)
    playable_song_ids = set()
    with Path(path).open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        required = {"song_id", "song_name_hex", "artist_names_hex"}
        if not reader.fieldnames or not required.issubset(set(reader.fieldnames)):
            raise ValueError("playable catalog must contain {}".format(",".join(sorted(required))))
        for row in reader:
            song_id = int(row["song_id"])
            if song_id <= 0:
                raise ValueError("playable catalog song_id must be positive")
            pair = (
                normalize_text(decode_hex_text(row["song_name_hex"], "song_name_hex")),
                normalize_text(decode_hex_text(row["artist_names_hex"], "artist_names_hex")),
            )
            if not all(pair):
                continue
            playable_song_ids.add(song_id)
            pair_to_song_ids[pair].add(song_id)
    if not playable_song_ids:
        raise ValueError("playable catalog is empty")
    return pair_to_song_ids, playable_song_ids


def write_csv(path, fieldnames, rows):
    path = Path(path)
    temporary_path = path.with_suffix(path.suffix + ".tmp")
    with temporary_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    temporary_path.replace(path)


def prepare(spotify_json_path, catalog_path, output_dir, minimum_interactions=20):
    spotify_json_path = Path(spotify_json_path)
    catalog_path = Path(catalog_path)
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    pair_to_song_ids, playable_song_ids = read_playable_catalog(catalog_path)
    with spotify_json_path.open("r", encoding="utf-8") as stream:
        payload = json.load(stream)
    playlists = payload.get("playlists")
    if not isinstance(playlists, list):
        raise ValueError("Spotify slice must contain a playlists array")

    candidate_interactions = []
    external_to_site_ids = defaultdict(set)
    total_track_interactions = 0
    for playlist in playlists:
        playlist_id = playlist.get("pid")
        if playlist_id is None:
            continue
        for track in playlist.get("tracks") or []:
            total_track_interactions += 1
            external_song_id = str(track.get("track_uri") or "").strip()
            pair = (
                normalize_text(track.get("track_name")),
                normalize_text(track.get("artist_name")),
            )
            site_ids = pair_to_song_ids.get(pair, set())
            if not external_song_id or len(site_ids) != 1:
                continue
            site_song_id = next(iter(site_ids))
            external_to_site_ids[external_song_id].add(site_song_id)
            candidate_interactions.append((str(playlist_id), external_song_id, site_song_id))

    unambiguous_mapping = {
        external_id: next(iter(site_ids))
        for external_id, site_ids in external_to_site_ids.items()
        if len(site_ids) == 1
    }
    interactions = sorted({
        (playlist_id, external_id)
        for playlist_id, external_id, site_song_id in candidate_interactions
        if unambiguous_mapping.get(external_id) == site_song_id
    })
    if len(interactions) < minimum_interactions:
        raise ValueError(
            "mapped interactions {} are below minimum {}".format(
                len(interactions), minimum_interactions
            )
        )

    interactions_path = output_dir / "spotify_interactions.csv"
    mapping_path = output_dir / "spotify_catalog_map.csv"
    playable_path = output_dir / "playable_catalog.csv"
    manifest_path = output_dir / "spotify_training_data_manifest.json"

    write_csv(
        interactions_path,
        ["userId", "id"],
        ({"userId": user_id, "id": external_id} for user_id, external_id in interactions),
    )
    write_csv(
        mapping_path,
        ["external_song_id", "song_id"],
        (
            {"external_song_id": external_id, "song_id": site_song_id}
            for external_id, site_song_id in sorted(unambiguous_mapping.items())
        ),
    )
    write_csv(
        playable_path,
        ["song_id"],
        ({"song_id": song_id} for song_id in sorted(playable_song_ids)),
    )

    matched_site_song_ids = {
        unambiguous_mapping[external_id] for _, external_id in interactions
    }
    manifest = {
        "format_version": 1,
        "mapping_strategy": "unique_nfkc_casefold_track_name_and_artist_name",
        "inputs": {
            "spotify_json": {
                "name": spotify_json_path.name,
                "sha256": sha256_file(spotify_json_path),
            },
            "playable_catalog": {
                "name": catalog_path.name,
                "sha256": sha256_file(catalog_path),
            },
        },
        "stats": {
            "playlist_count": len(playlists),
            "track_interaction_count": total_track_interactions,
            "playable_catalog_song_count": len(playable_song_ids),
            "ambiguous_catalog_pair_count": sum(
                1 for site_ids in pair_to_song_ids.values() if len(site_ids) > 1
            ),
            "ambiguous_external_song_count": sum(
                1 for site_ids in external_to_site_ids.values() if len(site_ids) > 1
            ),
            "mapped_interaction_count": len(interactions),
            "mapped_playlist_count": len({user_id for user_id, _ in interactions}),
            "mapped_external_song_count": len({external_id for _, external_id in interactions}),
            "mapped_site_song_count": len(matched_site_song_ids),
        },
    }
    manifest["outputs"] = {
        path.name: {"sha256": sha256_file(path)}
        for path in (interactions_path, mapping_path, playable_path)
    }
    temporary_manifest = manifest_path.with_suffix(".json.tmp")
    temporary_manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_manifest.replace(manifest_path)
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--spotify-json", required=True)
    parser.add_argument("--catalog", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--minimum-interactions", type=int, default=20)
    args = parser.parse_args()
    manifest = prepare(
        args.spotify_json,
        args.catalog,
        args.output_dir,
        args.minimum_interactions,
    )
    print(json.dumps(manifest, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
