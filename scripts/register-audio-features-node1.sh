#!/bin/bash
                                                                           
set -euo pipefail
umask 077

RESULTS=""
APPLY=false
for argument in "$@"; do
    case "$argument" in
        --results=*) RESULTS="${argument#*=}" ;;
        --apply) APPLY=true ;;
        *) echo "unsupported argument: $argument" >&2; exit 2 ;;
    esac
done
if [ "$(hostname -s)" != "node1" ]; then
    echo "registration must run on node1" >&2
    exit 2
fi
if [ -z "$RESULTS" ] || [ ! -r "$RESULTS" ]; then
    echo "a readable --results TSV from node3 is required" >&2
    exit 2
fi

ENV_FILE="${HAORAN_ENV_FILE:-/sdb1/myprojoct/haoranmusic/scripts/env.sh}"
                             
source "$ENV_FILE"
PYTHON_BIN="${PYTHON_BIN:-/usr/local/soft/python3.7.16/bin/python3}"
if [ ! -x "$PYTHON_BIN" ]; then
    PYTHON_BIN=$(command -v python3 || true)
fi
if [ -z "$PYTHON_BIN" ] || [ ! -x "$PYTHON_BIN" ]; then
    echo "python3 runtime is unavailable" >&2
    exit 2
fi
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
READY_COUNT=$(awk -F '\t' 'NR>1 && $2=="ready" {count++} END {print count+0}' "$RESULTS")
"$PYTHON_BIN" "$SCRIPT_DIR/register_audio_features.py" "$RESULTS" >/dev/null
if [ "$APPLY" != true ]; then
    echo "event=audio_feature_registration_validated ready=$READY_COUNT mode=dry-run"
    exit 0
fi
if [ "$READY_COUNT" -eq 0 ]; then
    echo "event=audio_feature_registration_skipped reason=no_ready_rows"
    exit 0
fi
if [ -z "${DB_PASS:-}" ]; then
    echo "database password is unavailable from the protected environment" >&2
    exit 2
fi

SQL_FILE=$(mktemp /tmp/haoran-audio-feature-register.XXXXXX.sql)
cleanup() {
    case "$SQL_FILE" in /tmp/haoran-audio-feature-register.*.sql) rm -f -- "$SQL_FILE" ;; esac
}
trap cleanup EXIT
chmod 600 "$SQL_FILE"
"$PYTHON_BIN" "$SCRIPT_DIR/register_audio_features.py" "$RESULTS" > "$SQL_FILE"
export MYSQL_PWD="$DB_PASS"
mysql --default-character-set=utf8mb4 \
    --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" "$DB_NAME" < "$SQL_FILE"
unset MYSQL_PWD

REDIS_CLI="${REDIS_HOME}/bin/redis-cli"
if [ -x "$REDIS_CLI" ]; then
    export REDISCLI_AUTH="${REDIS_PASSWORD:-}"
    "$REDIS_CLI" -h "$DB_HOST" -p "$REDIS_PORT" INCR audio:intelligence:candidate-version >/dev/null
    unset REDISCLI_AUTH
else
    echo "event=audio_feature_cache_bump_skipped reason=redis_cli_missing"
fi
echo "event=audio_feature_registration_completed ready=$READY_COUNT resultFile=$(basename "$RESULTS")"
