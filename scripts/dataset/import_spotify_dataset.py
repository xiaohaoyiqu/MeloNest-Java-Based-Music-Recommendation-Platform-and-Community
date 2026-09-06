\
\
\
\
\
\
\
   

import json
import os
import mysql.connector
from mysql.connector import Error
from pathlib import Path
from typing import Dict, Set, List
import sys

                                              
DB_CONFIG = {
    'host': os.environ.get('HAORAN_DB_HOST', '127.0.0.1'),
    'port': int(os.environ.get('HAORAN_DB_PORT', '3306')),
    'user': os.environ.get('HAORAN_DB_USER', 'hdfs'),
    'password': os.environ.get('HAORAN_DB_PASSWORD', ''),
    'database': os.environ.get('HAORAN_DB_NAME', 'haoranmusic_bus')
}

DATASET_PATH = os.environ.get(
    'HAORAN_SPOTIFY_DATASET_PATH',
    'D:/music_dataset/spotify_million_playlist_dataset.zip',
)

                                                
class ImportStats:
    def __init__(self):
        self.playlists_processed = 0
        self.songs_found = 0
        self.artists_found = 0
        self.songs_added = 0
        self.artists_added = 0
        self.playlists_added = 0
        self.errors = 0

    def print(self):
        print(f"\n=== 导入统计 ===")
        print(f"播放列表处理: {self.playlists_processed}")
        print(f"发现歌曲: {self.songs_found}")
        print(f"发现歌手: {self.artists_found}")
        print(f"新增歌曲: {self.songs_added}")
        print(f"新增歌手: {self.artists_added}")
        print(f"新增播放列表: {self.playlists_added}")
        print(f"错误: {self.errors}")

stats = ImportStats()

                                                 
def get_db_connection():
                 
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except Error as e:
        print(f"数据库连接失败: {e}")
        sys.exit(1)

def get_or_create_artist(conn, name: str) -> int:
                 
    cursor = conn.cursor()
    try:
             
        cursor.execute("SELECT id FROM artist WHERE name = %s LIMIT 1", (name,))
        result = cursor.fetchone()
        if result:
            return result[0]

                
        cursor.execute("""
            INSERT INTO artist (name, type, status, create_time)
            VALUES (%s, '歌手', 1, NOW())
        """, (name,))
        conn.commit()
        stats.artists_added += 1
        return cursor.lastrowid
    except Error as e:
        print(f"歌手操作错误 [{name}]: {e}")
        stats.errors += 1
        return None
    finally:
        cursor.close()

def get_or_create_song(conn, name: str, artist_id: int, album_name: str,
                       duration_ms: int, artist_name: str) -> int:
                 
    cursor = conn.cursor()
    try:
        duration_sec = duration_ms // 1000 if duration_ms else 0

             
        cursor.execute("""
            SELECT id FROM song WHERE name = %s AND artist_id = %s LIMIT 1
        """, (name, artist_id))
        result = cursor.fetchone()
        if result:
            return result[0]

                
        cursor.execute("""
            INSERT INTO song (name, artist_id, artist_names, album_name,
                             duration, main_genre, language, status, create_time)
            VALUES (%s, %s, %s, %s, %s, '流行', '英文', 1, NOW())
        """, (name, artist_id, artist_name, album_name, duration_sec))
        conn.commit()
        stats.songs_added += 1
        return cursor.lastrowid
    except Error as e:
        print(f"歌曲操作错误 [{name}]: {e}")
        stats.errors += 1
        return None
    finally:
        cursor.close()

def create_playlist(conn, user_id: int, name: str, song_ids: List[int]) -> int:
                
    cursor = conn.cursor()
    try:
                
        cursor.execute("""
            INSERT INTO playlist (user_id, name, description, type,
                                  is_public, song_count, status, create_time)
            VALUES (%s, %s, '从Spotify导入', 1, 1, %s, 1, NOW())
        """, (user_id, name, len(song_ids)))
        playlist_id = cursor.lastrowid

              
        for i, song_id in enumerate(song_ids):
            cursor.execute("""
                INSERT INTO playlist_song (playlist_id, song_id, sort_order, create_time)
                VALUES (%s, %s, %s, NOW())
            """, (playlist_id, song_id, i))

        conn.commit()
        stats.playlists_added += 1
        return playlist_id
    except Error as e:
        print(f"播放列表创建错误 [{name}]: {e}")
        conn.rollback()
        stats.errors += 1
        return None
    finally:
        cursor.close()

                                                
def import_from_slice(zip_path: str, slice_name: str, conn, max_playlists: int = 100):
                   
    import zipfile

    print(f"\n处理: {slice_name}")

    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        with zip_ref.open(f'data/{slice_name}.json') as f:
            data = json.load(f)

    for playlist in data['playlists'][:max_playlists]:
        stats.playlists_processed += 1

        playlist_name = playlist.get('name', 'Spotify Playlist')
        tracks = playlist.get('tracks', [])

        if not tracks:
            continue

        song_ids = []
        for track in tracks:
            stats.songs_found += 1

            artist_name = track.get('artist_name', 'Unknown')
            song_name = track.get('track_name', 'Unknown')
            album_name = track.get('album_name', '')

                     
            artist_id = get_or_create_artist(conn, artist_name)
            if not artist_id:
                continue

                     
            song_id = get_or_create_song(
                conn, song_name, artist_id, album_name,
                track.get('duration_ms', 0), artist_name
            )

            if song_id:
                song_ids.append(song_id)

                                   
        if song_ids and len(song_ids) >= 5:         
            create_playlist(conn, 1, f"[Spotify] {playlist_name}", song_ids)

        if stats.playlists_processed % 10 == 0:
            print(f"  已处理 {stats.playlists_processed} 个播放列表...")

def main():
    print("=" * 50)
    print("  Spotify数据集导入")
    print("  @author xiaohaoyiqu")
    print("=" * 50)

    if not DB_CONFIG['password']:
        raise RuntimeError('请通过 HAORAN_DB_PASSWORD 提供数据库密码')
    if not Path(DATASET_PATH).is_file():
        raise FileNotFoundError(f'Spotify数据集不存在: {DATASET_PATH}')

    conn = get_db_connection()

    try:
                           
                                          
        for i in range(0, 10):                  
            slice_name = f"{i*1000}-{i*1000+999}"
            import_from_slice(DATASET_PATH, slice_name, conn, max_playlists=100)

            if stats.playlists_processed >= 1000:          
                break

        stats.print()

        print("\n导入完成！")

    except Exception as e:
        print(f"导入过程出错: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    main()
