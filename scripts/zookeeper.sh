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

exec_hdfs() {
    if [ "$(whoami)" = "${CLUSTER_USER}" ]; then
        eval "$@"
    else
        sudo -u ${CLUSTER_USER} bash -c "source /etc/profile >/dev/null 2>&1; $*" 2>&1
    fi
}

get_myid() {
    case "$1" in
        ${NODE1_IP}) echo "1" ;;
        ${NODE2_IP}) echo "2" ;;
        ${NODE3_IP}) echo "3" ;;
        *) echo "0" ;;
    esac
}

init_myid() {
    local node=$1
    local myid=$(get_myid $node)
    local data_dir="${ZOOKEEPER_INSTALL_DATA_DIR:-/usr/local/soft/zookeeper-3.7.2/data}"

    if [ "$node" = "$(get_current_ip)" ]; then
        if [ ! -f "${data_dir}/myid" ]; then
            exec_hdfs "mkdir -p ${data_dir}"
            exec_hdfs "echo ${myid} > ${data_dir}/myid"
            log_info "创建 myid 文件: ${data_dir}/myid = ${myid}"
        fi
    else
        ssh_exec "$node" "
            if [ ! -f '${data_dir}/myid' ]; then
                mkdir -p ${data_dir} 2>/dev/null
                echo ${myid} > ${data_dir}/myid
                echo '[INFO] 创建 myid 文件: ${data_dir}/myid = ${myid}'
            fi
        " >/dev/null 2>&1
    fi
}

start_node() {
    local node=$1
    local myid=$(get_myid $node)

    echo -n "  节点${myid} (${node}): "

    init_myid "$node"

    local is_running=""
    if [ "$node" = "$(get_current_ip)" ]; then
        exec_hdfs "jps | grep -q QuorumPeerMain" && is_running="true"
    else
        ssh_exec "$node" "jps | grep -q QuorumPeerMain" >/dev/null 2>&1 && is_running="true"
    fi

    if [ "$is_running" = "true" ]; then
        echo -e "${YELLOW}已运行${NC}"
        return 0
    fi

    if [ "$node" = "$(get_current_ip)" ]; then
        local zk_log_dir=$(get_node_log_dir $node)
        zk_log_dir="${zk_log_dir}/zookeeper"
        local env_sh=$(get_node_env_sh $node)
        exec_hdfs "source ${env_sh} >/dev/null 2>&1; mkdir -p ${zk_log_dir} 2>/dev/null; export JVMFLAGS='-DlogDir=${zk_log_dir}'; ${ZOOKEEPER_HOME}/bin/zkServer.sh start" >/dev/null 2>&1
    else
        local zk_log_dir=$(get_node_log_dir $node)
        zk_log_dir="${zk_log_dir}/zookeeper"
        local env_sh=$(get_node_env_sh $node)
        ssh_exec "$node" "mkdir -p ${zk_log_dir} 2>/dev/null; export JVMFLAGS='-DlogDir=${zk_log_dir}'; ${ZOOKEEPER_HOME}/bin/zkServer.sh start" >/dev/null 2>&1
    fi

    sleep 2

    if [ "$node" = "$(get_current_ip)" ]; then
        if exec_hdfs "jps | grep -q QuorumPeerMain"; then
            echo -e "${GREEN}启动成功${NC}"
            return 0
        fi
    else
        if ssh_exec "$node" "jps | grep -q QuorumPeerMain" >/dev/null 2>&1; then
            echo -e "${GREEN}启动成功${NC}"
            return 0
        fi
    fi

    echo -e "${RED}启动失败${NC}"
    return 1
}

stop_node() {
    local node=$1
    local myid=$(get_myid $node)

    echo -n "  节点${myid} (${node}): "

    if [ "$node" = "$(get_current_ip)" ]; then
        exec_hdfs "${ZOOKEEPER_HOME}/bin/zkServer.sh stop" >/dev/null 2>&1
    else
        ssh_exec "$node" "${ZOOKEEPER_HOME}/bin/zkServer.sh stop" >/dev/null 2>&1
    fi

    sleep 1
    echo -e "${GREEN}已停止${NC}"
}

get_node_role() {
    local node=$1
    local result=""
    local pid=""
    local role="not_running"

    if [ "$node" = "$(get_current_ip)" ]; then
        if exec_hdfs "jps | grep -q QuorumPeerMain"; then
            pid=$(exec_hdfs "jps | grep QuorumPeerMain | awk '{print \$1}'")
            result=$(exec_hdfs "${ZOOKEEPER_HOME}/bin/zkServer.sh status" 2>&1)
        fi
    else
        result=$(ssh_exec "$node" "
            source /etc/profile 2>/dev/null
            if jps | grep -q QuorumPeerMain; then
                jps | grep QuorumPeerMain | awk '{print \$1}'
                ${ZOOKEEPER_HOME}/bin/zkServer.sh status 2>&1
            fi
        " 2>/dev/null)

        if [ -n "$result" ]; then
            pid=$(echo "$result" | head -1)
        fi
    fi

    if [ -n "$pid" ]; then
        if echo "$result" | grep -q "Mode: leader"; then
            role="leader"
        elif echo "$result" | grep -q "Mode: follower"; then
            role="follower"
        else
            role="electing"
        fi
    fi

    echo "${role}|${pid}"
}

start() {
    echo ""
    echo "=========================================="
    echo "     启动ZooKeeper集群"
    echo "=========================================="
    echo ""

    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    log_info "集群节点: ${NODE1_IP}(myid:1), ${NODE2_IP}(myid:2), ${NODE3_IP}(myid:3)"
    echo ""

    log_step "启动ZooKeeper服务..."
    echo ""

    local success_count=0
    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        start_node "$node" && success_count=$((success_count + 1))
    done

    echo ""

    if [ $success_count -ge 2 ]; then
        log_step "等待角色选举完成..."
        sleep 5
        echo ""

        status

        log_info "ZooKeeper集群启动完成！"
    else
        log_error "ZooKeeper集群启动失败（成功: ${success_count}/3）"
        return 1
    fi
    echo ""
}

stop() {
    echo ""
    echo "=========================================="
    echo "     停止ZooKeeper集群"
    echo "=========================================="
    echo ""

    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        stop_node "$node"
    done

    echo ""

    pkill -9 -f QuorumPeerMain 2>/dev/null || true

    log_info "ZooKeeper集群已停止"
    echo ""
}

restart() {
    stop
    sleep 3
    start
}

status() {
    echo ""
    echo "=========================================="
    echo "     ZooKeeper集群状态"
    echo "=========================================="
    echo ""

    local current_ip=$(get_current_ip)
    local current_node=$(hostname)
    local current_user=$(whoami)

    echo "当前节点: ${current_node} (${current_ip})"
    echo "执行用户: ${current_user}"
    echo ""

    echo "----------------------------------------"
    echo "节点状态:"
    echo "----------------------------------------"

    local has_leader=false
    local running_count=0

    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local myid=$(get_myid $node)
        local result=$(get_node_role "$node")
        local role=$(echo "$result" | cut -d'|' -f1)
        local pid=$(echo "$result" | cut -d'|' -f2)

        echo -n "  节点${myid} (${node}): "

        case "$role" in
            leader)
                echo -e "${GREEN}[LEADER]${NC} (PID: ${pid})"
                has_leader=true
                running_count=$((running_count + 1))
                ;;
            follower)
                echo -e "${CYAN}[FOLLOWER]${NC} (PID: ${pid})"
                running_count=$((running_count + 1))
                ;;
            electing)
                echo -e "${YELLOW}[ELECTING]${NC} (PID: ${pid})"
                running_count=$((running_count + 1))
                ;;
            *)
                echo -e "${YELLOW}[STOPPED]${NC}"
                ;;
        esac
    done

    echo ""
    echo "----------------------------------------"
    echo "  运行节点: ${running_count}/3"

    if [ "$has_leader" = true ]; then
        echo -e "  集群状态: ${GREEN}[正常]${NC} 有Leader节点"
    elif [ "$running_count" -eq 0 ]; then
        echo -e "  集群状态: ${RED}[未运行]${NC}"
    elif [ "$running_count" -lt 2 ]; then
        echo -e "  集群状态: ${RED}[异常]${NC} 节点数不足（至少需要2个节点）"
    else
        echo -e "  集群状态: ${YELLOW}[选举中]${NC}"
    fi
    echo "----------------------------------------"

    if [ $running_count -ge 2 ]; then
        echo ""
        echo "----------------------------------------"
        echo "集群连接测试:"
        echo "----------------------------------------"
        local test_result=$(echo "ruok" | nc ${NODE1_IP} ${ZK_CLIENT_PORT} 2>/dev/null)
        if [ "$test_result" = "imok" ]; then
            echo -e "  连接状态: ${GREEN}[正常]${NC}"
        else
            echo -e "  连接状态: ${YELLOW}[无法连接]${NC}"
        fi
        echo "----------------------------------------"
    fi

    echo ""
    echo "=========================================="
}

client() {
    local target_node="${2:-${NODE1_IP}}"

    echo ""
    log_info "连接到ZooKeeper: ${target_node}:${ZK_CLIENT_PORT}"
    echo ""

    exec_hdfs "${ZOOKEEPER_HOME}/bin/zkCli.sh -server ${target_node}:${ZK_CLIENT_PORT}"
}

clean() {
    echo ""
    log_warn "警告：将清理所有ZooKeeper数据！"
    echo ""
    read -p "确认执行清理？输入 yes 继续: " confirm

    if [ "$confirm" != "yes" ]; then
        log_info "取消清理"
        return 0
    fi

    log_step "停止ZooKeeper集群..."
    stop
    sleep 2

    log_step "清理数据目录..."
    local data_dir="${ZOOKEEPER_INSTALL_DATA_DIR:-/usr/local/soft/zookeeper-3.7.2/data}"

    for node in ${NODE1_IP} ${NODE2_IP} ${NODE3_IP}; do
        local myid=$(get_myid $node)
        echo -n "  节点${myid} (${node}): "

        if [ "$node" = "$(get_current_ip)" ]; then
            exec_hdfs "rm -rf ${data_dir:?}/*"
            exec_hdfs "rm -f ${data_dir}/myid"
        else
            ssh_exec "$node" "rm -rf ${data_dir:?}/*" 2>/dev/null
            ssh_exec "$node" "rm -f ${data_dir}/myid" 2>/dev/null
        fi

        echo -e "${GREEN}已清理${NC}"
    done

    echo ""
    log_info "数据清理完成，需要重新初始化myid"
    echo ""
}

four_word() {
    local cmd="${2:-mntr}"
    local node="${1:-}"

    echo ""
    echo "=========================================="
    echo "     ZooKeeper 四字命令"
    echo "=========================================="
    echo ""

    declare -A cmd_help
    cmd_help=(["ruok"]="检查服务是否运行"
             ["stat"]="查看服务器状态和连接信息"
             ["mntr"]="监控统计信息（推荐）"
             ["srvr"]="查看服务器详细信息"
             ["cons"]="查看完整客户端连接信息"
             ["wchs"]="查看Watch信息摘要"
             ["wchc"]="按Session查看Watch信息"
             ["dirs"]="查看节点数和临时节点数"
             ["conf"]="查看服务配置信息"
             ["isro"]="查看读写模式"
             ["envi"]="查看环境配置"
             ["crst"]="重置所有连接统计"
             ["srst"]="重置服务器统计"
             ["dump"]="列出未处理的会话和临时节点")

    if [ -z "$node" ]; then
        for zk_node in "${ZK_NODES[@]}"; do
            local node_name=$(get_node_name $zk_node)
            echo "=========================================="
            echo "  节点: ${node_name} (${zk_node})"
            echo "  命令: echo $cmd | nc ${zk_node} 2181"
            echo "=========================================="

            local result=$(echo $cmd | nc ${zk_node} 2181 2>/dev/null)
            if [ -n "$result" ]; then
                echo "$result"
            else
                log_warn "无响应，请检查ZooKeeper是否运行"
            fi
            echo ""
        done
    else
        local node_name=$(get_node_name $node)
        echo "=========================================="
        echo "  节点: ${node_name} (${node})"
        echo "  命令: $cmd"
        echo "=========================================="

        local result=$(echo $cmd | nc ${node} 2181 2>/dev/null)
        if [ -n "$result" ]; then
            echo "$result"
        else
            log_warn "无响应，请检查ZooKeeper是否运行"
        fi
        echo ""
    fi

    echo "=========================================="
    log_info "四字命令说明"
    echo "=========================================="
    for k in "${!cmd_help[@]}"; do
        printf "  %-10s - %s\n" "$k" "${cmd_help[$k]}"
    done
    echo ""
    echo "使用示例:"
    echo "  ./zookeeper.sh 4lw           # 所有节点执行mntr"
    echo "  ./zookeeper.sh 4lw node1     # node1执行mntr"
    echo "  ./zookeeper.sh 4lw node1 ruok  # node1执行ruok"
    echo ""
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
    client)
        client "$@"
        ;;
    clean)
        clean
        ;;
    4lw|four)
        four_word "$2" "$3"
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     ZooKeeper集群管理脚本 v4.0.0"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|restart|status|client|clean|4lw}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动整个ZooKeeper集群（所有节点）"
        echo "  stop    - 停止整个ZooKeeper集群（所有节点）"
        echo "  restart - 重启整个ZooKeeper集群（所有节点）"
        echo "  status  - 查看ZooKeeper集群状态和角色"
        echo "  client  - 连接到ZooKeeper客户端（可指定节点）"
        echo "  clean   - 清理所有ZooKeeper数据（危险操作）"
        echo "  4lw     - 执行四字命令监控"
        echo ""
        echo "集群节点:"
        echo "  ${NODE1_NAME} (${NODE1_IP}) - myid: 1"
        echo "  ${NODE2_NAME} (${NODE2_IP}) - myid: 2"
        echo "  ${NODE3_NAME} (${NODE3_IP}) - myid: 3"
        echo ""
        echo "使用示例:"
        echo "  ./zookeeper.sh start              # 启动集群"
        echo "  ./zookeeper.sh client             # 连接到node1"
        echo "  ./zookeeper.sh client ${NODE2_IP} # 连接到node2"
        echo "  ./zookeeper.sh 4lw                # 所有节点监控"
        echo "  ./zookeeper.sh 4lw node1 ruok     # node1执行ruok"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac