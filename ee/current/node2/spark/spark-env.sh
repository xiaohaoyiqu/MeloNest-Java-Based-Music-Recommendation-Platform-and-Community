#!/usr/bin/env bash

export JAVA_HOME=/usr/local/soft/jdk1.8.0_461
export HADOOP_HOME=/sdb1/hadoop-3.3.6
export HADOOP_CONF_DIR=/sdb1/hadoop-3.3.6/etc/hadoop
export PYSPARK_PYTHON=/usr/local/soft/python3.7.16/bin/python3
export PYSPARK_DRIVER_PYTHON=/usr/local/soft/python3.7.16/bin/python3
export PYTHONPATH=/sdb1/haoranmusicData/python-packages/spark-2.4.8:${PYTHONPATH:-}
export LD_LIBRARY_PATH=/usr/local/soft/python3.7.16/lib:${LD_LIBRARY_PATH:-}

export SPARK_MASTER_HOST=192.168.153.131
export SPARK_MASTER_PORT=7077
export SPARK_MASTER_WEBUI_PORT=8081
export SPARK_DAEMON_MEMORY=256m

export SPARK_WORKER_CORES=2
export SPARK_WORKER_MEMORY=1g
export SPARK_WORKER_DIR=/sdb1/haoranmusicData/spark/work
export SPARK_LOCAL_DIRS=/sdb1/haoranmusicData/spark/local
