#!/bin/bash
                                                                          
set -euo pipefail
umask 077

OUTPUT="${1:-/tmp/haoran-audio-feature-catalog.tsv}"
ENV_FILE="${HAORAN_ENV_FILE:-/sdb1/myprojoct/haoranmusic/scripts/env.sh}"
case "$OUTPUT" in /*) ;; *) echo "output must be an absolute path" >&2; exit 2 ;; esac
                             
source "$ENV_FILE"
if [ -z "${DB_PASS:-}" ]; then
    echo "database password is unavailable from the protected environment" >&2
    exit 2
fi
export MYSQL_PWD="$DB_PASS"
{
    printf 'song_id\tduration\turl_standard\turl_high\turl_lossless\turl_hires\n'
    mysql --default-character-set=utf8mb4 --batch --raw --skip-column-names \
        --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "$DB_NAME" \
        --execute="SELECT id,COALESCE(duration,0),COALESCE(url_standard,''),COALESCE(url_high,''),COALESCE(url_lossless,''),COALESCE(url_hires,'') FROM song WHERE status=1 AND deleted=0 AND (tempo IS NULL OR audio_key IS NULL OR mode IS NULL) AND COALESCE(NULLIF(url_hires,''),NULLIF(url_lossless,''),NULLIF(url_high,''),NULLIF(url_standard,'')) IS NOT NULL ORDER BY id"
} > "$OUTPUT"
unset MYSQL_PWD
chmod 600 "$OUTPUT"
echo "event=audio_feature_catalog_exported rows=$(($(wc -l < "$OUTPUT") - 1)) outputFile=$(basename "$OUTPUT")"
