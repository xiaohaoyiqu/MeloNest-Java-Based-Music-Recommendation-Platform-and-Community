package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.service.CacheService;
import com.haoran.music.common.util.*;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.MvFavorite;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.MvFavoriteMapper;
import com.haoran.music.mapper.FavoriteCollectionItemMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MVService;
import com.haoran.music.service.MediaAccessGrantService;
import com.haoran.music.service.MediaPreviewService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.vo.mv.MVVO;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;





@Slf4j
@Service
public class MVServiceImpl extends ServiceImpl<MVMapper, MV> implements MVService {

    @Autowired(required = false)
    private FavoriteCollectionItemMapper favoriteCollectionItemMapper;

    private static final Set<String> MV_AREAS = immutableSet("内地", "港台", "欧美", "日本", "韩国", "其他");
    private static final Set<String> MV_GENRES = immutableSet("流行", "摇滚", "说唱", "电子", "民谣", "古风",
            "爵士", "纯音乐", "乡村", "金属", "拉丁", "古典", "R&B", "其他");
    private static final Set<String> MV_LANGUAGES = immutableSet("zh", "en", "ja", "ko", "fr", "es", "de",
            "it", "th", "other");
    private static final Set<String> MV_QUALITIES = immutableSet("360p", "720p", "1080p", "4k");
    private static final Set<String> MV_BINDINGS = immutableSet("bound", "other");
    private static final Set<String> MV_ALBUM_TYPES = immutableSet("single", "ep", "album", "live",
            "compilation", "other");

    @Resource
    private MVMapper mvMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private SongMapper songMapper;

    @Resource
    private CacheService cacheService;

    @Resource
    private MvFavoriteMapper mvFavoriteMapper;

    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MediaAccessGrantService mediaAccessGrantService;

    @Resource
    private ContentAccessService contentAccessService;

    @Resource
    private MediaPreviewService mediaPreviewService;

    @Resource
    private UserVipService userVipService;

    @Value("${music.upload.nginx-url-prefix}")
    private String fileUrlPrefix;

    @Value("${music.playback.source-url-prefix:${music.upload.nginx-url-prefix}}")
    private String playbackSourceUrlPrefix;

    @Value("${music.playback.nginx-accel-enabled:false}")
    private boolean nginxAccelEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(MV mv) {
        boolean result = super.save(mv);
        if (result && mv != null) {
            searchIndexService.sync("mv", mv.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(MV mv) {
        boolean result = super.updateById(mv);
        if (result && mv != null && mv.getId() != null) {
            searchIndexService.sync("mv", mv.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result && id != null) {
            searchIndexService.sync("mv", Long.valueOf(String.valueOf(id)));
        }
        return result;
    }


    private static final String MV_LIKE_PREFIX = "mv:like:";

    @Override
    public MVVO getMVById(Long mvId, Long userId) {
        if (ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        contentAccessService.requireMvMetadataAccess(mvMapper.selectById(mvId), userId);


        String cacheKey = cacheService.MV_CACHE_PREFIX + mvId;
        MVVO mvVO = CacheHelper.getOrLoadWithNullProtection(
                redisUtils,
                cacheKey,
                () -> buildMVVOFromDB(mvId, userId),
                cacheService.getMVCacheExpire(),
                cacheService.getNullCacheExpire(),
                TimeUnit.SECONDS,
                MVVO.class
        );

        if (mvVO == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }


        setUserActionStatus(mvVO, userId);

        return mvVO;
    }




    private MVVO buildMVVOFromDB(Long mvId, Long userId) {
        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv) || CommonConstants.DELETED.equals(mv.getDeleted())) {
            return null;                    
        }

        return buildMVVO(mv, userId);
    }

    @Override
    public IPage<MVVO> pageMVs(PageQuery pageQuery, String area, String genre, String keyword, String sortBy, Long userId) {
        return pageMVs(pageQuery, area, genre, keyword, sortBy, null, null, null,
                null, null, null, null, null, null, userId);
    }

    @Override
    public IPage<MVVO> pageMVs(PageQuery pageQuery, String area, String genre, String keyword, String sortBy,
                               String language, Integer publishYear, LocalDate publishDateStart,
                               LocalDate publishDateEnd, Integer minDuration, Integer maxDuration,
                               String quality, String binding, String albumType, Long userId) {
        validateCatalogFilters(area, genre, keyword, sortBy, language, publishYear, publishDateStart,
                publishDateEnd, minDuration, maxDuration, quality, binding, albumType);
        Page<MV> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        String escapedKeyword = StrUtil.isBlank(keyword) ? null : escapeLikeKeyword(keyword.trim());
        IPage<MV> mvPage = mvMapper.selectPageWithFilters(page, area, genre, escapedKeyword, sortBy, language,
                publishYear, publishDateStart, publishDateEnd, minDuration, maxDuration, quality, binding, albumType);


        return convertToVOBatch(mvPage, userId);
    }

    private void validateCatalogFilters(String area, String genre, String keyword, String sortBy,
                                        String language, Integer publishYear, LocalDate publishDateStart,
                                        LocalDate publishDateEnd, Integer minDuration, Integer maxDuration,
                                        String quality, String binding, String albumType) {
        if (area != null && area.length() > 20 || genre != null && genre.length() > 30
                || keyword != null && keyword.length() > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV筛选文本过长");
        }
        if (containsControlCharacter(keyword)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV搜索关键词包含非法控制字符");
        }
        validateOptionalEnum(area, MV_AREAS, "MV地区筛选值不合法");
        validateOptionalEnum(genre, MV_GENRES, "MV类型筛选值不合法");
        if (StrUtil.isNotBlank(sortBy) && !"hot".equalsIgnoreCase(sortBy) && !"new".equalsIgnoreCase(sortBy)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV排序方式不合法");
        }
        validateOptionalEnum(language, MV_LANGUAGES, "MV语言筛选值不合法");
        int currentYear = java.time.Year.now().getValue();
        if (publishYear != null && (publishYear < 1900 || publishYear > currentYear + 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV发行年份不合法");
        }
        LocalDate earliestDate = LocalDate.of(1900, 1, 1);
        LocalDate latestDate = LocalDate.now().plusYears(1);
        if (publishDateStart != null && (publishDateStart.isBefore(earliestDate) || publishDateStart.isAfter(latestDate))
                || publishDateEnd != null && (publishDateEnd.isBefore(earliestDate) || publishDateEnd.isAfter(latestDate))
                || publishDateStart != null && publishDateEnd != null && publishDateStart.isAfter(publishDateEnd)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV发行日期范围不合法");
        }
        if (minDuration != null && (minDuration < 0 || minDuration > 86400)
                || maxDuration != null && (maxDuration < 0 || maxDuration > 86400)
                || minDuration != null && maxDuration != null && minDuration > maxDuration) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV时长范围不合法");
        }
        validateOptionalEnum(quality, MV_QUALITIES, "MV画质筛选值不合法");
        validateOptionalEnum(binding, MV_BINDINGS, "MV关联筛选值不合法");
        validateOptionalEnum(albumType, MV_ALBUM_TYPES, "MV专辑类型筛选值不合法");
    }

    private void validateOptionalEnum(String value, Set<String> allowedValues, String message) {
        if (StrUtil.isNotBlank(value) && !allowedValues.contains(value.trim())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    private boolean containsControlCharacter(String value) {
        if (value == null) {
            return false;
        }
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    @Override
    public List<MVVO> getMVsBySongId(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MV::getSongId, songId)
                .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(MV::getPublishDate);

        List<MV> mvList = mvMapper.selectList(wrapper);

        if (mvList.isEmpty()) {
            return new ArrayList<>();
        }

        return convertToVOBatch(mvList, userId);
    }

    @Override
    public IPage<MVVO> getMVsByArtistId(Long artistId, PageQuery pageQuery, Long userId) {
        if (ObjectUtils.isEmpty(artistId)) {
            return new Page<>();
        }

        Page<MV> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());

        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(artist -> artist.eq(MV::getArtistId, artistId)
                        .or()
                        .apply("FIND_IN_SET({0}, artist_ids) > 0", artistId))
                .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(MV::getPublishDate, MV::getPlayCount);

        IPage<MV> mvPage = mvMapper.selectPage(page, wrapper);

        return convertToVOBatch(mvPage, userId);
    }

    @Override
    public List<MVVO> getHotMVs(Integer limit, Long userId) {
        int actualLimit = ObjectUtils.isEmpty(limit) ? 20 : Math.min(limit, 100);

        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(MV::getPlayCount, MV::getFavoriteCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<MV> mvList = filterPlayableMvs(mvMapper.selectList(wrapper), actualLimit);

        if (mvList.isEmpty()) {
            return new ArrayList<>();
        }

        return convertToVOBatch(mvList, userId);
    }

    @Override
    public List<MVVO> getNewestMVs(Integer limit, Long userId) {
        int actualLimit = ObjectUtils.isEmpty(limit) ? 20 : Math.min(limit, 100);

        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(MV::getPublishDate)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<MV> mvList = filterPlayableMvs(mvMapper.selectList(wrapper), actualLimit);

        if (mvList.isEmpty()) {
            return new ArrayList<>();
        }

        return convertToVOBatch(mvList, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPlay(Long mvId, Long userId) {
        if (ObjectUtils.isEmpty(mvId)) {
            return;
        }
        if (ObjectUtils.isEmpty(userId)
                || !UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号MV播放热度: mvId={}, userId={}", mvId, userId);
            return;
        }

        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isNotEmpty(mv)) {

            mv.setPlayCount((mv.getPlayCount() != null ? mv.getPlayCount() : 0) + 1);
            mvMapper.updateById(mv);


            cacheService.clearMVCache(mvId);

            log.debug("记录MV播放: mvId={}, userId={}", mvId, userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean favoriteMV(Long userId, Long mvId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "收藏MV");


        MV mv = getById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }


        LambdaQueryWrapper<MvFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MvFavorite::getUserId, userId)
               .eq(MvFavorite::getMvId, mvId)
               .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED);

        MvFavorite existing = mvFavoriteMapper.selectOne(wrapper);
        if (existing != null) {
            log.debug("MV已收藏，跳过: userId={}, mvId={}", userId, mvId);
            return true;
        }


        String cleanupSql = "DELETE FROM mv_favorite WHERE user_id = ? AND mv_id = ? AND deleted = 1";
        jdbcTemplate.update(cleanupSql, userId, mvId);


        MvFavorite mvFavorite = new MvFavorite();
        mvFavorite.setUserId(userId);
        mvFavorite.setMvId(mvId);
        mvFavorite.setDeleted(CommonConstants.NOT_DELETED);
        mvFavoriteMapper.insert(mvFavorite);

        if (UserAccountStatusUtil.canContributePublicStats(user)) {
            mv.setFavoriteCount((mv.getFavoriteCount() != null ? mv.getFavoriteCount() : 0) + 1);
            mvMapper.updateById(mv);
        }

        log.info("用户收藏MV: userId={}, mvId={}", userId, mvId);


        return true;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfavoriteMV(Long userId, Long mvId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }


        LambdaQueryWrapper<MvFavorite> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(MvFavorite::getUserId, userId)
                .eq(MvFavorite::getMvId, mvId)
                .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED);

        MvFavorite activeRecord = mvFavoriteMapper.selectOne(activeWrapper);
        if (activeRecord == null) {
            removeFavoriteGrouping(userId, "mv", mvId);

            log.debug("MV未收藏，跳过: userId={}, mvId={}", userId, mvId);
            return true;
        }


        mvFavoriteMapper.deleteById(activeRecord.getId());
        removeFavoriteGrouping(userId, "mv", mvId);
        log.info("用户取消收藏MV: userId={}, mvId={}", userId, mvId);


        MV mv = getById(mvId);
        if (ObjectUtils.isNotEmpty(mv)
                && UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            mv.setFavoriteCount(Math.max(0, (mv.getFavoriteCount() != null ? mv.getFavoriteCount() : 0) - 1));
            updateById(mv);
        }

        return true;
    }

    private void removeFavoriteGrouping(Long userId, String resourceType, Long resourceId) {
        if (favoriteCollectionItemMapper != null) {
            favoriteCollectionItemMapper.deleteResource(userId, resourceType, resourceId);
        }
    }

    @Override
    public List<MVVO> getFavoriteMVs(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<MvFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MvFavorite::getUserId, userId)
                .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(MvFavorite::getCreateTime);

        List<MvFavorite> favorites = mvFavoriteMapper.selectList(wrapper);
        if (CollUtil.isEmpty(favorites)) {
            return new ArrayList<>();
        }


        List<Long> mvIds = favorites.stream()
                .map(MvFavorite::getMvId)
                .collect(Collectors.toList());


        List<MV> mvs = mvMapper.selectBatchIds(mvIds);
        if (CollUtil.isEmpty(mvs)) {
            return new ArrayList<>();
        }


        List<MVVO> result = ConvertHelper.toVOList(mvs, MVVO.class);


        Set<Long> favoriteMvIds = ConvertHelper.extractIdSet(favorites, MvFavorite::getMvId);
        ConvertHelper.setFieldFromSet(result, MVVO::getId, favoriteMvIds, MVVO::setIsFavorite);


        result.sort((a, b) -> {
            int idxA = mvIds.indexOf(a.getId());
            int idxB = mvIds.indexOf(b.getId());
            return Integer.compare(idxA, idxB);
        });

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean likeMV(Long userId, Long mvId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "点赞MV");

        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }

        String likeKey = MV_LIKE_PREFIX + userId;
        String member = String.valueOf(mvId);


        Boolean isMember = redisUtils.sIsMember(likeKey, member);
        if (Boolean.TRUE.equals(isMember)) {
            return true;
        }


        redisUtils.sAdd(likeKey, member);

        if (UserAccountStatusUtil.canContributePublicStats(user)) {

            mv.setLikeCount((mv.getLikeCount() != null ? mv.getLikeCount() : 0) + 1);
            mvMapper.updateById(mv);


            cacheService.clearMVCache(mvId);
        }

        log.info("用户点赞MV: userId={}, mvId={}", userId, mvId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unlikeMV(Long userId, Long mvId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        String likeKey = MV_LIKE_PREFIX + userId;
        String member = String.valueOf(mvId);


        redisUtils.sRemove(likeKey, member);

        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isNotEmpty(mv)
                && UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {

            Long currentCount = mv.getLikeCount() != null ? mv.getLikeCount() : 0L;
            mv.setLikeCount(Math.max(0L, currentCount - 1));
            mvMapper.updateById(mv);


            cacheService.clearMVCache(mvId);
        }

        log.info("用户取消点赞MV: userId={}, mvId={}", userId, mvId);
        return true;
    }

    @Override
    public String getPlayUrl(Long mvId, String quality, Long userId) {
        if (ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }

        contentAccessService.requireMvAccess(mv, userId);
        ResolvedMVSource resolved = resolveMVSource(mv, quality);
        String controlledUrl = MediaPlaybackUrlUtil.mvUrl(mvId, resolved.quality, resolved.sourceUrl);
        if (resolved.requireVip) {
            requireVip(userId);
        }
        if (userId != null) {
            controlledUrl += "&grant=" + mediaAccessGrantService.issue("mv", mvId, resolved.quality, userId);
        }
        return controlledUrl;
    }

    @Override
    public String getPreviewUrl(Long mvId, Long userId) {
        if (ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV ID不能为空");
        }
        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }
        contentAccessService.requireMvPreviewAccess(mv, userId);
        ResolvedMVSource source = resolveMVSource(mv, "360p");
        String previewSource = mediaPreviewService.ensureMvPreview(mv, source.sourceUrl);
        if (StrUtil.isBlank(previewSource)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试看片段暂时不可用");
        }
        String controlledUrl = MediaPlaybackUrlUtil.mvUrl(mvId, "preview", previewSource);
        return userId == null ? controlledUrl
                : controlledUrl + "&grant=" + mediaAccessGrantService.issue("mv", mvId, "preview", userId);
    }

    @Override
    public void streamMV(Long mvId, String quality, String grant, String range, Long userId,
                         javax.servlet.http.HttpServletResponse response) {
        if (ObjectUtils.isEmpty(mvId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV ID不能为空");
        }
        MV mv = mvMapper.selectById(mvId);
        if (ObjectUtils.isEmpty(mv)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "MV不存在");
        }

        Long authorizedUserId = userId;
        boolean preview = "preview".equalsIgnoreCase(quality);
        ResolvedMVSource resolved = preview
                ? new ResolvedMVSource("preview", mediaPreviewService.findMvPreview(mvId), false)
                : resolveMVSource(mv, quality);
        if (StrUtil.isBlank(resolved.sourceUrl)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试看片段暂时不可用");
        }
        if (authorizedUserId == null && StrUtil.isNotBlank(grant)) {
            authorizedUserId = mediaAccessGrantService.verify(grant, "mv", mvId, preview ? "preview" : resolved.quality);
        }
        if (preview) {
            contentAccessService.requireMvPreviewAccess(mv, authorizedUserId);
        } else {
            contentAccessService.requireMvAccess(mv, authorizedUserId);
        }
        if (!preview && resolved.requireVip) {
            requireVip(authorizedUserId);
        }

        response.setHeader("Cache-Control", authorizedUserId != null ? "private, no-store" : "public, max-age=300");
        String sourceUrl = toSourceUrl(resolved.sourceUrl);
        if (!redirectToNginx(sourceUrl, "/mvs/", response)) {
            streamVideo(sourceUrl, range, response);
        }
    }

    private ResolvedMVSource resolveMVSource(MV mv, String requestedQuality) {
        String normalized = StrUtil.blankToDefault(requestedQuality, "720p").toLowerCase(Locale.ROOT);
        String[] candidates;
        switch (normalized) {
            case "standard":
            case "360":
            case "360p":
            case "sd":
                candidates = new String[]{"360p", "720p", "1080p"};
                break;
            case "high":
            case "720":
            case "720p":
            case "hd":
                candidates = new String[]{"720p", "360p"};
                break;
            case "super":
            case "1080":
            case "1080p":
            case "fhd":
                candidates = new String[]{"1080p", "720p", "360p"};
                break;
            case "2160":
            case "2160p":
            case "4k":
                candidates = new String[]{"2160p", "1080p", "720p", "360p"};
                break;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的MV清晰度");
        }

        for (String candidate : candidates) {
            String sourceUrl = mvSourceUrlFor(mv, candidate);
            if (StrUtil.isNotBlank(sourceUrl)) {
                return new ResolvedMVSource(candidate, sourceUrl, "2160p".equals(candidate));
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "该MV暂无播放地址");
    }

    private String mvSourceUrlFor(MV mv, String quality) {
        switch (quality) {
            case "720p":
                return UrlHelper.normalizeUrl(mv.getUrl720p());
            case "1080p":
                return UrlHelper.normalizeUrl(mv.getUrl1080p());
            case "2160p":
                return UrlHelper.normalizeUrl(mv.getUrl2160p());
            default:
                return UrlHelper.normalizeUrl(mv.getUrl360p());
        }
    }

    private String toSourceUrl(String sourceUrl) {
        String url = MediaSourceUrlUtil.toInternalUrl(sourceUrl, playbackSourceUrlPrefix, "/mvs/");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = UrlHelper.toFullUrl(url, playbackSourceUrlPrefix);
        }
        return UrlHelper.encodePath(url);
    }

    private void requireVip(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "4K清晰度需要登录并开通VIP");
        }
        if (!Boolean.TRUE.equals(userVipService.isVip(userId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "4K清晰度为VIP专享");
        }
    }

    private boolean redirectToNginx(String sourceUrl, String mediaRoot,
                                    javax.servlet.http.HttpServletResponse response) {
        if (!nginxAccelEnabled) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(sourceUrl).normalize();
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(mediaRoot)
                    || path.toLowerCase(java.util.Locale.ROOT).contains("%2e")) {
                throw new BusinessException("媒体源路径无效");
            }
            response.setHeader("X-Accel-Redirect", "/__media_origin" + path);
            return true;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("媒体源路径无效");
        }
    }

    private void streamVideo(String fileUrl, String range, javax.servlet.http.HttpServletResponse response) {
        java.net.HttpURLConnection connection = null;
        try {
            ExternalUrlGuard.Validation validation = ExternalUrlGuard.validate(
                    fileUrl, playbackSourceUrlPrefix, fileUrlPrefix);
            if (!validation.isAllowed()) {
                log.warn("MV源地址校验失败: reason={}, sourceHost={}, trustedHost={}",
                        validation.getReason(), UrlHelper.getHost(fileUrl), UrlHelper.getHost(playbackSourceUrlPrefix));
                throw new BusinessException("外部MV地址不安全");
            }
            connection = ExternalStreamUtil.openGetConnection(fileUrl);
            ExternalStreamUtil.applyBrowserHeaders(connection, false);
            if (StrUtil.isNotBlank(range) && range.startsWith("bytes=")) {
                connection.setRequestProperty("Range", range);
            }
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 206) {
                throw new BusinessException("获取MV资源失败: HTTP " + responseCode);
            }
            String contentType = connection.getContentType();
            if (!isExpectedMediaContentType(contentType, "video/")) {
                log.error("MV源返回了非视频内容: contentType={}, sourceHost={}",
                        contentType, UrlHelper.getHost(fileUrl));
                writeBadGatewayIfPossible(response, "MV源返回了无效内容");
                return;
            }
            response.setStatus(responseCode);
            response.setContentType(contentType);
            copyHeader(connection, response, "Accept-Ranges");
            copyHeader(connection, response, "Content-Range");
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            try (java.io.InputStream input = connection.getInputStream()) {
                ExternalStreamUtil.copy(input, response.getOutputStream());
            }
        } catch (java.io.IOException e) {
            log.error("MV流传输失败: {}", e.getClass().getSimpleName());
            writeBadGatewayIfPossible(response, "MV源暂时不可用");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeBadGatewayIfPossible(javax.servlet.http.HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":502,\"message\":\"" + message + "\"}");
        } catch (java.io.IOException writeError) {
            log.warn("写入媒体源错误响应失败: {}", writeError.getClass().getSimpleName());
        }
    }

    private boolean isExpectedMediaContentType(String contentType, String expectedPrefix) {
        if (StrUtil.isBlank(contentType)) {
            return false;
        }
        String normalized = contentType.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith(expectedPrefix)
                || normalized.startsWith("application/octet-stream")
                || normalized.startsWith("binary/octet-stream");
    }

    private void copyHeader(java.net.HttpURLConnection connection,
                            javax.servlet.http.HttpServletResponse response, String name) {
        String value = connection.getHeaderField(name);
        if (StrUtil.isNotBlank(value)) {
            response.setHeader(name, value);
        }
    }

    private static final class ResolvedMVSource {
        private final String quality;
        private final String sourceUrl;
        private final boolean requireVip;

        private ResolvedMVSource(String quality, String sourceUrl, boolean requireVip) {
            this.quality = quality;
            this.sourceUrl = sourceUrl;
            this.requireVip = requireVip;
        }
    }








    private List<MVVO> convertToVOBatch(List<MV> mvList, Long userId) {
        if (mvList == null || mvList.isEmpty()) {
            return new ArrayList<>();
        }


        Set<Long> favoriteMvIds = Collections.emptySet();
        Set<Long> likedMvIds = Collections.emptySet();

        if (ObjectUtils.isNotEmpty(userId)) {

            favoriteMvIds = getFavoriteMvIds(userId, mvList);

            likedMvIds = RedisBatchHelper.getSetMemberIds(redisUtils, MV_LIKE_PREFIX + "{userId}", userId);
        }


        Map<Long, String> songLanguageMap = Collections.emptyMap();                                         


        final Set<Long> finalFavoriteMvIds = favoriteMvIds;
        final Set<Long> finalLikedMvIds = likedMvIds;

        return mvList.stream().map(mv -> {
            MVVO mvVO = BeanUtil.copyProperties(mv, MVVO.class);


            mvVO.setIsFavorite(finalFavoriteMvIds.contains(mv.getId()));
            mvVO.setIsLike(finalLikedMvIds.contains(mv.getId()));


            if (mv.getSongId() != null && songLanguageMap.containsKey(mv.getSongId())) {
                mvVO.setSongLanguage(songLanguageMap.get(mv.getSongId()));
            }


            if (StrUtil.isBlank(mvVO.getCover())) {
                mvVO.setCover("/default-cover.png");
            }


            if (StrUtil.isNotBlank(mv.getArtistIds())) {
                String[] ids = mv.getArtistIds().split(",");
                if (ids.length > 0) {
                    try {
                        mvVO.setArtistId(Long.parseLong(ids[0].trim()));
                    } catch (NumberFormatException e) {

                    }
                }
            }

            return mvVO;
        }).collect(Collectors.toList());
    }








    private Set<Long> getFavoriteMvIds(Long userId, List<MV> mvList) {
        if (mvList == null || mvList.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> mvIds = ConvertHelper.extractIds(mvList, MV::getId);

        LambdaQueryWrapper<MvFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MvFavorite::getUserId, userId)
                .in(MvFavorite::getMvId, mvIds)
                .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED)
                .select(MvFavorite::getMvId);

        List<MvFavorite> favorites = mvFavoriteMapper.selectList(wrapper);
        return ConvertHelper.extractIdSet(favorites, MvFavorite::getMvId);
    }

    private List<MV> filterPlayableMvs(List<MV> mvList, int limit) {
        if (mvList == null || mvList.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.stream.Stream<MV> stream = mvList.stream().filter(this::hasPlayableVideoUrl);
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    private boolean hasPlayableVideoUrl(MV mv) {
        return mv != null && (StrUtil.isNotBlank(mv.getUrl360p())
                || StrUtil.isNotBlank(mv.getUrl720p())
                || StrUtil.isNotBlank(mv.getUrl1080p())
                || StrUtil.isNotBlank(mv.getUrl2160p()));
    }

    private int expandedPublicQueryLimit(int limit) {
        return limit > 0 ? Math.min(limit * 3, 300) : limit;
    }








    private IPage<MVVO> convertToVOBatch(IPage<MV> mvPage, Long userId) {
        List<MV> mvList = mvPage.getRecords();
        if (mvList == null || mvList.isEmpty()) {
            return mvPage.convert(mv -> new MVVO());
        }

        List<MVVO> voList = convertToVOBatch(mvList, userId);

        Page<MVVO> voPage = new Page<>(mvPage.getCurrent(), mvPage.getSize(), mvPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }







    private Map<Long, String> getSongLanguageMap(List<MV> mvList) {

        Set<Long> songIds = mvList.stream()
                .map(MV::getSongId)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());

        if (songIds.isEmpty()) {
            return Collections.emptyMap();
        }


        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Song::getId, Song::getLanguage)
                .in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL);

        List<Song> songs = songMapper.selectList(wrapper);


        return songs.stream()
                .collect(Collectors.toMap(Song::getId, Song::getLanguage, (a, b) -> a));
    }








    private MVVO buildMVVO(MV mv, Long userId) {
        MVVO mvVO = BeanUtil.copyProperties(mv, MVVO.class);


        setUserActionStatus(mvVO, userId);


        if (mv.getSongId() != null) {
            Song song = songMapper.selectById(mv.getSongId());
            if (song != null) {
                mvVO.setSongLanguage(song.getLanguage());
            }
        }


        if (StrUtil.isNotBlank(mv.getArtistIds())) {
            String[] ids = mv.getArtistIds().split(",");
            if (ids.length > 0) {
                try {
                    mvVO.setArtistId(Long.parseLong(ids[0].trim()));
                } catch (NumberFormatException e) {

                }
            }
        }

        return mvVO;
    }







    private void setUserActionStatus(MVVO mvVO, Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            mvVO.setIsFavorite(false);
            mvVO.setIsLike(false);
            return;
        }


        LambdaQueryWrapper<MvFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(MvFavorite::getUserId, userId)
                .eq(MvFavorite::getMvId, mvVO.getId())
                .eq(MvFavorite::getDeleted, CommonConstants.NOT_DELETED);
        Long count = mvFavoriteMapper.selectCount(favoriteWrapper);
        boolean isFavorited = count != null && count > 0;


        String likeKey = MV_LIKE_PREFIX + userId;
        String member = String.valueOf(mvVO.getId());
        Boolean isLiked = redisUtils.sIsMember(likeKey, member);

        mvVO.setIsFavorite(isFavorited);
        mvVO.setIsLike(Boolean.TRUE.equals(isLiked));
    }









    @Override
    public List<MVVO> getSimilarMVs(Long mvId, Integer limit) {
        if (ObjectUtils.isEmpty(mvId)) {
            return new ArrayList<>();
        }

        int actualLimit = limit != null && limit > 0 ? Math.min(limit, 50) : 10;


        MV originalMV = getById(mvId);
        if (originalMV == null) {
            return new ArrayList<>();
        }

        Set<Long> mvIds = new LinkedHashSet<>();


        if (StrUtil.isNotBlank(originalMV.getArtistIds()) && mvIds.size() < actualLimit) {
            String[] artistIds = originalMV.getArtistIds().split(",");
            for (String artistId : artistIds) {
                if (mvIds.size() >= actualLimit) break;

                LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                        .ne(MV::getId, mvId)
                        .apply("FIND_IN_SET({0}, artist_ids) > 0", artistId.trim())
                        .orderByDesc(MV::getPlayCount, MV::getPublishDate)
                        .last("LIMIT " + Math.min(5, actualLimit - mvIds.size()));

                List<MV> artistMVs = mvMapper.selectList(wrapper);
                for (MV mv : artistMVs) {
                    mvIds.add(mv.getId());
                }
            }
        }


        if (StrUtil.isNotBlank(originalMV.getTags()) && mvIds.size() < actualLimit) {
            String[] tags = originalMV.getTags().split(",");
            for (String tag : tags) {
                if (mvIds.size() >= actualLimit) break;

                LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                        .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                        .ne(MV::getId, mvId)
                        .notIn(!mvIds.isEmpty(), MV::getId, mvIds)
                        .like(MV::getTags, tag.trim())
                        .orderByDesc(MV::getPlayCount)
                        .last("LIMIT " + (actualLimit - mvIds.size()));

                List<MV> similarMVs = mvMapper.selectList(wrapper);
                for (MV mv : similarMVs) {
                    mvIds.add(mv.getId());
                }
            }
        }


        if (mvIds.size() < actualLimit) {
            LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                    .ne(MV::getId, mvId)
                    .notIn(!mvIds.isEmpty(), MV::getId, mvIds)
                    .orderByDesc(MV::getPlayCount, MV::getFavoriteCount)
                    .last("LIMIT " + (actualLimit - mvIds.size()));

            List<MV> hotMVs = mvMapper.selectList(wrapper);
            for (MV mv : hotMVs) {
                mvIds.add(mv.getId());
            }
        }

        if (mvIds.isEmpty()) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MV::getId, mvIds)
                .eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED);

        List<MV> mvs = mvMapper.selectList(wrapper);
        return convertToVOBatch(mvs, null);
    }








    @Override
    public List<MVVO> getArtistOtherMVs(Long mvId, Integer limit) {
        if (ObjectUtils.isEmpty(mvId)) {
            return new ArrayList<>();
        }

        int actualLimit = limit != null && limit > 0 ? limit : 10;


        MV originalMV = getById(mvId);
        if (originalMV == null || StrUtil.isBlank(originalMV.getArtistIds())) {
            return new ArrayList<>();
        }


        String[] artistIds = originalMV.getArtistIds().split(",");
        if (artistIds.length == 0) {
            return new ArrayList<>();
        }

        Long primaryArtistId;
        try {
            primaryArtistId = Long.valueOf(artistIds[0].trim());
        } catch (NumberFormatException e) {
            return new ArrayList<>();
        }


        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MV::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(MV::getDeleted, CommonConstants.NOT_DELETED)
                .ne(MV::getId, mvId)
                .apply("FIND_IN_SET({0}, artist_ids) > 0", primaryArtistId)
                .orderByDesc(MV::getPublishDate, MV::getPlayCount)
                .last("LIMIT " + actualLimit);

        List<MV> mvs = mvMapper.selectList(wrapper);
        return convertToVOBatch(mvs, null);
    }
}
