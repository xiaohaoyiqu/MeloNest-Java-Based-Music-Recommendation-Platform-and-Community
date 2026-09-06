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




get_broker_id() {
    case "$1" in
        ${NODE1_IP}) echo "1" ;;
        ${NODE2_IP}) echo "2" ;;
        ${NODE3_IP}) echo "3" ;;
        ${NODE4_IP}) echo "4" ;;
        ${NODE5_IP}) echo "5" ;;
        ${NODE6_IP}) echo "6" ;;
        *) echo "0" ;;
    esac
}









ensure_kafka_config() {
    local node_ip="$1"
    local is_remote="$2"
    local broker_id=$(get_broker_id "$node_ip")
    local project_dir="${PROJECT_DIR:-/sdb1/myprojoct/haoranmusic}"
    local template_config="${project_dir}/ee/kafka/node1/server.properties"

    if [ "$is_remote" = "remote" ]; then

        ssh ${SSH_OPTS} ${CLUSTER_USER}@${node_ip} "
            source /etc/profile 2>/dev/null


            if [ ! -f \${KAFKA_HOME}/config/server.properties ]; then
                echo 'config_missing'


                if [ -f '${template_config}' ]; then
                    mkdir -p \${KAFKA_HOME}/config 2>/dev/null
                    cp '${template_config}' \${KAFKA_HOME}/config/server.properties
                    echo 'config_restored'
                else

                    mkdir -p \${KAFKA_HOME}/config 2>/dev/null
                    cat > \${KAFKA_HOME}/config/server.properties << 'EOFCONFIG'




broker.id=${broker_id}
listeners=PLAINTEXT://${node_ip}:9092
advertised.listeners=PLAINTEXT://${node_ip}:9092
log.dirs=/sdb1/haoranmusicData/kafka
zookeeper.connect=${NODE1_IP}:2181,${NODE2_IP}:2181,${NODE3_IP}:2181/kafka

num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
queued.max.requests=500

num.partitions=3                   
num.recovery.threads.per.data.dir=4
log.retention.hours=168
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

auto.create.topics.enable=true
delete.topic.enable=true

offsets.topic.replication.factor=3
transaction.state.log.replication.factor=3
transaction.state.log.min.isr=3
default.replication.factor=3
min.insync.replicas=2
EOFCONFIG
                    echo 'config_created'
                fi
            else
                echo 'config_exists'
            fi
        " 2>/dev/null
    else

        if [ ! -f "${KAFKA_HOME}/config/server.properties" ]; then
            log_warn "Kafka配置文件不存在，尝试恢复..."


            if [ -f "${template_config}" ]; then
                run_as_hdfs "mkdir -p ${KAFKA_HOME}/config"
                run_as_hdfs "cp ${template_config} ${KAFKA_HOME}/config/server.properties"
                log_info "从项目模板恢复配置文件"
                return 0
            else

                run_as_hdfs "mkdir -p ${KAFKA_HOME}/config"
                run_as_hdfs "cat > ${KAFKA_HOME}/config/server.properties << 'EOFCONFIG'




broker.id=${broker_id}
listeners=PLAINTEXT://${node_ip}:9092
advertised.listeners=PLAINTEXT://${node_ip}:9092
log.dirs=/sdb1/haoranmusicData/kafka
zookeeper.connect=${NODE1_IP}:2181,${NODE2_IP}:2181,${NODE3_IP}:2181/kafka

num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
queued.max.requests=500

num.partitions=3                   
num.recovery.threads.per.data.dir=4
log.retention.hours=168
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

auto.create.topics.enable=true
delete.topic.enable=true

offsets.topic.replication.factor=3
transaction.state.log.replication.factor=3
transaction.state.log.min.isr=3
default.replication.factor=3
min.insync.replicas=2
EOFCONFIG"
                log_info "创建默认配置文件"
                return 0
            fi
        fi
        return 0
    fi
}




start() {
    echo ""
    echo "=========================================="
    echo "     启动Kafka集群"
    echo "=========================================="
    echo ""
    log_info "集群节点: ${NODE1_IP}(broker.1), ${NODE2_IP}(broker.2), ${NODE3_IP}(broker.3)"
    echo ""


    local current_user=$(whoami)
    if [ "$current_user" != "${CLUSTER_USER}" ]; then
        log_warn "当前用户: ${current_user}，自动切换到 ${CLUSTER_USER} 用户执行"
    fi
    echo ""

    log_step "启动Kafka服务..."
    echo ""

    for node in "${KAFKA_NODES[@]}"; do
        local broker_id=$(get_broker_id $node)
        echo -n "  broker.${broker_id} (${node}): "


        local kafka_log_dir=$(get_node_log_dir $node)
        local kafka_log_dir="${kafka_log_dir}/kafka"
        local env_sh=$(get_node_env_sh $node)

        if [ "$node" = "$(get_current_ip)" ]; then


            if run_as_hdfs "jps | grep -q Kafka"; then
                echo -e "${YELLOW}已运行${NC}"
                continue
            fi


            ensure_kafka_config "$node" "local"


            run_as_hdfs "mkdir -p /sdb1/haoranmusicData/kafka ${kafka_log_dir}"


            run_as_hdfs "rm -f /sdb1/haoranmusicData/kafka/pid 2>/dev/null"


            if [ -f "${KAFKA_HOME}/config/server.properties" ]; then
                run_as_hdfs "cp ${KAFKA_HOME}/config/server.properties ${KAFKA_HOME}/config/server.properties.bak"
            fi


            run_as_hdfs "

                sed -i '/#===DYN_CONFIG_START===/,/#===DYN_CONFIG_END===/d' \${KAFKA_HOME}/config/server.properties 2>/dev/null || true

                echo '#===DYN_CONFIG_START===（自动生成，勿手动修改）' >> \${KAFKA_HOME}/config/server.properties
                echo 'broker.id=${broker_id}' >> \${KAFKA_HOME}/config/server.properties
                echo 'listeners=PLAINTEXT://${node}:9092' >> \${KAFKA_HOME}/config/server.properties
                echo 'advertised.listeners=PLAINTEXT://${node}:9092' >> \${KAFKA_HOME}/config/server.properties
                echo 'log.dirs=/sdb1/haoranmusicData/kafka' >> \${KAFKA_HOME}/config/server.properties
                echo 'zookeeper.connect=${NODE1_IP}:2181,${NODE2_IP}:2181,${NODE3_IP}:2181/kafka' >> \${KAFKA_HOME}/config/server.properties
                echo '#===DYN_CONFIG_END===' >> \${KAFKA_HOME}/config/server.properties
            "


            run_as_hdfs "export KAFKA_HEAP_OPTS='-Xmx1G -Xms1G'; export LOG_DIR=${kafka_log_dir}; nohup ${KAFKA_HOME}/bin/kafka-server-start.sh -daemon \${KAFKA_HOME}/config/server.properties >/dev/null 2>&1"

            sleep 3

            if run_as_hdfs "jps | grep -q Kafka"; then
                echo -e "${GREEN}启动成功${NC}"
            else
                echo -e "${RED}启动失败${NC}"
            fi
        else

            local config_check=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "
                source /etc/profile 2>/dev/null


                if [ ! -f \${KAFKA_HOME}/config/server.properties ]; then
                    echo 'missing'
                    exit 0
                fi
                echo 'exists'
            " 2>/dev/null)


            if [ "$config_check" = "missing" ]; then
                echo -e "${YELLOW}配置文件缺失，正在恢复...${NC}"
                local restore_result=$(ensure_kafka_config "$node" "remote")
                if [[ "$restore_result" == *"restored"* ]] || [[ "$restore_result" == *"created"* ]]; then
                    echo -e "${GREEN}配置文件已恢复${NC}"
                else
                    echo -e "${RED}配置文件恢复失败${NC}"
                    continue
                fi
            fi





            local result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "
                source /etc/profile 2>/dev/null
                source '${env_sh}' 2>/dev/null


                if jps 2>/dev/null | grep -q Kafka; then
                    echo 'already_running'
                    exit 0
                fi


                mkdir -p /sdb1/haoranmusicData/kafka '${kafka_log_dir}' 2>/dev/null


                rm -f /sdb1/haoranmusicData/kafka/pid 2>/dev/null


                cp \$KAFKA_HOME/config/server.properties \$KAFKA_HOME/config/server.properties.bak 2>/dev/null || true



                sed -i '/#===DYN_CONFIG_START===/,/#===DYN_CONFIG_END===/d' \$KAFKA_HOME/config/server.properties 2>/dev/null || true

                echo '#===DYN_CONFIG_START===（自动生成，勿手动修改）' >> \$KAFKA_HOME/config/server.properties
                echo 'broker.id=${broker_id}' >> \$KAFKA_HOME/config/server.properties
                echo 'listeners=PLAINTEXT://${node}:9092' >> \$KAFKA_HOME/config/server.properties
                echo 'advertised.listeners=PLAINTEXT://${node}:9092' >> \$KAFKA_HOME/config/server.properties
                echo 'log.dirs=/sdb1/haoranmusicData/kafka' >> \$KAFKA_HOME/config/server.properties
                echo 'zookeeper.connect=${NODE1_IP}:2181,${NODE2_IP}:2181,${NODE3_IP}:2181/kafka' >> \$KAFKA_HOME/config/server.properties
                echo '#===DYN_CONFIG_END===' >> \$KAFKA_HOME/config/server.properties


                export KAFKA_HEAP_OPTS='-Xmx1G -Xms1G'
                export LOG_DIR='${kafka_log_dir}'
                nohup \$KAFKA_HOME/bin/kafka-server-start.sh -daemon \$KAFKA_HOME/config/server.properties >/dev/null 2>&1

                sleep 3

                if jps 2>/dev/null | grep -q Kafka; then
                    echo 'started'
                else
                    echo 'failed'
                fi
            " 2>&1)

            case "$result" in
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
    log_info "Kafka集群启动完成！"
    echo ""
}




stop() {
    echo ""
    echo "=========================================="
    echo "     停止Kafka集群"
    echo "=========================================="
    echo ""

    for node in "${KAFKA_NODES[@]}"; do
        local broker_id=$(get_broker_id $node)
        echo -n "  broker.${broker_id} (${node}): "

        if [ "$node" = "$(get_current_ip)" ]; then

            run_as_hdfs "\${KAFKA_HOME}/bin/kafka-server-stop.sh" >/dev/null 2>&1
        else

            ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} '
                source /etc/profile 2>/dev/null
                source '$(get_node_env_sh $node)' 2>/dev/null
                ${KAFKA_HOME}/bin/kafka-server-stop.sh >/dev/null 2>&1
            ' 2>/dev/null || true
        fi

        sleep 2
        echo -e "${GREEN}已停止${NC}"
    done


    for node in "${KAFKA_NODES[@]}"; do
        if [ "$node" = "$(get_current_ip)" ]; then
            pkill -9 -f kafka 2>/dev/null || true
        else
            ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} 'pkill -9 -f kafka' 2>/dev/null || true
        fi
    done

    echo ""
    sleep 2
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
    echo "     Kafka集群状态"
    echo "=========================================="
    echo ""

    local running_count=0

    for node in "${KAFKA_NODES[@]}"; do
        local broker_id=$(get_broker_id $node)
        echo -n "  broker.${broker_id} (${node}): "

        if [ "$node" = "$(get_current_ip)" ]; then

            if run_as_hdfs "jps | grep -q Kafka"; then
                local pid=$(run_as_hdfs "jps | grep Kafka | awk '{print \$1}'")
                echo -e "${GREEN}[RUNNING]${NC} (PID: ${pid})"
                running_count=$((running_count + 1))
            else
                echo -e "${YELLOW}[STOPPED]${NC}"
            fi
        else

            local result=$(ssh ${SSH_OPTS} ${CLUSTER_USER}@${node} "
                source /etc/profile 2>/dev/null
                if jps | grep -q Kafka; then
                    jps | grep Kafka | awk '{print \$1}'
                fi
            " 2>/dev/null)

            if [ -n "$result" ]; then
                echo -e "${GREEN}[RUNNING]${NC} (PID: ${result})"
                running_count=$((running_count + 1))
            else
                echo -e "${YELLOW}[STOPPED]${NC}"
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
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo ""
        echo "=========================================="
        echo "     Kafka集群管理脚本"
        echo "     @author xiaohaoyiqu"
        echo "=========================================="
        echo ""
        echo "用法: $0 {start|stop|restart|status}"
        echo ""
        echo "命令说明:"
        echo "  start   - 启动Kafka集群（所有节点）"
        echo "  stop    - 停止Kafka集群（所有节点）"
        echo "  restart - 重启Kafka集群（所有节点）"
        echo "  status  - 查看Kafka集群状态"
        echo ""
        echo "注意: 脚本会自动切换到hdfs用户执行"
        echo ""
        echo "集群节点:"
        echo "  ${NODE1_NAME} (${NODE1_IP}) - broker.id: 1"
        echo "  ${NODE2_NAME} (${NODE2_IP}) - broker.id: 2"
        echo "  ${NODE3_NAME} (${NODE3_IP}) - broker.id: 3"
        echo ""
        echo "=========================================="
        exit 1
        ;;
esac
