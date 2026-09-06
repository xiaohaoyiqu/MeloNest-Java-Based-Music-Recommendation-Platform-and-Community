   
                      
   
package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.util.*;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.common.service.CacheService;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.PlaylistSong;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.SongArtist;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.PlaylistMapper;
import com.haoran.music.mapper.PlaylistSongMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongArtistMapper;
import com.haoran.music.mapper.ListenHistoryMapper;
import com.haoran.music.mapper.SongLikeMapper;
import com.haoran.music.mapper.LyricMapper;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.service.SongService;
import com.haoran.music.service.SongCreditProjectionService;
import com.haoran.music.service.ArtistService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.MediaAccessGrantService;
import com.haoran.music.service.MediaPreviewService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.enums.SoundQuality;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.entity.SongLike;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                               
   
@Slf4j
@Service
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private CacheService cacheService;

    @Value("${music.upload.nginx-url-prefix}")
    private String fileUrlPrefix;

    @Value("${music.playback.source-url-prefix:${music.upload.nginx-url-prefix}}")
    private String playbackSourceUrlPrefix;

    @Value("${music.playback.nginx-accel-enabled:false}")
    private boolean nginxAccelEnabled;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private ListenHistoryMapper listenHistoryMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private SongArtistMapper songArtistMapper;

    @Resource
    private SongCreditProjectionService songCreditProjectionService;

    @Resource
    private ArtistService artistService;

    @Resource
    private LyricMapper lyricMapper;

    @Resource
    private UserStatisticsService userStatisticsService;

    @Resource
    private MediaAssetService mediaAssetService;

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Resource
    private MediaAccessGrantService mediaAccessGrantService;

    @Resource
    private ContentAccessService contentAccessService;

    @Resource
    private MediaPreviewService mediaPreviewService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private com.haoran.music.mapper.UserMapper userMapper;

    @Resource
    private MVMapper mvMapper;

    @Override
    public SongVO getSongById(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "\u6b4c\u66f2ID\u4e0d\u80fd\u4e3a\u7a7a");
        }

        Song accessSong = getById(songId);
        contentAccessService.requireSongMetadataAccess(accessSong, userId);

        String cacheKey = RedisConstants.SONG_INFO_PREFIX + songId;
        SongVO vo = CacheHelper.getOrLoadWithNullProtection(
                redisUtils,
                cacheKey,
                () -> buildSongVOFromDB(songId),
                cacheService.getSongCacheExpire(),
                cacheService.getNullCacheExpire(),
                TimeUnit.SECONDS,
                SongVO.class
        );

        if (vo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "\u6b4c\u66f2\u4e0d\u5b58\u5728");
        }

                                        
                                       
        applyLiveCounters(vo, accessSong);

        if (ObjectUtils.isNotEmpty(userId)) {
            vo.setIsFavorite(checkIsFavorite(userId, songId));
        }

        return vo;
    }

    private SongVO buildSongVOFromDB(Long songId) {
        Song song = getById(songId);
        if (ObjectUtils.isEmpty(song) || !CommonConstants.STATUS_NORMAL.equals(song.getStatus())) {
            return null;
        }
        SongVO vo = convertToVO(song);
        vo.setMvId(resolveRelatedMvId(song));
        return vo;
    }

    private Long resolveRelatedMvId(Song song) {
        return mvMapper.selectPublicRelatedMvId(song.getId(), song.getMvId());
    }

    @Override
    public IPage<SongVO> pageSongs(PageQuery pageQuery, Long userId) {
        Page<Song> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

        handleSort(wrapper, pageQuery.getSortField(), pageQuery.getSortOrder());

        IPage<Song> songPage = page(page, wrapper);

        Set<Long> favoriteSongIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId) && userId > 0 && !songPage.getRecords().isEmpty()) {
            favoriteSongIds = getFavoriteSongIds(userId, songPage.getRecords());
        }

        IPage<SongVO> voPage = ConvertHelper.toVOPage(songPage, this::convertToVO);
        ConvertHelper.setFieldFromSet(voPage.getRecords(), SongVO::getId, favoriteSongIds, SongVO::setIsFavorite);

        return voPage;
    }

    @Override
    public IPage<SongVO> getNewSongs(PageQuery pageQuery, Long userId) {
        pageQuery.setKeyword(CatalogSearchInput.normalize(pageQuery.getKeyword()));
        List<Song> newSongList = CacheHelper.getOrLoad(
                redisUtils,
                RedisConstants.NEW_SONG_KEY,
                this::loadNewSongsFromDB,
                1,
                TimeUnit.HOURS,
                List.class
        );

                                                                                         
                                                                                         
                                            
        List<Song> filteredNewSongList = filterNewSongs(
                filterPublicUploaderSongs(newSongList, 0), pageQuery);
        IPage<SongVO> resultPage = ConvertHelper.toPage(
                filteredNewSongList,
                pageQuery.getPage(),
                pageQuery.getSize(),
                this::convertToVO
        );

        if (!resultPage.getRecords().isEmpty() && ObjectUtils.isNotEmpty(userId)) {
            int start = Math.min((pageQuery.getPage() - 1) * pageQuery.getSize(), filteredNewSongList.size());
            int end = Math.min(pageQuery.getPage() * pageQuery.getSize(), filteredNewSongList.size());
            List<Song> pageData = filteredNewSongList.subList(start, end);
            Set<Long> favoriteSongIds = getFavoriteSongIds(userId, pageData);
            ConvertHelper.setFieldFromSet(resultPage.getRecords(), SongVO::getId, favoriteSongIds, SongVO::setIsFavorite);
        } else {
            ConvertHelper.setFieldFromSet(resultPage.getRecords(), SongVO::getId, Collections.emptySet(), SongVO::setIsFavorite);
        }

        refreshLiveCounters(resultPage.getRecords());

        return resultPage;
    }

    private List<Song> filterNewSongs(List<Song> songs, PageQuery pageQuery) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        Comparator<Song> comparator;
        if ("hotScore".equalsIgnoreCase(pageQuery.getSortBy()) || "hot".equalsIgnoreCase(pageQuery.getSortBy())) {
            comparator = Comparator.comparingInt((Song song) -> song.getHotScore() == null ? 0 : song.getHotScore())
                    .reversed()
                    .thenComparing(Comparator.comparingLong(
                            (Song song) -> song.getPlayCount() == null ? 0L : song.getPlayCount()).reversed())
                    .thenComparing(Comparator.comparingLong(
                            (Song song) -> song.getFavoriteCount() == null ? 0L : song.getFavoriteCount()).reversed())
                    .thenComparing(this::catalogTime, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Song::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        } else {
            comparator = Comparator.comparing(this::catalogTime,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Song::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return songs.stream()
                .filter(song -> matchesNewSongFilters(song, pageQuery))
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private boolean matchesNewSongFilters(Song song, PageQuery pageQuery) {
        if (song == null || pageQuery == null) {
            return false;
        }
        if (StrUtil.isNotBlank(pageQuery.getMainType()) && !matchesText(song.getMainType(), pageQuery.getMainType())) {
            return false;
        }
        if (StrUtil.isNotBlank(pageQuery.getMainGenre())
                && !matchesText(song.getMainGenre(), pageQuery.getMainGenre())
                && !matchesText(song.getMainType(), pageQuery.getMainGenre())) {
            return false;
        }
        if (StrUtil.isNotBlank(pageQuery.getLanguage()) && !matchesText(song.getLanguage(), pageQuery.getLanguage())) {
            return false;
        }
        if (StrUtil.isNotBlank(pageQuery.getKeyword())
                && !matchesText(song.getName(), pageQuery.getKeyword())
                && !matchesText(song.getArtistNames(), pageQuery.getKeyword())) {
            return false;
        }
        LocalDateTime catalogTime = catalogTime(song);
        int daysWithin = pageQuery.getDaysWithin() == null ? 30 : Math.max(1, Math.min(pageQuery.getDaysWithin(), 90));
        if (catalogTime == null || catalogTime.isBefore(LocalDate.now().minusDays(daysWithin).atStartOfDay())) {
            return false;
        }
        if (pageQuery.getMinDuration() != null
                && (song.getDuration() == null || song.getDuration() < Math.max(0, pageQuery.getMinDuration()))) {
            return false;
        }
        if (pageQuery.getMaxDuration() != null
                && (song.getDuration() == null || song.getDuration() > Math.max(0, pageQuery.getMaxDuration()))) {
            return false;
        }
        if ("free".equalsIgnoreCase(pageQuery.getPaymentType()) && Integer.valueOf(1).equals(song.getIsPaid())) {
            return false;
        }
        if ("paid".equalsIgnoreCase(pageQuery.getPaymentType()) && !Integer.valueOf(1).equals(song.getIsPaid())) {
            return false;
        }
        if ("lossless".equalsIgnoreCase(pageQuery.getQuality()) && StrUtil.isBlank(song.getUrlLossless())) {
            return false;
        }
        return true;
    }

    private LocalDateTime catalogTime(Song song) {
        return song.getReleaseDate() != null ? song.getReleaseDate().atStartOfDay() : song.getCreateTime();
    }

    private boolean matchesText(String value, String expected) {
        if (StrUtil.isBlank(expected)) {
            return true;
        }
        if (StrUtil.isBlank(value)) {
            return false;
        }
        String normalizedValue = value.trim().toLowerCase();
        String normalizedExpected = expected.trim().toLowerCase();
        return normalizedValue.contains(normalizedExpected) || normalizedExpected.contains(normalizedValue);
    }

    private List<Song> loadNewSongsFromDB() {
        LocalDate newSongDate = LocalDate.now().minusDays(90);
        LocalDateTime newSongDateTime = newSongDate.atStartOfDay();

        List<Song> songs = new ArrayList<>();

        LambdaQueryWrapper<Song> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .isNotNull(Song::getReleaseDate)
                .ge(Song::getReleaseDate, newSongDate)
                .orderByDesc(Song::getReleaseDate);
        List<Song> byReleaseDate = list(wrapper1);

        LambdaQueryWrapper<Song> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .isNull(Song::getReleaseDate)
                .ge(Song::getCreateTime, newSongDateTime)
                .orderByDesc(Song::getCreateTime);
        List<Song> byCreateTime = list(wrapper2);

        songs.addAll(byReleaseDate);
        songs.addAll(byCreateTime);

        return songs.stream()
                .distinct()
                .sorted((a, b) -> {
                    LocalDateTime timeA = a.getReleaseDate() != null
                            ? a.getReleaseDate().atStartOfDay()
                            : a.getCreateTime();
                    LocalDateTime timeB = b.getReleaseDate() != null
                            ? b.getReleaseDate().atStartOfDay()
                            : b.getCreateTime();
                    return timeB.compareTo(timeA);
                })
                .limit(500)
                .collect(Collectors.toList());
    }

    @Override
    public List<SongVO> getHotSongs(String type, Integer limit, Long userId) {
        String safeType = ObjectUtils.isEmpty(type) || "all".equalsIgnoreCase(type) ? null : type.trim();
        int actualLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
                                           
        String cacheKey = RedisConstants.HOT_SONG_PREFIX + (safeType == null ? "all" : safeType);

                            
        List<SongVO> cachedList = CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> loadHotSongsFromDB(safeType, 100),
                cacheService.getHotCacheExpire(),
                TimeUnit.SECONDS,
                List.class
        );

                        
        if (cachedList != null && !cachedList.isEmpty()) {
            List<SongVO> result = ConvertHelper.cloneVOList(cachedList, SongVO.class);

                       
            if (ObjectUtils.isNotEmpty(userId)) {
                Set<Long> favoriteSongIds = getFavoriteSongIdsByList(userId, cachedList);
                ConvertHelper.setFieldFromSet(result, SongVO::getId, favoriteSongIds, SongVO::setIsFavorite);
            } else {
                ConvertHelper.setFieldFromSet(result, SongVO::getId, Collections.emptySet(), SongVO::setIsFavorite);
            }

            List<SongVO> limitedResult = result.stream().limit(actualLimit).collect(Collectors.toList());
            refreshLiveCounters(limitedResult);
            return limitedResult;
        }

        return new ArrayList<>();
    }

    @Override
    public List<SongVO> getPublicSongsByIdsInOrder(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> safeIds = songIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(100)
                .collect(Collectors.toList());
        if (safeIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Song> publicSongs = filterPublicUploaderSongs(baseMapper.selectBatchIds(safeIds), 0).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        return safeIds.stream()
                .map(publicSongs::get)
                .filter(Objects::nonNull)
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

       
                 
       
    private List<SongVO> loadHotSongsFromDB(String type, int limit) {
        int queryLimit = limit > 0 ? limit * 3 : limit;
        List<Song> hotSongs = baseMapper.selectHotSongsByType(type, queryLimit);
        return ConvertHelper.toVOList(filterPublicUploaderSongs(hotSongs, limit), this::convertToVO);
    }

    @Override
    public IPage<SongVO> getFavoriteSongDetails(Long userId, PageQuery pageQuery) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "\u7528\u6237ID\u4e0d\u80fd\u4e3a\u7a7a");
        }

        Page<SongLike> likePage = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .orderByDesc(SongLike::getCreateTime);

        IPage<SongLike> favoritePage = songLikeMapper.selectPage(likePage, likeWrapper);
        Page<SongVO> resultPage = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());

        List<Long> songIds = favoritePage.getRecords().stream()
                .map(SongLike::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (songIds.isEmpty()) {
            resultPage.setRecords(new ArrayList<>());
            return resultPage;
        }

        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.in(Song::getId, songIds)
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
        List<Song> songs = list(songWrapper);
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));

        List<SongVO> records = songIds.stream()
                .map(songMap::get)
                .filter(Objects::nonNull)
                .map(song -> {
                    SongVO vo = convertToVO(song);
                    vo.setIsFavorite(true);
                    return vo;
                })
                .collect(Collectors.toList());

        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPlay(Long songId, Long userId, String quality, Long playlistId) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(userId)) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (!UserAccountStatusUtil.canContributePublicStats(user)) {
            log.debug("跳过非公共统计账号播放历史和热度: userId={}, songId={}", userId, songId);
            return;
        }

                   
        Song song = getById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "\u6b4c\u66f2\u4e0d\u5b58\u5728");
        }
        if (!canContributeSongStats(song)) {
            log.debug("跳过公开作者异常歌曲播放热度: userId={}, songId={}", userId, songId);
            return;
        }

                 
        ListenHistory history = new ListenHistory();
        history.setUserId(userId);
        history.setSongId(songId);
        history.setQuality(ObjectUtils.isNotEmpty(quality) ? quality : CommonConstants.QUALITY_STANDARD);
        history.setProgress(0);
        history.setIsCompleted(CommonConstants.NO);
        listenHistoryMapper.insert(history);

                                                   
        userStatisticsService.recordPlay(userId, songId, 0, false);

                   
        LambdaQueryWrapper<ListenHistory> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(ListenHistory::getUserId, userId)
                .orderByDesc(ListenHistory::getCreateTime);
        List<ListenHistory> historyList = listenHistoryMapper.selectList(historyWrapper);

        if (historyList.size() > CommonConstants.HISTORY_MAX_COUNT) {
                           
            List<Long> idsToDelete = new ArrayList<>();
            for (int i = CommonConstants.HISTORY_MAX_COUNT; i < historyList.size(); i++) {
                idsToDelete.add(historyList.get(i).getId());
            }
            if (CollUtil.isNotEmpty(idsToDelete)) {
                listenHistoryMapper.deleteBatchIds(idsToDelete);
            }
        }

                                     
        String playCountKey = RedisConstants.PLAY_COUNT_PREFIX + songId;
        redisUtils.increment(playCountKey);

                             
        if (ObjectUtils.isNotEmpty(playlistId)) {
            try {
                Playlist playlist = playlistMapper.selectById(playlistId);
                if (ObjectUtils.isNotEmpty(playlist)
                        && !playlist.getUserId().equals(userId)
                        && canContributePlaylistStats(playlist)) {
                                    
                    playlist.setPlayCount((playlist.getPlayCount() != null ? playlist.getPlayCount() : 0L) + 1);
                    playlistMapper.updateById(playlist);
                    log.debug("歌单播放量+1: playlistId={}, songId={}, userId={}", playlistId, songId, userId);
                }
            } catch (Exception e) {
                log.warn("更新歌单播放量失败: playlistId={}, error={}", playlistId, e.getClass().getSimpleName());
            }
        }

                   
        baseMapper.updateHotScore(songId);

        log.debug("记录播放: userId={}, songId={}, quality={}", userId, songId, quality);
    }

    @Override
    public String getPlayUrl(Long songId, String quality, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "\u6b4c\u66f2ID\u4e0d\u80fd\u4e3a\u7a7a");
        }

        Song song = getById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "\u6b4c\u66f2\u4e0d\u5b58\u5728");
        }
        contentAccessService.requireSongAccess(song, userId);

        ResolvedSongSource resolved = resolveSongSource(song, quality);
        String controlledUrl = MediaPlaybackUrlUtil.songUrl(songId, resolved.quality.getCode(), resolved.sourceUrl);
        if (resolved.quality.isRequireVip()) {
            requireVip(userId);
        }
        if (userId != null) {
            controlledUrl += "&grant="
                    + mediaAccessGrantService.issue("song", songId, resolved.quality.getCode(), userId);
        }
        return controlledUrl;
    }

    @Override
    public String getPreviewUrl(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }
        Song song = getById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongPreviewAccess(song, userId);
        ResolvedSongSource source = resolveSongSource(song, CommonConstants.QUALITY_STANDARD);
        String previewSource = mediaPreviewService.ensureSongPreview(song, source.sourceUrl);
        if (StrUtil.isBlank(previewSource)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试听片段暂时不可用");
        }
        String controlledUrl = MediaPlaybackUrlUtil.songUrl(songId, "preview", previewSource);
        return userId == null ? controlledUrl
                : controlledUrl + "&grant=" + mediaAccessGrantService.issue("song", songId, "preview", userId);
    }

       
                      
      
                         
                        
                        
       
    private Set<Long> getFavoriteSongIds(Long userId, List<Song> songs) {
        if (ObjectUtils.isEmpty(userId) || userId <= 0 || songs.isEmpty()) {
            return Collections.emptySet();
        }

                   
        List<Long> songIds = ConvertHelper.extractIds(songs, Song::getId);

                                                        
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .in(SongLike::getSongId, songIds)
                .eq(SongLike::getIsFavorite, 1)
                .select(SongLike::getSongId);

        List<SongLike> songLikes = songLikeMapper.selectList(wrapper);

                      
        return ConvertHelper.extractIdSet(songLikes, SongLike::getSongId);
    }

       
                                 
      
                         
                            
                        
       
    private Set<Long> getFavoriteSongIdsByList(Long userId, List<SongVO> songVOs) {
        if (ObjectUtils.isEmpty(userId) || userId <= 0 || songVOs.isEmpty()) {
            return Collections.emptySet();
        }

                   
        List<Long> songIds = ConvertHelper.extractIds(songVOs, SongVO::getId);

                                                        
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .in(SongLike::getSongId, songIds)
                .eq(SongLike::getIsFavorite, 1)
                .select(SongLike::getSongId);

        List<SongLike> songLikes = songLikeMapper.selectList(wrapper);

                      
        return ConvertHelper.extractIdSet(songLikes, SongLike::getSongId);
    }

       
                              
      
                         
                         
                                 
       
    private Boolean checkIsFavorite(Long userId, Long songId) {
                                                        
        LambdaQueryWrapper<SongLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getSongId, songId)
                .eq(SongLike::getIsFavorite, 1);

        Long count = songLikeMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

       
           
      
                            
                            
                            
       
    private void handleSort(LambdaQueryWrapper<Song> wrapper, String sortField, String sortOrder) {
        if (StrUtil.isBlank(sortField)) {
            wrapper.orderByDesc(Song::getCreateTime);
            return;
        }

        boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);

        switch (sortField) {
            case MusicConstants.SortType.TIME:
                wrapper.orderBy(true, isAsc, Song::getCreateTime);
                break;
            case MusicConstants.SortType.HOT:
                wrapper.orderBy(true, isAsc, Song::getHotScore, Song::getPlayCount);
                break;
            case MusicConstants.SortType.NAME:
                wrapper.orderBy(true, isAsc, Song::getName);
                break;
            case MusicConstants.SortType.RELEASE_TIME:
                wrapper.orderBy(true, isAsc, Song::getReleaseDate);
                break;
            case MusicConstants.SortType.PLAY_COUNT:
                wrapper.orderBy(true, isAsc, Song::getPlayCount);
                break;
            default:
                wrapper.orderByDesc(Song::getCreateTime);
                break;
        }
    }

       
            
      
                       
                   
       
    private SongVO convertToVO(Song song) {
        SongVO vo = BeanUtil.copyProperties(song, SongVO.class);

                                   
        vo.setIsSingle(song.getIsSingle());

                   
        vo.setVersionType(song.getVersionType());
        vo.setVersionName(song.getVersionName());
        
                             
        vo.setLanguage(song.getLanguage());

                
        if (StrUtil.isNotBlank(song.getSubTypes())) {
            try {
                vo.setSubTypes(JSON.parseArray(song.getSubTypes(), String.class));
            } catch (Exception e) {
                vo.setSubTypes(new ArrayList<>());
            }
        } else {
            vo.setSubTypes(new ArrayList<>());
        }

                             
        vo.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
        vo.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
        vo.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));

                  
           
        vo.setPlayable(hasPlayableAudioUrl(song));

                 
        vo.setSizeStandard(song.getSizeStandard());
        vo.setSizeHigh(song.getSizeHigh());
        vo.setSizeLossless(song.getSizeLossless());
                              
        String coverUrl = UrlHelper.buildRelativeCoverUrl(song.getCover());
        vo.setCover(StrUtil.isNotBlank(coverUrl) ? coverUrl : "/default-cover.png");

                 
           
        if (song.getHasLyric() != null && song.getHasLyric() == 1) {
            try {
                LambdaQueryWrapper<com.haoran.music.entity.Lyric> lyricWrapper = new LambdaQueryWrapper<>();
                lyricWrapper.eq(com.haoran.music.entity.Lyric::getSongId, song.getId())
                        .eq(com.haoran.music.entity.Lyric::getLyricType, 1)        
                        .eq(com.haoran.music.entity.Lyric::getStatus, 1)        
                        .orderByDesc(com.haoran.music.entity.Lyric::getCreateTime)
                        .last("LIMIT 1");
                com.haoran.music.entity.Lyric lyric = lyricMapper.selectOne(lyricWrapper);
                if (lyric != null && StrUtil.isNotBlank(lyric.getContent())) {
                    vo.setLyric(lyric.getContent());
                }
            } catch (Exception e) {
                log.warn("查询歌词失败: songId={}", song.getId());
            }
        }

        return vo;
    }

    void refreshLiveCounters(Collection<SongVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> songIds = records.stream()
                .filter(Objects::nonNull)
                .map(SongVO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (songIds.isEmpty()) {
            return;
        }
        Map<Long, Song> liveSongs = baseMapper.selectBatchIds(songIds).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));
        for (SongVO record : records) {
            if (record != null) {
                applyLiveCounters(record, liveSongs.get(record.getId()));
            }
        }
    }

    static void applyLiveCounters(SongVO target, Song liveSong) {
        if (target == null || liveSong == null) {
            return;
        }
        target.setPlayCount(liveSong.getPlayCount());
        target.setFavoriteCount(toDisplayCount(liveSong.getFavoriteCount()));
        target.setCommentCount(toDisplayCount(liveSong.getCommentCount()));
        target.setDownloadCount(toDisplayCount(liveSong.getDownloadCount()));
        target.setAvgRating(liveSong.getAvgRating());
        target.setHotScore(liveSong.getHotScore());
    }

    private static Integer toDisplayCount(Long value) {
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, value.intValue());
    }

       
                        
      
                         
                        
                          
                         
                             
       
    @Override
    public void streamSong(Long songId, String quality, String grant, String range, Long userId,
                           javax.servlet.http.HttpServletResponse response) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "\u6b4c\u66f2ID\u4e0d\u80fd\u4e3a\u7a7a");
        }

                 
        Song song = getById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "\u6b4c\u66f2\u4e0d\u5b58\u5728");
        }

        Long authorizedUserId = userId;
        boolean preview = "preview".equalsIgnoreCase(quality);
        ResolvedSongSource resolved = preview
                ? new ResolvedSongSource(SoundQuality.STANDARD, mediaPreviewService.findSongPreview(songId))
                : resolveSongSource(song, quality);
        if (StrUtil.isBlank(resolved.sourceUrl)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试听片段暂时不可用");
        }
        if (authorizedUserId == null && StrUtil.isNotBlank(grant)) {
            authorizedUserId = mediaAccessGrantService.verify(
                    grant, "song", songId, preview ? "preview" : resolved.quality.getCode());
        }
        if (preview) {
            contentAccessService.requireSongPreviewAccess(song, authorizedUserId);
        } else {
            contentAccessService.requireSongAccess(song, authorizedUserId);
        }
        if (!preview && resolved.quality.isRequireVip()) {
            requireVip(authorizedUserId);
        }

               
        response.setHeader("Cache-Control", authorizedUserId != null ? "private, no-store" : "public, max-age=300");
        String sourceUrl = toSourceUrl(resolved.sourceUrl);
        if (!redirectToNginx(sourceUrl, "/songs/", response)) {
            streamAudio(sourceUrl, range, response);
        }
    }

    private ResolvedSongSource resolveSongSource(Song song, String requestedQuality) {
        SoundQuality quality = parseSongQuality(requestedQuality);
        SoundQuality[] candidates;
        switch (quality) {
            case HIGH:
                candidates = new SoundQuality[]{SoundQuality.HIGH, SoundQuality.STANDARD};
                break;
            case LOSSLESS:
                candidates = new SoundQuality[]{SoundQuality.LOSSLESS, SoundQuality.HIGH, SoundQuality.STANDARD};
                break;
            default:
                candidates = new SoundQuality[]{SoundQuality.STANDARD, SoundQuality.HIGH};
        }

        for (SoundQuality candidate : candidates) {
            String sourceUrl = sourceUrlFor(song, candidate);
            if (StrUtil.isNotBlank(sourceUrl)) {
                return new ResolvedSongSource(candidate, sourceUrl);
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "播放资源不存在");
    }

    private SoundQuality parseSongQuality(String quality) {
        String normalized = StrUtil.blankToDefault(quality, CommonConstants.QUALITY_STANDARD).toLowerCase(java.util.Locale.ROOT);
        for (SoundQuality candidate : SoundQuality.values()) {
            if (candidate.getLevel() <= SoundQuality.LOSSLESS.getLevel() && candidate.getCode().equals(normalized)) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的音质");
    }

    private String sourceUrlFor(Song song, SoundQuality quality) {
        switch (quality) {
            case HIGH:
                return UrlHelper.normalizeUrl(song.getUrlHigh());
            case LOSSLESS:
                return UrlHelper.normalizeUrl(song.getUrlLossless());
            default:
                return UrlHelper.normalizeUrl(song.getUrlStandard());
        }
    }

    private String toSourceUrl(String sourceUrl) {
        String url = MediaSourceUrlUtil.toInternalUrl(sourceUrl, playbackSourceUrlPrefix, "/songs/");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = UrlHelper.toFullUrl(url, playbackSourceUrlPrefix);
        }
        return UrlHelper.encodePath(url);
    }

    private void requireVip(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "该音质需要登录并开通VIP");
        }
        if (!Boolean.TRUE.equals(userVipService.isVip(userId))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该音质为VIP专享");
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

    private static final class ResolvedSongSource {
        private final SoundQuality quality;
        private final String sourceUrl;

        private ResolvedSongSource(SoundQuality quality, String sourceUrl) {
            this.quality = quality;
            this.sourceUrl = sourceUrl;
        }
    }

       
             
      
                           
                          
                             
       
    private void streamAudio(String fileUrl, String range, javax.servlet.http.HttpServletResponse response) {
        java.net.HttpURLConnection conn = null;
        java.io.InputStream in = null;
        try {
            ExternalUrlGuard.Validation validation = ExternalUrlGuard.validate(
                    fileUrl, playbackSourceUrlPrefix, fileUrlPrefix);
            if (!validation.isAllowed()) {
                log.warn("音频源地址校验失败: reason={}, sourceHost={}, trustedHost={}",
                        validation.getReason(), UrlHelper.getHost(fileUrl), UrlHelper.getHost(playbackSourceUrlPrefix));
                throw new BusinessException("外部音频地址不安全");
            }

                               
            String encodedUrl = UrlHelper.encodePath(fileUrl);
            conn = ExternalStreamUtil.openGetConnection(encodedUrl);
            ExternalStreamUtil.applyBrowserHeaders(conn, false);

                        
            if (StrUtil.isNotBlank(range) && range.startsWith("bytes=")) {
                conn.setRequestProperty("Range", range);
            }

            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 206) {
                throw new BusinessException("获取音频资源失败: HTTP " + responseCode);
            }
            String contentType = conn.getContentType();
            if (!isExpectedMediaContentType(contentType, "audio/")) {
                log.error("音频源返回了非音频内容: contentType={}, sourceHost={}",
                        contentType, UrlHelper.getHost(fileUrl));
                writeBadGatewayIfPossible(response, "音频源返回了无效内容");
                return;
            }
            response.setStatus(responseCode);
            response.setContentType(contentType);

                            
            String acceptRanges = conn.getHeaderField("Accept-Ranges");
            if (StrUtil.isNotBlank(acceptRanges)) {
                response.setHeader("Accept-Ranges", acceptRanges);
            }

            String contentRange = conn.getHeaderField("Content-Range");
            if (StrUtil.isNotBlank(contentRange)) {
                response.setHeader("Content-Range", contentRange);
            }

            int contentLength = conn.getContentLength();
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }

                   
            in = conn.getInputStream();
            javax.servlet.ServletOutputStream out = response.getOutputStream();
            ExternalStreamUtil.copy(in, out);
            log.debug("音频流传输完成");

        } catch (java.io.IOException e) {
            log.error("音频流传输失败: {}", e.getClass().getSimpleName());
            writeBadGatewayIfPossible(response, "音频源暂时不可用");
        } finally {
            try {
                if (in != null) in.close();
            } catch (java.io.IOException e) {
                log.error("关闭输入流失败: {}", e.getClass().getSimpleName());
            }
            if (conn != null) {
                conn.disconnect();
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

       
                                                                                        
                                                              
       
    private List<Long> parseArtistIds(String artistIds) {
        if (StrUtil.isBlank(artistIds)) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (String value : artistIds.split(",")) {
            String trimmed = value.trim();
            if (StrUtil.isBlank(trimmed)) {
                continue;
            }
            try {
                result.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                log.warn("Ignore invalid artist id: {}", trimmed);
            }
        }
        return result;
    }

    private List<Long> resolveArtistIds(Song song) {
        if (song == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (song.getArtistId() != null) {
            ids.add(song.getArtistId());
        }
        ids.addAll(parseArtistIds(song.getArtistIds()));
        return new ArrayList<>(ids);
    }

    private boolean hasArtistInput(Song song) {
        return song != null
                && (song.getArtistId() != null
                || StrUtil.isNotBlank(song.getArtistIds())
                || StrUtil.isNotBlank(song.getArtistNames()));
    }

    private void normalizeSongArtistFields(Song song) {
        List<Long> artistIds = resolveArtistIds(song);
        if (artistIds.isEmpty()) {
            return;
        }
        if (song.getArtistId() == null) {
            song.setArtistId(artistIds.get(0));
        }
        if (StrUtil.isBlank(song.getArtistIds())) {
            song.setArtistIds(artistIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
    }

    private List<String> parseArtistNames(String artistNames) {
        if (StrUtil.isBlank(artistNames)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (String value : artistNames.split("[,\uFF0C\u3001/]")) {
            String trimmed = value.trim();
            if (StrUtil.isNotBlank(trimmed)) {
                names.add(trimmed);
            }
        }
        return names;
    }

    private void syncSongArtists(Song song) {
        if (song == null || song.getId() == null) {
            return;
        }
        songCreditProjectionService.lockForDisplayCreditSync(song.getId());
        List<Long> artistIds = resolveArtistIds(song);
        songArtistMapper.delete(new LambdaQueryWrapper<SongArtist>()
                .eq(SongArtist::getSongId, song.getId()));

        if (artistIds.isEmpty()) {
            songCreditProjectionService.syncDisplayCredits(song.getId(), Collections.emptyList(),
                    "song_service", "song artist relations cleared");
            return;
        }

        List<String> artistNames = parseArtistNames(song.getArtistNames());
        LocalDateTime now = LocalDateTime.now();
        List<SongArtist> relations = new ArrayList<>();
        for (int i = 0; i < artistIds.size(); i++) {
            SongArtist relation = new SongArtist();
            relation.setSongId(song.getId());
            relation.setArtistId(artistIds.get(i));
            relation.setArtistName(i < artistNames.size() ? artistNames.get(i) : null);
            relation.setType(i == 0 ? 1 : 2);
            relation.setSortOrder(i);
            relation.setCreateTime(now);
            if (songArtistMapper.insert(relation) != 1) {
                throw new IllegalStateException("song artist relation insert conflict");
            }
            relations.add(relation);
        }
        songCreditProjectionService.syncDisplayCredits(song.getId(), relations,
                "song_service", "song artist relation sync");
    }

    private void updateArtistCounts(List<Long> artistIds) {
        if (CollUtil.isEmpty(artistIds)) {
            return;
        }
        List<Long> uniqueIds = artistIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (uniqueIds.isEmpty()) {
            return;
        }
        try {
            artistService.batchUpdateArtistCount(uniqueIds);
        } catch (Exception e) {
            log.error("Update artist counts failed: artistIds={}", uniqueIds);
        }
    }

       
                                                              
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Song song) {
        normalizeSongArtistFields(song);
        boolean result = super.save(song);
        if (result) {
            syncSongArtists(song);
            updateArtistCounts(resolveArtistIds(song));
            searchIndexService.sync("song", song.getId());
            bumpPublicCacheVersions("song created:" + song.getId());
            invalidateNewSongCache();
        }
        return result;
    }

       
                                                                                           
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Song song) {
        Song oldSong = song != null && song.getId() != null ? this.getById(song.getId()) : null;
        List<Long> oldArtistIds = resolveArtistIds(oldSong);
        boolean artistInput = hasArtistInput(song);
        if (artistInput) {
            normalizeSongArtistFields(song);
        }

        boolean result = super.updateById(song);
        if (result && artistInput) {
            Song updatedSong = this.getById(song.getId());
            List<Long> newArtistIds = resolveArtistIds(updatedSong);
            boolean idsChanged = !oldArtistIds.equals(newArtistIds);
            boolean namesChanged = oldSong == null || !Objects.equals(oldSong.getArtistNames(), updatedSong.getArtistNames());
            if (idsChanged || namesChanged) {
                syncSongArtists(updatedSong);
            }

            List<Long> allArtistIds = new ArrayList<>(oldArtistIds);
            allArtistIds.addAll(newArtistIds);
            updateArtistCounts(allArtistIds);
        }
        if (result && song != null && song.getId() != null) {
            searchIndexService.sync("song", song.getId());
            if (publicCacheFieldsChanged(oldSong, song)) {
                bumpPublicCacheVersions("song updated:" + song.getId());
            }
            if (newSongCacheFieldsChanged(song)) {
                invalidateNewSongCache();
            }
        }
        return result;
    }

       
                                                                
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        Song song = this.getById(id);
        List<Long> artistIds = resolveArtistIds(song);

        boolean result = super.removeById(id);
        if (result) {
            songArtistMapper.delete(new LambdaQueryWrapper<SongArtist>()
                    .eq(SongArtist::getSongId, id));
            songCreditProjectionService.retireDisplayCredits(song.getId());
            updateArtistCounts(artistIds);
            mediaAssetService.releaseTargetReferences("song", song.getId());
            searchIndexService.sync("song", song.getId());
            bumpPublicCacheVersions("song deleted:" + id);
            invalidateNewSongCache();
        }
        return result;
    }

    private boolean publicCacheFieldsChanged(Song oldSong, Song update) {
        if (update == null) {
            return false;
        }
        return update.getStatus() != null
                || update.getDeleted() != null
                || update.getUploaderId() != null
                || update.getName() != null
                || update.getMainType() != null
                || update.getArtistId() != null
                || update.getArtistIds() != null
                || update.getArtistNames() != null
                || update.getIsHot() != null
                || update.getIsNew() != null
                || update.getHotScore() != null
                || update.getPlayCount() != null
                || update.getFavoriteCount() != null;
    }

    private boolean newSongCacheFieldsChanged(Song update) {
        if (update == null) {
            return false;
        }
        return update.getStatus() != null
                || update.getDeleted() != null
                || update.getUploaderId() != null
                || update.getName() != null
                || update.getReleaseDate() != null
                || update.getMainType() != null
                || update.getMainGenre() != null
                || update.getLanguage() != null
                || update.getArtistId() != null
                || update.getArtistIds() != null
                || update.getArtistNames() != null
                || update.getDuration() != null
                || update.getIsPaid() != null
                || update.getUrlStandard() != null
                || update.getUrlHigh() != null
                || update.getUrlLossless() != null
                || update.getHotScore() != null
                || update.getPlayCount() != null
                || update.getFavoriteCount() != null;
    }

    private void invalidateNewSongCache() {
        try {
            redisUtils.delete(RedisConstants.NEW_SONG_KEY);
        } catch (Exception e) {
            log.warn("歌曲变更后清理新歌缓存失败，不阻断歌曲写入: error={}",
                    e.getClass().getSimpleName());
        }
    }

    private void bumpPublicCacheVersions(String reason) {
        try {
            musicIntelligenceCacheService.bumpRecommendCacheVersion(reason, null);
            musicIntelligenceCacheService.bumpRankingCacheVersion(reason, null);
        } catch (Exception e) {
            log.warn("歌曲变更后更新推荐/排行榜缓存版本失败，不阻断歌曲写入: reason={}, error={}",
                    reason, e.getClass().getSimpleName());
        }
    }
    @Override
    public List<SongVO> getRisingSongs(Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Song::getHotScore)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Song> songs = filterPublicUploaderSongs(list(wrapper), actualLimit);
        return ConvertHelper.toVOList(songs, this::convertToVO);
    }

       
                   
      
                        
                        
                   
       
    @Override
    public List<SongVO> getSongsByGenre(String genre, Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
        
        if (StrUtil.isNotBlank(genre)) {
                                 
            wrapper.eq(Song::getMainType, genre);
        }
        
        wrapper.orderByDesc(Song::getHotScore, Song::getPlayCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Song> songs = filterPublicUploaderSongs(list(wrapper), actualLimit);
        return ConvertHelper.toVOList(songs, this::convertToVO);
    }

       
                  
      
                        
                   
       
    @Override
    public List<SongVO> getNewSongs(Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LocalDateTime newSongTime = LocalDateTime.now().minusDays(CommonConstants.NEW_SONG_DAYS);
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.ge(Song::getReleaseDate, LocalDate.now().minusDays(CommonConstants.NEW_SONG_DAYS))
                        .or()
                        .ge(Song::getCreateTime, newSongTime))
                .orderByDesc(Song::getReleaseDate, Song::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<Song> songs = filterPublicUploaderSongs(list(wrapper), actualLimit);
        return ConvertHelper.toVOList(songs, this::convertToVO);
    }

       
                  
      
                                                
                        
                   
       
    @Override
    public List<SongVO> getSongsByLanguage(String language, Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED);
        
                       
        if (ObjectUtils.isNotEmpty(language)) {
            String langCondition = language;
                       
            switch (language) {
                case "zh":
                    langCondition = "zh";       
                    break;
                case "en":
                    langCondition = "en";       
                    break;
                case "ko":
                    langCondition = "ko";       
                    break;
                case "ja":
                    langCondition = "ja";       
                    break;
                default:
                    langCondition = language;
            }
            wrapper.eq(Song::getLanguage, langCondition);
        }
        
        wrapper.orderByDesc(Song::getHotScore, Song::getPlayCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Song> songs = filterPublicUploaderSongs(list(wrapper), actualLimit);
        return ConvertHelper.toVOList(songs, this::convertToVO);
    }

    private boolean canContributePlaylistStats(Playlist playlist) {
        if (playlist == null || playlist.getUserId() == null) {
            return false;
        }
        return UserAccountStatusUtil.canExposePublicContent(playlist.getUserId(), userMapper::selectById);
    }

    private boolean canContributeSongStats(Song song) {
        if (song == null || song.getUploaderId() == null) {
            return true;
        }
        return UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById);
    }

    private int expandedPublicQueryLimit(int limit) {
        return limit > 0 ? limit * 3 : limit;
    }

    private List<Song> filterPublicUploaderSongs(List<Song> songs, int limit) {
        if (songs == null || songs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> uploaderMap = uploaderIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(uploaderIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));

        java.util.stream.Stream<Song> stream = songs.stream()
                .filter(song -> CommonConstants.STATUS_NORMAL.equals(song.getStatus()))
                .filter(song -> CommonConstants.NOT_DELETED.equals(song.getDeleted()))
                .filter(this::hasPlayableAudioUrl)
                .filter(song -> song.getUploaderId() == null
                        || UserAccountStatusUtil.canExposePublicContent(uploaderMap.get(song.getUploaderId())));
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    private boolean hasPlayableAudioUrl(Song song) {
        return song != null && (StrUtil.isNotBlank(song.getUrlStandard())
                || StrUtil.isNotBlank(song.getUrlHigh())
                || StrUtil.isNotBlank(song.getUrlLossless()));
    }
}

