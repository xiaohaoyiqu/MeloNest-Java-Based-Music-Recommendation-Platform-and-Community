




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.dto.curated.CuratedBannerRequest;
import com.haoran.music.entity.HotEvent;
import com.haoran.music.mapper.HotEventMapper;
import com.haoran.music.service.HotEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;




@Slf4j
@Service
public class HotEventServiceImpl extends ServiceImpl<HotEventMapper, HotEvent> implements HotEventService {

    private static final String HOT_EVENT_FEATURED_CACHE_PREFIX = "hot:events:featured:";
    private static final int DEFAULT_FEATURED_LIMIT = 5;
    private static final int MAX_FEATURED_LIMIT = 20;
    private static final String HOT_EVENT_VIEW_DEDUPE_PREFIX = "view:dedupe:hot-event:";
    private static final long VIEW_DEDUPE_MINUTES = 10L;
    private static final String SOURCE_INTERNAL = "internal";
    private static final String SOURCE_EXTERNAL = "external";
    private static final String SOURCE_NEWS = "news";
    private static final int REVIEW_PENDING = 0;
    private static final int REVIEW_APPROVED = 1;
    private static final int REVIEW_REJECTED = 2;

    @Autowired
    private HotEventMapper hotEventMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Override
    public List<Object> getFeaturedEvents(Integer limit) {
        int resolvedLimit = ObjectUtils.isEmpty(limit) || limit <= 0
                ? DEFAULT_FEATURED_LIMIT
                : Math.min(limit, MAX_FEATURED_LIMIT);

        String cacheKey = HOT_EVENT_FEATURED_CACHE_PREFIX + resolvedLimit;
        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> loadFeaturedEvents(resolvedLimit),
                5,
                TimeUnit.MINUTES,
                List.class
        );
    }

    @Override
    public Map<String, Object> getEventDetail(Long eventId, String viewerKey) {
        if (ObjectUtils.isEmpty(eventId)) {
            log.warn("事件ID为空: eventId={}", eventId);
            return null;
        }

        HotEvent event = hotEventMapper.selectById(eventId);
        if (ObjectUtils.isEmpty(event)
                || Boolean.TRUE.equals(event.getIsDeleted())
                || !isApproved(event)) {
            log.warn("事件不存在或已删除: eventId={}", eventId);
            return null;
        }

        incrementViewCountOnce(eventId, viewerKey);

        return convertToVO(event);
    }

    private void incrementViewCountOnce(Long eventId, String viewerKey) {
        if (viewerKey == null || viewerKey.trim().isEmpty()) {
            return;
        }
        try {
            String key = HOT_EVENT_VIEW_DEDUPE_PREFIX + eventId + ":" + viewerKey;
            if (redisUtils.setIfAbsent(key, 1, VIEW_DEDUPE_MINUTES, TimeUnit.MINUTES)) {
                incrementViewCount(eventId);
            }
        } catch (Exception ex) {
            log.warn("事件曝光去重不可用，本次不累计浏览量: eventId={}", eventId);
        }
    }

    @Override
    public Boolean incrementViewCount(Long eventId) {
        if (ObjectUtils.isEmpty(eventId)) {
            log.warn("事件ID为空: eventId={}", eventId);
            return false;
        }

        int result = hotEventMapper.update(null, new LambdaUpdateWrapper<HotEvent>()
                .eq(HotEvent::getId, eventId)
                .eq(HotEvent::getIsDeleted, false)
                .and(query -> query.isNull(HotEvent::getReviewStatus)
                        .or()
                        .eq(HotEvent::getReviewStatus, REVIEW_APPROVED))
                .setSql("view_count = IFNULL(view_count, 0) + 1"));

        log.info("增加事件浏览量: eventId={}, result={}", eventId, result);
        return result > 0;
    }

    private List<Object> loadFeaturedEvents(int limit) {
        LambdaQueryWrapper<HotEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotEvent::getIsDeleted, false)
                .eq(HotEvent::getIsFeatured, true)
                .and(query -> query.isNull(HotEvent::getReviewStatus)
                        .or()
                        .eq(HotEvent::getReviewStatus, REVIEW_APPROVED))
                .orderByAsc(HotEvent::getSortOrder)
                .orderByDesc(HotEvent::getEventDate)
                .orderByDesc(HotEvent::getCreateTime)
                .last("LIMIT " + limit);

        List<HotEvent> events = hotEventMapper.selectList(wrapper);
        return events.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }




    private Map<String, Object> convertToVO(HotEvent event) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", event.getId());
        vo.put("title", event.getTitle());
        vo.put("description", event.getDescription());
        vo.put("cover", event.getCover());
        vo.put("eventType", event.getEventType());
        vo.put("eventDate", event.getEventDate());
        vo.put("source", event.getSource());
        vo.put("sourceUrl", event.getSourceUrl());
        vo.put("fallbackSourceUrl", event.getFallbackSourceUrl());
        vo.put("sourceType", StringUtils.defaultIfBlank(event.getSourceType(), SOURCE_EXTERNAL));
        vo.put("viewCount", event.getViewCount() != null ? event.getViewCount() : 0);


        if (!ObjectUtils.isEmpty(event.getRelatedArtists())) {
            vo.put("relatedArtists", event.getRelatedArtists());
        }
        if (!ObjectUtils.isEmpty(event.getRelatedSongs())) {
            vo.put("relatedSongs", event.getRelatedSongs());
        }

        return vo;
    }

    @Override
    public List<HotEvent> listAdminFeaturedEvents() {
        return hotEventMapper.selectList(new LambdaQueryWrapper<HotEvent>()
                .orderByDesc(HotEvent::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAdminFeaturedEvent(CuratedBannerRequest request, Long operatorId) {
        validateBannerRequest(request);
        HotEvent event = new HotEvent();
        applyRequest(event, request);
        event.setViewCount(0);
        event.setIsFeatured(false);
        event.setIsDeleted(false);
        event.setReviewStatus(REVIEW_PENDING);
        hotEventMapper.insert(event);
        clearFeaturedCache();
        return event.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAdminFeaturedEvent(Long eventId, CuratedBannerRequest request, Long operatorId) {
        validateBannerRequest(request);
        HotEvent event = hotEventMapper.selectById(eventId);
        if (event == null || Boolean.TRUE.equals(event.getIsDeleted())) {
            return false;
        }
        applyRequest(event, request);
        event.setIsFeatured(false);
        event.setReviewStatus(REVIEW_PENDING);
        event.setReviewerId(null);
        event.setReviewTime(null);
        event.setReviewRemark(null);
        hotEventMapper.updateById(event);
        clearFeaturedCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewAdminFeaturedEvent(Long eventId, boolean approved, String remark, Long reviewerId) {
        HotEvent event = hotEventMapper.selectById(eventId);
        if (event == null || Boolean.TRUE.equals(event.getIsDeleted())) {
            return false;
        }
        int updated = hotEventMapper.update(null,
                new LambdaUpdateWrapper<HotEvent>()
                        .eq(HotEvent::getId, eventId)
                        .eq(HotEvent::getIsDeleted, false)
                        .eq(HotEvent::getReviewStatus, REVIEW_PENDING)
                        .set(HotEvent::getReviewStatus,
                                approved ? REVIEW_APPROVED : REVIEW_REJECTED)
                        .set(HotEvent::getIsFeatured, approved)
                        .set(HotEvent::getReviewerId, reviewerId)
                        .set(HotEvent::getReviewTime, java.time.LocalDateTime.now())
                        .set(HotEvent::getReviewRemark,
                                StringUtils.abbreviate(normalize(remark), 500)));
        if (updated != 1) {
            return false;
        }
        clearFeaturedCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableAdminFeaturedEvent(Long eventId, Long operatorId) {
        HotEvent event = hotEventMapper.selectById(eventId);
        if (event == null) {
            return false;
        }
        event.setIsFeatured(false);
        event.setIsDeleted(true);
        hotEventMapper.updateById(event);
        clearFeaturedCache();
        return true;
    }

    private void applyRequest(HotEvent event, CuratedBannerRequest request) {
        event.setTitle(request.getTitle().trim());
        event.setDescription(normalize(request.getDescription()));
        event.setCover(normalize(request.getCover()));
        event.setEventType(StringUtils.defaultIfBlank(normalize(request.getEventType()), "news"));
        event.setEventDate(request.getEventDate());
        event.setSource(normalize(request.getSource()));
        event.setSourceUrl(normalize(request.getSourceUrl()));
        event.setFallbackSourceUrl(normalize(request.getFallbackSourceUrl()));
        event.setSourceType(normalizeSourceType(request.getSourceType()));
        event.setRelatedArtists(normalize(request.getRelatedArtists()));
        event.setRelatedSongs(normalize(request.getRelatedSongs()));
        event.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private void validateBannerRequest(CuratedBannerRequest request) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            throw new IllegalArgumentException("轮播标题不能为空");
        }
        if (StringUtils.isBlank(request.getSourceUrl())
                && StringUtils.isBlank(request.getFallbackSourceUrl())) {
            throw new IllegalArgumentException("主链接和备用链接至少填写一个");
        }
        String sourceType = normalizeSourceType(request.getSourceType());
        if (SOURCE_NEWS.equals(sourceType)) {
            throw new IllegalArgumentException("新闻轮播只能由新闻审核流程同步，不能从轮播入口直接录入");
        }
        validateCoverUrl(request.getCover(), "封面");
        validateSourceUrl(request.getSourceUrl(), sourceType, "主链接");
        validateSourceUrl(request.getFallbackSourceUrl(), sourceType, "备用链接");
    }

    private void validateSourceUrl(String url, String sourceType, String fieldName) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (SOURCE_INTERNAL.equals(sourceType)) {
            if (!value.startsWith("/") || value.startsWith("//")) {
                throw new IllegalArgumentException(fieldName + "必须是站内路径，例如 /song/1");
            }
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + "必须使用 http 或 https 链接");
        }
    }

    private String normalizeSourceType(String sourceType) {
        String value = StringUtils.defaultIfBlank(normalize(sourceType), SOURCE_EXTERNAL)
                .toLowerCase(Locale.ROOT);
        if (!SOURCE_INTERNAL.equals(value)
                && !SOURCE_EXTERNAL.equals(value)
                && !SOURCE_NEWS.equals(value)) {
            throw new IllegalArgumentException("来源类型只能是 internal、external 或 news");
        }
        return value;
    }

    private void validateCoverUrl(String url, String fieldName) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return;
        }
        if (value.startsWith("/") && !value.startsWith("//")) {
            return;
        }
        throw new IllegalArgumentException(fieldName + "必须使用 http、https 或站内路径");
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private boolean isApproved(HotEvent event) {
        return event.getReviewStatus() == null || event.getReviewStatus() == REVIEW_APPROVED;
    }

    private void clearFeaturedCache() {
        CacheHelper.deleteByPattern(redisUtils, HOT_EVENT_FEATURED_CACHE_PREFIX + "*");
    }
}
