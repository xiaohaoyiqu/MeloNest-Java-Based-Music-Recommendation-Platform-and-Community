package com.haoran.music.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

   
                      
                         
  
        
                                             
           
                                 
                                
                                
   
@Slf4j
public class PlayEventRecordInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record) {
        log.debug("event=play_event_consumption_started topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        return record;
    }
}
