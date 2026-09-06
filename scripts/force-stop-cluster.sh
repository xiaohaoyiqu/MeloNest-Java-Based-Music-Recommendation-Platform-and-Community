#!/bin/bash
            
                     
                  

echo "=== 强制停止集群服务 ==="

         
echo "[1/3] 停止Node1服务..."
pkill -9 -f NameNode
pkill -9 -f DataNode
pkill -9 -f JournalNode
pkill -9 -f ResourceManager
pkill -9 -f NodeManager
pkill -9 -f Kafka
pkill -9 -f QuorumPeerMain
pkill -9 -f redis-server
pkill -9 -f hao-ran-music-backend
sleep 3
jps || echo "Node1: 无Java进程"

              
echo "[2/3] 停止Node2服务..."
ssh hdfs@192.168.153.132 'pkill -9 -f NameNode; pkill -9 -f DataNode; pkill -9 -f JournalNode; pkill -9 -f Kafka; pkill -9 -f QuorumPeerMain; pkill -9 -f redis-server' 2>/dev/null
sleep 2

              
echo "[3/3] 停止Node3服务..."
ssh hdfs@192.168.153.133 'pkill -9 -f DataNode; pkill -9 -f JournalNode; pkill -9 -f Kafka; pkill -9 -f QuorumPeerMain; pkill -9 -f redis-server' 2>/dev/null
sleep 2

echo "=== 验证停止状态 ==="
echo "Node1: $(jps | wc -l) 个Java进程"
ssh hdfs@192.168.153.132 'jps | wc -l' 2>/dev/null
ssh hdfs@192.168.153.133 'jps | wc -l' 2>/dev/null

echo "=== 集群已停止 ==="
