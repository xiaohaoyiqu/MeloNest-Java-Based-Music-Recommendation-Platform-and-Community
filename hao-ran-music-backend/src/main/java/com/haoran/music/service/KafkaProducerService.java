package com.haoran.music.service;

import com.alibaba.fastjson2.JSON;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.kafka.PlayEventListener.PlayEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.UUID;





@Service
@Slf4j
public class KafkaProducerService {

    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 2L;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${haoran.kafka.play-topic:play-events}")
    private String playTopic;

    public String newPlayEventId() {
        return UUID.randomUUID().toString();
    }





    public void sendPlayEvent(Long userId, String songId, Integer isLocal, Integer progress, String quality) {
        PlayEvent event = new PlayEvent();
        event.setEventId(newPlayEventId());
        event.setUserId(String.valueOf(userId));
        event.setSongId(songId);
        event.setIsLocal(isLocal);
        event.setProgress(progress);
        event.setQuality(quality);

        String message = JSON.toJSONString(event);

        kafkaTemplate.send(playTopic, userId.toString(), message);
    }











    public boolean sendPlayEventSync(Long userId, String songId, Integer isLocal, Integer progress, String quality) {
        return sendPlayEventSync(userId, songId, isLocal, progress, quality, newPlayEventId());
    }




    public boolean sendPlayEventSync(Long userId, String songId, Integer isLocal, Integer progress,
                                     String quality, String eventId) {
        try {
            PlayEvent event = new PlayEvent();
            event.setEventId(eventId);
            event.setUserId(String.valueOf(userId));
            event.setSongId(songId);
            event.setIsLocal(isLocal);
            event.setProgress(progress);
            event.setQuality(quality);

            String message = JSON.toJSONString(event);

            kafkaTemplate.send(playTopic, userId.toString(), message)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("event=kafka_message_send_failed errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }










    @Async(CommonConstants.TASK_EXECUTOR)
    public void sendPlayEventAsync(Long userId, String songId, Integer isLocal, Integer progress, String quality) {
        try {
            PlayEvent event = new PlayEvent();
            event.setEventId(newPlayEventId());
            event.setUserId(String.valueOf(userId));
            event.setSongId(songId);
            event.setIsLocal(isLocal);
            event.setProgress(progress);
            event.setQuality(quality);

            String message = JSON.toJSONString(event);

            kafkaTemplate.send(playTopic, userId.toString(), message);
            log.debug("Kafka播放事件发送成功: userId={}, songId={}", userId, songId);
        } catch (Exception e) {
            log.error("event=kafka_play_history_send_failed userId={} songId={} errorType={}",
                    userId, songId, e.getClass().getSimpleName());
        }
    }
}
