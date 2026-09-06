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

                                                                               
        
                                                                               
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

                                                                               
               
                                                                               
                                 
       
               
           
                                
run_as_hdfs() {
    if [ "$(whoami)" = "${CLUSTER_USER}" ]; then
                        
        eval "$@"
    else
                                    
        sudo -u ${CLUSTER_USER} bash -c "source /etc/profile >/dev/null 2>&1; $*"
    fi
}

                                                                               
             
                                                                               
                                 
REDIS_CONF="${REDIS_HOME}/conf/redis.conf"
REDIS_SENTINEL_CONF="${REDIS_HOME}/conf/sentinel.conf"

                                                                               
           
                                                                               
start() {
    echo ""
    echo "=========================================="
    echo "     启动Redis集群"
    echo "=========================================="
    echo ""

          
    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    log_step "启动Redis服务..."
    echo ""

    for node in "${ALL_NODES[@]}"; do
        local node_name=$(get_node_name "$node")
        local role="Slave"
        local is_master="false"
        if [ "$node" = "$NODE1_IP" ]; then
            role="Master"
            is_master="true"
        fi
        echo -n "  ${node_name} (${node}, ${role}): "

        if [ "$node" = "$(get_current_ip)" ]; then
                  
                    
            if [ ! -f "${REDIS_CONF}" ]; then
                echo -e "${RED}配置文件不存在${NC}"
                continue
            fi

                                 
            if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                echo -e "${YELLOW}已运行${NC}"
                continue
            fi

                    
            run_as_hdfs "mkdir -p /sdb1/haoranmusicData/redis ${REDIS_HOME}/logs ${REDIS_HOME}/data"

                                         
            run_as_hdfs "rm -f /sdb1/haoranmusicData/redis/appendonly.aof /usr/local/soft/redis5.0.14/data/appendonly.aof 2>/dev/null"
            run_as_hdfs "sed -i 's/^appendonly yes/appendonly no/' ${REDIS_CONF} 2>/dev/null || true"

                                     
            local auth_args=""
            if [ -n "${REDIS_PASSWORD:-}" ]; then
                printf -v auth_args -- "--requirepass %q" "$REDIS_PASSWORD"
            fi
            run_as_hdfs "${REDIS_HOME}/bin/redis-server --daemonize yes --port 6379 ${auth_args} --bind $(get_current_ip) --dir /sdb1/haoranmusicData/redis --appendonly no"

            sleep 2

            if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                echo -e "${GREEN}启动成功${NC}"

                                   
                if is_redis_master && [ -f "${REDIS_SENTINEL_CONF}" ]; then
                    run_as_hdfs "${REDIS_HOME}/bin/redis-sentinel ${REDIS_SENTINEL_CONF} --daemonize yes" >/dev/null 2>&1
                fi
            else
                echo -e "${RED}启动失败${NC}"
                echo ""
                echo "  错误信息:"
                run_as_hdfs "tail -20 \${REDIS_HOME}/logs/redis-node1.log 2>/dev/null || echo '    日志文件不存在'"
            fi
        else
                                    
                               
            if [ "$is_master" = "true" ]; then
                local result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} '
                    source /etc/profile 2>/dev/null
                    source '$(get_node_env_sh $node)' 2>/dev/null

                            
                    if [ ! -f ${REDIS_HOME}/conf/redis.conf ]; then
                        echo "no_config"
                        exit 0
                    fi

                                         
                    if netstat -an 2>/dev/null | grep -q ":6379.*LISTEN"; then
                        echo "already_running"
                        exit 0
                    fi

                            
                    mkdir -p /sdb1/haoranmusicData/redis ${REDIS_HOME}/logs ${REDIS_HOME}/data 2>/dev/null

                             
                    ${REDIS_HOME}/bin/redis-server ${REDIS_HOME}/conf/redis.conf
                    sleep 2

                    if netstat -an 2>/dev/null | grep -q ":6379.*LISTEN"; then
                        echo "started"

                                       
                        if [ -f ${REDIS_HOME}/conf/sentinel.conf ]; then
                            ${REDIS_HOME}/bin/redis-sentinel ${REDIS_HOME}/conf/sentinel.conf --daemonize yes >/dev/null 2>&1
                        fi
                    else
                        echo "failed"
                        tail -20 ${REDIS_HOME}/logs/redis-node*.log 2>/dev/null || true
                    fi
                ' 2>&1)
            else
                local result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} '
                    source /etc/profile 2>/dev/null
                    source '$(get_node_env_sh $node)' 2>/dev/null

                            
                    if [ ! -f ${REDIS_HOME}/conf/redis.conf ]; then
                        echo "no_config"
                        exit 0
                    fi

                                         
                    if netstat -an 2>/dev/null | grep -q ":6379.*LISTEN"; then
                        echo "already_running"
                        exit 0
                    fi

                            
                    mkdir -p /sdb1/haoranmusicData/redis ${REDIS_HOME}/logs ${REDIS_HOME}/data 2>/dev/null

                             
                    ${REDIS_HOME}/bin/redis-server ${REDIS_HOME}/conf/redis.conf
                    sleep 2

                    if netstat -an 2>/dev/null | grep -q ":6379.*LISTEN"; then
                        echo "started"
                    else
                        echo "failed"
                        tail -20 ${REDIS_HOME}/logs/redis-node*.log 2>/dev/null || true
                    fi
                ' 2>&1)
            fi

            case "$result" in
                *no_config*)
                    echo -e "${RED}配置文件不存在${NC}"
                    ;;
                *already_running*)
                    echo -e "${YELLOW}已运行${NC}"
                    ;;
                *started*)
                    echo -e "${GREEN}启动成功${NC}"
                    ;;
                *)
                    echo -e "${RED}启动失败${NC}"
                    ;;
            esac
        fi
    done

    echo ""
    log_info "Redis集群启动完成！"
    echo ""
}

                                                                               
                 
                                                                               
                                   
                                
       
                          
                           
                          
graceful_shutdown_redis() {
    local node_ip="$1"
    local is_remote="$2"
    local shutdown_result=""

                                                                         
    local redis_password="${REDIS_PASSWORD:-}"

    if [ "$is_remote" = "remote" ]; then
                  
        shutdown_result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node_ip} "
            source /etc/profile 2>/dev/null
            source '${SCRIPT_DIR}/env.sh' 2>/dev/null

                                
            if netstat -an 2>/dev/null | grep -q ':26379.*LISTEN'; then
                \${REDIS_HOME}/bin/redis-cli -p 26379 shutdown 2>/dev/null || true
                sleep 1
            fi

                                 
            if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                \${REDIS_HOME}/bin/redis-cli -a '${redis_password}' --no-auth-warning shutdown 2>/dev/null || true
                sleep 2
            fi

                         
            if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                      
                pkill -9 -f redis-sentinel 2>/dev/null || true
                pkill -9 -f redis-server 2>/dev/null || true
                echo 'forced'
            else
                echo 'graceful'
            fi
        " 2>/dev/null)
    else
                  
                            
        if netstat -an 2>/dev/null | grep -q ':26379.*LISTEN'; then
            run_as_hdfs "${REDIS_HOME}/bin/redis-cli -p 26379 shutdown" 2>/dev/null || true
            sleep 1
        fi

                             
        if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
            run_as_hdfs "${REDIS_HOME}/bin/redis-cli -a ${redis_password} --no-auth-warning shutdown" 2>/dev/null || true
            sleep 2
        fi

                     
        if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                  
            run_as_hdfs "pkill -9 -f redis-sentinel" 2>/dev/null || true
            run_as_hdfs "pkill -9 -f redis-server" 2>/dev/null || true
            shutdown_result="forced"
        else
            shutdown_result="graceful"
        fi
    fi

    echo "$shutdown_result"
}

                                                                               
                      
                                                                               
stop() {
    echo ""
    echo "=========================================="
    echo "     停止Redis集群"
    echo "=========================================="
    echo ""
    log_info "使用优雅关闭方式（redis-cli shutdown）"
    echo ""

    for node in "${ALL_NODES[@]}"; do
        local node_name=$(get_node_name "$node")
        echo -n "  ${node_name} (${node}): "

        if [ "$node" = "$(get_current_ip)" ]; then
                  
            local shutdown_type=$(graceful_shutdown_redis "" "local")
            if [ "$shutdown_type" = "graceful" ]; then
                echo -e "${GREEN}已停止（优雅）${NC}"
            else
                echo -e "${YELLOW}已停止（强制）${NC}"
            fi
        else
                  
            local shutdown_type=$(graceful_shutdown_redis "$node" "remote")
            if [ "$shutdown_type" = "graceful" ]; then
                echo -e "${GREEN}已停止（优雅）${NC}"
            else
                echo -e "${YELLOW}已停止（强制）${NC}"
            fi
        fi
    done

    echo ""
    sleep 2
    status
}

                                                                               
                        
                                                                               
force_stop() {
    echo ""
    echo "=========================================="
    echo "     强制停止Redis集群"
    echo "=========================================="
    echo ""
    log_warn "警告：此操作不会保存Redis数据！"
    echo ""

    for node in "${ALL_NODES[@]}"; do
        local node_name=$(get_node_name "$node")
        echo -n "  ${node_name} (${node}): "

        if [ "$node" = "$(get_current_ip)" ]; then
            run_as_hdfs "pkill -9 -f redis-sentinel" 2>/dev/null || true
            run_as_hdfs "pkill -9 -f redis-server" 2>/dev/null || true
            echo -e "${RED}已强制停止${NC}"
        else
            ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} 'pkill -9 -f redis-sentinel; pkill -9 -f redis-server' 2>/dev/null || true
            echo -e "${RED}已强制停止${NC}"
        fi
    done

    echo ""
    sleep 1
    status
}

                                                                               
           
                                                                               
restart() {
    stop
    sleep 3
    start
}

                                                                               
             
                                                                               
status() {
    echo ""
    echo "=========================================="
    echo "     Redis集群状态"
    echo "=========================================="
    echo ""

            
    local current_ip=$(get_current_ip)
    local current_node=$(hostname)
    local current_user=$(whoami)

    echo "当前节点: ${current_node} (${current_ip})"
    echo "执行用户: ${current_user}"
    echo ""

    local running_count=0

    for node in "${ALL_NODES[@]}"; do
        local node_name=$(get_node_name "$node")
        local role="Slave"
        if [ "$node" = "$NODE1_IP" ]; then
            role="Master"
        fi

        echo -n "  ${node_name} (${node}, ${role}): "

        if [ "$node" = "$current_ip" ]; then
                           
            if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                local pid=$(pgrep -f "redis-server" | head -1)
                echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
                running_count=$((running_count + 1))
            else
                echo -e "${YELLOW}[STOPPED]${NC}"
            fi

                              
            if [ "$node" = "$NODE1_IP" ]; then
                echo -n "    Sentinel: "
                if netstat -an 2>/dev/null | grep -q ':26379.*LISTEN'; then
                    local pid=$(pgrep -f "redis-sentinel" | head -1)
                    echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
                else
                    echo -e "${YELLOW}[STOPPED]${NC}"
                fi
            fi
        else
                           
            local result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "
                source /etc/profile 2>/dev/null
                if netstat -an 2>/dev/null | grep -q ':6379.*LISTEN'; then
                    pgrep -f 'redis-server' | head -1
                fi
            " 2>/dev/null)

            if [ -n "$result" ]; then
                echo -e "${GREEN}[RUNNING]${NC} (PID: ${result})"
                running_count=$((running_count + 1))
            else
                echo -e "${YELLOW}[STOPPED]${NC}"
            fi

                              
            if [ "$node" = "$NODE1_IP" ]; then
                echo -n "    Sentinel: "
                local sentinel_result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "
                    source /etc/profile 2>/dev/null
                    if netstat -an 2>/dev/null | grep -q ':26379.*LISTEN'; then
                        pgrep -f 'redis-sentinel' | head -1
                    fi
                " 2>/dev/null)

                if [ -n "$sentinel_result" ]; then
                    echo -e "${GREEN}[RUNNING]${NC} (PID: ${sentinel_result})"
                else
                    echo -e "${YELLOW}[STOPPED]${NC}"
                fi
            fi
        fi
    done

    echo ""
    echo "----------------------------------------"
    echo "  运行节点: ${running_count}/3"
    echo "----------------------------------------"
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
    force-stop)
        force_stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Redis集群管理脚本"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|force-stop|restart|status}"
        echo ""
        echo "命令说明:"
        echo "  start      - 启动Redis集群（所有节点）"
        echo "  stop       - 停止Redis集群（优雅关闭，保存数据）"
        echo "  force-stop - 强制停止Redis集群（不保存数据，紧急用）"
        echo "  restart    - 重启Redis集群（所有节点）"
        echo "  status     - 查看Redis集群状态"
        echo ""
        echo "注意: 脚本会自动切换到hdfs用户执行"
        echo ""
        echo "集群节点:"
        echo "  ${NODE1_NAME} (${NODE1_IP}) - Master + Sentinel"
        echo "  ${NODE2_NAME} (${NODE2_IP}) - Slave"
        echo "  ${NODE3_NAME} (${NODE3_IP}) - Slave"
        echo ""
        echo "配置文件: ${REDIS_CONF}"
        echo "数据目录: ${DATA_DIR}/redis"
        echo "日志目录: ${REDIS_LOG_DIR}"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
