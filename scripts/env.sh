#!/bin/bash
                                                                               
                 
                     
                  
                                                                               

                                                                        
                                                                  
if [ -z "${HAORAN_SECRETS_FILE:-}" ]; then
    if [ -r /etc/haoranmusic/secrets.env ]; then
        HAORAN_SECRETS_FILE=/etc/haoranmusic/secrets.env
    else
        HAORAN_SECRETS_FILE=/sdb1/myprojoct/haoranmusic-private/secrets.env
    fi
fi
if [ -r "$HAORAN_SECRETS_FILE" ]; then
    source "$HAORAN_SECRETS_FILE"
fi

        
if [ -z "${HADOOP_HOME:-}" ]; then
    _HAORAN_RESTORE_NOUNSET=false
    case "$-" in
        *u*)
            _HAORAN_RESTORE_NOUNSET=true
            set +u
            ;;
    esac
    source /etc/profile
    if [ "$_HAORAN_RESTORE_NOUNSET" = true ]; then
        set -u
    fi
    unset _HAORAN_RESTORE_NOUNSET
fi

                                                                               
        
                                                                               
JAVA_HOME=/usr/local/soft/jdk1.8.0_461
HADOOP_HOME=/sdb1/hadoop-3.3.6
HIVE_HOME=/usr/local/soft/hive-3.1.2
SPARK_HOME=/usr/local/soft/spark-2.4.8
ZOOKEEPER_HOME=/usr/local/soft/zookeeper-3.7.2
KAFKA_HOME=/usr/local/soft/kafka-2.4.1
REDIS_HOME=/usr/local/soft/redis5.0.14
SCALA_HOME=/usr/local/soft/scala-2.11.12
MAVEN_HOME=/usr/local/soft/maven-3.6.3
NGINX_HOME=/usr/local/soft/nginx-1.28.0
PYTHON_HOME=/usr/local/soft/python3.7.16
MEDIA_FFMPEG_HOME="${MEDIA_FFMPEG_HOME:-/usr/local/soft/ffmpeg-6.1.1}"
FFMPEG_PATH="${FFMPEG_PATH:-${MEDIA_FFMPEG_HOME}/bin/ffmpeg}"
FFPROBE_PATH="${FFPROBE_PATH:-${MEDIA_FFMPEG_HOME}/bin/ffprobe}"
MUSIC_POST_VIDEO_FFMPEG_PATH="${MUSIC_POST_VIDEO_FFMPEG_PATH:-${FFMPEG_PATH}}"
MUSIC_POST_VIDEO_FFPROBE_PATH="${MUSIC_POST_VIDEO_FFPROBE_PATH:-${FFPROBE_PATH}}"

export MEDIA_FFMPEG_HOME FFMPEG_PATH FFPROBE_PATH
export MUSIC_POST_VIDEO_FFMPEG_PATH MUSIC_POST_VIDEO_FFPROBE_PATH

                                                                               
                      
                                                                               
NODE1_IP="192.168.153.131"
NODE2_IP="192.168.153.132"
NODE3_IP="192.168.153.133"
            
NODE4_IP=""          
NODE5_IP=""        
NODE6_IP=""        

NODE1_NAME="node1"
NODE2_NAME="node2"
NODE3_NAME="node3"
NODE4_NAME="node4"          
NODE5_NAME="node5"        
NODE6_NAME="node6"        

                  
MASTER_IP="${NODE1_IP}"
MASTER_NAME="${NODE1_NAME}"

               
ALL_NODES=("${NODE1_IP}" "${NODE2_IP}" "${NODE3_IP}")
ALL_NODE_NAMES=("${NODE1_NAME}" "${NODE2_NAME}" "${NODE3_NAME}")
WORKER_NODES=("${NODE2_IP}" "${NODE3_IP}")
ZK_NODES=("${NODE1_IP}" "${NODE2_IP}" "${NODE3_IP}")
KAFKA_NODES=("${NODE1_IP}" "${NODE2_IP}" "${NODE3_IP}")

                            
                                                                                                 
                                                                                                                  
                                                                                      
                                                                                                   

CLUSTER_USER="hdfs"
HAORAN_KNOWN_HOSTS_FILE="${HAORAN_KNOWN_HOSTS_FILE:-/home/${CLUSTER_USER}/.ssh/known_hosts}"
SSH_OPTS="-o StrictHostKeyChecking=yes -o UserKnownHostsFile=${HAORAN_KNOWN_HOSTS_FILE} -o ConnectTimeout=10 -o BatchMode=yes"

                                                                               
        
                                                                               
PROJECT_DIR="/sdb1/myprojoct/haoranmusic"
DATA_DIR="/sdb1/haoranmusicData"
CONFIG_DIR="$PROJECT_DIR/ee"

                                     
CURRENT_IP=$(hostname -I | awk '{print $1}')
if [ "$CURRENT_IP" = "$NODE1_IP" ]; then
    PROJECT_LOG_DIR="$PROJECT_DIR/logs"
else
    PROJECT_LOG_DIR="/sdb1/logs"
fi

HADOOP_LOG_DIR="$PROJECT_LOG_DIR/hadoop"
HIVE_LOG_DIR="$PROJECT_LOG_DIR/hive"
SPARK_LOG_DIR="$PROJECT_LOG_DIR/spark"
REDIS_LOG_DIR="$PROJECT_LOG_DIR/redis"
KAFKA_LOG_DIR="$PROJECT_LOG_DIR/kafka"
ZOOKEEPER_LOG_DIR="$PROJECT_LOG_DIR/zookeeper"
MYSQL_LOG_DIR="$PROJECT_LOG_DIR/mysql"
NGINX_LOG_DIR="$PROJECT_LOG_DIR/nginx"
BACKEND_LOG_DIR="$PROJECT_LOG_DIR/backend"
BACKEND_JAVA_OPTS="${BACKEND_JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BACKEND_LOG_DIR}}"
MUSIC_EMOJI_PATH="${MUSIC_EMOJI_PATH:-${PROJECT_DIR}/data/emojis}"
MUSIC_PLAYBACK_SOURCE_URL_PREFIX="${MUSIC_PLAYBACK_SOURCE_URL_PREFIX:-http://${NODE3_IP}:8081/}"
MUSIC_PLAYBACK_NGINX_ACCEL_ENABLED="${MUSIC_PLAYBACK_NGINX_ACCEL_ENABLED:-false}"
SEARCH_ENGINE="${SEARCH_ENGINE:-elasticsearch}"
SEARCH_ES_ENABLED="${SEARCH_ES_ENABLED:-true}"
SEARCH_ES_URIS="${SEARCH_ES_URIS:-http://127.0.0.1:9200}"
SEARCH_ES_REBUILD_ENABLED="${SEARCH_ES_REBUILD_ENABLED:-true}"
SEARCH_ES_BOOTSTRAP_REBUILD_ENABLED="${SEARCH_ES_BOOTSTRAP_REBUILD_ENABLED:-true}"
SEARCH_ES_BOOTSTRAP_MIN_DOCUMENTS="${SEARCH_ES_BOOTSTRAP_MIN_DOCUMENTS:-1000}"

                                                                               
          
                                                                               
HDFS_BASE_PATH="hdfs://mycluster"
HDFS_DATA_PATH="$HDFS_BASE_PATH/Datas/haoranMusicPlatform"
HDFS_WAREHOUSE_DIR="$HDFS_DATA_PATH/hive/warehouse"

                           
if [ "$CURRENT_IP" = "$NODE1_IP" ]; then
    NN_DIR="/sdb1/hdfsdata/namenode"
elif [ "$CURRENT_IP" = "$NODE2_IP" ]; then
    NN_DIR="/sdb1/hdfsdata/namesecondary"
else
    NN_DIR="/sdb1/hdfsdata/namenode"
fi
DN_DIR="/sdb1/hdfsdata/datanode"
JN_DIR="/sdb1/hdfsdata/journalnode"

                                                                               
         
                                                                               
HADOOP_DATA_DIR="$DATA_DIR/hadoop"
HIVE_DATA_DIR="$DATA_DIR/hive"
SPARK_DATA_DIR="$DATA_DIR/spark"
ZOOKEEPER_DATA_DIR="$DATA_DIR/zookeeper"
KAFKA_DATA_DIR="$DATA_DIR/kafka"
REDIS_DATA_DIR="$DATA_DIR/redis"
MYSQL_DATA_DIR="$DATA_DIR/mysql"
NGINX_DATA_DIR="$DATA_DIR/nginx"

SPARK_EVENT_LOG_DIR="hdfs://mycluster/spark/eventLogs"

                                                                               
                   
                                                                               
DB_NAME="haoranmusic_bus"
DB_USER="hdfs"
DB_PASS="${HAORAN_DB_PASSWORD:-${DB_PASS:-}}"
DB_HOST="${NODE1_IP}"
DB_PORT=3306

                                        
PAYMENT_COMPLETION_RECOVERY_ENABLED="${PAYMENT_COMPLETION_RECOVERY_ENABLED:-false}"
PAYMENT_COMPLETION_RECOVERY_CRON="${PAYMENT_COMPLETION_RECOVERY_CRON:-0 */1 * * * ?}"
PAYMENT_COMPLETION_RECOVERY_BATCH_SIZE="${PAYMENT_COMPLETION_RECOVERY_BATCH_SIZE:-20}"
PAYMENT_COMPLETION_RECOVERY_MAX_ATTEMPTS="${PAYMENT_COMPLETION_RECOVERY_MAX_ATTEMPTS:-8}"
PAYMENT_COMPLETION_RECOVERY_LEASE_SECONDS="${PAYMENT_COMPLETION_RECOVERY_LEASE_SECONDS:-300}"
PAYMENT_COMPLETION_RECOVERY_BASE_BACKOFF_SECONDS="${PAYMENT_COMPLETION_RECOVERY_BASE_BACKOFF_SECONDS:-30}"
PAYMENT_COMPLETION_RECOVERY_MAX_BACKOFF_SECONDS="${PAYMENT_COMPLETION_RECOVERY_MAX_BACKOFF_SECONDS:-3600}"

REDIS_PASSWORD="${HAORAN_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"
REDIS_PORT=6379
REDIS_SENTINEL_PORT=26379

KAFKA_PORT=9092

ZK_CLIENT_PORT=2181

                                                                               
      
                                                                               
HDFS_NAMENODE_HTTP_PORT=9870
YARN_RM_WEB_PORT=8088
SPARK_HISTORY_UI_PORT=18080
HIVESERVER2_PORT=10000
HIVE_METASTORE_PORT=9083
NGINX_HTTP_PORT=3223
NGINX_HTTPS_PORT=8443
BACKEND_PORT=9090
FRONTEND_PORT=80

                                                                               
          
                                                                               
get_current_ip() {
    local ip=""
    local cluster_ips=$(ip addr show 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | cut -d'/' -f1)
    for test_ip in $cluster_ips; do
        if echo "$test_ip" | grep -q "^192\.168\.153\."; then
            ip="$test_ip"
            break
        fi
    done
    [ -z "$ip" ] && ip=$(ip route get 1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)
    [ -z "$ip" ] && ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    echo "$ip"
}

is_namenode() { [ "$(get_current_ip)" = "$NODE1_IP" ] || [ "$(get_current_ip)" = "$NODE2_IP" ]; }
is_datanode() { is_namenode || [ "$(get_current_ip)" = "$NODE3_IP" ]; }
is_journalnode() { is_datanode; }
is_rm_node() { is_namenode; }
is_nm_node() { is_datanode; }
is_spark_master() { [ "$(get_current_ip)" = "$NODE1_IP" ]; }
is_spark_worker() { is_datanode; }
is_hive_server() { [ "$(get_current_ip)" = "$NODE1_IP" ]; }
is_redis_master() { [ "$(get_current_ip)" = "$NODE1_IP" ]; }
is_kafka_node() { is_datanode; }
is_zookeeper_node() { is_datanode; }
is_master_node() { [ "$(get_current_ip)" = "$NODE1_IP" ]; }

get_node_ip() {
    case "$1" in node1) echo "${NODE1_IP}";; node2) echo "${NODE2_IP}";; node3) echo "${NODE3_IP}";; *) echo "";; esac
}

get_node_name() {
    case "$1" in ${NODE1_IP}) echo "node1";; ${NODE2_IP}) echo "node2";; ${NODE3_IP}) echo "node3";; *) echo "unknown";; esac
}

get_node_log_dir() {
    case "$1" in ${NODE1_IP}|node1) echo "/sdb1/myprojoct/haoranmusic/logs";; ${NODE2_IP}|node2|${NODE3_IP}|node3) echo "/sdb1/logs";; *) echo "/sdb1/logs";; esac
}

get_node_env_sh() {
    case "$1" in ${NODE1_IP}|node1) echo "/sdb1/myprojoct/haoranmusic/scripts/env.sh";; ${NODE2_IP}|node2|${NODE3_IP}|node3) echo "/sdb1/scripts/env.sh";; *) echo "/sdb1/scripts/env.sh";; esac
}

get_node_config_dir() {
    case "$1" in ${NODE1_IP}|node1) echo "/sdb1/myprojoct/haoranmusic/ee";; ${NODE2_IP}|node2|${NODE3_IP}|node3) echo "/sdb1/ee";; *) echo "/sdb1/ee";; esac
}

                                                                               
        
                                                                               
export PROJECT_DIR DATA_DIR CONFIG_DIR PROJECT_LOG_DIR
export JAVA_HOME HADOOP_HOME HIVE_HOME SPARK_HOME ZOOKEEPER_HOME
export KAFKA_HOME REDIS_HOME SCALA_HOME MAVEN_HOME NGINX_HOME PYTHON_HOME

export NODE1_IP NODE2_IP NODE3_IP NODE1_NAME NODE2_NAME NODE3_NAME
export MASTER_IP MASTER_NAME
export ALL_NODES ALL_NODE_NAMES WORKER_NODES ZK_NODES KAFKA_NODES
export CLUSTER_USER SSH_OPTS CURRENT_IP

export NN_DIR DN_DIR JN_DIR
export HDFS_BASE_PATH HDFS_DATA_PATH HDFS_WAREHOUSE_DIR

export HADOOP_LOG_DIR HIVE_LOG_DIR SPARK_LOG_DIR REDIS_LOG_DIR KAFKA_LOG_DIR ZOOKEEPER_LOG_DIR MYSQL_LOG_DIR NGINX_LOG_DIR BACKEND_LOG_DIR
export BACKEND_JAVA_OPTS MUSIC_EMOJI_PATH MUSIC_PLAYBACK_SOURCE_URL_PREFIX MUSIC_PLAYBACK_NGINX_ACCEL_ENABLED
export SEARCH_ENGINE SEARCH_ES_ENABLED SEARCH_ES_URIS SEARCH_ES_REBUILD_ENABLED
export SEARCH_ES_BOOTSTRAP_REBUILD_ENABLED SEARCH_ES_BOOTSTRAP_MIN_DOCUMENTS
export HADOOP_DATA_DIR HIVE_DATA_DIR SPARK_DATA_DIR ZOOKEEPER_DATA_DIR KAFKA_DATA_DIR REDIS_DATA_DIR MYSQL_DATA_DIR NGINX_DATA_DIR

export DB_NAME DB_USER DB_PASS DB_HOST DB_PORT
export PAYMENT_COMPLETION_RECOVERY_ENABLED PAYMENT_COMPLETION_RECOVERY_CRON
export PAYMENT_COMPLETION_RECOVERY_BATCH_SIZE PAYMENT_COMPLETION_RECOVERY_MAX_ATTEMPTS
export PAYMENT_COMPLETION_RECOVERY_LEASE_SECONDS PAYMENT_COMPLETION_RECOVERY_BASE_BACKOFF_SECONDS
export PAYMENT_COMPLETION_RECOVERY_MAX_BACKOFF_SECONDS
export REDIS_PASSWORD REDIS_PORT REDIS_SENTINEL_PORT
export KAFKA_PORT ZK_CLIENT_PORT

export HDFS_NAMENODE_HTTP_PORT YARN_RM_WEB_PORT SPARK_HISTORY_UI_PORT HIVESERVER2_PORT HIVE_METASTORE_PORT NGINX_HTTP_PORT NGINX_HTTPS_PORT BACKEND_PORT FRONTEND_PORT
export SPARK_EVENT_LOG_DIR

export -f get_current_ip get_node_ip get_node_name get_node_log_dir get_node_env_sh get_node_config_dir
export -f is_namenode is_datanode is_journalnode is_rm_node is_nm_node
export -f is_spark_master is_spark_worker is_hive_server is_redis_master
export -f is_kafka_node is_zookeeper_node is_master_node
