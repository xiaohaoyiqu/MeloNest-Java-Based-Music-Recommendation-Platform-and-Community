


package com.haoran.music.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.entity.HotEvent;
import com.haoran.music.entity.PushNotification;
import com.haoran.music.mapper.HotEventMapper;
import com.haoran.music.mapper.PushNotificationMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;








@Slf4j
@Component
public class NewsFeedRefreshTask {

    private static final String NEWS_TYPE = "news";
    private static final String NEWS_SOURCE_TYPE = "news";
    private static final String PUSH_CACHE_PATTERN = "push:notifications:active*";
    private static final String HOT_EVENT_CACHE_PATTERN = "hot:events:featured:*";
    private static final int REVIEW_APPROVED = 1;
    private static final int DEFAULT_LIMIT = 5;

    private final Object refreshLock = new Object();

    @Resource
    private PushNotificationMapper pushNotificationMapper;

    @Resource
    private HotEventMapper hotEventMapper;

    @Resource
    private RedisUtils redisUtils;

    @Value("${news.limit:5}")
    private Integer limit;

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refreshNewsFeed("startup");
    }

    @Scheduled(cron = "${schedule.task.news.refresh-cron:0 10 */3 * * ?}")
    public void refreshScheduled() {
        refreshNewsFeed("scheduled");
    }




    public void refreshNow() {
        refreshNewsFeed("manual");
    }

    private void refreshNewsFeed(String trigger) {
        synchronized (refreshLock) {
            try {
                List<NewsFeedItem> items = loadApprovedNews();
                syncNewsProjection(items);
                log.info("[NewsFeed] database refresh completed: trigger={}, count={}",
                        trigger, items.size());
            } catch (Exception e) {
                log.error("[NewsFeed] database refresh failed: trigger={}, error={}",
                        trigger, e.getClass().getSimpleName());
            }
        }
    }

    private List<NewsFeedItem> loadApprovedNews() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<PushNotification> wrapper = new LambdaQueryWrapper<PushNotification>()
                .eq(PushNotification::getType, NEWS_TYPE)
                .eq(PushNotification::getStatus, 1)
                .and(query -> query.isNull(PushNotification::getReviewStatus)
                        .or()
                        .eq(PushNotification::getReviewStatus, REVIEW_APPROVED))
                .and(query -> query.isNull(PushNotification::getStartTime)
                        .or()
                        .le(PushNotification::getStartTime, now))
                .and(query -> query.isNull(PushNotification::getEndTime)
                        .or()
                        .ge(PushNotification::getEndTime, now))
                .orderByDesc(PushNotification::getCreateTime)
                .last("LIMIT " + resolveLimit());

        List<PushNotification> entities = pushNotificationMapper.selectList(wrapper);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<NewsFeedItem> items = new ArrayList<>(entities.size());
        for (PushNotification entity : entities) {
            String primaryLink = normalize(entity.getLink());
            String fallbackLink = normalize(entity.getFallbackLink());
            String resolvedLink = StringUtils.defaultIfBlank(primaryLink, fallbackLink);
            if (StringUtils.isBlank(entity.getTitle()) || StringUtils.isBlank(resolvedLink)) {
                continue;
            }

            NewsFeedItem item = new NewsFeedItem();
            item.setTitle(entity.getTitle().trim());
            item.setDescription(normalize(entity.getDescription()));
            item.setCoverUrl(normalize(entity.getCoverUrl()));
            item.setLink(resolvedLink);
            item.setFallbackLink(StringUtils.equals(resolvedLink, primaryLink) ? fallbackLink : null);
            item.setSourceName(StringUtils.defaultIfBlank(normalize(entity.getBadge()), "新闻"));
            item.setPublishedAt(entity.getStartTime() != null
                    ? entity.getStartTime()
                    : entity.getCreateTime());
            item.setPriority(entity.getPriority() == null ? 0 : entity.getPriority());
            items.add(item);
        }
        return items;
    }

    private void syncNewsProjection(List<NewsFeedItem> items) {
        Set<String> activeSourceUrls = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            NewsFeedItem item = items.get(i);
            activeSourceUrls.add(item.getLink());
            upsertHotEvent(item, i);
        }

        disableStaleNewsProjection(activeSourceUrls);
        clearCaches();
    }

    private void upsertHotEvent(NewsFeedItem item, int index) {
        HotEvent event = hotEventMapper.selectOne(new LambdaQueryWrapper<HotEvent>()
                .eq(HotEvent::getEventType, NEWS_TYPE)
                .eq(HotEvent::getSourceType, NEWS_SOURCE_TYPE)
                .eq(HotEvent::getSourceUrl, item.getLink())
                .last("LIMIT 1"));

        if (event == null) {
            event = new HotEvent();
            event.setCreateTime(LocalDateTime.now());
        }

        event.setTitle(item.getTitle());
        event.setDescription(item.getDescription());
        event.setCover(item.getCoverUrl());
        event.setFallbackSourceUrl(item.getFallbackLink());
        event.setEventType(NEWS_TYPE);
        event.setEventDate(item.getPublishedAt() == null
                ? LocalDate.now()
                : item.getPublishedAt().toLocalDate());
        event.setSource(item.getSourceName());
        event.setSourceUrl(item.getLink());
        event.setSourceType(NEWS_SOURCE_TYPE);
        event.setViewCount(event.getViewCount() == null ? 0 : event.getViewCount());
        event.setIsFeatured(true);
        event.setSortOrder(index + 1);
        event.setIsDeleted(false);
        event.setReviewStatus(REVIEW_APPROVED);
        event.setReviewerId(null);
        event.setReviewTime(null);
        event.setReviewRemark("新闻审核数据同步");

        if (event.getId() == null) {
            hotEventMapper.insert(event);
        } else {
            hotEventMapper.updateById(event);
        }
    }

    private void disableStaleNewsProjection(Set<String> activeSourceUrls) {
        LambdaUpdateWrapper<HotEvent> wrapper = new LambdaUpdateWrapper<HotEvent>()
                .eq(HotEvent::getEventType, NEWS_TYPE)
                .eq(HotEvent::getSourceType, NEWS_SOURCE_TYPE)
                .eq(HotEvent::getIsFeatured, true)
                .eq(HotEvent::getIsDeleted, false);
        if (!activeSourceUrls.isEmpty()) {
            wrapper.notIn(HotEvent::getSourceUrl, activeSourceUrls);
        }
        wrapper.set(HotEvent::getIsFeatured, false)
                .set(HotEvent::getIsDeleted, true);
        hotEventMapper.update(null, wrapper);
    }

    private void clearCaches() {
        CacheHelper.deleteByPattern(redisUtils, PUSH_CACHE_PATTERN);
        CacheHelper.deleteByPattern(redisUtils, HOT_EVENT_CACHE_PATTERN);
    }

    private int resolveLimit() {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 20);
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    @Data
    private static class NewsFeedItem {
        private String title;
        private String description;
        private String coverUrl;
        private String link;
        private String fallbackLink;
        private String sourceName;
        private LocalDateTime publishedAt;
        private Integer priority;
    }
}
