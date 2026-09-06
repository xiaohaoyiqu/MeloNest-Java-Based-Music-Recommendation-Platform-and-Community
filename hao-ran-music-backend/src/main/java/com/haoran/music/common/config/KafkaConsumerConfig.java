package com.haoran.music.common.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.haoran.music.kafka.PlayEventRecordInterceptor;
import com.haoran.music.service.PlayEventDeadLetterService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;













@Configuration
public class KafkaConsumerConfig {

    @Resource
    private PlayEventDeadLetterService playEventDeadLetterService;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${haoran.kafka.play-consumer-group:haoran-music-consumer}")
    private String playConsumerGroup;




    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, playConsumerGroup);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);         
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");            
        return new DefaultKafkaConsumerFactory<>(config);
    }







    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);


        factory.setRecordInterceptor(new PlayEventRecordInterceptor());


        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000);          
        backOff.setMultiplier(2.0);                 
        backOff.setMaxInterval(10000);               

        CommonErrorHandler errorHandler = buildErrorHandler(backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }






    @Bean("fixedRetryKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> fixedRetryKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);


        factory.setRecordInterceptor(new PlayEventRecordInterceptor());


        FixedBackOff backOff = new FixedBackOff(2000, 5);              
        CommonErrorHandler errorHandler = buildErrorHandler(backOff);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    private CommonErrorHandler buildErrorHandler(org.springframework.util.backoff.BackOff backOff) {
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            ConsumerRecord<?, ?> consumerRecord = (ConsumerRecord<?, ?>) record;
            playEventDeadLetterService.recordFailure(
                    extractEventId(consumerRecord.value()),
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    consumerRecord.key() == null ? null : String.valueOf(consumerRecord.key()),
                    consumerRecord.value() == null ? null : String.valueOf(consumerRecord.value()),
                    exception == null ? null : exception.getMessage());
        };
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        errorHandler.setAckAfterHandle(true);
        return errorHandler;
    }

    private String extractEventId(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            JSONObject object = JSON.parseObject(String.valueOf(payload));
            return object == null ? null : object.getString("eventId");
        } catch (Exception ignored) {
            return null;
        }
    }
}
