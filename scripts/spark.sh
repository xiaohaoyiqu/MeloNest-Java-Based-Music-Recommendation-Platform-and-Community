#!/bin/bash




SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "${SCRIPT_DIR}/env.sh" ]; then
    source "${SCRIPT_DIR}/env.sh"
else
    echo "[ERROR] 环境变量文件不存在: ${SCRIPT_DIR}/env.sh"
    exit 1
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SPARK_WORKER_START_SCRIPT="${SPARK_HOME}/sbin/start-worker.sh"
SPARK_WORKER_STOP_SCRIPT="${SPARK_HOME}/sbin/stop-worker.sh"
if [ ! -x "${SPARK_WORKER_START_SCRIPT}" ]; then
    SPARK_WORKER_START_SCRIPT="${SPARK_HOME}/sbin/start-slave.sh"
    SPARK_WORKER_STOP_SCRIPT="${SPARK_HOME}/sbin/stop-slave.sh"
fi

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }


exec_hdfs() {
    if [ "$(whoami)" = "${CLUSTER_USER}" ]; then
        eval "$@"
    else
        sudo -u ${CLUSTER_USER} bash -c "source /etc/profile >/dev/null 2>&1; $*" 2>&1
    fi
}


ssh_exec() {
    local node=$1 cmd=$2 env_sh=$(get_node_env_sh $node)
    if [ "$(whoami)" = "${CLUSTER_USER}" ]; then
        ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null && source /etc/profile 2>/dev/null; $cmd" 2>&1
    else
        sudo -u ${CLUSTER_USER} ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null && source /etc/profile 2>/dev/null; $cmd" 2>&1
    fi
}



get_worker_webui_port() {
    local node=$1
    if [ "$node" = "${NODE3_IP}" ]; then
        echo "${NODE3_SPARK_WORKER_WEBUI_PORT:-8083}"
    else
        echo "${SPARK_WORKER_WEBUI_PORT:-8081}"
    fi
}


get_deploy_mode() {
    if [ -f "${SPARK_HOME}/conf/spark-defaults.conf" ]; then
        local master_config=$(grep "^spark.master" ${SPARK_HOME}/conf/spark-defaults.conf 2>/dev/null | awk '{print $2}')
        [ "$master_config" = "yarn" ] && echo "yarn" || echo "standalone"
    else
        echo "standalone"
    fi
}


init_log_dirs() {
    log_step "初始化Spark日志目录..."
    echo ""
    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local node_name=$(get_node_name "$node")
        echo -n "  ${node_name}: "
        local base_log_dir=$(get_node_log_dir $node)
        base_log_dir="${base_log_dir}/spark"
        if [ "$node" = "$(get_current_ip)" ]; then
            exec_hdfs "mkdir -p ${base_log_dir} ${base_log_dir}/sql ${base_log_dir}/mllib ${base_log_dir}/scheduling" 2>/dev/null
        else
            ssh_exec "$node" "mkdir -p ${base_log_dir} ${base_log_dir}/sql ${base_log_dir}/mllib ${base_log_dir}/scheduling" 2>/dev/null
        fi
        echo -e "${GREEN}完成${NC}"
    done
    echo ""
}


start_history_server() {
    if [ "${HAORAN_SPARK_HISTORY_ENABLED:-false}" != "true" ]; then
        echo "History Server:"
        echo -e "  ${NODE1_NAME}: ${YELLOW}已按配置禁用${NC}"
        echo ""
        return 0
    fi
    echo "History Server:"
    if [ "$(get_current_ip)" != "${NODE1_IP}" ]; then
        echo -e "  ${NODE1_NAME}: ${YELLOW}应在node1启动${NC}"
        return 0
    fi
    if exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+HistoryServer$'"; then
        echo -e "  ${NODE1_NAME}: ${YELLOW}已运行${NC}"
    else
        exec_hdfs "${SPARK_HOME}/sbin/start-history-server.sh" 2>/dev/null
        sleep 3
        if exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+HistoryServer$'"; then
            echo -e "  ${NODE1_NAME}: ${GREEN}启动成功${NC}"
        else
            echo -e "  ${NODE1_NAME}: ${RED}启动失败${NC}"
        fi
    fi
    echo ""
}


start_standalone() {
    log_step "启动Standalone模式..."
    echo ""


    echo "Master:"
    if [ "$(get_current_ip)" = "${NODE1_IP}" ]; then
        if exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+Master$'"; then
            echo -e "  ${NODE1_NAME} Master: ${YELLOW}已运行${NC}"
        else
            exec_hdfs "${SPARK_HOME}/sbin/start-master.sh" 2>/dev/null
            sleep 3
            exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+Master$'" && echo -e "  ${NODE1_NAME} Master: ${GREEN}启动成功${NC}" || echo -e "  ${NODE1_NAME} Master: ${RED}启动失败${NC}"
        fi
    else
        local result=$(ssh_exec "${NODE1_IP}" "source /etc/profile 2>/dev/null; if jps | grep -qE '^[0-9]+[[:space:]]+Master$'; then echo 'running'; else \${SPARK_HOME}/sbin/start-master.sh 2>/dev/null; sleep 3; jps | grep -qE '^[0-9]+[[:space:]]+Master$' && echo 'started' || echo 'failed'; fi")
        case "$result" in
            *running*) echo -e "  ${NODE1_NAME} Master: ${YELLOW}已运行${NC}" ;;
            *started*) echo -e "  ${NODE1_NAME} Master: ${GREEN}启动成功${NC}" ;;
            *)       echo -e "  ${NODE1_NAME} Master: ${RED}启动失败${NC}" ;;
        esac
    fi
    echo ""


    echo "Workers:"
    local master_url="spark://${NODE1_IP}:${SPARK_MASTER_PORT:-7077}"
    for node in "${WORKER_NODES[@]}"; do
        local node_name=$(get_node_name "$node")
        local worker_webui_port=$(get_worker_webui_port "$node")
        echo -n "  ${node_name}: "
        if [ "$node" = "$(get_current_ip)" ]; then
            if exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+Worker$'"; then
                echo -e "${YELLOW}已运行${NC}"
            else
                exec_hdfs "SPARK_WORKER_WEBUI_PORT=${worker_webui_port} ${SPARK_WORKER_START_SCRIPT} ${master_url}" 2>/dev/null
                sleep 3
                exec_hdfs "jps | grep -qE '^[0-9]+[[:space:]]+Worker$'" && echo -e "${GREEN}启动成功${NC}" || echo -e "${RED}启动失败${NC}"
            fi
        else
                local result=$(ssh_exec "$node" "source /etc/profile 2>/dev/null; if jps | grep -qE '^[0-9]+[[:space:]]+Worker$'; then echo 'running'; else SPARK_WORKER_WEBUI_PORT=${worker_webui_port} ${SPARK_WORKER_START_SCRIPT} ${master_url} 2>/dev/null; sleep 3; jps | grep -qE '^[0-9]+[[:space:]]+Worker$' && echo 'started' || echo 'failed'; fi")
            case "$result" in
                *running*) echo -e "${YELLOW}已运行${NC}" ;;
                *started*) echo -e "${GREEN}启动成功${NC}" ;;
                *)       echo -e "${RED}启动失败${NC}" ;;
            esac
        fi
    done
    echo ""

    start_history_server
}


start_yarn_mode() {
    log_step "启动YARN模式..."
    echo ""
    log_info "Spark运行在YARN上，只需启动History Server"
    echo ""
    start_history_server
}

start() {
    echo ""
    echo "=========================================="
    echo "     启动Spark集群"
    echo "=========================================="
    echo ""

    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    log_info "集群节点: ${NODE1_IP}, ${NODE2_IP}, ${NODE3_IP}"
    echo ""

    init_log_dirs

    local deploy_mode=$(get_deploy_mode)
    log_info "检测到部署模式: ${deploy_mode}"
    echo ""

    [ "$deploy_mode" = "yarn" ] && start_yarn_mode || start_standalone

    log_info "Spark服务启动完成！"
    echo ""
    echo "服务地址:"
    if [ "$deploy_mode" = "yarn" ]; then
        echo "  - YARN ResourceManager: http://${MASTER_IP}:${YARN_RM_WEB_PORT}/"
        echo "  - History Server:      http://${NODE1_IP}:18080/"
    else
        echo "  - Master:              spark://${NODE1_IP}:${SPARK_MASTER_PORT:-7077}/"
        echo "  - Master WebUI:        http://${NODE1_IP}:${SPARK_MASTER_WEB_PORT:-8080}/"
        echo "  - History Server:      http://${NODE1_IP}:18080/"
    fi
    echo ""
}

stop() {
    echo ""
    echo "=========================================="
    echo "     停止Spark集群"
    echo "=========================================="
    echo ""

    local deploy_mode=$(get_deploy_mode)

    log_step "停止History Server..."
    exec_hdfs "${SPARK_HOME}/sbin/stop-history-server.sh" 2>/dev/null || true
    echo ""

    if [ "$deploy_mode" = "standalone" ]; then
        log_step "停止Workers..."
        for node in "${WORKER_NODES[@]}"; do
            [ "$node" = "$(get_current_ip)" ] && exec_hdfs "${SPARK_WORKER_STOP_SCRIPT}" 2>/dev/null || ssh_exec "$node" "${SPARK_WORKER_STOP_SCRIPT}" 2>/dev/null || true
        done
        echo ""

        log_step "停止Master..."
        exec_hdfs "${SPARK_HOME}/sbin/stop-master.sh" 2>/dev/null || true
        echo ""
    fi

    pkill -9 -f Worker 2>/dev/null || true
    pkill -9 -f Master 2>/dev/null || true
    pkill -9 -f HistoryServer 2>/dev/null || true

    sleep 2
    log_info "Spark服务已停止"
    echo ""
}

restart() {
    stop
    sleep 5
    start
}

status() {
    echo ""
    echo "=========================================="
    echo "     Spark集群状态"
    echo "=========================================="
    echo ""

    echo "当前节点: $(hostname) ($(get_current_ip))"
    echo "执行用户: $(whoami)"
    echo ""
    echo "部署模式: $(get_deploy_mode)"
    echo ""

    echo "----------------------------------------"
    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local node_name=$(get_node_name "$node")
        echo "${node_name} (${node}):"

        for proc in Master Worker HistoryServer; do
            local pid=""
            if [ "$node" = "$(get_current_ip)" ]; then
                pid=$(exec_hdfs "jps 2>/dev/null" | awk -v process="$proc" '$2 == process {print $1}')
            else
                local env_sh=$(get_node_env_sh $node)
                pid=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null && source /etc/profile 2>/dev/null && jps 2>/dev/null" | awk -v process="$proc" '$2 == process {print $1}')
            fi
            if [ -n "$pid" ]; then
                echo "  $proc:        ${GREEN}[RUNNING]${NC} (PID: ${pid})"
            else
                echo "  $proc:        ${YELLOW}[STOPPED]${NC}"
            fi
        done
        echo ""
    done
    echo "=========================================="
}

COMMAND="${1:-start}"

case "${COMMAND}" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Spark集群管理脚本"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动Spark集群"
        echo "  stop    - 停止Spark集群"
        echo "  restart - 重启Spark集群"
        echo "  status  - 查看Spark集群状态"
        echo ""
        echo "支持模式:"
        echo "  - YARN:       History Server (Spark on YARN)"
        echo "  - Standalone: Master + Workers + History Server"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
