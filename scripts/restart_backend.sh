#!/bin/bash
                                          
               
                     
                  
                                          

echo "========================================"
echo "浩然音乐后端重启脚本"
echo "========================================"

       
SERVER_HOST="192.168.153.131"
SERVER_USER="hdfs"
SERVER_DIR="/sdb1/myprojoct/haoranmusic"
SSH_CONFIG="D:/ideaproject/HaoRanMusic/scripts/ssh/config"

       
echo "正在连接服务器 $SERVER_HOST..."
ssh -F "$SSH_CONFIG" -o ConnectTimeout=10 node1 << 'ENDSSH'
    cd /sdb1/myprojoct/haoranmusic
    source /sdb1/myprojoct/haoranmusic/scripts/env.sh
    export DB_USERNAME="${DB_USER}"
    export DB_PASSWORD="${DB_PASS}"
    export REDIS_PASSWORD
    export JWT_SECRET

    echo "========================================"
    echo "1. 停止现有后端服务..."
    pkill -f "^java.*hao-ran-music-backend-1.0.0.jar" || true
    sleep 2

    echo "========================================"
    echo "2. 检查是否还有残留进程..."
    ps aux | grep hao-ran-music-backend | grep -v grep

    echo "========================================"
    echo "3. 启动后端服务..."
    nohup java ${BACKEND_JAVA_OPTS:-} -jar hao-ran-music-backend-1.0.0.jar --server.port=9090 > logs/backend.log 2>&1 &

    sleep 3

    echo "========================================"
    echo "4. 检查后端服务状态..."
    ps aux | grep hao-ran-music-backend | grep -v grep

    echo "========================================"
    echo "5. 查看启动日志（最近20行）..."
    tail -n 20 logs/backend.log

    echo "========================================"
    echo "后端服务重启完成！"
    echo "API地址: http://192.168.153.131:9090/api"
ENDSSH

echo "========================================"
echo "脚本执行完成"
echo "========================================"
