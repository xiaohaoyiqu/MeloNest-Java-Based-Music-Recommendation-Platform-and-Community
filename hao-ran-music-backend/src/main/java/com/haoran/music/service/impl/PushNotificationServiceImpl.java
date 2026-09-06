package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.dto.curated.CuratedNewsRequest;
import com.haoran.music.entity.PushNotification;
import com.haoran.music.mapper.PushNotificationMapper;
import com.haoran.music.service.PushNotificationService;
import com.haoran.music.vo.push.PushNotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final String PUSH_CACHE_KEY = "push:notifications:active";
    private static final String PUSH_TYPE_CACHE_KEY_PREFIX = "push:notifications:active:type:";
    private static final String NEWS_TYPE = "news";
    private static final int REVIEW_PENDING = 0;
    private static final int REVIEW_APPROVED = 1;
    private static final int REVIEW_REJECTED = 2;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private PushNotificationMapper pushNotificationMapper;

    @Override
    public List<PushNotificationVO> getActivePushNotifications() {
        return loadActivePushNotifications(null);
    }

    @Override
    public List<PushNotificationVO> getPushNotificationsByType(String type) {
        return loadActivePushNotifications(normalizeType(type));
    }

    private List<PushNotificationVO> loadActivePushNotifications(String type) {
        String cacheKey = ObjectUtils.isNotEmpty(type)
                ? PUSH_TYPE_CACHE_KEY_PREFIX + type
                : PUSH_CACHE_KEY;

        try {

            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                List<PushNotificationVO> result = convertCachedPushes((List<?>) cached);
                return filterActivePushes(result);
            }
        } catch (Exception e) {

            log.warn("[PushNotification] 读取缓存失败，回退数据库: type={}, error={}",
                    type, e.getClass().getSimpleName());
        }

        try {
            LambdaQueryWrapper<PushNotification> wrapper = new LambdaQueryWrapper<PushNotification>()
                    .eq(PushNotification::getStatus, 1)
                    .and(query -> query.isNull(PushNotification::getReviewStatus)
                            .or()
                            .eq(PushNotification::getReviewStatus, REVIEW_APPROVED))
                    .orderByDesc(PushNotification::getCreateTime)
                    .orderByDesc(PushNotification::getPriority);
            if (ObjectUtils.isNotEmpty(type)) {
                wrapper.eq(PushNotification::getType, type);
            }
            List<PushNotification> entities = pushNotificationMapper.selectList(wrapper);

            List<PushNotificationVO> pushes = entities.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            try {
                redisTemplate.opsForValue().set(cacheKey, pushes, 5, TimeUnit.MINUTES);
            } catch (Exception cacheError) {
                log.warn("[PushNotification] 写入缓存失败，不影响数据库结果: type={}, error={}",
                        type, cacheError.getClass().getSimpleName());
            }

            return filterActivePushes(pushes);
        } catch (Exception e) {
            log.error("[PushNotification] 获取推送通知失败: type={}, error={}", type, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPushNotification(PushNotificationVO push) {

        if (ObjectUtils.isNotEmpty(push.getPushId())) {
            PushNotification existing = pushNotificationMapper.selectOne(
                    new LambdaQueryWrapper<PushNotification>()
                            .eq(PushNotification::getPushId, push.getPushId())
            );
            if (ObjectUtils.isNotEmpty(existing)) {

                existing.setType(push.getType());
                existing.setTitle(push.getTitle());
                existing.setDescription(push.getDescription());
                existing.setBadge(push.getBadge());
                existing.setCoverUrl(push.getCoverUrl());
                existing.setLink(push.getLink());
                existing.setFallbackLink(push.getFallbackLink());
                existing.setPriority(push.getPriority());
                existing.setStartTime(push.getStartTime());
                existing.setEndTime(push.getEndTime());
                existing.setStatus(1);
                if (existing.getReviewStatus() == null) {
                    existing.setReviewStatus(REVIEW_APPROVED);
                }
                pushNotificationMapper.updateById(existing);
                clearCache();
                log.info("[PushNotification] 更新推送通知: pushId={}, title={}", push.getPushId(), push.getTitle());
                return push.getPushId();
            }
        }


        PushNotification entity = new PushNotification();
        BeanUtils.copyProperties(push, entity);


        String pushId = push.getPushId();
        if (ObjectUtils.isEmpty(pushId)) {
            pushId = UUID.randomUUID().toString();
        }
        entity.setPushId(pushId);
        entity.setStatus(1);

        pushNotificationMapper.insert(entity);
        clearCache();

        log.info("[PushNotification] 创建推送通知: pushId={}, title={}", pushId, push.getTitle());
        return pushId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePushNotification(String pushId) {
        try {

            pushNotificationMapper.delete(
                    new LambdaQueryWrapper<PushNotification>()
                            .eq(PushNotification::getPushId, pushId)
            );

            clearCache();

            log.info("[PushNotification] 删除推送通知: pushId={}", pushId);
            return true;

        } catch (Exception e) {
            log.error("[PushNotification] 删除推送通知失败: pushId={}, error={}", pushId, e.getClass().getSimpleName());
            return false;
        }
    }







    public boolean disablePushNotification(String pushId) {
        try {
            PushNotification entity = pushNotificationMapper.selectOne(
                    new LambdaQueryWrapper<PushNotification>()
                            .eq(PushNotification::getPushId, pushId)
            );

            if (ObjectUtils.isNotEmpty(entity)) {
                entity.setStatus(0);
                pushNotificationMapper.updateById(entity);
                clearCache();
                log.info("[PushNotification] 禁用推送通知: pushId={}", pushId);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("[PushNotification] 禁用推送通知失败: pushId={}, error={}", pushId, e.getClass().getSimpleName());
            return false;
        }
    }




    private void clearCache() {
        try {
            redisTemplate.delete(PUSH_CACHE_KEY);
            CacheHelper.deleteByPattern(redisUtils, PUSH_TYPE_CACHE_KEY_PREFIX + "*");
        } catch (Exception e) {

            log.warn("[PushNotification] 清理缓存失败: {}", e.getClass().getSimpleName());
        }
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private List<PushNotificationVO> convertCachedPushes(List<?> cached) {
        if (ObjectUtils.isEmpty(cached)) {
            return Collections.emptyList();
        }

        List<PushNotificationVO> pushes = new ArrayList<>(cached.size());
        for (Object item : cached) {
            if (item instanceof PushNotificationVO) {
                pushes.add((PushNotificationVO) item);
                continue;
            }
            if (item instanceof Map) {
                pushes.add(JSON.parseObject(JSON.toJSONString(item), PushNotificationVO.class));
            }
        }
        return pushes;
    }




    private List<PushNotificationVO> filterActivePushes(List<PushNotificationVO> pushes) {
        if (ObjectUtils.isEmpty(pushes)) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        return pushes.stream()
                .filter(push -> {

                    if (push.getStartTime() != null && push.getStartTime().isAfter(now)) {
                        return false;
                    }
                    if (push.getEndTime() != null && push.getEndTime().isBefore(now)) {
                        return false;
                    }
                    return true;
                })
                .sorted((left, right) -> {
                    int createTimeCompare = compareCreateTimeDesc(left.getCreateTime(), right.getCreateTime());
                    if (createTimeCompare != 0) {
                        return createTimeCompare;
                    }
                    return Integer.compare(
                            right.getPriority() == null ? 0 : right.getPriority(),
                            left.getPriority() == null ? 0 : left.getPriority());
                })
                .collect(Collectors.toList());
    }




    private PushNotificationVO convertToVO(PushNotification entity) {
        PushNotificationVO vo = new PushNotificationVO();
        vo.setId(entity.getPushId());
        vo.setType(entity.getType());
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setBadge(entity.getBadge());
        vo.setCoverUrl(entity.getCoverUrl());
        vo.setLink(entity.getLink());
        vo.setFallbackLink(entity.getFallbackLink());
        vo.setPriority(entity.getPriority());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    @Override
    public List<PushNotification> listAdminNews() {
        return pushNotificationMapper.selectList(new LambdaQueryWrapper<PushNotification>()
                .eq(PushNotification::getType, NEWS_TYPE)
                .orderByDesc(PushNotification::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createAdminNews(CuratedNewsRequest request, Long operatorId) {
        validateNewsRequest(request);

        PushNotification entity = new PushNotification();
        entity.setPushId("news:manual:" + UUID.randomUUID());
        entity.setType(NEWS_TYPE);
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(normalize(request.getDescription()));
        entity.setBadge(normalize(request.getBadge()));
        entity.setCoverUrl(normalize(request.getCoverUrl()));
        entity.setLink(normalize(request.getLink()));
        entity.setFallbackLink(normalize(request.getFallbackLink()));
        entity.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setStatus(0);
        entity.setReviewStatus(REVIEW_PENDING);
        entity.setReviewRemark(null);
        pushNotificationMapper.insert(entity);
        clearCache();
        return entity.getPushId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAdminNews(String pushId, CuratedNewsRequest request, Long operatorId) {
        validateNewsRequest(request);
        PushNotification entity = findAdminNews(pushId);
        if (entity == null) {
            return false;
        }

        entity.setTitle(request.getTitle().trim());
        entity.setDescription(normalize(request.getDescription()));
        entity.setBadge(normalize(request.getBadge()));
        entity.setCoverUrl(normalize(request.getCoverUrl()));
        entity.setLink(normalize(request.getLink()));
        entity.setFallbackLink(normalize(request.getFallbackLink()));
        entity.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setStatus(0);
        entity.setReviewStatus(REVIEW_PENDING);
        entity.setReviewerId(null);
        entity.setReviewTime(null);
        entity.setReviewRemark(null);
        pushNotificationMapper.updateById(entity);
        clearCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewAdminNews(String pushId, boolean approved, String remark, Long reviewerId) {
        PushNotification entity = findAdminNews(pushId);
        if (entity == null) {
            return false;
        }

        int updated = pushNotificationMapper.update(null,
                new LambdaUpdateWrapper<PushNotification>()
                        .eq(PushNotification::getPushId, pushId)
                        .eq(PushNotification::getType, NEWS_TYPE)
                        .eq(PushNotification::getDeleted, 0)
                        .eq(PushNotification::getReviewStatus, REVIEW_PENDING)
                        .set(PushNotification::getReviewStatus,
                                approved ? REVIEW_APPROVED : REVIEW_REJECTED)
                        .set(PushNotification::getStatus, approved ? 1 : 0)
                        .set(PushNotification::getReviewerId, reviewerId)
                        .set(PushNotification::getReviewTime, LocalDateTime.now())
                        .set(PushNotification::getReviewRemark,
                                StringUtils.abbreviate(normalize(remark), 500)));
        if (updated != 1) {
            return false;
        }
        clearCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableAdminNews(String pushId, Long operatorId) {
        PushNotification entity = findAdminNews(pushId);
        if (entity == null) {
            return false;
        }
        entity.setStatus(0);
        pushNotificationMapper.updateById(entity);
        clearCache();
        return true;
    }

    private PushNotification findAdminNews(String pushId) {
        if (StringUtils.isBlank(pushId)) {
            return null;
        }
        return pushNotificationMapper.selectOne(new LambdaQueryWrapper<PushNotification>()
                .eq(PushNotification::getPushId, pushId)
                .eq(PushNotification::getType, NEWS_TYPE)
                .last("LIMIT 1"));
    }

    private void validateNewsRequest(CuratedNewsRequest request) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            throw new IllegalArgumentException("新闻标题不能为空");
        }
        requireMaxLength(request.getTitle().trim(), 128, "新闻标题");
        requireMaxLength(request.getDescription(), 2000, "新闻摘要");
        requireMaxLength(request.getBadge(), 32, "新闻标签");
        requireMaxLength(request.getCoverUrl(), 512, "封面地址");
        requireMaxLength(request.getLink(), 512, "主链接");
        requireMaxLength(request.getFallbackLink(), 512, "备用链接");
        if (StringUtils.isBlank(request.getLink()) && StringUtils.isBlank(request.getFallbackLink())) {
            throw new IllegalArgumentException("主链接和备用链接至少填写一个");
        }
        validateHttpUrl(request.getLink(), "主链接");
        validateHttpUrl(request.getFallbackLink(), "备用链接");
        validateImageUrl(request.getCoverUrl(), "封面");
        if (request.getStartTime() != null
                && request.getEndTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
    }

    private void validateHttpUrl(String url, String fieldName) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + "必须使用 http 或 https 链接");
        }
    }

    private void validateImageUrl(String url, String fieldName) {
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

    private void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
    }

    private int compareCreateTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left != null && right != null) {
            return right.compareTo(left);
        }
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        return -1;
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
