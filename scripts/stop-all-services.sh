#!/bin/bash



echo "=== 开始停止集群服务 === $(date '+%Y-%m-%d %H:%M:%S') ===" >> /tmp/stop.log

SSH_OPTS="-o StrictHostKeyChecking=yes -o UserKnownHostsFile=/home/hdfs/.ssh/known_hosts -o BatchMode=yes -o ConnectTimeout=10"


echo "[1/3] 停止Node1服务..." >> /tmp/stop.log
{
    pkill -9 -f NameNode
    pkill -9 -f DataNode
    pkill -9 -f JournalNode
    pkill -9 -f ResourceManager
    pkill -9 -f NodeManager
    pkill -9 -f Kafka
    pkill -9 -f QuorumPeerMain
    pkill -9 -f redis-server
    pkill -9 -f hao-ran-music-backend
    sleep 5
    jps >> /tmp/stop.log 2>&1
} &


echo "[2/3] 停止Node2服务..." >> /tmp/stop.log
{
    ssh ${SSH_OPTS} hdfs@192.168.153.132 "pkill -9 -f NameNode DataNode JournalNode Kafka QuorumPeerMain redis-server" >> /tmp/stop2.log 2>&1
    sleep 3
    ssh ${SSH_OPTS} hdfs@192.168.153.132 "jps" >> /tmp/stop2.log 2>&1
} &


echo "[3/3] 停止Node3服务..." >> /tmp/stop.log
{
    ssh ${SSH_OPTS} hdfs@192.168.153.133 "pkill -9 -f DataNode JournalNode Kafka QuorumPeerMain redis-server" >> /tmp/stop3.log 2>&1
    sleep 3
    ssh ${SSH_OPTS} hdfs@192.168.153.133 "jps" >> /tmp/stop3.log 2>&1
} &


wait

echo "=== 集群停止完成 $(date '+%Y-%m-%d %H:%M:%S') ===" >> /tmp/stop.log
echo "请查看日志: tail -f /tmp/stop.log"
