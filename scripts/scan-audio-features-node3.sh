#!/bin/bash
                                                                               
                                                                    
set -euo pipefail
umask 077

LIMIT=0
WORKERS=2
CATALOG_INPUT=""
RESULTS_INPUT=""
for argument in "$@"; do
    case "$argument" in
        --limit=*) LIMIT="${argument#*=}" ;;
        --workers=*) WORKERS="${argument#*=}" ;;
        --catalog=*) CATALOG_INPUT="${argument#*=}" ;;
        --output=*) RESULTS_INPUT="${argument#*=}" ;;
        *) echo "unsupported argument: $argument" >&2; exit 2 ;;
    esac
done
case "$LIMIT" in ''|*[!0-9]*) echo "limit must be a non-negative integer" >&2; exit 2 ;; esac
case "$WORKERS" in 1|2|3|4) ;; *) echo "workers must be between 1 and 4" >&2; exit 2 ;; esac

if [ "$(hostname -s)" != "node3" ]; then
    echo "this scanner must run on node3" >&2
    exit 2
fi

ENV_FILE="${HAORAN_ENV_FILE:-/sdb1/scripts/env.sh}"
if [ ! -r "$ENV_FILE" ]; then
    echo "environment file not found: $ENV_FILE" >&2
    exit 2
fi
                             
source "$ENV_FILE"

PYTHON_BIN="${PYTHON_BIN:-/usr/local/soft/python3.7.16/bin/python3}"
ANALYZER_PATH="${AUDIO_ANALYZER_PYTHONPATH:-/sdb1/haoranmusicData/tools/essentia-py37}"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
LOG_DIR="${AUDIO_FEATURE_LOG_DIR:-/sdb1/logs/audio-feature}"
mkdir -p "$LOG_DIR"
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)
CATALOG="$LOG_DIR/catalog-$RUN_ID.tsv"
RESULTS="$LOG_DIR/results-$RUN_ID.tsv"
RUN_LOG="$LOG_DIR/scan-$RUN_ID.log"
LOCK_FILE="$LOG_DIR/scan.lock"

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "another audio-feature scan is already running" >&2
    exit 3
fi

if [ -z "$CATALOG_INPUT" ] || [ ! -r "$CATALOG_INPUT" ]; then
    echo "a readable --catalog TSV exported by node1 is required" >&2
    exit 2
fi
cp "$CATALOG_INPUT" "$CATALOG"
if [ -n "$RESULTS_INPUT" ]; then
    RESULTS="$RESULTS_INPUT"
fi

export PYTHONPATH="$ANALYZER_PATH"
"$PYTHON_BIN" "$SCRIPT_DIR/audio_feature_scan.py" \
    --catalog "$CATALOG" --output "$RESULTS" --ffmpeg "$FFMPEG_PATH" \
    --workers "$WORKERS" --limit "$LIMIT" 2>&1 | tee "$RUN_LOG"

READY_COUNT=$(awk -F '\t' 'NR>1 && $2=="ready" {count++} END {print count+0}' "$RESULTS")
echo "event=audio_feature_scan_ready_for_registration ready=$READY_COUNT resultFile=$(basename "$RESULTS")" | tee -a "$RUN_LOG"
