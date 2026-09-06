#!/bin/bash










export HADOOP_HOME=/sdb1/hadoop-3.3.6
export JAVA_HOME=/usr/local/soft/jdk1.8.0_461
export HIVE_HOME=/usr/local/soft/hive-3.1.2
export SPARK_HOME=/usr/local/soft/spark-2.4.8

export HIVE_AUX_JARS_PATH=$SPARK_HOME/jars
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$HADOOP_HOME/lib/native


export HIVE_METASTORE_HEAPSIZE=1024
export HIVE_SERVER2_HEAPSIZE=2048

export HIVE_METASTORE_OPTS="-Dhive.tez.enabled=false -Dhive.execution.engine=spark"
export HIVE_SERVER2_OPTS="-Dhive.tez.enabled=false -Dhive.execution.engine=spark"





if [ -n "$PROJECT_LOG_DIR" ]; then
    export HIVE_PROJECT_LOG_DIR="$PROJECT_LOG_DIR/hive"
else
    export HIVE_PROJECT_LOG_DIR="$HIVE_HOME/logs"
fi




export HIVE_LOG_DIR=$HIVE_PROJECT_LOG_DIR
export HIVE_CONF_DIR=$HIVE_HOME/conf
export HIVE_ZOOKEEPER_QUORUM=node1:2181,node2:2181,node3:2181






export HIVE_METASTORE_OPTS="$HIVE_METASTORE_OPTS -DlogDir=$HIVE_PROJECT_LOG_DIR"
export HIVE_SERVER2_OPTS="$HIVE_SERVER2_OPTS -DlogDir=$HIVE_PROJECT_LOG_DIR"
export HIVE_CLIENT_OPTS="$HIVE_CLIENT_OPTS -DlogDir=$HIVE_PROJECT_LOG_DIR"








