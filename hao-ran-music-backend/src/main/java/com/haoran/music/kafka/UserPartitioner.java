package com.haoran.music.kafka;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.Map;











public class UserPartitioner implements Partitioner {











    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        Integer partitionCount = cluster.partitionCountForTopic(topic);


        if (key == null) {
            return (int) (Math.random() * partitionCount);
        }


        return Math.abs(key.hashCode()) % partitionCount;
    }




    @Override
    public void close() {

    }





    @Override
    public void configure(Map<String, ?> configs) {

    }
}
