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

init_log_dirs() {
    log_step "初始化Hive日志目录..."
    echo ""

    local base_log_dir="${PROJECT_LOG_DIR}/hive"

    exec_hdfs "mkdir -p ${base_log_dir} ${base_log_dir}/hiveserver2 ${base_log_dir}/metastore ${base_log_dir}/query ${base_log_dir}/performance"

    echo -e "${GREEN}日志目录创建完成${NC}"
    echo ""
    echo "  ${base_log_dir}/"
    echo "  ├── hiveserver2/hiveserver2.log   # HiveServer2主日志"
    echo "  ├── metastore/metastore.log       # Metastore主日志"
    echo "  ├── query/                          # 查询日志目录"
    echo "  └── performance/                   # 性能日志目录"
    echo ""
}

start_metastore() {
    log_step "启动Hive Metastore..."
    echo ""

    local running=$(exec_hdfs "jps | grep -c RunJar" || true)
    if [ "$running" -gt 0 ]; then
        local metastore_check=$(exec_hdfs "ps aux | grep metastore | grep -v grep" | grep -c hive || true)
        if [ "$metastore_check" -gt 0 ]; then
            log_warn "Hive Metastore已在运行"
            return 0
        fi
    fi

    local base_log_dir="${PROJECT_LOG_DIR}/hive"
    local log_file="${base_log_dir}/metastore/metastore.log"

    exec_hdfs "nohup \${HIVE_HOME}/bin/hive --service metastore > ${log_file} 2>&1 &"

    sleep 15

    local port_check=$(exec_hdfs "netstat -an 2>/dev/null | grep :${HIVE_METASTORE_PORT} | grep LISTEN | wc -l" || true)
    if [ "$port_check" -gt 0 ]; then
        local pid=$(exec_hdfs "jps | grep RunJar | head -1 | awk '{print \$1}'")
        log_info "Hive Metastore启动成功 (PID: ${pid})"
        echo "  日志: ${log_file}"
        echo "  端口: ${HIVE_METASTORE_PORT}"
    else
        local runjar_count=$(exec_hdfs "jps | grep -c RunJar" || true)
        if [ "$runjar_count" -gt 0 ]; then
            local log_check=$(exec_hdfs "grep -c 'MetaStore' ${log_file} 2>/dev/null || true")
            if [ "$log_check" -gt 0 ]; then
                local pid=$(exec_hdfs "jps | grep RunJar | head -1 | awk '{print \$1}'")
                log_info "Hive Metastore启动成功 (PID: ${pid}) [通过日志确认]"
                echo "  日志: ${log_file}"
            else
                log_error "Hive Metastore启动状态未确认"
            fi
        else
            log_error "Hive Metastore启动失败，请检查日志: ${log_file}"
        fi
    fi
    echo ""
}

start_hiveserver2() {
    log_step "启动HiveServer2..."
    echo ""

    local running=$(exec_hdfs "jps | grep -c RunJar" || true)
    if [ "$running" -gt 0 ]; then
        local port_check=$(exec_hdfs "netstat -an 2>/dev/null | grep :${HIVESERVER2_PORT} | grep LISTEN | wc -l" || true)
        if [ "$port_check" -gt 0 ]; then
            log_warn "HiveServer2已在运行"
            return 0
        fi
    fi

    local base_log_dir="${PROJECT_LOG_DIR}/hive"
    local log_file="${base_log_dir}/hiveserver2/hiveserver2.log"
    exec_hdfs "nohup \${HIVE_HOME}/bin/hive --service hiveserver2 > ${log_file} 2>&1 &"

    sleep 30

    local port_check=$(exec_hdfs "netstat -an 2>/dev/null | grep :${HIVESERVER2_PORT} | grep LISTEN | wc -l" || true)
    if [ "$port_check" -gt 0 ]; then
        local pid=$(exec_hdfs "jps | grep RunJar | head -1 | awk '{print \$1}'")
        log_info "HiveServer2启动成功 (PID: ${pid})"
        echo "  日志: ${log_file}"
        echo "  端口: ${HIVESERVER2_PORT}"

        sleep 5
        local test_result=$(exec_hdfs "echo 'show databases;' | \${HIVE_HOME}/bin/beeline -u jdbc:hive2://localhost:${HIVESERVER2} 2>&1 | grep -c 'Connected' || true")
        if [ "$test_result" -gt 0 ]; then
            log_info "HiveServer2 连接测试通过"
        fi
    else
        local log_check=$(exec_hdfs "grep -c 'Hive Session ID' ${log_file} 2>/dev/null || true")
        if [ "$log_check" -gt 0 ]; then
            local pid=$(exec_hdfs "jps | grep RunJar | head -1 | awk '{print \$1}'")
            log_info "HiveServer2启动成功 (PID: ${pid}) [通过日志确认]"
            echo "  日志: ${log_file}"
            echo "  说明: 端口可能仍在绑定中，稍后可连接"
        else
            log_error "HiveServer2启动失败，请检查日志: ${log_file}"
            echo ""
            echo "  最近日志:"
            exec_hdfs "tail -20 ${log_file}"
            return 1
        fi
    fi
    echo ""
}

start() {
    echo ""
    echo "=========================================="
    echo "     启动Hive服务"
    echo "=========================================="
    echo ""

    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    if ! is_hive_server; then
        log_warn "当前节点不是HiveServer2节点"
        echo "Hive服务部署在: ${NODE1_NAME} (${NODE1_IP})"
        echo ""
        return 0
    fi

    init_log_dirs

    start_metastore || return 1

    start_hiveserver2 || return 1

    log_info "Hive服务启动完成！"
    echo ""
    echo "服务地址:"
    echo "  - JDBC:           jdbc:hive2://${MASTER_IP}:${HIVESERVER2_PORT}/"
    echo "  - Beeline:        beeline -u jdbc:hive2://${MASTER_IP}:${HIVESERVER2_PORT}/"
    echo "  - Metastore:       thrift://${MASTER_IP}:${HIVE_METASTORE_PORT}/"
    echo ""
    local base_log_dir="${PROJECT_LOG_DIR}/hive"
    echo "日志目录: ${base_log_dir}"
    echo "  ├── hiveserver2/hiveserver2.log   # HiveServer2主日志"
    echo "  ├── metastore/metastore.log       # Metastore主日志"
    echo "  ├── query/                          # 查询日志目录"
    echo "  └── performance/                   # 性能日志目录"
    echo ""
}

stop() {
    echo ""
    echo "=========================================="
    echo "     停止Hive服务"
    echo "=========================================="
    echo ""

    local hiveserver_running=$(exec_hdfs "netstat -an 2>/dev/null | grep ${HIVESERVER2_PORT} | grep LISTEN | wc -l" || true)
    if [ "$hiveserver_running" -gt 0 ]; then
        log_step "停止HiveServer2..."
        exec_hdfs "pkill -9 -f 'hiveserver2'"
        sleep 2
    fi

    local metastore_running=$(exec_hdfs "netstat -an 2>/dev/null | grep ${HIVE_METASTORE_PORT} | grep LISTEN | wc -l" || true)
    if [ "$metastore_running" -gt 0 ]; then
        log_step "停止Hive Metastore..."
        exec_hdfs "pkill -9 -f 'metastore'"
        sleep 2
    fi

    local runjar_count=$(exec_hdfs "jps | grep -c RunJar" || true)
    if [ "$runjar_count" -gt 0 ]; then
        log_step "清理残留进程..."
        exec_hdfs "pkill -9 -f 'RunJar'" 2>/dev/null || true
        sleep 2
    fi

    echo ""
    log_info "Hive服务已停止"
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
    echo "     Hive服务状态"
    echo "=========================================="
    echo ""

    local current_ip=$(get_current_ip)
    local current_node=$(hostname)
    local current_user=$(whoami)

    echo "当前节点: ${current_node} (${current_ip})"
    echo "执行用户: ${current_user}"
    echo ""

    if ! is_hive_server; then
        log_warn "当前节点不是HiveServer2节点"
        echo "Hive服务部署在: ${NODE1_NAME} (${NODE1_IP})"
        echo ""
        echo "=========================================="
        return 0
    fi

    echo "----------------------------------------"
    echo -n "  Metastore:    "
    local metastore_port=$(exec_hdfs "netstat -an 2>/dev/null | grep ${HIVE_METASTORE_PORT} | grep LISTEN | wc -l" || true)
    if [ "$metastore_port" -gt 0 ]; then
        local pid=$(exec_hdfs "jps | grep RunJar | head -1 | awk '{print \$1}'")
        echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid}, 端口: ${HIVE_METASTORE_PORT})"
    else
        echo -e "${YELLOW}[STOPPED]${NC}"
    fi

    echo -n "  HiveServer2:  "
    local hiveserver_port=$(exec_hdfs "netstat -an 2>/dev/null | grep ${HIVESERVER2_PORT} | grep LISTEN | wc -l" || true)
    if [ "$hiveserver_port" -gt 0 ]; then
        local pid=$(exec_hdfs "jps | grep RunJar | tail -1 | awk '{print \$1}'")
        echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid}, 端口: ${HIVESERVER2_PORT})"
    else
        echo -e "${YELLOW}[STOPPED]${NC}"
    fi

    echo -n "  RunJar进程:  "
    local runjar_count=$(exec_hdfs "jps | grep -c RunJar" || true)
    if [ "$runjar_count" -gt 0 ]; then
        echo -e "${GREEN}${runjar_count} 个${NC}"
        exec_hdfs "jps | grep RunJar" | while read pid name; do
            echo "                    - $pid $name"
        done
    else
        echo -e "${YELLOW}无${NC}"
    fi
    echo "----------------------------------------"

    echo ""
    echo "=========================================="
}

client() {
    log_info "连接到Hive客户端..."
    echo ""

    export HIVE_LOG4J2_OPTS="-Dlog4j2.configurationFile=$HIVE_LOG4J2_CONF"

    exec_hdfs "\${HIVE_HOME}/bin/beeline -u jdbc:hive2://${MASTER_IP}:${HIVESERVER2_PORT}/"
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
        client
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Hive服务管理脚本 v5.0.0"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|restart|status|client}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动Hive服务（Metastore + HiveServer2）"
        echo "  stop    - 停止Hive服务"
        echo "  restart - 重启Hive服务"
        echo "  status  - 查看Hive服务状态"
        echo "  client  - 连接Hive客户端（Beeline）"
        echo ""
        echo "服务节点:"
        echo "  ${NODE1_NAME} (${NODE1_IP}) - Hive Metastore + HiveServer2"
        echo ""
        echo "日志目录: ${PROJECT_LOG_DIR}/hive"
        echo "  ├── hiveserver2/hiveserver2.log   # HiveServer2主日志"
        echo "  ├── metastore/metastore.log       # Metastore主日志"
        echo "  ├── query/                          # 查询日志目录"
        echo "  └── performance/                   # 性能日志目录"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
