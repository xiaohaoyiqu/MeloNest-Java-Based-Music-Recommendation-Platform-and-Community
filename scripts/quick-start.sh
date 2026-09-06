#!/bin/bash
                                                                               
                 
                     
                  
                         
                                                                               

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

        
if [ -f "${SCRIPT_DIR}/env.sh" ]; then
    source "${SCRIPT_DIR}/env.sh"
else
    echo "[ERROR] 环境变量文件不存在"
    exit 1
fi

export DB_USERNAME="${DB_USER}"
export DB_PASSWORD="${DB_PASS}"
export REDIS_PASSWORD
export JWT_SECRET

      
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

trap 'log_error "启动失败（脚本第 ${LINENO} 行）"' ERR

                                            
run_local_script() {
    local script_name=$1
    shift
    bash "${SCRIPT_DIR}/${script_name}" "$@"
}

         
check_ssh() {
    log_step "检查SSH连接..."
    for node in "${ALL_NODES[@]}"; do
        local name=$(get_node_name $node)
        echo -n "  ${name} (${node}): "
        if ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "echo ok" 2>/dev/null | grep -q "ok"; then
            echo -e "${GREEN}✓${NC}"
        else
            echo -e "${RED}✗${NC}"
            log_error "SSH连接失败，请配置免密登录"
            exit 1
        fi
    done
    echo ""
}

             
start_zk() {
    log_step "[1/8] 启动 ZooKeeper..."
    run_local_script "zookeeper.sh" start
    sleep 5
    echo -e "${GREEN}完成${NC}"
}

          
start_hadoop() {
    log_step "[2/8] 启动 Hadoop..."
    run_local_script "hadoop.sh" start
    sleep 10
    echo -e "${GREEN}完成${NC}"
}

         
start_kafka() {
    log_step "[3/8] 启动 Kafka..."
    run_local_script "kafka.sh" start
    sleep 5
    echo -e "${GREEN}完成${NC}"
}

         
start_redis() {
    log_step "[4/8] 启动 Redis..."
    run_local_script "redis.sh" start
    sleep 3
    echo -e "${GREEN}完成${NC}"
}

        
start_hive() {
    log_step "[5/8] 启动 Hive..."
    run_local_script "hive.sh" start
    sleep 5
    echo -e "${GREEN}完成${NC}"
}

         
start_spark() {
    log_step "[6/8] 启动 Spark..."
    run_local_script "spark.sh" start
    sleep 3
    echo -e "${GREEN}完成${NC}"
}

      
start_backend() {
    log_step "[7/8] 启动后端..."
    pkill -f "^java.*hao-ran-music-backend-1.0.0.jar" 2>/dev/null || true
    sleep 1
    mkdir -p "${PROJECT_DIR}/logs"
    nohup java ${BACKEND_JAVA_OPTS:-} -jar "${PROJECT_DIR}/hao-ran-music-backend-1.0.0.jar" --server.port=9090 > "${PROJECT_DIR}/logs/backend.log" 2>&1 &
    local attempt http_code
    for attempt in $(seq 1 45); do
        http_code=$(curl -sS --max-time 3 -o /dev/null -w '%{http_code}' \
            "http://127.0.0.1:9090/api/song/hot?limit=1" 2>/dev/null || true)
        if [ "$http_code" = "200" ]; then
            echo -e "${GREEN}完成${NC}"
            return 0
        fi
        sleep 2
    done
    log_error "后端在90秒内未通过健康检查，请查看 ${PROJECT_DIR}/logs/backend.log"
    return 1
}

         
start_nginx() {
    log_step "[8/8] 启动 Nginx..."
    run_local_script "nginx.sh" start
    echo -e "${GREEN}完成（node1控制 node1:3223、node2:8082、node3:8081）${NC}"
}

      
show_status() {
    echo ""
    echo "=========================================="
    echo "  服务地址"
    echo "=========================================="
    echo ""
    echo "前端:     http://${NODE1_IP}:3223/"
    echo "后端:     http://${NODE1_IP}:9090/api"
    echo "HDFS:     http://${NODE1_IP}:9870/"
    echo "YARN:     http://${NODE1_IP}:8088/"
    echo "Spark:    http://${NODE1_IP}:18080/"
    echo ""
    echo "=========================================="
}

     
main() {
    echo ""
    echo "=========================================="
    echo "  浩然音乐 - 快速启动"
    echo "=========================================="
    echo ""
    echo "集群节点: ${NODE1_IP}(主), ${NODE2_IP}, ${NODE3_IP}"
    echo ""

    check_ssh
    start_zk
    start_hadoop
    start_kafka
    start_redis
    start_hive
    start_spark
    start_backend
    start_nginx

    echo ""
    log_info "所有服务启动完成！"
    show_status
}

main "$@"
