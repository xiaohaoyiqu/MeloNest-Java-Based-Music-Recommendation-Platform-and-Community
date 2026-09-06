#!/bin/bash









SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"


if [ -f "${SCRIPT_DIR}/env.sh" ]; then
    source "${SCRIPT_DIR}/env.sh"
else
    echo "[ERROR] 环境变量文件不存在: ${SCRIPT_DIR}/env.sh"
    exit 1
fi


CURRENT_IP=$(hostname -I | awk '{print $1}')


if [ "$CURRENT_IP" != "$NODE1_IP" ]; then
    echo "[ERROR] nginx.sh只能在node1执行，当前节点: $CURRENT_IP"
    exit 1
fi

NGINX_CONF="$PROJECT_DIR/ee/nginx/node1_nginx.conf"
NGINX_PORT="${NGINX_HTTP_PORT:-3223}"
NODE_NAME="node1"
NODE_ROLE="统一入口/反向代理"




RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }


remote_nginx() {
    local node=$1
    local action=$2
    local env_sh
    local conf
    local port

    env_sh=$(get_node_env_sh "$node")
    case "$node" in
        "$NODE2_IP")
            conf="/sdb1/myprojoct/haoranmusic/ee/nginx/node2_nginx.conf"
            port="${NODE2_NGINX_PORT:-8082}"
            ;;
        "$NODE3_IP")

            conf="/usr/local/soft/nginx-1.28.0/conf/nginx.conf"
            port="${NODE3_NGINX_PORT:-8081}"
            ;;
        *)
            log_error "不支持的远程Nginx节点: $node"
            return 1
            ;;
    esac

    ssh ${SSH_OPTS} "${CLUSTER_USER}@${node}" "
        set -e
        source /etc/profile 2>/dev/null
        source '${env_sh}' 2>/dev/null || exit 1
        nginx_home=\"\${NGINX_HOME}\"
        conf='${conf}'
        port='${port}'
        nginx_bin=\"\${nginx_home}/sbin/nginx\"
        installed_conf=\"\${nginx_home}/conf/nginx.conf\"
        if [ ! -x \"\${nginx_home}/sbin/nginx\" ]; then
            echo '[ERROR] Nginx可执行文件不存在: '\${nginx_home}'/sbin/nginx'
            exit 1
        fi
        mkdir -p \"\${nginx_home}/logs\" \"\${nginx_home}/cache\" 2>/dev/null
        if [ ! -f \"\${conf}\" ]; then
            echo '[ERROR] 节点配置文件不存在: '\${conf}
            exit 1
        fi

        if [ '${action}' = 'start' ] || [ '${action}' = 'reload' ]; then
            \"\${nginx_bin}\" -t -c \"\${conf}\" >/dev/null 2>&1 || {
                echo '[ERROR] 候选Nginx配置语法检查失败: '\${conf}
                exit 1
            }
            if [ ! -f \"\${installed_conf}\" ] || ! cmp -s \"\${conf}\" \"\${installed_conf}\"; then
                staged_conf=\"\${installed_conf}.new.\$\$\"
                cp -f \"\${conf}\" \"\${staged_conf}\"
                \"\${nginx_bin}\" -t -c \"\${staged_conf}\" >/dev/null 2>&1 || {
                    rm -f -- \"\${staged_conf}\"
                    echo '[ERROR] 暂存Nginx配置语法检查失败'
                    exit 1
                }
                if [ -f \"\${installed_conf}\" ]; then
                    cp -p -- \"\${installed_conf}\" \"\${installed_conf}.bak.\$(date +%Y%m%d%H%M%S)\"
                fi
                mv -- \"\${staged_conf}\" \"\${installed_conf}\"
            fi
        fi

        nginx_version=\$(\"\${nginx_bin}\" -v 2>&1)
        case '${action}' in
            start)
                \"\${nginx_bin}\" -t >/dev/null 2>&1 || exit 1
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    result='already-running'
                else
                    \"\${nginx_bin}\"
                    result='started'
                fi
                ;;
            stop)
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    \"\${nginx_bin}\" -s stop >/dev/null 2>&1 || true
                    sleep 2
                fi
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    pgrep -f '^nginx: master process ' | xargs -r kill
                    sleep 1
                fi
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    exit 1
                fi
                echo \"stopped version=\${nginx_version}\"
                exit 0
                ;;
            reload)
                \"\${nginx_bin}\" -t >/dev/null 2>&1 || exit 1
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    \"\${nginx_bin}\" -s reload
                    result='reloaded'
                else
                    \"\${nginx_bin}\"
                    result='started'
                fi
                ;;
            status)
                if pgrep -f '^nginx: master process ' >/dev/null 2>&1; then
                    result=\"running pid=\$(pgrep -f '^nginx: master process ' | head -1)\"
                else
                    echo 'stopped'
                    exit 1
                fi
                ;;
            test)
                \"\${nginx_bin}\" -t
                echo \"tested version=\${nginx_version}\"
                exit 0
                ;;
            *)
                echo '[ERROR] 未知Nginx操作: '${action}
                exit 1
                ;;
        esac

        http_code=\$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
            \"http://127.0.0.1:\${port}/\" 2>/dev/null || true)
        if [ -z \"\${http_code}\" ] || [ \"\${http_code}\" = '000' ]; then
            echo '[ERROR] Nginx进程存在但HTTP端口无响应: '\${port}
            exit 1
        fi
        echo \"\${result} port=\${port} http=\${http_code} version=\${nginx_version}\"
    "
}




check_nginx() {
    if [ ! -d "$NGINX_HOME" ]; then
        log_error "Nginx未安装: $NGINX_HOME"
        exit 1
    fi
}




ensure_nginx_config() {
    local conf_file="$NGINX_HOME/conf/nginx.conf"
    local staged_file="${conf_file}.new.$$"
    local candidate_result

    mkdir -p "$NGINX_HOME/conf" 2>/dev/null

    if [ ! -f "$NGINX_CONF" ]; then
        log_error "节点配置文件不存在: $NGINX_CONF"
        return 1
    fi

    if ! candidate_result=$(${NGINX_HOME}/sbin/nginx -t -c "$NGINX_CONF" 2>&1); then
        log_error "候选Nginx配置有误: $NGINX_CONF"
        echo "$candidate_result"
        return 1
    fi


    if [ ! -f "$conf_file" ] || ! cmp -s "$NGINX_CONF" "$conf_file"; then
        cp -f "$NGINX_CONF" "$staged_file" || return 1
        if ! candidate_result=$(${NGINX_HOME}/sbin/nginx -t -c "$staged_file" 2>&1); then
            rm -f -- "$staged_file"
            log_error "暂存Nginx配置有误，保留当前运行配置"
            echo "$candidate_result"
            return 1
        fi

        if [ -f "$conf_file" ]; then
            local backup_file="${conf_file}.bak.$(date +%Y%m%d%H%M%S)"
            cp -p "$conf_file" "$backup_file" || return 1
            log_info "已备份当前配置: $backup_file"
        else
            log_warn "Nginx配置文件不存在: $conf_file"
        fi

        mv -- "$staged_file" "$conf_file" || return 1
        log_info "已同步节点配置: $NGINX_CONF -> $conf_file"
    fi


    if [ ! -f "$NGINX_HOME/conf/mime.types" ]; then
        local mime_conf="$PROJECT_DIR/ee/nginx/mime.types"
        if [ "$CURRENT_IP" != "$NODE1_IP" ]; then
            mime_conf="/sdb1/ee/nginx/mime.types"
        fi

        if [ -f "$mime_conf" ]; then
            cp -f "$mime_conf" "$NGINX_HOME/conf/mime.types"
        else
            log_warn "mime.types不存在，使用默认配置"
        fi
    fi

    if [ "$CURRENT_IP" = "$NODE1_IP" ]; then
        local frontend_root="/sdb1/myprojoct/haoranmusic/hao-ran-music-frontend/dist"
        if [ ! -d "$frontend_root" ]; then
            log_warn "前端静态文件目录不存在: $frontend_root"
            mkdir -p "$frontend_root" 2>/dev/null

            cat > "$frontend_root/index.html" 2>/dev/null << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>浩然音乐平台</title>
    <meta charset="utf-8">
</head>
<body>
    <h1>浩然音乐平台</h1>
    <p>前端应用正在部署中...</p>
    <p>如果此页面长期显示，请联系管理员部署前端应用。</p>
</body>
</html>
EOF
            log_info "已创建临时前端页面"
        fi
    fi
}




init_dirs() {
    log_step "初始化Nginx目录..."


    mkdir -p ${NGINX_HOME}/logs 2>/dev/null
    mkdir -p ${NGINX_HOME}/cache 2>/dev/null


    if [ ! -d "/sdb1/myprojoct/haoranmusic/hao-ran-music-frontend/dist" ]; then
        mkdir -p /sdb1/myprojoct/haoranmusic/hao-ran-music-frontend/dist 2>/dev/null
    fi


    chown -R hdfs:hdfs ${NGINX_HOME}/logs 2>/dev/null
    chown -R hdfs:hdfs ${NGINX_HOME}/cache 2>/dev/null

    log_info "目录初始化完成"
}




test_config() {
    log_step "测试Nginx配置..."

    local result
    if result=$(${NGINX_HOME}/sbin/nginx -t 2>&1); then
        log_info "配置文件语法正确"
        echo "$result"
    else
        log_error "配置文件有误:"
        echo "$result"
        return 1
    fi
}

verify_local_http() {
    local http_code
    http_code=$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
        "http://127.0.0.1:${NGINX_PORT}/" 2>/dev/null || true)
    if [ -z "$http_code" ] || [ "$http_code" = "000" ]; then
        log_error "node1 Nginx进程存在但端口${NGINX_PORT}无HTTP响应"
        return 1
    fi
    log_info "node1:${NGINX_PORT} HTTP ${http_code}"
}




start() {
    echo ""
    echo "=========================================="
    echo "     启动Nginx服务"
    echo "=========================================="
    echo ""
    log_info "节点: ${NODE_NAME} (${CURRENT_IP})"
    log_info "端口: ${NGINX_PORT} (${NODE_ROLE})"
    echo ""

    check_nginx


    if ! ensure_nginx_config; then
        log_error "配置同步失败，取消启动"
        return 1
    fi


    init_dirs


    if ! test_config; then
        log_error "配置测试失败，取消启动"
        return 1
    fi

    if pgrep -f "^nginx: master process " > /dev/null; then
        log_warn "node1 Nginx已在运行"
    else
        log_step "启动node1 Nginx..."
        ${NGINX_HOME}/sbin/nginx
    fi

    sleep 2

    if ! pgrep -f "^nginx: master process " > /dev/null; then
        log_error "Nginx启动失败，请检查错误日志: ${NGINX_HOME}/logs/error.log"
        return 1
    fi

    verify_local_http || return 1

    log_step "通过node1启动/校验node2 Nginx..."
    remote_nginx "$NODE2_IP" start || {
        log_error "node2 Nginx启动失败"
        return 1
    }
    log_step "通过node1启动/校验node3 Nginx..."
    remote_nginx "$NODE3_IP" start || {
        log_error "node3 Nginx启动失败"
        return 1
    }

    log_info "三节点Nginx启动/校验完成"
    status
}




stop() {
    echo ""
    echo "=========================================="
    echo "     停止Nginx服务"
    echo "=========================================="
    echo ""

    if pgrep -f "^nginx: master process " > /dev/null; then
        log_step "停止node1 Nginx..."
        ${NGINX_HOME}/sbin/nginx -s stop 2>/dev/null || true
        sleep 2
    fi

    if pgrep -f "^nginx: master process " > /dev/null; then
        log_error "Nginx停止失败"
        return 1
    fi

    remote_nginx "$NODE2_IP" stop || return 1
    remote_nginx "$NODE3_IP" stop || return 1
    log_info "三节点Nginx已停止"
}




restart() {
    stop || return 1
    sleep 1
    start
}




reload() {
    echo ""
    echo "=========================================="
    echo "     重新加载Nginx配置"
    echo "=========================================="
    echo ""

    if ! ensure_nginx_config; then
        log_error "配置同步失败，取消重载"
        return 1
    fi


    if ! test_config; then
        log_error "配置测试失败，取消重载"
        return 1
    fi

    if pgrep -f "^nginx: master process " > /dev/null; then
        log_step "重新加载node1配置..."
        ${NGINX_HOME}/sbin/nginx -s reload
    else
        log_warn "node1 Nginx未运行，改为启动..."
        ${NGINX_HOME}/sbin/nginx
    fi

    verify_local_http || return 1

    remote_nginx "$NODE2_IP" reload || return 1
    remote_nginx "$NODE3_IP" reload || return 1

    sleep 1
    log_info "三节点Nginx配置重新加载完成"
}




status() {
    echo ""
    echo "=========================================="
    echo "     Nginx服务状态"
    echo "=========================================="
    echo ""
    echo "  节点: ${NODE_NAME} (${CURRENT_IP})"
    echo "  端口: ${NGINX_PORT} (${NODE_ROLE})"
    echo ""

    local has_error=0
    if ! pgrep -f "^nginx: master process " > /dev/null; then
        echo "  状态: ${YELLOW}未运行${NC}"
        has_error=1
    else
        local master_pid=$(pgrep -f "^nginx: master process ")
        local worker_count=$(pgrep -f "^nginx: worker process" | wc -l)

        echo "  状态: ${GREEN}运行中${NC}"
        echo "  主进程PID: ${master_pid}"
        echo "  Worker进程数: ${worker_count}"
        echo ""

        echo "  监听端口:"
        netstat -tlnp 2>/dev/null | grep nginx | awk '{print "    " $4 " -> " $7}'
        echo ""

        echo "  健康检查:"
        local http_code
        http_code=$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' \
            "http://127.0.0.1:${NGINX_PORT}/" 2>/dev/null || true)
        if [ -n "$http_code" ] && [ "$http_code" != "000" ]; then
            echo -e "    ${GREEN}正常${NC} (HTTP ${http_code})"
        else
            echo -e "    ${YELLOW}无响应${NC}"
            has_error=1
        fi
    fi

    echo ""
    echo "  远程节点:"
    local node2_status node3_status
    if node2_status=$(remote_nginx "$NODE2_IP" status 2>/dev/null); then
        echo "    node2: ${node2_status}"
    else
        echo "    node2: unavailable"
        has_error=1
    fi
    if node3_status=$(remote_nginx "$NODE3_IP" status 2>/dev/null); then
        echo "    node3: ${node3_status}"
    else
        echo "    node3: unavailable"
        has_error=1
    fi
    echo ""
    return "$has_error"
}




logs() {
    local lines="${1:-50}"

    echo ""
    echo "=========================================="
    echo "     Nginx访问日志 (最近${lines}行)"
    echo "=========================================="
    echo ""

    if [ -f "${NGINX_HOME}/logs/access.log" ]; then
        tail -n ${lines} ${NGINX_HOME}/logs/access.log
    else
        log_warn "访问日志文件不存在"
    fi

    echo ""
    echo "=========================================="
    echo "     Nginx错误日志 (最近${lines}行)"
    echo "=========================================="
    echo ""

    if [ -f "${NGINX_HOME}/logs/error.log" ]; then
        tail -n ${lines} ${NGINX_HOME}/logs/error.log
    else
        log_warn "错误日志文件不存在"
    fi
}




benchmark() {
    echo ""
    echo "=========================================="
    echo "     Nginx性能测试"
    echo "=========================================="
    echo ""

    if ! pgrep -f "^nginx: master process " > /dev/null; then
        log_error "Nginx未运行，请先执行: ./nginx.sh start"
        return 1
    fi

    log_step "使用ab进行压力测试..."
    echo ""


    if ! command -v ab &>/dev/null; then
        log_warn "ab工具未安装，跳过性能测试"
        log_info "安装命令: yum install httpd-tools"
        return 0
    fi


    local test_url="http://localhost:${NGINX_PORT}/"

    echo "  测试URL: ${test_url}"
    echo "  并发数: 100"
    echo "  请求数: 10000"
    echo ""

    ab -n 10000 -c 100 ${test_url} 2>&1 | tail -20
}




diagnose() {
    echo ""
    echo "=========================================="
    echo "     Nginx诊断检查"
    echo "=========================================="
    echo ""

    local has_error=0


    echo -n "  [1/7] 安装目录检查: "
    if [ -d "$NGINX_HOME" ]; then
        echo -e "${GREEN}OK${NC} ($NGINX_HOME)"
    else
        echo -e "${RED}FAIL${NC} - 目录不存在"
        has_error=1
    fi


    echo -n "  [2/7] 配置文件检查: "
    if [ -f "$NGINX_HOME/conf/nginx.conf" ]; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAIL${NC} - 配置文件缺失"
        has_error=1
    fi


    echo -n "  [3/7] mime.types检查: "
    if [ -f "$NGINX_HOME/conf/mime.types" ]; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${YELLOW}WARN${NC} - mime.types缺失"
    fi


    echo -n "  [4/7] 进程检查: "
    if pgrep -f "^nginx: master process " > /dev/null; then
        local pid=$(pgrep -f "^nginx: master process ")
        echo -e "${GREEN}RUNNING${NC} (PID: ${pid})"
    else
        echo -e "${RED}STOPPED${NC}"
        has_error=1
    fi


    echo -n "  [5/7] 端口${NGINX_HTTP_PORT}监听: "
    if netstat -tln 2>/dev/null | grep -q ":${NGINX_HTTP_PORT}.*LISTEN"; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAIL${NC} - 端口未监听"
        has_error=1
    fi


    echo -n "  [6/7] HTTP连接测试: "
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${NGINX_HTTP_PORT}/health 2>/dev/null)
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}OK${NC} (HTTP 200)"
    else
        echo -e "${RED}FAIL${NC} (HTTP ${http_code})"
        has_error=1
    fi


    echo -n "  [7/7] 日志目录检查: "
    if [ -d "$NGINX_HOME/logs" ]; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${YELLOW}WARN${NC} - 日志目录不存在"
    fi

    echo ""
    echo "----------------------------------------"
    echo "  详细信息:"
    echo "----------------------------------------"


    echo "  监听端口:"
    netstat -tlnp 2>/dev/null | grep nginx | awk '{print "    " $4 " -> " $7}' || echo "    无"


    if [ -f "$NGINX_HOME/logs/error.log" ]; then
        local error_count=$(grep -c "error" "$NGINX_HOME/logs/error.log" 2>/dev/null || echo "0")
        echo "  错误日志: 最近有 ${error_count} 条错误记录"
        if [ "$error_count" -gt 0 ]; then
            echo "  最近的3条错误:"
            grep -i "error" "$NGINX_HOME/logs/error.log" 2>/dev/null | tail -3 | sed 's/^/    /'
        fi
    fi

    echo ""
    if [ $has_error -eq 0 ]; then
        echo -e "  ${GREEN}诊断结果: 正常${NC}"
    else
        echo -e "  ${RED}诊断结果: 发现问题${NC}"
        echo ""
        echo "  建议操作:"
        echo "    1. 重启nginx: ./nginx.sh restart"
        echo "    2. 查看日志: ./nginx.sh logs"
        echo "    3. 测试配置: ./nginx.sh test"
    fi
    echo ""
    echo "=========================================="
}




info() {
    echo ""
    echo "=========================================="
    echo "     Nginx配置信息"
    echo "=========================================="
    echo ""

    echo "  安装目录: ${NGINX_HOME}"
    echo "  配置文件: ${NGINX_HOME}/conf/nginx.conf"
    echo "  日志目录: ${NGINX_HOME}/logs"
    echo "  缓存目录: ${NGINX_HOME}/cache"
    echo ""

    echo "  配置参数:"
    grep "worker_processes" ${NGINX_HOME}/conf/nginx.conf | grep -v "#"
    grep "worker_connections" ${NGINX_HOME}/conf/nginx.conf | grep -v "#"
    grep "keepalive_timeout" ${NGINX_HOME}/conf/nginx.conf | grep -v "#"
    grep "client_max_body_size" ${NGINX_HOME}/conf/nginx.conf | grep -v "#"
    echo ""


    local workers=$(grep "worker_processes" ${NGINX_HOME}/conf/nginx.conf | grep "auto" | wc -l)
    local connections=$(grep "worker_connections" ${NGINX_HOME}/conf/nginx.conf | awk '{print $2}' | tr -d ';')

    if [ "$workers" -gt 0 ]; then
        local cpu_cores=$(nproc)
        local total_capacity=$((cpu_cores * connections))
        echo "  理论承载能力:"
        echo "    CPU核心数: ${cpu_cores}"
        echo "    Worker进程数: auto (=${cpu_cores})"
        echo "    每Worker连接数: ${connections}"
        echo "    理论最大连接数: ${total_capacity}"
    fi
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
    reload)
        reload
        ;;
    status)
        status
        ;;
    test)
        test_config
        ;;
    logs)
        logs "${2:-50}"
        ;;
    benchmark)
        benchmark
        ;;
    diagnose)
        diagnose
        ;;
    info)
        info
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Nginx管理脚本"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "节点: ${NODE_NAME} (${CURRENT_IP})"
        echo "端口: ${NGINX_PORT} (${NODE_ROLE})"
        echo ""
        echo "用法: $0 {start|stop|restart|reload|status|test|logs|benchmark|diagnose|info}"
        echo ""
        echo "命令说明:"
        echo "  start     - 启动Nginx服务"
        echo "  stop      - 停止Nginx服务"
        echo "  restart   - 重启Nginx服务"
        echo "  reload    - 重新加载配置（不中断服务）"
        echo "  status    - 查看Nginx状态"
        echo "  test      - 测试配置文件语法"
        echo "  logs [N]  - 查看日志（默认50行）"
        echo "  benchmark - 性能压力测试（端口${NGINX_PORT}）"
        echo "  diagnose  - 诊断检查（排查问题）"
        echo "  info      - 显示配置信息"
        echo ""
        echo "访问地址:"
        echo "  http://$(hostname -I | awk '{print $1}'):${NGINX_PORT}/"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
