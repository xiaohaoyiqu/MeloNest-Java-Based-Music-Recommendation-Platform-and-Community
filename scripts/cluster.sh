#!/bin/bash







SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


if [ -f "${SCRIPT_DIR}/env.sh" ]; then
    source "${SCRIPT_DIR}/env.sh"
else
    echo "[ERROR] 环境变量文件不存在: ${SCRIPT_DIR}/env.sh"
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

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }
log_ok()    { echo -e "${GREEN}✓${NC} $1"; }
log_fail()  { echo -e "${RED}✗${NC} $1"; }




get_script_dir() {
    local node=$1
    case "$node" in
        ${NODE1_IP}|node1) echo "/sdb1/myprojoct/haoranmusic/scripts" ;;
        ${NODE2_IP}|node2) echo "/sdb1/scripts" ;;
        ${NODE3_IP}|node3) echo "/sdb1/scripts" ;;
        *) echo "/sdb1/scripts" ;;
    esac
}




check_ssh() {
    ssh ${SSH_OPTS} ${CLUSTER_USER}@$1 "echo ok" 2>/dev/null | grep -q "ok"
}

check_all_ssh() {
    echo ""
    log_step "检查SSH连接"
    local all_ok=true

    for i in "${!ALL_NODES[@]}"; do
        local ip=${ALL_NODES[$i]}
        local name=${ALL_NODE_NAMES[$i]}
        echo -n "  ${name} (${ip}): "
        if check_ssh ${ip}; then
            log_ok "正常"
        else
            log_fail "失败"
            all_ok=false
        fi
    done
    echo ""

    if [ "${all_ok}" = false ]; then
        log_error "SSH连接失败，请配置免密登录："
        echo "  ssh-keygen -t rsa"
        echo "  ssh-copy-id ${CLUSTER_USER}@${NODE1_IP}"
        echo "  ssh-copy-id ${CLUSTER_USER}@${NODE2_IP}"
        echo "  ssh-copy-id ${CLUSTER_USER}@${NODE3_IP}"
        return 1
    fi
    return 0
}




exec_remote() {
    local node=$1 cmd=$2 silent=${3:-false}
    local env_sh=$(get_node_env_sh $node)
    local script_dir=$(get_script_dir $node)

    if [ "${silent}" = "true" ]; then
        ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null; cd ${script_dir} 2>/dev/null && ${cmd}" 2>&1
    else
        echo -e "${CYAN}[${node}]${NC} ${cmd}"
        ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null; cd ${script_dir} 2>/dev/null && ${cmd}" 2>&1
    fi
}

exec_on_all() {
    local cmd=$1 parallel=${2:-true}
    if [ "${parallel}" = "true" ]; then
        for node in "${ALL_NODES[@]}"; do exec_remote "${node}" "${cmd}" & done; wait
    else
        for node in "${ALL_NODES[@]}"; do exec_remote "${node}" "${cmd}"; done
    fi
}

exec_on_zk() {
    for node in "${ZK_NODES[@]}"; do exec_remote "${node}" "$1" & done; wait
}



run_local_script() {
    local script_name=$1
    shift
    bash "${SCRIPT_DIR}/${script_name}" "$@"
}




start_zk() {
    log_step "[1/9] 启动 ZooKeeper..."
    run_local_script "zookeeper.sh" start
    sleep 5
    log_ok "ZooKeeper"
}

start_hadoop() {
    log_step "[2/9] 启动 Hadoop..."
    run_local_script "hadoop.sh" start
    sleep 15
    log_ok "Hadoop"
}

start_kafka() {
    log_step "[3/9] 启动 Kafka..."
    run_local_script "kafka.sh" start
    sleep 5
    log_ok "Kafka"
}

start_redis() {
    log_step "[4/9] 启动 Redis..."
    run_local_script "redis.sh" start
    sleep 3
    log_ok "Redis"
}

start_mysql() {
    log_step "[5/9] 启动 MySQL..."
    exec_remote ${NODE1_IP} "sudo systemctl start mysqld 2>/dev/null || sudo service mysql start 2>/dev/null || echo 'MySQL启动失败'" "true"
    sleep 2
    log_ok "MySQL"
}

start_hive() {
    log_step "[6/9] 启动 Hive..."
    run_local_script "hive.sh" start
    sleep 5
    log_ok "Hive"
}

start_spark() {
    log_step "[7/9] 启动 Spark..."
    run_local_script "spark.sh" start
    sleep 3
    log_ok "Spark"
}

start_backend() {
    log_step "[8/9] 启动后端..."
    pkill -f "^java.*hao-ran-music-backend-1.0.0.jar" 2>/dev/null || true
    nohup java ${BACKEND_JAVA_OPTS:-} -jar "${PROJECT_DIR}/hao-ran-music-backend-1.0.0.jar" --server.port=9090 > "${PROJECT_DIR}/logs/backend.log" 2>&1 &
    sleep 5
    log_ok "后端"
}

start_nginx() {
    log_step "[9/9] 启动 Nginx..."
    run_local_script "nginx.sh" start
    log_ok "Nginx (node1:3223, node2:8082, node3:8081)"
}




stop_nginx() {
    log_step "停止 Nginx..."
    run_local_script "nginx.sh" stop
    sleep 2
    log_ok "Nginx"
}

stop_backend() {
    log_step "停止后端..."
    pkill -f "^java.*hao-ran-music-backend-1.0.0.jar" 2>/dev/null || true
    sleep 2
    log_ok "后端"
}

stop_spark() {
    log_step "停止 Spark..."
    run_local_script "spark.sh" stop
    sleep 3
    log_ok "Spark"
}

stop_hive() {
    log_step "停止 Hive..."
    run_local_script "hive.sh" stop
    sleep 3
    log_ok "Hive"
}

stop_mysql() {
    log_step "停止 MySQL..."
    sudo systemctl stop mysqld 2>/dev/null || true
    sleep 2
    log_ok "MySQL"
}

stop_redis() {
    log_step "停止 Redis..."
    run_local_script "redis.sh" stop
    sleep 3
    log_ok "Redis"
}

stop_kafka() {
    log_step "停止 Kafka..."
    run_local_script "kafka.sh" stop
    sleep 3
    log_ok "Kafka"
}

stop_hadoop() {
    log_step "停止 Hadoop..."
    run_local_script "hadoop.sh" stop
    sleep 8
    log_ok "Hadoop"
}

stop_zk() {
    log_step "停止 ZooKeeper..."
    run_local_script "zookeeper.sh" stop
    sleep 3
    log_ok "ZooKeeper"
}




start() {
    echo ""
    echo "=========================================="
    echo "     启动集群"
    echo "=========================================="
    echo ""
    log_info "主节点: ${MASTER_NAME} (${MASTER_IP})"
    echo ""

    check_all_ssh || exit 1

    start_zk
    start_hadoop
    start_kafka
    start_redis
    start_mysql
    start_hive
    start_spark
    start_backend
    start_nginx

    echo ""
    log_info "集群启动完成！"
    show_urls
}




stop() {
    echo ""
    echo "=========================================="
    echo "     停止集群"
    echo "=========================================="
    echo ""

    stop_nginx
    stop_backend
    stop_spark
    stop_hive
    stop_mysql
    stop_redis
    stop_kafka
    stop_hadoop
    stop_zk

    echo ""
    log_info "集群已停止"
}




restart() {
    stop
    sleep 5
    start
}




status() {
    echo ""
    echo "=========================================="
    echo "     集群状态"
    echo "=========================================="
    echo ""

    for node in "${ALL_NODES[@]}"; do
        local name=$(get_node_name $node)
        echo "--- ${name} (${node}) ---"
        exec_remote ${node} "jps 2>/dev/null | grep -v Jps || echo 'jps不可用'"
        echo ""
    done
}




health() {
    echo ""
    echo "=========================================="
    echo "     健康检查"
    echo "=========================================="
    echo ""

    local services=(
        "QuorumPeerMain:ZooKeeper"
        "NameNode:HDFS"
        "DataNode:HDFS"
        "JournalNode:HDFS"
        "ResourceManager:YARN"
        "NodeManager:YARN"
        "Kafka:Kafka"
        "Master:Spark"
        "Worker:Spark"
        "HiveServer2:Hive"
        "redis-server:Redis"
        "nginx:Nginx"
        "Bootstrap:后端"
        "hao-ran-music-backend:后端"
    )

    local step=1 total=${#services[@]}

    for svc_info in "${services[@]}"; do
        local proc_name=$(echo $svc_info | cut -d':' -f1)
        local svc_name=$(echo $svc_info | cut -d':' -f2)
        log_step "[${step}/${total}] ${svc_name}..."

        local count=0 nodes=""
        for node in "${ALL_NODES[@]}"; do
            if exec_remote ${node} "jps 2>/dev/null" | grep -q "${proc_name}"; then
                ((count++))
                nodes="${nodes} $(get_node_name $node)"
            fi
        done

        if [ $count -gt 0 ]; then
            log_ok "${svc_name} (${count}个)${nodes}"
        else
            log_fail "${svc_name} 未运行"
        fi
        ((step++))
    done

    echo ""
    log_info "健康检查完成"
    echo "=========================================="
    echo ""
}




init_cluster() {
    echo ""
    echo "=========================================="
    echo "     初始化集群"
    echo "=========================================="
    echo ""

    log_step "创建数据目录..."

    for node in "${ALL_NODES[@]}"; do
        local name=$(get_node_name $node)
        echo -n "  ${name}: "
        exec_remote ${node} "
            mkdir -p /sdb1/haoranmusicData/{zookeeper,kafka,redis,mysql,nginx}
            mkdir -p /sdb1/hdfsdata/{namenode,namesecondary,datanode,journalnode}
            mkdir -p $(get_node_log_dir $node)/{hadoop,hive,spark,redis,kafka,zookeeper,mysql,nginx,backend}
            [ \"$(hostname -I | awk '{print \$1}')\" = \"${MASTER_IP}\" ] && mkdir -p /usr/local/soft/nginx-1.28.0/{cache,logs}
            chown -R hdfs:hdfs /sdb1/haoranmusicData 2>/dev/null
            chown -R hdfs:hdfs /sdb1/hdfsdata 2>/dev/null
            chown -R hdfs:hdfs $(get_node_log_dir $node) 2>/dev/null
            echo '完成'
        " "true"
    done

    echo ""
    log_info "集群初始化完成！"
    echo ""
    echo "下一步操作："
    echo "  1. 格式化HDFS (仅首次): ./hadoop.sh format"
    echo "  2. 启动集群: ./cluster.sh start"
    echo ""
}




show_urls() {
    echo ""
    echo "=========================================="
    echo "     服务访问地址"
    echo "=========================================="
    echo ""
    echo "前端:     http://${NODE1_IP}/"
    echo "后端:     http://${NODE1_IP}:9090/api"
    echo "HDFS:     http://${NODE1_IP}:9870/"
    echo "YARN:     http://${NODE1_IP}:8088/"
    echo "Spark:    http://${NODE1_IP}:18080/"
    echo "Redis:    ${NODE1_IP}:6379"
    echo "Kafka:    ${NODE1_IP}:9092"
    echo "Nginx:    http://${NODE1_IP}:3223/"
    echo ""
    echo "=========================================="
}




show_help() {
    echo ""
    echo "=========================================="
    echo "  集群管理脚本 v3.1"
    echo "  @author xiaohaoyiqu"
    echo "=========================================="
    echo ""
    echo "用法: $0 {start|stop|restart|status|health|init|help}"
    echo ""
    echo "命令:"
    echo "  start   - 启动所有服务"
    echo "  stop    - 停止所有服务"
    echo "  restart - 重启所有服务"
    echo "  status  - 查看服务状态"
    echo "  health  - 健康检查"
    echo "  init    - 初始化集群目录"
    echo "  help    - 显示帮助"
    echo ""
    echo "集群节点:"
    echo "  ${NODE1_NAME}: ${NODE1_IP} (主节点)"
    echo "  ${NODE2_NAME}: ${NODE2_IP}"
    echo "  ${NODE3_NAME}: ${NODE3_IP}"
    echo ""
echo "脚本路径:"
echo "  node1: /sdb1/myprojoct/haoranmusic/scripts"
echo "  node2/3: /sdb1/scripts/env.sh（仅环境变量）"
echo ""
echo "启动顺序:"
echo "  node1控制: ZooKeeper -> Hadoop -> Kafka -> Redis -> MySQL -> Hive -> Spark -> Backend -> Nginx"
    echo ""
    echo "=========================================="
}




COMMAND="${1:-}"

case "${COMMAND}" in
    start)
        check_all_ssh || exit 1
        start
        ;;
    stop)
        stop
        ;;
    restart)
        check_all_ssh || exit 1
        restart
        ;;
    status)
        status
        ;;
    health)
        check_all_ssh || exit 1
        health
        ;;
    init)
        check_all_ssh || exit 1
        init_cluster
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        show_help
        exit 1
        ;;
esac
