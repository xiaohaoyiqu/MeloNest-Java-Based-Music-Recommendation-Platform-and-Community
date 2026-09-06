   
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.CreatorEligibilityOutboxEvent;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.CreatorEligibilityOutboxMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.CreatorEligibilityOutboxService;
import com.haoran.music.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

   
                    
                                        
   
@Slf4j
@Service
public class CreatorEligibilityOutboxServiceImpl implements CreatorEligibilityOutboxService {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_BATCH_SIZE = 50;

    private final CreatorEligibilityOutboxMapper outboxMapper;
    private final UserMapper userMapper;
    private final CreatorEligibilityProjectionService projectionService;
    private final TransactionTemplate eventTransaction;

    @javax.annotation.Resource
    private NotificationService notificationService;

    public CreatorEligibilityOutboxServiceImpl(CreatorEligibilityOutboxMapper outboxMapper,
                                               UserMapper userMapper,
                                               CreatorEligibilityProjectionService projectionService,
                                               PlatformTransactionManager transactionManager) {
        this.outboxMapper = outboxMapper;
        this.userMapper = userMapper;
        this.projectionService = projectionService;
        this.eventTransaction = new TransactionTemplate(transactionManager);
        this.eventTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(User user, String eventType, String oldStatus, Long operatorId, String reason) {
        if (user == null || user.getId() == null || user.getCreatorEligibilityVersion() == null) {
            throw new BusinessException("创作者资格事件缺少主记录或版本");
        }
        if (!isSupportedEventType(eventType)) {
            throw new IllegalArgumentException("创作者资格事件类型不受支持");
        }
        CreatorEligibilityOutboxEvent event = new CreatorEligibilityOutboxEvent();
        event.setCreatorId(user.getId());
        event.setEventVersion(user.getCreatorEligibilityVersion());
        event.setSchemaVersion(SCHEMA_VERSION);
        event.setEventType(eventType);
        event.setOldStatus(oldStatus);
        event.setNewStatus(user.getCreatorStatus());
        event.setIsCreator(user.getIsCreator());
        event.setCreatorType(user.getCreatorType());
        event.setOperatorId(operatorId);
        event.setReason(truncate(reason, 500));
        event.setStatus("pending");
        event.setMaxAttempts(MAX_ATTEMPTS);
        if (outboxMapper.insertEvent(event) != 1) {
            throw new BusinessException("创作者资格事件写入失败");
        }
    }

    @Override
    public int retryDueEvents(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, MAX_BATCH_SIZE);
        int recovered = outboxMapper.recoverStaleProcessingEvents();
        if (recovered > 0) {
            log.warn("event=creator_eligibility_outbox_stale_events_recovered count={}", recovered);
        }
        int claimed = 0;
        for (Long eventId : outboxMapper.selectDueEventIds(safeLimit)) {
                                           
            Boolean eventClaimed = eventTransaction.execute(status -> {
                if (outboxMapper.claimEvent(eventId) != 1) {
                    return false;
                }
                processClaimedEvent(eventId);
                return true;
            });
            if (Boolean.TRUE.equals(eventClaimed)) {
                claimed++;
            }
        }
        return claimed;
    }

    private void processClaimedEvent(Long eventId) {
        CreatorEligibilityOutboxEvent event = outboxMapper.selectActiveById(eventId);
        if (event == null) {
            log.warn("event=creator_eligibility_outbox_claimed_event_missing eventId={}", eventId);
            return;
        }
        if (event.getStartedAt() == null) {
            int marked = outboxMapper.markFailedWithoutClaimToken(eventId);
            log.warn("event=creator_eligibility_outbox_terminal_failed eventId={} category=CLAIM_TOKEN_MISSING stateMarked={}",
                    eventId, marked == 1);
            return;
        }
        if (!Integer.valueOf(SCHEMA_VERSION).equals(event.getSchemaVersion())) {
            markTerminalFailure(event, "创作者资格事件结构不受支持", "UNSUPPORTED_SCHEMA");
            return;
        }
        if (!isSupportedEventType(event.getEventType())) {
            markTerminalFailure(event, "创作者资格事件类型不受支持", "UNSUPPORTED_EVENT_TYPE");
            return;
        }
        try {
            User current = userMapper.selectByIdForUpdate(event.getCreatorId());
            if (current == null) {
                markTerminalFailure(event, "创作者资格主记录不存在", "CREATOR_MISSING");
                return;
            }
            long currentVersion = current.getCreatorEligibilityVersion() == null
                    ? 0L : current.getCreatorEligibilityVersion();
            if (currentVersion < event.getEventVersion()) {
                                                          
                markTerminalFailure(event, "创作者资格事件版本超前", "FUTURE_EVENT_VERSION");
                return;
            }
            projectionService.synchronize(current);
            notificationService.sendCreatorEligibilityNotificationOnce(
                    current.getId(), current.getCreatorStatus(), event.getReason(),
                    "creator-eligibility:" + eventId);
            if (outboxMapper.markSuccess(eventId, event.getStartedAt()) != 1) {
                throw new BusinessException("创作者资格事件完成状态写入失败");
            }
        } catch (Exception e) {
            int attempts = event.getAttemptCount() == null ? 1 : Math.max(1, event.getAttemptCount());
            int maxAttempts = event.getMaxAttempts() == null ? MAX_ATTEMPTS : event.getMaxAttempts();
            Integer retryDelayMinutes = attempts < maxAttempts
                    ? Math.toIntExact(Math.min(60L, 2L * attempts)) : null;
            int marked = outboxMapper.markFailed(
                    eventId, event.getStartedAt(), "创作者资格投影补偿失败", retryDelayMinutes);
            log.warn("event=creator_eligibility_outbox_processing_failed eventId={} creatorId={} version={} stateMarked={} errorType={}",
                    eventId, event.getCreatorId(), event.getEventVersion(), marked == 1,
                    e.getClass().getSimpleName());
        }
    }

    private boolean isSupportedEventType(String eventType) {
        return "activated".equals(eventType)
                || "status_changed".equals(eventType)
                || "removed".equals(eventType);
    }

    private void markTerminalFailure(CreatorEligibilityOutboxEvent event,
                                     String errorMessage,
                                     String category) {
        int marked = outboxMapper.markFailed(event.getId(), event.getStartedAt(), errorMessage, null);
        log.warn("event=creator_eligibility_outbox_terminal_failed eventId={} category={} stateMarked={}",
                event.getId(), category, marked == 1);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
