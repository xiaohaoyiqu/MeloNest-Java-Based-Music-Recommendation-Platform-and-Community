#!/bin/bash






SSH_CONFIG="D:/ideaproject/HaoRanMusic/scripts/ssh/config"
SERVER="node1"
PROJECT_DIR="/sdb1/myprojoct/haoranmusic"
JAR_FILE="$PROJECT_DIR/hao-ran-music-backend-1.0.0.jar"
LOG_FILE="$PROJECT_DIR/logs/backend.log"

echo "=== 重启后端服务 ==="


ssh -F "$SSH_CONFIG" -o ConnectTimeout=10 "$SERVER" << 'ENDSSH'
cd /sdb1/myprojoct/haoranmusic
source ./scripts/env.sh
export DB_USERNAME="${DB_USER}"
export DB_PASSWORD="${DB_PASS}"
export REDIS_PASSWORD
export JWT_SECRET


echo "停止旧服务..."
pkill -f "^java.*hao-ran-music-backend-1.0.0.jar" || true
sleep 3


echo "启动新服务..."
nohup java ${BACKEND_JAVA_OPTS:-} -jar hao-ran-music-backend-1.0.0.jar --server.port=9090 > logs/backend.log 2>&1 &


sleep 5


echo "=== 服务状态 ==="
ps aux | grep hao-ran-music-backend | grep -v grep
echo ""
echo "=== 最近日志 ==="
tail -20 $LOG_FILE
ENDSSH

echo ""
echo "=== 完成 ==="
echo "后端API: http://192.168.153.131:9090/api"
