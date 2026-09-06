\
\
\
\
\
\
\
\
\
\
   

import argparse
import csv
import os
import sqlite3
import sys
from pathlib import Path
from typing import Dict, Iterable, Optional

        
sys.path.insert(0, str(Path(__file__).parent.parent))


DEFAULT_MAX_ROWS = int(os.environ.get("HAORAN_IMPORT_MAX_ROWS", "1000"))


def _resolve_path(env_name: str, defaults: Iterable[str], suffix: Optional[str] = None) -> Path:
    configured = os.environ.get(env_name)
    candidates = [Path(configured)] if configured else [Path(item) for item in defaults]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
        if candidate.is_dir():
            if suffix:
                matches = sorted(candidate.rglob(suffix))
                if matches:
                    return matches[0]
    searched = ", ".join(str(item) for item in candidates)
    raise FileNotFoundError("数据集不存在，请通过 {} 指定有效路径。已检查: {}".format(env_name, searched))


def _database_connection():
    password = os.environ.get("HAORAN_DB_PASSWORD", "")
    if not password:
        raise RuntimeError("请通过 HAORAN_DB_PASSWORD 提供数据库密码")
    import mysql.connector

    return mysql.connector.connect(
        host=os.environ.get("HAORAN_DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("HAORAN_DB_PORT", "3306")),
        user=os.environ.get("HAORAN_DB_USER", "hdfs"),
        password=password,
        database=os.environ.get("HAORAN_DB_NAME", "haoranmusic_bus"),
    )


def _get_or_create_artist(conn, name: str) -> Optional[int]:
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id FROM artist WHERE name = %s LIMIT 1", (name,))
        row = cursor.fetchone()
        if row:
            return row[0]
        cursor.execute(
            "INSERT INTO artist (name, type, status, create_time) VALUES (%s, '歌手', 1, NOW())",
            (name,),
        )
        conn.commit()
        return cursor.lastrowid
    finally:
        cursor.close()


def _get_or_create_song(conn, name: str, artist_id: int, artist_name: str,
                        album_name: str, duration_seconds: int) -> Optional[int]:
    cursor = conn.cursor()
    try:
        cursor.execute(
            "SELECT id FROM song WHERE name = %s AND artist_id = %s LIMIT 1",
            (name, artist_id),
        )
        row = cursor.fetchone()
        if row:
            return row[0]
        cursor.execute(
            """
            INSERT INTO song (name, artist_id, artist_names, album_name, duration,
                              main_genre, language, status, create_time)
            VALUES (%s, %s, %s, %s, %s, '流行', '英文', 1, NOW())
            """,
            (name, artist_id, artist_name, album_name, max(duration_seconds, 0)),
        )
        conn.commit()
        return cursor.lastrowid
    finally:
        cursor.close()


def _import_rows(rows, conn, source: str, max_rows: int) -> Dict[str, int]:
    imported = 0
    skipped = 0
    for row in rows:
        if max_rows > 0 and imported + skipped >= max_rows:
            break
        name = str(row.get("name") or row.get("title") or "").strip()
        artist_name = str(row.get("artist") or row.get("artist_name") or "").strip()
        if not name or not artist_name:
            skipped += 1
            continue
        artist_id = _get_or_create_artist(conn, artist_name)
        if not artist_id:
            skipped += 1
            continue
        duration = row.get("duration") or row.get("duration_sec") or row.get("duration_seconds") or 0
        try:
            duration_seconds = int(float(duration))
        except (TypeError, ValueError):
            duration_seconds = 0
        if row.get("duration_ms"):
            try:
                duration_seconds = int(float(row["duration_ms"]) / 1000)
            except (TypeError, ValueError):
                duration_seconds = 0
        song_id = _get_or_create_song(
            conn,
            name,
            artist_id,
            artist_name,
            str(row.get("album") or row.get("release") or "").strip(),
            duration_seconds,
        )
        if song_id:
            imported += 1
        else:
            skipped += 1
    print("{}导入完成: imported={}, skipped={}".format(source, imported, skipped))
    return {"imported": imported, "skipped": skipped}


def import_spotify():
                                       
    print("\n=== 导入Spotify数据集 ===")
    import import_spotify_dataset
    return import_spotify_dataset.main()


def import_million_song(max_rows: int = DEFAULT_MAX_ROWS):
                                  
    print("\n=== 导入Million Song数据集 ===")
    metadata_path = _resolve_path(
        "HAORAN_MILLION_SONG_PATH",
        (
            "D:/music_dataset/MillionSongSubset/track_metadata.db",
            "D:/music_dataset/millionsongsubset/track_metadata.db",
        ),
        "track_metadata.db",
    )
    source = sqlite3.connect(str(metadata_path))
    destination = _database_connection()
    try:
        rows = (
            {
                "name": row[1],
                "artist": row[2],
                "release": row[3],
                "duration": row[4],
            }
            for row in source.execute(
                "SELECT track_id, title, artist_name, release, duration "
                "FROM songs WHERE title IS NOT NULL AND artist_name IS NOT NULL"
            )
        )
        return _import_rows(rows, destination, "Million Song", max_rows)
    finally:
        source.close()
        destination.close()


def _find_kaggle_metadata_path() -> Path:
    return _resolve_path(
        "HAORAN_KAGGLE_METADATA_PATH",
        (
            "D:/music_dataset/kaggle/data_2genre.csv",
            "D:/music_dataset/kaggle",
        ),
        "*.csv",
    )


def _csv_rows(path: Path):
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        fields = {field.strip().lower() for field in (reader.fieldnames or []) if field}
        name_fields = {"name", "song_name", "track_name", "title"}
        artist_fields = {"artist", "artist_name", "artistname", "singer"}
        if not fields.intersection(name_fields) or not fields.intersection(artist_fields):
            raise ValueError(
                "Kaggle元数据必须包含歌曲名和歌手列；当前列: {}".format(
                    ", ".join(reader.fieldnames or [])
                )
            )
        for raw in reader:
            normalized = {
                (key or "").strip().lower(): value
                for key, value in raw.items()
            }
            yield {
                "name": next((normalized.get(key) for key in name_fields if normalized.get(key)), ""),
                "artist": next((normalized.get(key) for key in artist_fields if normalized.get(key)), ""),
                "album": normalized.get("album") or normalized.get("release") or "",
                "duration": normalized.get("duration") or normalized.get("duration_sec") or "",
                "duration_ms": normalized.get("duration_ms") or "",
            }


def import_kaggle(max_rows: int = DEFAULT_MAX_ROWS):
                     
    print("\n=== 导入Kaggle数据集 ===")
    metadata_path = _find_kaggle_metadata_path()
    destination = _database_connection()
    try:
        return _import_rows(_csv_rows(metadata_path), destination, "Kaggle", max_rows)
    finally:
        destination.close()

def main():
    parser = argparse.ArgumentParser(description='浩然音乐数据集导入工具')
    parser.add_argument('--spotify', action='store_true', help='导入Spotify数据集')
    parser.add_argument('--million', action='store_true', help='导入Million Song数据集')
    parser.add_argument('--kaggle', action='store_true', help='导入Kaggle数据集')
    parser.add_argument('--all', action='store_true', help='导入全部数据集')
    parser.add_argument(
        '--max-rows',
        type=int,
        default=DEFAULT_MAX_ROWS,
        help='Million Song/Kaggle元数据最多导入行数，0表示不限制',
    )

    args = parser.parse_args()

    if args.all or args.spotify:
        import_spotify()

    if args.all or args.million:
        import_million_song(args.max_rows)

    if args.all or args.kaggle:
        import_kaggle(args.max_rows)

    if not any([args.spotify, args.million, args.kaggle, args.all]):
        parser.print_help()

if __name__ == "__main__":
    main()
