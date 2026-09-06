#!/usr/bin/env bash
set -eu

TRAINING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NODE_ENV_FILE="${HAORAN_NODE_ENV_FILE:-/sdb1/scripts/env.sh}"
if [ ! -r "${NODE_ENV_FILE}" ]; then
    echo "[ERROR] node environment is not readable: ${NODE_ENV_FILE}" >&2
    exit 2
fi
if [ -z "${HAORAN_SECRETS_FILE:-}" ] || [ ! -r "${HAORAN_SECRETS_FILE}" ]; then
    echo "[ERROR] HAORAN_SECRETS_FILE must point to a protected readable file" >&2
    exit 2
fi

source "${NODE_ENV_FILE}" >/dev/null 2>&1
source "${HAORAN_SECRETS_FILE}" >/dev/null 2>&1
if [ -z "${DB_PASS:-}" ]; then
    echo "[ERROR] database password is unavailable" >&2
    exit 2
fi

RUN_ID="${HAORAN_TRAINING_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export HAORAN_DB_HOST="${DB_HOST}"
export HAORAN_DB_PORT="${DB_PORT}"
export HAORAN_DB_NAME="${DB_NAME}"
export HAORAN_DB_USER="${DB_USER}"
export HAORAN_DB_PASSWORD="${DB_PASS}"
export HAORAN_MODEL_OUTPUT_DIR="${HAORAN_MODEL_OUTPUT_DIR:-/sdb1/haoranmusicData/models/staging/${RUN_ID}}"
export HAORAN_SPARK_MODEL_BASE_PATH="${HAORAN_SPARK_MODEL_BASE_PATH:-hdfs://mycluster/Datas/haoranMusicPlatform/models/recommendation/als-staging}"
export HAORAN_HIVE_ENABLED="${HAORAN_HIVE_ENABLED:-true}"
export HAORAN_MIN_USER_INTERACTIONS="${HAORAN_MIN_USER_INTERACTIONS:-1}"
export HAORAN_MIN_ITEM_INTERACTIONS="${HAORAN_MIN_ITEM_INTERACTIONS:-1}"
export HAORAN_MIN_TRAINING_INTERACTIONS="${HAORAN_MIN_TRAINING_INTERACTIONS:-20}"
export HAORAN_ALS_RANK="${HAORAN_ALS_RANK:-8}"
export HAORAN_ALS_MAX_ITER="${HAORAN_ALS_MAX_ITER:-10}"
export HAORAN_SPARK_SHUFFLE_PARTITIONS="${HAORAN_SPARK_SHUFFLE_PARTITIONS:-16}"
export JAVA_HOME=/usr/local/soft/jdk1.8.0_461
export PYSPARK_PYTHON=/usr/local/soft/python3.7.16/bin/python3
export PYSPARK_DRIVER_PYTHON=/usr/local/soft/python3.7.16/bin/python3
export PYTHONPATH=/sdb1/haoranmusicData/python-packages/spark-2.4.8:${PYTHONPATH:-}
export LD_LIBRARY_PATH=/usr/local/soft/python3.7.16/lib:${LD_LIBRARY_PATH:-}

mkdir -p "${HAORAN_MODEL_OUTPUT_DIR}"
echo "[INFO] staged model output: ${HAORAN_MODEL_OUTPUT_DIR}"

exec /usr/local/soft/spark-2.4.8/bin/spark-submit \
    --master spark://192.168.153.131:7077 \
    --deploy-mode client \
    --conf spark.ui.showConsoleProgress=false \
    "${TRAINING_DIR}/train_hybrid_model.py"
