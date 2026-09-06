   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.dto.curated.CuratedCarouselRequest;
import com.haoran.music.entity.CuratedCarouselItem;
import com.haoran.music.mapper.CuratedCarouselItemMapper;
import com.haoran.music.service.CuratedCarouselService;
import com.haoran.music.vo.curated.CuratedCarouselItemVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CuratedCarouselServiceImpl
        extends ServiceImpl<CuratedCarouselItemMapper, CuratedCarouselItem>
        implements CuratedCarouselService {

    private static final String CACHE_PREFIX = "curated:carousel:";
    private static final String SCENE_DISCOVER = "discover_top";
    private static final String SCENE_SQUARE = "square_top";
    private static final String SCENE_ALL = "all";
    private static final String SOURCE_INTERNAL = "internal";
    private static final String SOURCE_EXTERNAL = "external";
    private static final int REVIEW_PENDING = 0;
    private static final int REVIEW_APPROVED = 1;
    private static final int REVIEW_REJECTED = 2;
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private static final Set<String> SCENES = new HashSet<>(
            Arrays.asList(SCENE_DISCOVER, SCENE_SQUARE, SCENE_ALL));
    private static final Set<String> CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "official", "news", "hot_post", "hot_topic", "new_song", "playlist",
            "collab_playlist", "vote"));

    @Resource
    private CuratedCarouselItemMapper curatedCarouselItemMapper;

    @Resource
    private RedisUtils redisUtils;

    @Override
    public List<CuratedCarouselItemVO> getPublicItems(String scene, Integer limit) {
        String normalizedScene = normalizeScene(scene);
        int resolvedLimit = resolveLimit(limit);
        String cacheKey = CACHE_PREFIX + normalizedScene + ":" + resolvedLimit;
        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> loadPublicItems(normalizedScene, resolvedLimit),
                5,
                TimeUnit.MINUTES,
                List.class
        );
    }

    private List<CuratedCarouselItemVO> loadPublicItems(String scene, int limit) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CuratedCarouselItem> wrapper = new LambdaQueryWrapper<CuratedCarouselItem>()
                .eq(CuratedCarouselItem::getStatus, 1)
                .eq(CuratedCarouselItem::getReviewStatus, REVIEW_APPROVED)
                .and(query -> query.eq(CuratedCarouselItem::getScene, scene)
                        .or()
                        .eq(CuratedCarouselItem::getScene, SCENE_ALL))
                .and(query -> query.isNull(CuratedCarouselItem::getStartTime)
                        .or()
                        .le(CuratedCarouselItem::getStartTime, now))
                .and(query -> query.isNull(CuratedCarouselItem::getEndTime)
                        .or()
                        .ge(CuratedCarouselItem::getEndTime, now))
                .orderByAsc(CuratedCarouselItem::getSortOrder)
                .orderByDesc(CuratedCarouselItem::getPriority)
                .orderByDesc(CuratedCarouselItem::getCreateTime)
                .last("LIMIT " + limit);
        return curatedCarouselItemMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean incrementViewCount(Long itemId) {
        if (itemId == null) {
            return false;
        }
        int updated = curatedCarouselItemMapper.update(
                null,
                new LambdaUpdateWrapper<CuratedCarouselItem>()
                        .eq(CuratedCarouselItem::getId, itemId)
                        .eq(CuratedCarouselItem::getStatus, 1)
                        .eq(CuratedCarouselItem::getReviewStatus, REVIEW_APPROVED)
                        .eq(CuratedCarouselItem::getDeleted, 0)
                        .setSql("view_count = IFNULL(view_count, 0) + 1"));
        return updated > 0;
    }

    @Override
    public List<CuratedCarouselItem> listAdminItems(String scene, boolean removed) {
        String normalizedScene = StringUtils.isBlank(scene) ? null : normalizeScene(scene);
        return curatedCarouselItemMapper.selectByDeleted(normalizedScene, removed ? 1 : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CuratedCarouselRequest request, Long operatorId) {
        validateRequest(request);
        CuratedCarouselItem item = new CuratedCarouselItem();
        applyRequest(item, request);
        item.setOperatorId(operatorId);
        item.setPriority(defaultInt(request.getPriority()));
        item.setSortOrder(defaultInt(request.getSortOrder()));
        item.setStatus(0);
        item.setReviewStatus(REVIEW_PENDING);
        item.setViewCount(0L);
        item.setDeleted(0);
        curatedCarouselItemMapper.insert(item);
        clearCache();
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long itemId, CuratedCarouselRequest request, Long operatorId) {
        validateRequest(request);
        CuratedCarouselItem item = findActiveItem(itemId);
        if (item == null) {
            return false;
        }
        applyRequest(item, request);
        item.setOperatorId(operatorId);
        item.setPriority(defaultInt(request.getPriority()));
        item.setSortOrder(defaultInt(request.getSortOrder()));
        item.setStatus(0);
        item.setReviewStatus(REVIEW_PENDING);
        item.setReviewerId(null);
        item.setReviewTime(null);
        item.setReviewRemark(null);
        curatedCarouselItemMapper.updateById(item);
        clearCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean review(Long itemId, boolean approved, String remark, Long reviewerId) {
        CuratedCarouselItem item = findActiveItem(itemId);
        if (item == null) {
            return false;
        }
        int updated = curatedCarouselItemMapper.update(null,
                new LambdaUpdateWrapper<CuratedCarouselItem>()
                        .eq(CuratedCarouselItem::getId, itemId)
                        .eq(CuratedCarouselItem::getDeleted, 0)
                        .eq(CuratedCarouselItem::getReviewStatus, REVIEW_PENDING)
                        .set(CuratedCarouselItem::getReviewStatus,
                                approved ? REVIEW_APPROVED : REVIEW_REJECTED)
                        .set(CuratedCarouselItem::getStatus, approved ? 1 : 0)
                        .set(CuratedCarouselItem::getReviewerId, reviewerId)
                        .set(CuratedCarouselItem::getReviewTime, LocalDateTime.now())
                        .set(CuratedCarouselItem::getReviewRemark,
                                StringUtils.abbreviate(normalize(remark), 500)));
        if (updated != 1) {
            return false;
        }
        clearCache();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long itemId, Long operatorId) {
        if (itemId == null || operatorId == null) {
            return false;
        }
        int updated = curatedCarouselItemMapper.softRemove(itemId, operatorId);
        if (updated != 1) {
            return false;
        }
        clearCache();
        log.info("轮播内容已临时下架, itemId={}, operatorId={}", itemId, operatorId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restore(Long itemId, Long operatorId) {
        if (itemId == null || operatorId == null) {
            return false;
        }
        int updated = curatedCarouselItemMapper.restore(itemId, operatorId);
        if (updated != 1) {
            return false;
        }
        clearCache();
        log.info("轮播内容已重新上架, itemId={}, operatorId={}", itemId, operatorId);
        return true;
    }

    private CuratedCarouselItem findActiveItem(Long itemId) {
        if (itemId == null) {
            return null;
        }
        return curatedCarouselItemMapper.selectOne(new LambdaQueryWrapper<CuratedCarouselItem>()
                .eq(CuratedCarouselItem::getId, itemId)
                .eq(CuratedCarouselItem::getDeleted, 0)
                .last("LIMIT 1"));
    }

    private void applyRequest(CuratedCarouselItem item, CuratedCarouselRequest request) {
        item.setScene(normalizeScene(request.getScene()));
        item.setContentType(normalize(request.getContentType()));
        item.setTitle(request.getTitle().trim());
        item.setDescription(normalize(request.getDescription()));
        item.setBadge(normalize(request.getBadge()));
        item.setImageUrl(normalize(request.getImageUrl()));
        item.setFallbackImageUrl(normalize(request.getFallbackImageUrl()));
        item.setSourceType(normalizeSourceType(request.getSourceType()));
        item.setSourceName(normalize(request.getSourceName()));
        item.setLink(normalize(request.getLink()));
        item.setFallbackLink(normalize(request.getFallbackLink()));
        item.setTargetType(normalize(request.getTargetType()));
        item.setTargetId(normalize(request.getTargetId()));
        item.setStartTime(request.getStartTime());
        item.setEndTime(request.getEndTime());
    }

    private void validateRequest(CuratedCarouselRequest request) {
        if (request == null || StringUtils.isBlank(request.getTitle())) {
            throw new IllegalArgumentException("轮播标题不能为空");
        }
        String scene = normalizeScene(request.getScene());
        String contentType = normalize(request.getContentType());
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("内容类型不受支持");
        }
        if (StringUtils.isBlank(request.getLink()) && StringUtils.isBlank(request.getFallbackLink())) {
            throw new IllegalArgumentException("主链接和备用链接至少填写一个");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
        String sourceType = normalizeSourceType(request.getSourceType());
        validateImageUrl(request.getImageUrl(), "图片");
        validateImageUrl(request.getFallbackImageUrl(), "备用图片");
        validateLink(request.getLink(), sourceType, "主链接");
        validateLink(request.getFallbackLink(), sourceType, "备用链接");
        if (SCENE_DISCOVER.equals(scene) && "collab_playlist".equals(contentType)) {
            throw new IllegalArgumentException("协作歌单不能投放到发现页顶部");
        }
    }

    private void validateLink(String link, String sourceType, String fieldName) {
        if (StringUtils.isBlank(link)) {
            return;
        }
        String value = link.trim().toLowerCase(Locale.ROOT);
        if (SOURCE_INTERNAL.equals(sourceType)) {
            if (!value.startsWith("/") || value.startsWith("//")) {
                throw new IllegalArgumentException(fieldName + "必须是站内路径，例如 /playlist/1");
            }
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + "必须使用 http 或 https 链接");
        }
    }

    private void validateImageUrl(String url, String fieldName) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        if ((value.startsWith("http://") || value.startsWith("https://"))
                || (value.startsWith("/") && !value.startsWith("//"))) {
            return;
        }
        throw new IllegalArgumentException(fieldName + "必须使用 http、https 或站内路径");
    }

    private String normalizeScene(String scene) {
        String value = StringUtils.defaultIfBlank(normalize(scene), SCENE_ALL)
                .toLowerCase(Locale.ROOT);
        if (!SCENES.contains(value)) {
            throw new IllegalArgumentException("展示场景只能是 discover_top、square_top 或 all");
        }
        return value;
    }

    private String normalizeSourceType(String sourceType) {
        String value = StringUtils.defaultIfBlank(normalize(sourceType), SOURCE_EXTERNAL)
                .toLowerCase(Locale.ROOT);
        if (!SOURCE_INTERNAL.equals(value) && !SOURCE_EXTERNAL.equals(value)) {
            throw new IllegalArgumentException("来源类型只能是 internal 或 external");
        }
        return value;
    }

    private CuratedCarouselItemVO toVO(CuratedCarouselItem item) {
        CuratedCarouselItemVO vo = new CuratedCarouselItemVO();
        vo.setId(String.valueOf(item.getId()));
        vo.setScene(item.getScene());
        vo.setContentType(item.getContentType());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setBadge(item.getBadge());
        vo.setImageUrl(item.getImageUrl());
        vo.setFallbackImageUrl(item.getFallbackImageUrl());
        vo.setSourceType(item.getSourceType());
        vo.setSourceName(item.getSourceName());
        vo.setLink(item.getLink());
        vo.setFallbackLink(item.getFallbackLink());
        vo.setTargetType(item.getTargetType());
        vo.setTargetId(item.getTargetId());
        vo.setPriority(item.getPriority());
        vo.setSortOrder(item.getSortOrder());
        vo.setViewCount(item.getViewCount() == null ? 0L : item.getViewCount());
        vo.setStartTime(item.getStartTime());
        vo.setEndTime(item.getEndTime());
        vo.setCreateTime(item.getCreateTime());
        return vo;
    }

    private int resolveLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private void clearCache() {
        CacheHelper.deleteByPattern(redisUtils, CACHE_PREFIX + "*");
    }
}
