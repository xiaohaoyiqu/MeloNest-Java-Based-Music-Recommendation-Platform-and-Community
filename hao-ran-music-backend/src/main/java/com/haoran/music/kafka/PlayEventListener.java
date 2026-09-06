package com.haoran.music.kafka;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.ValidPlayPolicy;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.entity.LocalMusic;
import com.haoran.music.entity.QualifiedPlayFact;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.ListenHistoryMapper;
import com.haoran.music.mapper.LocalMusicMapper;
import com.haoran.music.mapper.QualifiedPlayFactMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.LocalMusicService;
import com.haoran.music.service.PlayEventReceiptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

   
                      
                             
  
        
                    
                    
                                         
   
@Slf4j
@Component
public class PlayEventListener {

    @Resource
    private ListenHistoryMapper listenHistoryMapper;
    @Resource
    private LocalMusicService localMusicService;
    @Resource
    private LocalMusicMapper localMusicMapper;
    @Resource
    private SongMapper songMapper;
    @Resource
    private ArtistMapper artistMapper;
    @Resource
    private AlbumMapper albumMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PlayEventReceiptService playEventReceiptService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private QualifiedPlayFactMapper qualifiedPlayFactMapper;

       
                   
                         
      
          
                      
                                  
                                
      
                          
                                   
                           
                            
                          
       
    @KafkaListener(
        topics = "${haoran.kafka.play-topic:play-events}",
        containerFactory = "kafkaListenerContainerFactory",
        groupId = "${haoran.kafka.play-consumer-group:haoran-music-consumer}"
    )
    @Transactional(rollbackFor = Exception.class)
    public void handlePlayEvent(
            @Payload String message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.currentTimeMillis();

        try {
            log.debug("event=play_event_received topic={} partition={} offset={} payloadLength={}",
                    topic, partition, offset, message == null ? 0 : message.length());

            PlayEvent event;
            try {
                event = JSON.parseObject(message, PlayEvent.class);
            } catch (Exception e) {
                log.warn("event=play_event_rejected topic={} partition={} offset={} category=PARSE_ERROR",
                        topic, partition, offset);
                acknowledgeAfterCommit(acknowledgment);
                return;
            }
            normalizeEventId(event, topic, partition, offset);

                   
            if (!validateEvent(event)) {
                log.warn("[Kafka消息校验失败] topic={}, partition={}, offset={}",
                        topic, partition, offset);
                acknowledgeAfterCommit(acknowledgment);
                return;
            }

            processPlayEvent(event);
            acknowledgeAfterCommit(acknowledgment);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("event=play_event_processed topic={} partition={} offset={} durationMs={}",
                    topic, partition, offset, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("event=play_event_processing_failed topic={} partition={} offset={} durationMs={} errorType={}",
                    topic, partition, offset, duration, e.getClass().getSimpleName());
            throw e;
        }
    }

       
                                    
       
    @Transactional(rollbackFor = Exception.class)
    public void replayDeadLetter(String message, String topic, int partition, long offset) {
        PlayEvent event = JSON.parseObject(message, PlayEvent.class);
        normalizeEventId(event, topic, partition, offset);
        if (!validateEvent(event)) {
            throw new IllegalArgumentException("播放事件消息校验失败");
        }
        processPlayEvent(event);
    }

       
                
       
    private void processPlayEvent(PlayEvent event) {
        Long userId = parseId(event.getUserId(), "user");
        Long songId = parseId(event.getSongId(), "song");
        if (userId == null || songId == null) {
            return;
        }

        if (!playEventReceiptService.tryClaim(event.getEventId(), userId, event.getSongId(), event.getIsLocal())) {
            log.debug("跳过重复播放事件: eventId={}, userId={}, songId={}",
                    event.getEventId(), event.getUserId(), event.getSongId());
            return;
        }

        Song song = null;
        LocalMusic localMusic = null;
        if (Integer.valueOf(1).equals(event.getIsLocal())) {
                                                   
            if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
                log.debug("跳过不可互动账号的本地播放事件: userId={}, songId={}", event.getUserId(), event.getSongId());
                playEventReceiptService.markProcessed(event.getEventId());
                return;
            }
            localMusic = localMusicMapper.selectById(songId);
            if (localMusic == null || !userId.equals(localMusic.getUserId())) {
                log.warn("忽略非归属本地播放事件: userId={}, songId={}", event.getUserId(), event.getSongId());
                playEventReceiptService.markProcessed(event.getEventId());
                return;
            }
        } else {
            if (!canContributePlayStats(userId)) {
                log.debug("跳过非公共统计账号播放事件: userId={}, songId={}", event.getUserId(), event.getSongId());
                playEventReceiptService.markProcessed(event.getEventId());
                return;
            }
            song = songMapper.selectById(songId);
            if (!canContributeSongStats(song)) {
                log.debug("跳过公开作者异常歌曲播放事件: userId={}, songId={}", event.getUserId(), event.getSongId());
                playEventReceiptService.markProcessed(event.getEventId());
                return;
            }
        }

        boolean qualified = ValidPlayPolicy.isQualified(event.getProgress(),
                localMusic != null ? localMusic.getDuration() : (song != null ? song.getDuration() : null));
                                   
        saveListenHistory(event, userId, songId, qualified);

        if (!qualified) {
            playEventReceiptService.markProcessed(event.getEventId());
            return;
        }

        appendQualifiedPlayFact(event, userId, songId, song);

                 
        if (Integer.valueOf(1).equals(event.getIsLocal())) {
            localMusicService.incrementPlayCount(userId, songId);
        } else {
            incrementPlayCounts(songId, song);
        }
        playEventReceiptService.markProcessed(event.getEventId());
    }

       
                          
       
    private void appendQualifiedPlayFact(PlayEvent event, Long userId, Long songId, Song song) {
        if (Integer.valueOf(1).equals(event.getIsLocal())
                || ObjectUtils.isEmpty(qualifiedPlayFactMapper)) {
            return;
        }
        QualifiedPlayFact fact = new QualifiedPlayFact();
        fact.setEventId(event.getEventId());
        fact.setUserId(userId);
        fact.setSongId(songId);
        fact.setProgressSeconds(event.getProgress());
        fact.setDurationSeconds(ObjectUtils.isEmpty(song) ? null : song.getDuration());
        fact.setPolicyVersion("valid-play-v1");
        qualifiedPlayFactMapper.insertIgnore(fact);
    }

       
             
      
                        
                   
       
    private boolean validateEvent(PlayEvent event) {
        if (event == null) {
            return false;
        }
        if (ObjectUtils.isEmpty(event.getEventId()) || event.getEventId().length() > 96) {
            log.warn("eventId为空或过长");
            return false;
        }
        if (ObjectUtils.isEmpty(event.getUserId())) {
            log.warn("userId为空");
            return false;
        }
        if (ObjectUtils.isEmpty(event.getSongId())) {
            log.warn("songId为空");
            return false;
        }
        event.setIsLocal(Integer.valueOf(1).equals(event.getIsLocal()) ? 1 : 0);
        return true;
    }

       
                      
      
                         
       
    private void incrementPlayCounts(Long songId, Song song) {
        try {
            if (!canContributeSongStats(song)) {
                log.debug("跳过公开作者异常歌曲播放事件热度: songId={}", songId);
                return;
            }

                      
            songMapper.incrementPlayCount(songId);

            if (song != null) {
                for (Long artistId : resolveArtistIds(song)) {
                    artistMapper.incrementPlayCount(artistId);
                }

                          
                if (ObjectUtils.isNotEmpty(song.getAlbumId())) {
                    albumMapper.incrementPlayCount(song.getAlbumId());
                }
            }

            log.debug("更新播放量成功: songId={}", songId);
        } catch (Exception e) {
            log.warn("event=play_count_projection_failed songId={} errorType={}",
                    songId, e.getClass().getSimpleName());
            throw e;             
        }
    }

       
             
       
    private Set<Long> resolveArtistIds(Song song) {
        Set<Long> artistIds = new LinkedHashSet<>();
        if (song == null) {
            return artistIds;
        }
        if (song.getArtistId() != null) {
            artistIds.add(song.getArtistId());
        }
        if (ObjectUtils.isNotEmpty(song.getArtistIds())) {
            for (String artistIdStr : song.getArtistIds().split(",")) {
                try {
                    String trimmed = artistIdStr.trim();
                    if (!trimmed.isEmpty()) {
                        artistIds.add(Long.parseLong(trimmed));
                    }
                } catch (NumberFormatException e) {
                    log.warn("event=play_event_artist_id_invalid category=INVALID_ARTIST_ID");
                }
            }
        }
        return artistIds;
    }

    private boolean canContributePlayStats(Long userId) {
        if (userId == null) {
            return false;
        }
        return UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById);
    }

    private boolean canContributeSongStats(Song song) {
        if (song == null
                || !CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                || !CommonConstants.NOT_DELETED.equals(song.getDeleted())) {
            return false;
        }
        if (song.getUploaderId() == null) {
            return true;
        }
        return UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById);
    }

    private Long parseId(String value, String fieldName) {
        if (ObjectUtils.isEmpty(value)) {
            log.warn("event=play_event_id_invalid field={}", fieldName);
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("event=play_event_id_invalid field={}", fieldName);
            return null;
        }
    }

    private void saveListenHistory(PlayEvent event, Long userId, Long songId, boolean qualified) {
        try {
            LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ListenHistory::getUserId, userId)
                    .eq(ListenHistory::getSongId, songId)
                    .eq(ListenHistory::getIsLocal, event.getIsLocal())
                    .orderByDesc(ListenHistory::getCreateTime)
                    .last("LIMIT 1");

            ListenHistory existing = listenHistoryMapper.selectOne(queryWrapper);

            if (existing != null) {
                existing.setProgress(event.getProgress());
                existing.setQuality(event.getQuality());
                existing.setIsCompleted(qualified ? 1 : 0);
                existing.setCreateTime(LocalDateTime.now());
                listenHistoryMapper.updateById(existing);
            } else {
                ListenHistory history = new ListenHistory();
                history.setUserId(userId);
                history.setSongId(songId);
                history.setIsLocal(event.getIsLocal());
                history.setProgress(event.getProgress());
                history.setQuality(event.getQuality());
                history.setIsCompleted(qualified ? 1 : 0);
                listenHistoryMapper.insert(history);
            }
        } catch (Exception e) {
            log.warn("event=play_history_persist_failed userId={} songId={} errorType={}",
                    userId, songId, e.getClass().getSimpleName());
            throw e;             
        }
    }

    private void acknowledgeAfterCommit(Acknowledgment acknowledgment) {
        if (acknowledgment == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            acknowledgment.acknowledge();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                acknowledgment.acknowledge();
            }
        });
    }

    private void normalizeEventId(PlayEvent event, String topic, int partition, long offset) {
        if (event != null && ObjectUtils.isEmpty(event.getEventId())) {
            event.setEventId("legacy-" + topic + "-" + partition + "-" + offset);
        }
    }

       
           
       
    public static class PlayEvent {
        private String eventId;
        private String userId;
        private String songId;
        private Integer isLocal;
        private Integer progress;
        private String quality;

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getSongId() { return songId; }
        public void setSongId(String songId) { this.songId = songId; }
        public Integer getIsLocal() { return isLocal; }
        public void setIsLocal(Integer isLocal) { this.isLocal = isLocal; }
        public Integer getProgress() { return progress; }
        public void setProgress(Integer progress) { this.progress = progress; }
        public String getQuality() { return quality; }
        public void setQuality(String quality) { this.quality = quality; }
    }
}
