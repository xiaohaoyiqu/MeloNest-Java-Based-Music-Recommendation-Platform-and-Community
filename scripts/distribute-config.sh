#!/usr/bin/env bash
set -euo pipefail






SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

MODE="${1:-plan}"
COMPONENT="${2:-all}"
DEPLOY_ROOT="${HAORAN_DEPLOY_CONFIG_ROOT:-${SCRIPT_DIR}/../ee/deploy}"

case "${MODE}" in
    plan|apply) ;;
    *) echo "Usage: $0 [plan|apply] [all|hadoop|hive|hbase|spark|kafka|zookeeper|redis|nginx|elasticsearch|mysql]" >&2; exit 2 ;;
esac

if [ "${MODE}" = "apply" ] && [ "${HAORAN_CONFIG_APPLY:-}" != "YES" ]; then
    echo "[ERROR] Set HAORAN_CONFIG_APPLY=YES for an explicit apply." >&2
    exit 2
fi

if [ ! -d "${DEPLOY_ROOT}" ]; then
    echo "[ERROR] Reviewed deploy directory is missing: ${DEPLOY_ROOT}" >&2
    echo "Copy selected files from ee/current, fill protected values outside source control, then run plan." >&2
    exit 2
fi

nodes=(node1 node2 node3)

node_address() {
    case "$1" in
        node1) printf '%s\n' "${NODE1_IP}" ;;
        node2) printf '%s\n' "${NODE2_IP}" ;;
        node3) printf '%s\n' "${NODE3_IP}" ;;
        *) return 2 ;;
    esac
}

remote_target() {
    local component=$1 relative=$2
    case "${component}" in
        hadoop) printf '/usr/local/soft/hadoop-3.3.6/etc/hadoop/%s\n' "${relative}" ;;
        hive) printf '/usr/local/soft/hive-3.1.2/conf/%s\n' "${relative}" ;;
        hbase) printf '/usr/local/soft/hbase-2.5.12/conf/%s\n' "${relative}" ;;
        spark) printf '/usr/local/soft/spark-2.4.8/conf/%s\n' "${relative}" ;;
        kafka) printf '/usr/local/soft/kafka-2.4.1/config/%s\n' "${relative}" ;;
        zookeeper) printf '/usr/local/soft/zookeeper-3.7.2/conf/%s\n' "${relative}" ;;
        redis) printf '/usr/local/soft/redis5.0.14/%s\n' "${relative}" ;;
        nginx) printf '/usr/local/soft/nginx-1.28.0/conf/%s\n' "${relative}" ;;
        elasticsearch) printf '/sdb1/haoranmusicData/elasticsearch-7.17.24/config/%s\n' "${relative}" ;;
        mysql) printf '/etc/%s\n' "${relative}" ;;
        *) return 2 ;;
    esac
}

validate_local() {
    local file=$1
    if grep -Eq '\$\{(REDACTED_SECRET|REDIS_PASSWORD)\}' "${file}"; then
        echo "[ERROR] Unresolved protected placeholder: ${file}" >&2
        return 1
    fi
    case "${file}" in
        *.xml) command -v xmllint >/dev/null 2>&1 && xmllint --noout "${file}" ;;
        *.json) python3 -m json.tool "${file}" >/dev/null ;;
        *.sh) bash -n "${file}" ;;
    esac
}

install_remote() {
    local node=$1 source=$2 target=$3 component=$4
    local address stage base64_target
    address=$(node_address "${node}")
    stage="/tmp/haoran-config-$RANDOM-$RANDOM"
    base64_target=$(printf '%s' "${target}" | base64 | tr -d '\n')

    ssh ${SSH_OPTS} "${CLUSTER_USER}@${address}" "umask 077; mkdir -p '${stage}'"
    scp ${SSH_OPTS} "${source}" "${CLUSTER_USER}@${address}:${stage}/candidate" >/dev/null
    ssh ${SSH_OPTS} "${CLUSTER_USER}@${address}" "set -eu
target=\$(printf '%s' '${base64_target}' | base64 -d)
upload='${stage}/candidate'
candidate=\"\${target}.candidate.\$\$\"
cleanup() { sudo rm -f \"\$candidate\"; rm -rf '${stage}'; }
trap cleanup EXIT
sudo install -m 640 \"\$upload\" \"\$candidate\"
case '${component}' in
  nginx) sudo /usr/local/soft/nginx-1.28.0/sbin/nginx -t -c \"\$candidate\" ;;
esac
stamp=\$(date +%Y%m%d%H%M%S)
if [ -f \"\$target\" ]; then sudo cp -p \"\$target\" \"\$target.pre-\$stamp\"; fi
sudo mv -f \"\$candidate\" \"\$target\"
trap - EXIT
rm -rf '${stage}'"
}

planned=0
for node in "${nodes[@]}"; do
    node_root="${DEPLOY_ROOT}/${node}"
    [ -d "${node_root}" ] || continue
    while IFS= read -r -d '' file; do
        relative="${file#${node_root}/}"
        component="${relative%%/*}"
        component_file="${relative#*/}"
        if [ "${COMPONENT}" != "all" ] && [ "${component}" != "${COMPONENT}" ]; then
            continue
        fi
        target=$(remote_target "${component}" "${component_file}")
        validate_local "${file}"
        printf '%s\t%s\t%s\n' "${node}" "${relative}" "${target}"
        planned=$((planned + 1))
        if [ "${MODE}" = "apply" ]; then
            install_remote "${node}" "${file}" "${target}" "${component}"
        fi
    done < <(find "${node_root}" -type f -print0 | sort -z)
done

if [ "${planned}" -eq 0 ]; then
    echo "[ERROR] No matching configuration files found." >&2
    exit 3
fi

if [ "${MODE}" = "plan" ]; then
    echo "[INFO] Plan only. Files checked: ${planned}. No remote changes were made."
else
    echo "[INFO] Installed ${planned} files. Restart or reload each affected component after its health check."
fi
