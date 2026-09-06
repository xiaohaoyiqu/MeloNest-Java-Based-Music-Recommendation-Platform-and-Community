#!/usr/bin/env bash
set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh" >/dev/null 2>&1

if [ -z "${DB_PASS:-}" ]; then
    echo "DB_PASSWORD=missing"
    exit 2
fi

export MYSQL_PWD="${DB_PASS}"
mysql --batch --skip-column-names \
    -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" <<'SQL'
SELECT 'listen', COUNT(*), COUNT(DISTINCT h.user_id), COUNT(DISTINCT h.song_id)
FROM listen_history h
INNER JOIN song s ON s.id = h.song_id
WHERE h.deleted = 0
  AND h.user_id IS NOT NULL
  AND h.song_id IS NOT NULL
  AND s.deleted = 0
  AND s.status = 1
  AND COALESCE(NULLIF(s.url_standard, ''), NULLIF(s.url_high, ''), NULLIF(s.url_lossless, '')) IS NOT NULL;

SELECT 'favorite', COUNT(*), COUNT(DISTINCT sl.user_id), COUNT(DISTINCT sl.song_id)
FROM song_like sl
INNER JOIN song s ON s.id = sl.song_id
WHERE sl.deleted = 0
  AND sl.is_favorite = 1
  AND sl.user_id IS NOT NULL
  AND sl.song_id IS NOT NULL
  AND s.deleted = 0
  AND s.status = 1
  AND COALESCE(NULLIF(s.url_standard, ''), NULLIF(s.url_high, ''), NULLIF(s.url_lossless, '')) IS NOT NULL;

SELECT 'combined', COUNT(*), COUNT(DISTINCT user_id), COUNT(DISTINCT song_id)
FROM (
    SELECT user_id, song_id
    FROM listen_history
    WHERE deleted = 0 AND user_id IS NOT NULL AND song_id IS NOT NULL
    UNION
    SELECT user_id, song_id
    FROM song_like
    WHERE deleted = 0 AND is_favorite = 1 AND user_id IS NOT NULL AND song_id IS NOT NULL
) interactions;

SELECT 'eligible_users_min5', COUNT(*)
FROM (
    SELECT user_id
    FROM (
        SELECT user_id, song_id FROM listen_history
        WHERE deleted = 0 AND user_id IS NOT NULL AND song_id IS NOT NULL
        UNION
        SELECT user_id, song_id FROM song_like
        WHERE deleted = 0 AND is_favorite = 1 AND user_id IS NOT NULL AND song_id IS NOT NULL
    ) interactions
    GROUP BY user_id
    HAVING COUNT(*) >= 5
) eligible_users;

SELECT 'eligible_songs_min3', COUNT(*)
FROM (
    SELECT song_id
    FROM (
        SELECT user_id, song_id FROM listen_history
        WHERE deleted = 0 AND user_id IS NOT NULL AND song_id IS NOT NULL
        UNION
        SELECT user_id, song_id FROM song_like
        WHERE deleted = 0 AND is_favorite = 1 AND user_id IS NOT NULL AND song_id IS NOT NULL
    ) interactions
    GROUP BY song_id
    HAVING COUNT(*) >= 3
) eligible_songs;
SQL

unset MYSQL_PWD

"${HADOOP_HOME}/bin/hdfs" dfsadmin -safemode get
"${HADOOP_HOME}/bin/hdfs" dfs -test -d /Datas/haoranMusicPlatform/hive/warehouse
echo "HDFS_WAREHOUSE=ok"
