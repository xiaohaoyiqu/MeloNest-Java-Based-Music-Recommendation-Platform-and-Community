#!/bin/bash
set -eo pipefail

PROJECT_DIR="/sdb1/myprojoct/haoranmusic"
MODEL_DIR="$PROJECT_DIR/models"
ARTIFACT_DIR="${1:?artifact directory is required}"
DEPLOY_TAG="${2:-$(date +%Y%m%d-%H%M%S)}"
MODEL_NAME="recommendation_model.json"
MANIFEST_NAME="$MODEL_NAME.manifest.json"
MODEL_SOURCE="$ARTIFACT_DIR/$MODEL_NAME"
MANIFEST_SOURCE="$ARTIFACT_DIR/$MANIFEST_NAME"
MODEL_TARGET="$MODEL_DIR/$MODEL_NAME"
MANIFEST_TARGET="$MODEL_DIR/$MANIFEST_NAME"
MODEL_NEXT="$MODEL_DIR/$MODEL_NAME.next-$DEPLOY_TAG"
MANIFEST_NEXT="$MODEL_DIR/$MANIFEST_NAME.next-$DEPLOY_TAG"
MODEL_BACKUP="$MODEL_DIR/$MODEL_NAME.pre-$DEPLOY_TAG"
MANIFEST_BACKUP="$MODEL_DIR/$MANIFEST_NAME.pre-$DEPLOY_TAG"
HAD_MANIFEST=false

test -s "$MODEL_SOURCE"
test -s "$MANIFEST_SOURCE"
test -s "$MODEL_TARGET"

python3 - "$MODEL_SOURCE" "$MANIFEST_SOURCE" <<'PY'
import hashlib
import json
import os
import sys

model_path, manifest_path = sys.argv[1:]
with open(model_path, "rb") as handle:
    payload = handle.read()
with open(model_path, "r", encoding="utf-8") as handle:
    model = json.load(handle)
with open(manifest_path, "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

metadata = model.get("metadata") or {}
checks = {
    "schema": metadata.get("schema_version") == 2 == manifest.get("schema_version"),
    "model type": metadata.get("model_type") == "als_collaborative_filtering" == manifest.get("model_type"),
    "model version": metadata.get("model_version") == manifest.get("model_version"),
    "catalog version": metadata.get("catalog_version") == manifest.get("catalog_version"),
    "artifact file": manifest.get("artifact_file") == os.path.basename(model_path),
    "artifact size": manifest.get("artifact_size") == len(payload),
    "artifact checksum": manifest.get("artifact_sha256") == hashlib.sha256(payload).hexdigest(),
    "recommendations": isinstance(model.get("recommendations"), dict),
}
failed = [label for label, passed in checks.items() if not passed]
if failed:
    raise SystemExit("model validation failed: " + ", ".join(failed))
print("model-validation-ok version=" + str(metadata.get("model_version")))
PY

install -m 640 "$MODEL_SOURCE" "$MODEL_NEXT"
install -m 640 "$MANIFEST_SOURCE" "$MANIFEST_NEXT"

cp -p "$MODEL_TARGET" "$MODEL_BACKUP"
if test -f "$MANIFEST_TARGET"; then
    cp -p "$MANIFEST_TARGET" "$MANIFEST_BACKUP"
    HAD_MANIFEST=true
fi

mv -f "$MODEL_NEXT" "$MODEL_TARGET"
mv -f "$MANIFEST_NEXT" "$MANIFEST_TARGET"

start_backend() (
    cd "$PROJECT_DIR"
    source "$PROJECT_DIR/scripts/env.sh"
    export DB_USERNAME="${DB_USER}"
    export DB_PASSWORD="${DB_PASS}"
    export REDIS_PASSWORD JWT_SECRET PAYMENT_VERIFICATION_HMAC_SECRET
    nohup java ${BACKEND_JAVA_OPTS:-} -jar "$PROJECT_DIR/hao-ran-music-backend-1.0.0.jar" \
        --server.port=9090 > "$PROJECT_DIR/logs/backend.log" 2>&1 &
)

wait_until_healthy() {
    local attempt
    for attempt in $(seq 1 60); do
        if curl -fsS --max-time 3 'http://127.0.0.1:9090/api/song/hot?limit=1' >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

restart_backend() {
    pkill -f '^java.*hao-ran-music-backend-1.0.0.jar' 2>/dev/null || true
    sleep 2
    start_backend
}

restart_backend
if wait_until_healthy; then
    echo "Recommendation model promotion healthy."
    echo "Model backup: $MODEL_BACKUP"
    if "$HAD_MANIFEST"; then
        echo "Manifest backup: $MANIFEST_BACKUP"
    fi
    exit 0
fi

echo "Model promotion health check failed; restoring previous model." >&2
pkill -f '^java.*hao-ran-music-backend-1.0.0.jar' 2>/dev/null || true
cp -p "$MODEL_BACKUP" "$MODEL_TARGET"
if "$HAD_MANIFEST"; then
    cp -p "$MANIFEST_BACKUP" "$MANIFEST_TARGET"
else
    rm -f "$MANIFEST_TARGET"
fi
start_backend
if wait_until_healthy; then
    echo "Model rollback completed and backend is healthy." >&2
else
    echo "Model rollback failed; manual recovery is required." >&2
    exit 2
fi
exit 1
