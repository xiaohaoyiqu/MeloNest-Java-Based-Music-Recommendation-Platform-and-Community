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
CYAN='\033[0;36m'
NC='\033[0m'

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
    local node=$1
    local cmd=$2
    local current_user=$(whoami)

    local remote_env_sh
    if [ "$node" = "$NODE1_IP" ]; then
        remote_env_sh="/sdb1/myprojoct/haoranmusic/scripts/env.sh"
    else
        remote_env_sh="/sdb1/scripts/env.sh"
    fi

    if [ "$current_user" = "${CLUSTER_USER}" ]; then
        ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source /etc/profile 2>/dev/null; source ${remote_env_sh} 2>/dev/null; $cmd" 2>&1
    else
        sudo -u ${CLUSTER_USER} ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source /etc/profile 2>/dev/null; source ${remote_env_sh} 2>/dev/null; $cmd" 2>&1
    fi
}

journalnode_rpc_ready() {
    local node="$1"
    local check_command="netstat -lnt 2>/dev/null | grep -q ':8485 '"

    if [ "$node" = "$(get_current_ip)" ]; then
        exec_hdfs "$check_command"
    else
        ssh_exec "$node" "$check_command" >/dev/null 2>&1
    fi
}

wait_for_journalnode_quorum() {
    local max_attempts=60
    local attempt

    for attempt in $(seq 1 "$max_attempts"); do
        local ready_count=0
        local node
        for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
            if journalnode_rpc_ready "$node"; then
                ready_count=$((ready_count + 1))
            fi
        done
        if [ "$ready_count" -eq 3 ]; then
            log_info "JournalNode RPC 全部就绪 (3/3)"
            return 0
        fi
        if [ $((attempt % 5)) -eq 0 ]; then
            log_info "等待 JournalNode RPC 8485 监听 (${ready_count}/3)..."
        fi
        sleep 2
    done

    log_error "JournalNode RPC 未在 120 秒内全部就绪，停止启动 NameNode"
    return 1
}

init_dirs() {
    log_step "初始化数据目录..."
    echo ""

    local dirs="${NN_DIR} ${DN_DIR} ${JN_DIR} /sdb1/hdfsdata/namesecondary"

    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local node_name=$(get_node_name "$node")
        echo -n "  ${node_name}: "

        if [ "$node" = "$(get_current_ip)" ]; then
            exec_hdfs "mkdir -p ${dirs}" 2>/dev/null
        else
            ssh_exec "$node" "mkdir -p ${dirs}" 2>/dev/null
        fi

        echo -e "${GREEN}完成${NC}"
    done
    echo ""
}

start_hdfs() {
    log_step "启动HDFS..."
    echo ""

    echo "启动JournalNode..."
    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local node_name=$(get_node_name "$node")
        echo -n "  ${node_name} JournalNode: "

        if [ "$node" = "$(get_current_ip)" ]; then
            if exec_hdfs "jps | grep -q JournalNode"; then
                echo -e "${YELLOW}已运行${NC}"
            else
                local env_sh="/sdb1/scripts/env.sh"
                if [ "$node" = "$NODE1_IP" ]; then
                    env_sh="/sdb1/myprojoct/haoranmusic/scripts/env.sh"
                fi
                exec_hdfs "source ${env_sh} >/dev/null 2>&1; ${HADOOP_HOME}/bin/hdfs --daemon start journalnode" 2>/dev/null
                sleep 2
                exec_hdfs "jps | grep -q JournalNode" && echo -e "${GREEN}启动成功${NC}" || echo -e "${RED}启动失败${NC}"
            fi
        else
            local result=$(ssh_exec "$node" "jps | grep -q JournalNode && echo 'running' || echo 'stopped'")
            if [ "$result" = "running" ]; then
                echo -e "${YELLOW}已运行${NC}"
            else
                ssh_exec "$node" "\${HADOOP_HOME}/bin/hdfs --daemon start journalnode" 2>/dev/null
                sleep 2
                result=$(ssh_exec "$node" "jps | grep -q JournalNode && echo 'ok' || echo 'fail'")
                [ "$result" = "ok" ] && echo -e "${GREEN}启动成功${NC}" || echo -e "${RED}启动失败${NC}"
            fi
        fi
    done
    echo ""

    log_info "等待JournalNode RPC就绪..."
    wait_for_journalnode_quorum || return 1
    echo ""

    log_info "执行 start-dfs.sh..."
    local env_sh="/sdb1/scripts/env.sh"
    if [ "$(get_current_ip)" = "$NODE1_IP" ]; then
        env_sh="/sdb1/myprojoct/haoranmusic/scripts/env.sh"
    fi
    exec_hdfs "source ${env_sh} >/dev/null 2>&1; \${HADOOP_HOME}/sbin/start-dfs.sh"
    echo ""

    sleep 10

    local nn_count=0
    for ip in ${NODE1_IP} ${NODE2_IP}; do
        if [ "$ip" = "$(get_current_ip)" ]; then
            exec_hdfs "jps | grep -q NameNode" && nn_count=$((nn_count + 1))
        else
            ssh_exec "$ip" "jps | grep -q NameNode" >/dev/null 2>&1 && nn_count=$((nn_count + 1))
        fi
    done

    if [ $nn_count -ge 1 ]; then
        log_info "HDFS启动成功 (NameNode: ${nn_count}/2)"
    else
        log_error "HDFS启动失败"
        return 1
    fi
}

stop_hdfs() {
    log_step "停止HDFS..."
    echo ""

    exec_hdfs "${HADOOP_HOME}/sbin/stop-dfs.sh"
    echo ""

    sleep 5

    pkill -9 -f NameNode DataNode JournalNode DFSZKFailoverController 2>/dev/null || true

    log_info "HDFS已停止"
}

start_yarn() {
    log_step "启动YARN..."
    echo ""

    local env_sh="/sdb1/scripts/env.sh"
    if [ "$(get_current_ip)" = "$NODE1_IP" ]; then
        env_sh="/sdb1/myprojoct/haoranmusic/scripts/env.sh"
    fi
    exec_hdfs "source ${env_sh} >/dev/null 2>&1; ${HADOOP_HOME}/sbin/start-yarn.sh"
    echo ""

    sleep 5

    local rm_count=0
    for ip in ${NODE1_IP} ${NODE2_IP}; do
        if [ "$ip" = "$(get_current_ip)" ]; then
            exec_hdfs "jps | grep -q ResourceManager" && rm_count=$((rm_count + 1))
        else
            ssh_exec "$ip" "jps | grep -q ResourceManager" >/dev/null 2>&1 && rm_count=$((rm_count + 1))
        fi
    done

    if [ $rm_count -ge 1 ]; then
        log_info "YARN启动成功 (ResourceManager: ${rm_count}/2)"
    else
        log_error "YARN启动失败"
        return 1
    fi
}

stop_yarn() {
    log_step "停止YARN..."
    echo ""

    exec_hdfs "${HADOOP_HOME}/sbin/stop-yarn.sh"
    echo ""

    sleep 3

    pkill -9 -f ResourceManager NodeManager 2>/dev/null || true

    log_info "YARN已停止"
}

start_historyserver() {
    log_step "启动历史日志服务器..."

    if [ "$(get_current_ip)" != "${NODE1_IP}" ]; then
        log_warn "历史日志服务器应在node1(${NODE1_IP})上启动"
        return 0
    fi

    if exec_hdfs "jps | grep -q JobHistoryServer"; then
        log_warn "历史日志服务器已在运行"
        return 0
    fi

    local env_sh="/sdb1/myprojoct/haoranmusic/scripts/env.sh"
    exec_hdfs "source ${env_sh} >/dev/null 2>&1; ${HADOOP_HOME}/sbin/mr-jobhistory-daemon.sh start historyserver"

    sleep 3
    if exec_hdfs "jps | grep -q JobHistoryServer"; then
        log_info "历史日志服务器启动成功"
        echo "  历史日志服务地址: http://${MASTER_IP}:19888/"
    else
        log_error "历史日志服务器启动失败"
    fi
}

stop_historyserver() {
    log_step "停止历史日志服务器..."

    if [ "$(get_current_ip)" = "${NODE1_IP}" ]; then
        exec_hdfs "${HADOOP_HOME}/sbin/mr-jobhistory-daemon.sh stop historyserver" 2>/dev/null || true
    fi
    pkill -9 -f JobHistoryServer 2>/dev/null || true

    log_info "历史日志服务器已停止"
}

wait_for_safemode() {
    log_step "等待HDFS退出安全模式..."

    local max_wait=120
    local waited=0

    while [ $waited -lt $max_wait ]; do
        local safemode=$(exec_hdfs "${HADOOP_HOME}/bin/hdfs dfsadmin -safemode get" 2>/dev/null | grep "Safe mode is OFF")
        if [ -n "$safemode" ]; then
            log_info "HDFS已退出安全模式"
            return 0
        fi

        exec_hdfs "${HADOOP_HOME}/bin/hdfs dfsadmin -safemode leave" 2>/dev/null || true
        echo -n "."
        sleep 5
        waited=$((waited + 5))
    done
    echo ""
    log_warn "HDFS可能仍在安全模式，请检查"
}

start() {
    echo ""
    echo "=========================================="
    echo "     启动Hadoop集群"
    echo "=========================================="
    echo ""

    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    log_info "集群节点: ${NODE1_IP}, ${NODE2_IP}, ${NODE3_IP}"
    echo ""

    init_dirs

    start_hdfs || return 1
    echo ""

    start_yarn || return 1
    echo ""

    start_historyserver
    echo ""

    wait_for_safemode
    echo ""

    log_info "Hadoop集群启动完成！"
    echo ""
    echo "服务地址:"
    echo "  - HDFS WebUI:        http://${MASTER_IP}:${HDFS_NAMENODE_HTTP_PORT}/"
    echo "  - YARN WebUI:         http://${MASTER_IP}:${YARN_RM_WEB_PORT}/"
    echo "  - 历史日志服务:       http://${MASTER_IP}:19888/"
    echo ""
}

stop() {
    echo ""
    echo "=========================================="
    echo "     停止Hadoop集群"
    echo "=========================================="
    echo ""

    stop_historyserver
    echo ""

    stop_yarn
    echo ""

    stop_hdfs
    echo ""

    log_info "Hadoop集群已停止"
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
    echo "     Hadoop集群状态"
    echo "=========================================="
    echo ""

    local current_ip=$(get_current_ip)
    local current_node=$(hostname)
    local current_user=$(whoami)

    echo "当前节点: ${current_node} (${current_ip})"
    echo "执行用户: ${current_user}"
    echo ""

    echo "----------------------------------------"
    echo "HDFS进程:"
    echo "----------------------------------------"
    for proc in NameNode DataNode JournalNode DFSZKFailoverController; do
        echo -n "  ${proc}: "
        if exec_hdfs "jps | grep -q ${proc}"; then
            local pid=$(exec_hdfs "jps | grep ${proc} | awk '{print \$1}'")
            echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
        else
            echo -e "${YELLOW}[STOPPED]${NC}"
        fi
    done
    echo ""

    echo "----------------------------------------"
    echo "YARN进程:"
    echo "----------------------------------------"
    for proc in ResourceManager NodeManager; do
        echo -n "  ${proc}: "
        if exec_hdfs "jps | grep -q ${proc}"; then
            local pid=$(exec_hdfs "jps | grep ${proc} | awk '{print \$1}'")
            echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
        else
            echo -e "${YELLOW}[STOPPED]${NC}"
        fi
    done
    echo ""

    echo "----------------------------------------"
    echo "历史服务器:"
    echo "----------------------------------------"
    echo -n "  JobHistoryServer: "
    if exec_hdfs "jps | grep -q JobHistoryServer"; then
        local pid=$(exec_hdfs "jps | grep JobHistoryServer | awk '{print \$1}'")
        echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
    else
        echo -e "${YELLOW}[STOPPED]${NC}"
    fi
    echo ""

    echo "----------------------------------------"
    echo "HDFS HA状态:"
    exec_hdfs "${HADOOP_HOME}/bin/hdfs haadmin -getAllServiceState" 2>/dev/null || echo "无法获取HA状态"
    echo ""
    echo "YARN ResourceManager状态:"
    exec_hdfs "${HADOOP_HOME}/bin/yarn rmadmin -getServiceState rm1" 2>/dev/null
    exec_hdfs "${HADOOP_HOME}/bin/yarn rmadmin -getServiceState rm2" 2>/dev/null
    echo ""
    echo "=========================================="
}

format() {
    echo ""
    echo "=========================================="
    echo "     格式化功能已移除"
    echo "=========================================="
    echo ""
    log_warn "格式化操作需要手动执行，请按以下步骤操作："
    echo ""
    echo "1. 停止HDFS服务:"
    echo "   ./hadoop.sh stop"
    echo ""
    echo "2. 清空数据目录:"
    echo "   su - hdfs"
    echo "   rm -rf ${NN_DIR:?}/* ${DN_DIR:?}/* ${JN_DIR:?}/*"
    echo ""
    echo "3. 格式化NameNode (仅node1):"
    echo "   cd /sdb1/hadoop-3.3.6"
    echo "   bin/hdfs namenode -format"
    echo ""
    echo "4. 如果需要格式化ZKFC:"
    echo "   hdfs zkfc -formatZK -force"
    echo ""
    echo "5. 启动Hadoop:"
    echo "   ./hadoop.sh start"
    echo ""
    echo "=========================================="
}

COMMAND="${1:-start}"

case "${COMMAND}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    format)
        format
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Hadoop集群管理脚本 v5.0.0"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|restart|status|format}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动Hadoop集群（HDFS + YARN + 历史服务器）"
        echo "  stop    - 停止Hadoop集群"
        echo "  restart - 重启Hadoop集群"
        echo "  status  - 查看集群状态"
        echo "  format  - 显示手动格式化步骤（不执行自动格式化）"
        echo ""
        echo "服务组件:"
        echo "  HDFS:   NameNode(HA) + DataNode + JournalNode + ZKFC"
        echo "  YARN:   ResourceManager(HA) + NodeManager"
        echo "  历史服务器: JobHistoryServer (端口: 19888)"
        echo ""
        echo "节点角色:"
        echo "  ${NODE1_NAME} (${NODE1_IP}) - NameNode(active), ResourceManager(active)"
        echo "  ${NODE2_NAME} (${NODE2_IP}) - NameNode(standby), ResourceManager(standby)"
        echo "  ${NODE3_NAME} (${NODE3_IP}) - DataNode, NodeManager, JournalNode"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
