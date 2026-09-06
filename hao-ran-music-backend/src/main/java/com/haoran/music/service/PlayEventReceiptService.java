


package com.haoran.music.service;

import com.haoran.music.mapper.PlayEventReceiptMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;




@Service
public class PlayEventReceiptService {

    @Resource
    private PlayEventReceiptMapper receiptMapper;




    @Transactional(rollbackFor = Exception.class)
    public boolean tryClaim(String eventId, Long userId, String songId, Integer isLocal) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return true;
        }
        String normalizedEventId = eventId.trim();
        if (receiptMapper.selectByEventId(normalizedEventId) != null) {
            return false;
        }
        try {
            return receiptMapper.insertProcessing(normalizedEventId, userId, songId, isLocal) > 0;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markProcessed(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        if (receiptMapper.markProcessed(eventId.trim()) <= 0) {
            throw new IllegalStateException("播放事件回执状态更新失败: " + eventId);
        }
    }
}
