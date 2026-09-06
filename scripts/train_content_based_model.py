#!/usr/bin/env python3
                       
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
\
\
\
\
\
   

import os
import sys
import pymysql
from collections import defaultdict
from datetime import datetime
from recommendation_model_utils import (
    build_catalog_version,
    build_model_metadata,
    split_csv_tokens,
    write_model_artifact,
)

         
DB_CONFIG = {
    'host': os.environ.get('HAORAN_DB_HOST', '127.0.0.1'),
    'port': int(os.environ.get('HAORAN_DB_PORT', '3306')),
    'user': os.environ.get('HAORAN_DB_USER', 'hdfs'),
    'password': os.environ.get('HAORAN_DB_PASSWORD', ''),
    'database': os.environ.get('HAORAN_DB_NAME', 'haoranmusic_bus'),
    'charset': 'utf8mb4'
}

      
MODEL_OUTPUT_PATH = os.environ.get(
    "HAORAN_CONTENT_MODEL_PATH",
    "/sdb1/myprojoct/haoranmusic/models/content_based_model.json",
)


def get_db_connection():
                 
    if not DB_CONFIG['password']:
        raise RuntimeError("请通过 HAORAN_DB_PASSWORD 提供训练数据库密码")
    return pymysql.connect(**DB_CONFIG)


def load_songs(conn):
                
    print("=" * 60)
    print("加载歌曲数据...")

    with conn.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute("""
            SELECT id, name, artist_id, artist_names, album_id,
                   main_type, sub_types, language, play_count,
                   favorite_count, is_hot, is_new
            FROM song
            WHERE status = 1 AND deleted = 0
              AND (
                NULLIF(TRIM(url_standard), '') IS NOT NULL
                OR NULLIF(TRIM(url_high), '') IS NOT NULL
                OR NULLIF(TRIM(url_lossless), '') IS NOT NULL
              )
            ORDER BY play_count DESC
        """)

        songs = cursor.fetchall()
        print(f"加载歌曲数: {len(songs)}")

    return songs


def build_genre_index(songs):
\
\
\
       
    print("构建流派索引...")

    genre_index = defaultdict(list)

    for song in songs:
        main_type = song.get('main_type', 'Unknown')
        if main_type:
            genre_index[main_type].append(song['id'])

        for sub in split_csv_tokens(song.get('sub_types')):
            genre_index[sub].append(song['id'])

    print(f"流派数量: {len(genre_index)}")
    for genre, song_ids in list(genre_index.items())[:5]:
        print(f"  {genre}: {len(song_ids)} 首歌曲")

    return genre_index


def build_artist_index(songs):
                 
    print("构建艺术家索引...")

    artist_index = defaultdict(list)

    for song in songs:
        artist_id = song.get('artist_id')
        if artist_id:
            artist_index[artist_id].append(song['id'])

    print(f"艺术家数量: {len(artist_index)}")

    return artist_index


def calculate_song_similarity(song1, song2):
\
\
       
    score = 0.0

          
    if song1.get('main_type') and song1.get('main_type') == song2.get('main_type'):
        score += 0.4

           
    sub_overlap = split_csv_tokens(song1.get('sub_types')) & split_csv_tokens(song2.get('sub_types'))
    if sub_overlap:
        score += 0.2 * len(sub_overlap)

           
    if song1.get('artist_id') and song1.get('artist_id') == song2.get('artist_id'):
        score += 0.3

          
    if song1.get('language') and song1.get('language') == song2.get('language'):
        score += 0.1

    return score


def build_similarity_matrix(songs, top_n=50):
\
\
\
       
    print("=" * 60)
    print(f"构建歌曲相似度矩阵 (Top-{top_n})...")

    similarity_matrix = {}
    candidate_buckets = defaultdict(set)
    for song in songs:
        song_id = song['id']
        for genre in split_csv_tokens(song.get('sub_types')) | split_csv_tokens(song.get('main_type')):
            candidate_buckets[('genre', genre)].add(song_id)
        if song.get('artist_id'):
            candidate_buckets[('artist', str(song['artist_id']))].add(song_id)

    song_dict = {song['id']: song for song in songs}

    for i, song in enumerate(songs):
        if i % 10000 == 0:
            print(f"进度: {i}/{len(songs)}")

        song_id = song['id']
        candidate_ids = set()
        for genre in split_csv_tokens(song.get('sub_types')) | split_csv_tokens(song.get('main_type')):
            candidate_ids.update(candidate_buckets.get(('genre', genre), set()))
        if song.get('artist_id'):
            candidate_ids.update(candidate_buckets.get(('artist', str(song['artist_id'])), set()))
        candidate_ids.discard(song_id)

        similarities = []
        for candidate_id in candidate_ids:
            sim = calculate_song_similarity(song, song_dict[candidate_id])
            if sim > 0:
                similarities.append((candidate_id, sim))

                       
        similarities.sort(key=lambda x: x[1], reverse=True)
        similarity_matrix[str(song_id)] = {
            str(sid): score for sid, score in similarities[:top_n]
        }

    print(f"相似度矩阵构建完成: {len(similarity_matrix)} 首歌曲")

    return similarity_matrix


def generate_user_recommendations(conn, songs, genre_index, artist_index):
\
\
       
    print("=" * 60)
    print("生成用户推荐...")

    recommendations = {}
    song_dict = {song['id']: song for song in songs}

    with conn.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute("""
            SELECT user_id, song_id, MAX(action_weight) AS action_weight
            FROM (
                SELECT user_id, song_id, 1 AS action_weight
                FROM listen_history
                WHERE deleted = 0 AND user_id IS NOT NULL AND song_id IS NOT NULL
                UNION ALL
                SELECT user_id, song_id, 3 AS action_weight
                FROM song_like
                WHERE deleted = 0 AND is_favorite = 1
                  AND user_id IS NOT NULL AND song_id IS NOT NULL
            ) actions
            GROUP BY user_id, song_id
        """)
        actions_by_user = defaultdict(list)
        for action in cursor.fetchall():
            if action['song_id'] in song_dict:
                actions_by_user[action['user_id']].append(action)

        print(f"活跃用户数: {len(actions_by_user)}")

        for user_id, actions in actions_by_user.items():
            preferred_genres = defaultdict(int)
            preferred_artists = defaultdict(int)
            seen_song_ids = {action['song_id'] for action in actions}

            for action in actions:
                song_data = song_dict[action['song_id']]
                weight = int(action.get('action_weight') or 1)
                if song_data.get('main_type'):
                    preferred_genres[song_data['main_type']] += weight
                if song_data.get('artist_id'):
                    preferred_artists[song_data['artist_id']] += weight

                  
            recommended = set(seen_song_ids)
            scores = []

                    
            top_genres = sorted(preferred_genres.items(), key=lambda x: x[1], reverse=True)[:3]
            for genre, _ in top_genres:
                for song_id in genre_index.get(genre, [])[:20]:
                    if song_id not in recommended:
                        recommended.add(song_id)
                        scores.append((song_id, 0.8))

                     
            top_artists = sorted(preferred_artists.items(), key=lambda x: x[1], reverse=True)[:3]
            for artist_id, _ in top_artists:
                for song_id in artist_index.get(artist_id, [])[:10]:
                    if song_id not in recommended:
                        recommended.add(song_id)
                        scores.append((song_id, 0.7))

                    
            for song in songs[:50]:
                if song['id'] not in recommended:
                    recommended.add(song['id'])
                    scores.append((song['id'], 0.5))
                    if len(scores) >= 100:
                        break

                   
            scores.sort(key=lambda x: x[1], reverse=True)
            recommendations[str(user_id)] = [
                {"song_id": str(sid), "score": float(score)}
                for sid, score in scores[:50]
            ]

    print(f"生成推荐: {len(recommendations)} 个用户")

    return recommendations


def save_model(similarity_matrix, recommendations, genre_index, artist_index):
              
    print("=" * 60)
    print("保存模型...")

    model = {
        "model_type": "content_based_recommendation",
        "created_at": datetime.now().isoformat(),
        "metadata": build_model_metadata(
            "content_based_recommendation",
            source_counts={
                "genre_count": len(genre_index),
                "artist_count": len(artist_index),
                "similarity_song_count": len(similarity_matrix),
                "recommendation_user_count": len(recommendations),
            },
            model_params={"top_n_similarity": 30, "candidate_strategy": "genre_artist_inverted_index"},
            output_paths={"content_model": MODEL_OUTPUT_PATH},
            catalog_version=build_catalog_version(similarity_matrix.keys()),
        ),
        "genre_index": {k: list(v) for k, v in genre_index.items()},
        "artist_index": {str(k): list(v) for k, v in artist_index.items()},
        "global_hot_songs": [str(song_id) for song_id in list(similarity_matrix.keys())[:100]],
        "similarity_matrix": similarity_matrix,
        "user_recommendations": recommendations
    }

    write_model_artifact(
        MODEL_OUTPUT_PATH,
        model,
        coverage={
            "playable_song_count": len(similarity_matrix),
            "recommendation_user_count": len(recommendations),
        },
    )

    print(f"模型已保存: {MODEL_OUTPUT_PATH}")
    print(f"  - 流派索引: {len(genre_index)} 个流派")
    print(f"  - 艺术家索引: {len(artist_index)} 个艺术家")
    print(f"  - 相似度矩阵: {len(similarity_matrix)} 首歌曲")
    print(f"  - 用户推荐: {len(recommendations)} 个用户")


def main():
             
    print("=" * 60)
    print("浩然音乐 - 内容推荐模型训练")
    print("=" * 60)

    conn = None
    try:
        conn = get_db_connection()

              
        songs = load_songs(conn)

        if len(songs) < 100:
            raise RuntimeError("可播放歌曲不足 100 首，拒绝发布内容模型")

              
        genre_index = build_genre_index(songs)
        artist_index = build_artist_index(songs)

                 
        similarity_matrix = build_similarity_matrix(songs, top_n=30)

                
        recommendations = generate_user_recommendations(
            conn, songs, genre_index, artist_index
        )

              
        save_model(similarity_matrix, recommendations, genre_index, artist_index)

        print("=" * 60)
        print("训练完成!")
        print("=" * 60)

    except Exception as e:
        print(f"训练失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

    finally:
        if conn:
            conn.close()


if __name__ == "__main__":
    main()
