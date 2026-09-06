#!/usr/bin/env python3
                       
                                                                          

import csv
import hashlib
import json
import os
import tempfile
from datetime import datetime

MYSQL_USER_ID_OFFSET = 10000000
MYSQL_SONG_ID_OFFSET = 100000000


def split_csv_tokens(value):
                                                                          
    if value is None:
        return set()
    return {token.strip() for token in str(value).split(",") if token.strip()}


def offset_mysql_id(value, offset):
    return int(value) + int(offset)


def restore_mysql_id(value, offset):
    value = int(value)
    offset = int(offset)
    if value < offset:
        return None
    return value - offset


def is_offset_mysql_id(value, offset):
    return restore_mysql_id(value, offset) is not None


MODEL_SCHEMA_VERSION = 2


def build_model_metadata(
        model_type,
        source_counts=None,
        model_params=None,
        metrics=None,
        output_paths=None,
        training_window=None,
        catalog_version=None,
        model_version=None,
        id_mapping=None,
        minimum_backend_version="1.0.0"):
    created_at = datetime.utcnow().replace(microsecond=0).isoformat() + "Z"
    return {
        "schema_version": MODEL_SCHEMA_VERSION,
        "model_type": model_type,
        "model_version": model_version or created_at,
        "created_at": created_at,
        "training_window": training_window or {},
        "catalog_version": catalog_version or os.environ.get("HAORAN_CATALOG_VERSION", "unknown"),
        "minimum_backend_version": minimum_backend_version,
        "source_counts": source_counts or {},
        "model_params": model_params or {},
        "metrics": metrics or {},
        "output_paths": output_paths or {},
        "id_mapping": id_mapping or {
            "mysql_user_offset": MYSQL_USER_ID_OFFSET,
            "mysql_song_offset": MYSQL_SONG_ID_OFFSET,
        },
    }


def canonical_json_bytes(value):
                                                                         
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def sha256_bytes(value):
    return hashlib.sha256(value).hexdigest()


def build_catalog_version(song_ids):
    normalized = sorted({str(song_id).strip() for song_id in song_ids if str(song_id).strip()})
    if not normalized:
        raise ValueError("catalog version requires at least one song id")
    return "sha256:" + sha256_bytes("\n".join(normalized).encode("utf-8"))


def _atomic_write_bytes(path, payload):
    directory = os.path.dirname(os.path.abspath(path))
    os.makedirs(directory, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(prefix=".model-", suffix=".tmp", dir=directory, delete=False)
    temporary_path = handle.name
    try:
        with handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary_path, path)
    finally:
        if os.path.exists(temporary_path):
            os.unlink(temporary_path)


def write_model_artifact(path, model, coverage=None):
                                                                             
    metadata = model.get("metadata")
    if not isinstance(metadata, dict) or metadata.get("schema_version") != MODEL_SCHEMA_VERSION:
        raise ValueError("model metadata must use the current schema")
    catalog_version = str(metadata.get("catalog_version") or "").strip()
    if not catalog_version or catalog_version.lower() == "unknown":
        raise ValueError("model metadata must contain a catalog version")

    model_payload = canonical_json_bytes(model)
    model_sha256 = sha256_bytes(model_payload)
    manifest = {
        "schema_version": MODEL_SCHEMA_VERSION,
        "model_type": metadata.get("model_type"),
        "model_version": metadata.get("model_version"),
        "catalog_version": catalog_version,
        "artifact_file": os.path.basename(path),
        "artifact_size": len(model_payload),
        "artifact_sha256": model_sha256,
        "coverage": coverage or {},
    }

    _atomic_write_bytes(path, model_payload)
    _atomic_write_bytes(path + ".manifest.json", canonical_json_bytes(manifest))
    return manifest


def verify_model_artifact(path, expected_model_type=None):
                                                                            
    with open(path, "rb") as stream:
        model_payload = stream.read()
    with open(path + ".manifest.json", "r", encoding="utf-8") as stream:
        manifest = json.load(stream)
    model = json.loads(model_payload.decode("utf-8"))
    metadata = model.get("metadata", {})

    if manifest.get("schema_version") != MODEL_SCHEMA_VERSION:
        raise ValueError("unsupported manifest schema")
    if metadata.get("schema_version") != MODEL_SCHEMA_VERSION:
        raise ValueError("unsupported model schema")
    if manifest.get("artifact_sha256") != sha256_bytes(model_payload):
        raise ValueError("model checksum mismatch")
    if manifest.get("artifact_size") != len(model_payload):
        raise ValueError("model size mismatch")
    if manifest.get("artifact_file") != os.path.basename(path):
        raise ValueError("artifact filename mismatch")
    if manifest.get("model_type") != metadata.get("model_type"):
        raise ValueError("manifest model type mismatch")
    if manifest.get("catalog_version") != metadata.get("catalog_version"):
        raise ValueError("manifest catalog version mismatch")
    if manifest.get("model_version") != metadata.get("model_version"):
        raise ValueError("manifest model version mismatch")
    catalog_version = str(metadata.get("catalog_version") or "").strip()
    if not catalog_version or catalog_version.lower() == "unknown":
        raise ValueError("catalog version is missing")
    if expected_model_type and manifest.get("model_type") != expected_model_type:
        raise ValueError("unexpected model type")
    return model, manifest


def load_catalog_mapping(path, source_column, song_column="song_id"):
                                                                          
    if not path:
        return {}
    mapping = {}
    with open(path, "r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        required = {source_column, song_column}
        if not required.issubset(set(reader.fieldnames or [])):
            raise ValueError("catalog mapping must contain {}".format(sorted(required)))
        for row in reader:
            source_id = str(row.get(source_column, "")).strip()
            site_song_id = str(row.get(song_column, "")).strip()
            if not source_id or not site_song_id:
                continue
            if not site_song_id.isdigit() or int(site_song_id) <= 0:
                raise ValueError("site song ids must be positive integers")
            previous = mapping.get(source_id)
            if previous and previous != site_song_id:
                raise ValueError("one source id cannot map to multiple site songs")
            mapping[source_id] = site_song_id
    return mapping


def load_playable_song_ids(path, song_column="song_id"):
                                                                                  
    if not path:
        return set()
    song_ids = set()
    with open(path, "r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if song_column not in set(reader.fieldnames or []):
            raise ValueError("playable catalog must contain {}".format(song_column))
        for row in reader:
            song_id = str(row.get(song_column, "")).strip()
            if not song_id:
                continue
            if not song_id.isdigit() or int(song_id) <= 0:
                raise ValueError("playable catalog song ids must be positive integers")
            song_ids.add(song_id)
    return song_ids
