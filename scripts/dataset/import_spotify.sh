#!/bin/bash
set -euo pipefail
                                                                               
                                
                     
                  
                                                                               

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"

echo "=========================================="
echo "  Spotify数据集导入"
echo "  @author xiaohaoyiqu"
echo "=========================================="

if [ -z "${HAORAN_DB_PASSWORD:-}" ]; then
    echo "错误: 请通过 HAORAN_DB_PASSWORD 提供数据库密码" >&2
    exit 1
fi

exec "$PYTHON_BIN" "$SCRIPT_DIR/import_spotify_dataset.py"
