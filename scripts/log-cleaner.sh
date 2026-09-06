#!/bin/bash
                                                                               
                 
                     
                  
                                        
                                                                               

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

        
if [ -f "${SCRIPT_DIR}/env.sh" ]; then
    source "${SCRIPT_DIR}/env.sh"
else
    echo "[ERROR] 环境变量文件不存在"
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

      
exec_on_node() {
    local node=$1 cmd=$2
    local env_sh=$(get_node_env_sh $node)
    ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null; ${cmd}"
}

      
compress_logs() {
    local node=$1 log_dir=$2 days=$3

    exec_on_node $node "
        find ${log_dir} -type f -name '*.log' -mtime +${days} ! -name '*.gz' -exec gzip -f {} \; 2>/dev/null || true
    " 2>/dev/null
}

      
clean_logs() {
    local node=$1 log_dir=$2 days=$3 size=$4

    local find_cmd="find ${log_dir} -type f -name '*.log*'"

           
    [ -n "$days" ] && find_cmd="${find_cmd} -mtime +${days}"

           
    [ -n "$size" ] && find_cmd="${find_cmd} -size +${size}"

    exec_on_node $node "
        ${find_cmd} -exec rm -f {} \; 2>/dev/null || true
    " 2>/dev/null
}

          
get_log_status() {
    local node=$1
    local env_sh=$(get_node_env_sh $node)
    local log_dir=$(get_node_log_dir $node)

    ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "source ${env_sh} 2>/dev/null;
        echo \"节点: \$(hostname)\"
        echo \"日志目录: ${log_dir}\"
        echo \"\"
        echo \"组件            大小      文件数\"
        echo \"----------------  -------  -------\"

        for comp in hadoop hive spark kafka redis zookeeper mysql backend nginx; do
            local dir=${log_dir}/\${comp}
            if [ -d \${dir} ]; then
                local size=\$(du -sh \${dir} 2>/dev/null | awk '{print \$1}')
                local files=\$(find \${dir} -type f -name '*.log*' 2>/dev/null | wc -l)
                printf \"  %-14s  %-7s  %5d\\n\" \"\$comp\" \"\$size\" \"\$files\"
            fi
        done
    " 2>/dev/null
}

      
show_help() {
    echo ""
    echo "=========================================="
    echo "  日志清理脚本 v2.0"
    echo "  @author xiaohaoyiqu"
    echo "=========================================="
    echo ""
    echo "用法: $0 [选项] [参数]"
    echo ""
    echo "选项:"
    echo "  -a, --all          清理所有节点"
    echo "  -n, --node N       指定节点 (1/2/3)"
    echo "  -c, --component C   指定组件"
    echo "  -d, --days N       清理N天前的日志 (默认7)"
    echo "  -s, --size S       清理大于S的文件 (如: 100M, 1G)"
    echo "  -z, --compress     压缩旧日志而不是删除"
    echo "  --status          查看日志状态"
    echo "  -y, --yes, --non-interactive"
    echo "                     跳过确认，供cron等定时任务调用"
    echo "  -h, --help        显示帮助"
    echo ""
    echo "组件: hadoop, hive, spark, kafka, redis, zookeeper, mysql, backend, nginx, all"
    echo ""
    echo "示例:"
    echo "  $0 -a -d 30                    # 清理所有节点30天前的日志"
    echo "  $0 -n 1 -c hadoop -d 7          # 清理node1的hadoop 7天前日志"
    echo "  $0 -a -s 100M                   # 清理所有节点大于100M的日志"
    echo "  $0 -a -z                        # 压缩所有节点7天前的日志"
    echo "  $0 -a -c backend -d 14 -y        # 非交互清理后端14天前日志"
    echo "  $0 --status                     # 查看日志状态"
    echo ""
    echo "=========================================="
}

     
main() {
    local nodes=()
    local components=()
    local days=7
    local size=""
    local compress=false
    local status_only=false
    local assume_yes=false

          
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -a|--all)
                nodes=("${ALL_NODES[@]}")
                shift
                ;;
            -n|--node)
                case "$2" in
                    1) nodes=("${NODE1_IP}") ;;
                    2) nodes=("${NODE2_IP}") ;;
                    3) nodes=("${NODE3_IP}") ;;
                    *)
                        echo "无效节点: $2"
                        exit 1
                        ;;
                esac
                shift 2
                ;;
            -c|--component)
                if [ "$2" = "all" ]; then
                    components=("hadoop" "hive" "spark" "kafka" "redis" "zookeeper" "mysql" "backend" "nginx")
                else
                    components+=("$2")
                fi
                shift 2
                ;;
            -d|--days)
                days="$2"
                shift 2
                ;;
            -s|--size)
                size="$2"
                shift 2
                ;;
            -z|--compress)
                compress=true
                shift
                ;;
            --status)
                status_only=true
                shift
                ;;
            -y|--yes|--non-interactive)
                assume_yes=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                echo "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done

            
    [ ${#nodes[@]} -eq 0 ] && nodes=("${ALL_NODES[@]}")
            
    [ ${#components[@]} -eq 0 ] && components=("hadoop" "hive" "spark" "kafka" "redis" "zookeeper" "mysql" "backend" "nginx")

          
    if [ "$status_only" = true ]; then
        echo ""
        echo "=========================================="
        echo "  日志状态"
        echo "=========================================="
        echo ""
        for node in "${nodes[@]}"; do
            get_log_status "$node"
            echo ""
        done
        exit 0
    fi

          
    echo ""
    log_warn "即将执行以下操作:"
    echo "  节点: ${nodes[@]}"
    echo "  组件: ${components[@]}"
    echo "  条件: ${days}天前"
    [ -n "$size" ] && echo "  大小: >${size}"
    [ "$compress" = true ] && echo "  操作: 压缩" || echo "  操作: 删除"
    echo ""
    if [ "$assume_yes" != true ]; then
        read -p "确认继续? (y/n): " confirm
        if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
            echo "操作已取消"
            exit 0
        fi
    fi

             
    echo ""
    echo "=========================================="
    echo "  日志清理"
    echo "=========================================="
    echo ""

    for node in "${nodes[@]}"; do
        local node_name=$(get_node_name $node)
        echo "${node_name} (${node}):"

        local log_dir=$(get_node_log_dir $node)

        if [ "$components" = "all" ]; then
            local comps=("hadoop" "hive" "spark" "kafka" "redis" "zookeeper" "mysql" "backend" "nginx")
        else
            comps=("${components[@]}")
        fi

        for comp in "${comps[@]}"; do
            local comp_log_dir="${log_dir}/${comp}"
            echo -n "  ${comp}: "

            if [ "$compress" = true ]; then
                compress_logs "$node" "$comp_log_dir" "$days"
                echo -e "${GREEN}已压缩${NC}"
            else
                clean_logs "$node" "$comp_log_dir" "$days" "$size"
                echo -e "${GREEN}已清理${NC}"
            fi
        done
        echo ""
    done

    echo "=========================================="
    log_info "操作完成！"
    echo ""
}

main "$@"
