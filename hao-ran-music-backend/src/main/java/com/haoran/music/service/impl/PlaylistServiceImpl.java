package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.enums.VipLevel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.*;
import com.haoran.music.dto.playlist.PlaylistCopyMoveResult;
import com.haoran.music.dto.playlist.PlaylistCreateDTO;
import com.haoran.music.dto.playlist.PlaylistUpdateDTO;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.service.ContentAccessService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.vo.playlist.PlaylistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                       
   
@Slf4j
@Service
public class PlaylistServiceImpl extends ServiceImpl<PlaylistMapper, Playlist> implements PlaylistService {

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    @Resource
    private PlaylistFavoriteMapper playlistFavoriteMapper;

    @Autowired(required = false)
    private FavoriteCollectionItemMapper favoriteCollectionItemMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private com.haoran.music.service.UserVipService userVipService;

    @Resource
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private com.haoran.music.mapper.PaidResourceMapper paidResourceMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private com.haoran.music.mapper.PlaylistSubscribeMapper playlistSubscribeMapper;

    @Resource
    private com.haoran.music.mapper.PlaylistCollaboratorMapper playlistCollaboratorMapper;

    @Resource
    private PlaylistOperationLogMapper playlistOperationLogMapper;
    @Resource
    private com.haoran.music.service.NotificationService notificationService;

    @Resource
    private com.haoran.music.service.PlaylistCollaborationAuditService playlistCollaborationAuditService;

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private ContentAccessService contentAccessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Playlist playlist) {
        boolean result = super.save(playlist);
        if (result && playlist != null) {
            searchIndexService.sync("playlist", playlist.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Playlist playlist) {
        boolean result = super.updateById(playlist);
        if (result && playlist != null && playlist.getId() != null) {
            searchIndexService.sync("playlist", playlist.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result && id != null) {
            searchIndexService.sync("playlist", Long.valueOf(String.valueOf(id)));
        }
        return result;
    }

    public PlaylistVO getPlaylistById(Long playlistId, Long userId, Integer page, Integer size) {
        return getPlaylistById(playlistId, userId, page, size, null, null, "default");
    }

    @Override
    public PlaylistVO getPlaylistById(Long playlistId, Long userId, Integer page, Integer size,
                                      String keyword, String language, String sortBy) {
        if (ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单ID不能为空");
        }
        String safeKeyword = CatalogSearchInput.normalizeForLike(keyword);
        String normalizedLanguage = normalizeOptionalLanguage(language);
        String normalizedSort = normalizeDetailSort(sortBy);

        Playlist playlist = getById(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

                   
        if (playlist.getIsPublic() == CommonConstants.PUBLIC_PRIVATE) {
                            
            String playlistUserIdStr = String.valueOf(playlist.getUserId());
            String requestUserIdStr = userId != null ? String.valueOf(userId) : null;

            log.info("私密歌单权限检查: playlistId={}, playlistUserId={}, requestUserId={}, match={}",
                playlistId, playlistUserIdStr, requestUserIdStr, playlistUserIdStr.equals(requestUserIdStr));

            if (requestUserIdStr == null || !requestUserIdStr.equals(playlistUserIdStr)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此歌单");
            }
        }
        contentAccessService.requirePlaylistMetadataAccess(playlist, userId);
        if (!canExposePlaylistToViewer(playlist, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        PlaylistVO vo = convertToVOWithUser(playlist, userId);
        vo.setPrimaryLanguage(playlist.getLanguage());
        Map<String, Integer> detailLanguageCounts = getPlaylistLanguageStats(Collections.singletonList(playlist))
                .getOrDefault(playlistId, Collections.emptyMap());
        vo.setLanguageCounts(detailLanguageCounts);
        vo.setContentLanguages(new ArrayList<>(detailLanguageCounts.keySet()));
        if (StrUtil.isNotBlank(playlist.getTags())) {
            try {
                vo.setTags(JSON.parseArray(playlist.getTags(), String.class));
            } catch (Exception ignored) {
                vo.setTags(new ArrayList<>());
            }
        }

        boolean contentAccessible = canAccessPlaylistContent(playlist, userId);
        vo.setContentAccessible(contentAccessible);


                                
        if (ObjectUtils.isNotEmpty(userId)) {
            String visitKey = RedisConstants.PLAYLIST_VISIT_PREFIX + playlistId + ":user:" + userId;
            Boolean hasVisited = redisUtils.hasKey(visitKey);
            if (!hasVisited) {
                                    
                playlist.setVisitCount((long)((playlist.getVisitCount() != null ? playlist.getVisitCount().intValue() : 0) + 1));
                updateById(playlist);
                vo.setVisitCount(playlist.getVisitCount() != null ? playlist.getVisitCount().intValue() : 0);
                           
                redisUtils.set(visitKey, "1", RedisConstants.VISIT_EXPIRE, RedisConstants.VISIT_TIME_UNIT);
                log.info("歌单访问量+1: playlistId={}, userId, visitCount={}", playlistId, userId, playlist.getVisitCount());
            } else {
                vo.setVisitCount(playlist.getVisitCount() != null ? playlist.getVisitCount().intValue() : 0);
            }
        } else {
            vo.setVisitCount(playlist.getVisitCount() != null ? playlist.getVisitCount().intValue() : 0);
        }

        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 50 : Math.max(1, Math.min(size, 100));
        if (!contentAccessible) {
            vo.setSongs(new ArrayList<>());
            vo.setCurrentPage(safePage);
            vo.setPageSize(safeSize);
            vo.setFilteredSongCount(0);
            vo.setTotalPages(0);
            return vo;
        }
        boolean publicPlaylist = CommonConstants.PUBLIC_PUBLIC.equals(playlist.getIsPublic());
        Set<Long> allowedUploaderIds = Collections.emptySet();
        if (publicPlaylist) {
            List<Long> uploaderIds = playlistSongMapper.selectUploaderIdsByPlaylist(playlistId);
            if (CollUtil.isNotEmpty(uploaderIds)) {
                List<User> uploaders = userMapper.selectBatchIds(uploaderIds);
                if (uploaders != null) {
                    allowedUploaderIds = uploaders.stream()
                            .filter(UserAccountStatusUtil::canExposePublicContent)
                            .map(User::getId)
                            .collect(Collectors.toSet());
                }
            }
        }
        Long visibleSongCount = playlistSongMapper.countVisibleSongs(playlistId, publicPlaylist, allowedUploaderIds);
        vo.setSongCount((int) Math.min(Integer.MAX_VALUE, Math.max(0L,
                visibleSongCount == null ? 0L : visibleSongCount)));
        IPage<PlaylistSong> relationPage = playlistSongMapper.selectVisibleSongPage(
                new Page<>(safePage, safeSize), playlistId, safeKeyword, normalizedLanguage, normalizedSort,
                publicPlaylist, allowedUploaderIds);
        List<PlaylistSong> playlistSongs = relationPage.getRecords();
        vo.setOrderVersion(playlistOrderSummary(playlistId));
        vo.setCurrentPage((int) relationPage.getCurrent());
        vo.setPageSize((int) relationPage.getSize());
        vo.setFilteredSongCount((int) Math.min(Integer.MAX_VALUE, relationPage.getTotal()));
        vo.setTotalPages((int) relationPage.getPages());

        if (CollUtil.isNotEmpty(playlistSongs)) {
                     
            List<Long> songIds = playlistSongs.stream()
                    .map(PlaylistSong::getSongId)
                    .collect(Collectors.toList());

            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(Song::getId, songIds)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

            List<Song> songs = songMapper.selectList(wrapper);
            Map<Long, Song> songMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, s -> s));

                             
            Set<Long> favoriteSongIds = Collections.emptySet();
            if (ObjectUtils.isNotEmpty(userId)) {
                favoriteSongIds = getFavoriteSongIds(userId, songs);
            }

            List<PlaylistVO.SongSimpleVO> songSimpleList = new ArrayList<>();
            for (PlaylistSong ps : playlistSongs) {
                Song song = songMap.get(ps.getSongId());
                if (ObjectUtils.isNotEmpty(song)) {
                    PlaylistVO.SongSimpleVO simpleVO = new PlaylistVO.SongSimpleVO();
                    simpleVO.setId(song.getId());
                    simpleVO.setName(song.getName());
                    simpleVO.setArtistNames(song.getArtistNames());
                    simpleVO.setAlbumName(song.getAlbumName());
                    simpleVO.setAlbumId(song.getAlbumId());
                    simpleVO.setDuration(song.getDuration());
                    simpleVO.setMainType(song.getMainType());
                    simpleVO.setLanguage(song.getLanguage());
                    simpleVO.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
                    simpleVO.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
                    simpleVO.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
                    simpleVO.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
                    simpleVO.setSortOrder(ps.getSortOrder());
                    simpleVO.setPlayCount(song.getPlayCount());
                                     
                    simpleVO.setIsFavorite(favoriteSongIds.contains(song.getId()));
                    simpleVO.setVersionType(song.getVersionType());
                    simpleVO.setVersionName(song.getVersionName());

                    songSimpleList.add(simpleVO);
                }
            }
            vo.setSongs(songSimpleList);
        } else {
            vo.setSongs(new ArrayList<>());
        }

                 
        if (ObjectUtils.isNotEmpty(userId) && !userId.equals(playlist.getUserId())) {
            vo.setIsFavorite(checkIsFavorite(userId, playlistId));
        }

        return vo;
    }

    @Override
       
                     
      
                            
                           
                           
                      
                         
                     
       
    public IPage<PlaylistVO> pagePlaylists(PageQuery pageQuery, String keyword, String language,
                                           List<String> languages, String languageMode, String tag, String category,
                                           Integer minSongCount, Integer maxSongCount, String paymentType, Long userId) {
        String safeKeyword = CatalogSearchInput.normalizeForLike(keyword);
        requireMaxLength(tag, 50, "标签");
        requireMaxLength(category, 30, "分类");
        if (!"any".equalsIgnoreCase(languageMode) && !"all".equalsIgnoreCase(languageMode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "多语言匹配方式不合法");
        }
        if (minSongCount != null && (minSongCount < 0 || minSongCount > CommonConstants.PLAYLIST_MAX_SONGS)
                || maxSongCount != null && (maxSongCount < 0 || maxSongCount > CommonConstants.PLAYLIST_MAX_SONGS)
                || minSongCount != null && maxSongCount != null && minSongCount > maxSongCount) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲数量范围不合法");
        }
        if (StrUtil.isNotBlank(paymentType)
                && !"free".equalsIgnoreCase(paymentType) && !"paid".equalsIgnoreCase(paymentType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "价格筛选值不合法");
        }
        Page<Playlist> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Set<Long> retainedCreatorIds = getRetainedPublicPlaylistCreatorIds();

        if (retainedCreatorIds.isEmpty() && ObjectUtils.isEmpty(userId)) {
            return new Page<>(pageQuery.getPage(), pageQuery.getSize(), 0L);
        }

        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

                                                          
        wrapper.and(group -> {
            if (!retainedCreatorIds.isEmpty()) {
                group.and(publicGroup -> publicGroup
                        .eq(Playlist::getIsPublic, CommonConstants.PUBLIC_PUBLIC)
                        .in(Playlist::getUserId, retainedCreatorIds));
            }
            if (ObjectUtils.isNotEmpty(userId)) {
                if (!retainedCreatorIds.isEmpty()) {
                    group.or(ownerGroup -> ownerGroup
                            .eq(Playlist::getUserId, userId)
                            .eq(Playlist::getType, MusicConstants.PlaylistType.CUSTOM));
                } else {
                    group.and(ownerGroup -> ownerGroup
                            .eq(Playlist::getUserId, userId)
                            .eq(Playlist::getType, MusicConstants.PlaylistType.CUSTOM));
                }
            }
        });

                          
        if (StrUtil.isNotBlank(safeKeyword)) {
            wrapper.like(Playlist::getName, safeKeyword);
        }

               
        List<String> requestedLanguages = normalizeLanguages(language, languages);
        applyContentLanguageFilter(wrapper, requestedLanguages, languageMode);

               
        if (StrUtil.isNotBlank(tag)) {
            wrapper.like(Playlist::getTags, tag);
        }
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(Playlist::getCategory, category);
        }
        if (minSongCount != null) {
            wrapper.ge(Playlist::getSongCount, Math.max(0, minSongCount));
        }
        if (maxSongCount != null) {
            wrapper.le(Playlist::getSongCount, Math.max(0, maxSongCount));
        }
        if ("free".equalsIgnoreCase(paymentType)) {
            wrapper.and(group -> group.isNull(Playlist::getIsPaid).or().ne(Playlist::getIsPaid, 1));
        } else if ("paid".equalsIgnoreCase(paymentType)) {
            wrapper.eq(Playlist::getIsPaid, 1);
        }

               
        handleSort(wrapper, pageQuery.getSortField(), pageQuery.getSortOrder());

        IPage<Playlist> playlistPage = page(page, wrapper);
        playlistPage.setRecords(filterPublicCreatorPlaylists(playlistPage.getRecords(), userId, 0));

                 
        if (!playlistPage.getRecords().isEmpty()) {
            return convertToVOBatch(playlistPage, userId);
        }
        return playlistPage.convert(playlist -> new PlaylistVO());
    }

    public IPage<PlaylistVO> pagePlaylists(PageQuery pageQuery, String keyword, String language,
                                           String tag, Long userId) {
        return pagePlaylists(pageQuery, keyword, language, null, "any", tag, null,
                null, null, null, userId);
    }

    private List<String> normalizeLanguages(String language, List<String> languages) {
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        if (languages != null) languages.forEach(value -> addLanguageFilter(normalized, value));
        addLanguageFilter(normalized, language);
        if (normalized.size() > 8) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "最多选择8种语言");
        }
        return new ArrayList<>(normalized);
    }

    private void addLanguageFilter(Set<String> target, String value) {
        if (StrUtil.isBlank(value)) return;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,8}")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "语言筛选值不合法");
        }
        target.add(normalized);
    }

    private String normalizeOptionalLanguage(String language) {
        if (StrUtil.isBlank(language)) return null;
        Set<String> normalized = new LinkedHashSet<>();
        addLanguageFilter(normalized, language);
        return normalized.iterator().next();
    }

    private String normalizeDetailSort(String sortBy) {
        String normalized = StrUtil.blankToDefault(sortBy, "default").trim().toLowerCase(Locale.ROOT);
        if (!Arrays.asList("default", "name", "artist", "duration").contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲排序方式不合法");
        }
        return normalized;
    }

    private void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "过长");
        }
    }

    private boolean canAccessPlaylistContent(Playlist playlist, Long userId) {
        try {
            contentAccessService.requirePlaylistAccess(playlist, userId);
            return true;
        } catch (BusinessException exception) {
            Integer code = exception.getCode();
            if (Objects.equals(code, ResultCode.UNAUTHORIZED.getCode())
                    || Objects.equals(code, ResultCode.FORBIDDEN.getCode())
                    || Objects.equals(code, ResultCode.NOT_FOUND.getCode())) {
                return false;
            }
            throw exception;
        }
    }

    private void applyContentLanguageFilter(LambdaQueryWrapper<Playlist> wrapper, List<String> languages,
                                            String languageMode) {
        if (languages.isEmpty()) return;
        String placeholders = java.util.stream.IntStream.range(0, languages.size())
                .mapToObj(index -> "{" + index + "}").collect(Collectors.joining(","));
        String matchingSongs = " FROM playlist_song ps JOIN song s ON s.id = ps.song_id "
                + "WHERE ps.playlist_id = playlist.id AND ps.deleted = 0 AND s.deleted = 0 AND s.status = 1 "
                + "AND s.language IN (" + placeholders + ")";
        Object[] values = languages.toArray();
        if ("all".equalsIgnoreCase(languageMode)) {
            wrapper.apply("(SELECT COUNT(DISTINCT s.language)" + matchingSongs
                    + ") = " + languages.size(), values);
        } else {
            wrapper.apply("EXISTS (SELECT 1" + matchingSongs + ")", values);
        }
    }

       
                                           
       
    private Set<Long> getRetainedPublicPlaylistCreatorIds() {
        List<Long> creatorIds = baseMapper.selectPublicCreatorIds();
        if (creatorIds == null || creatorIds.isEmpty()) {
            return Collections.emptySet();
        }

        return UserAccountStatusUtil.filterRetainedPublicContentUserIds(
                new HashSet<>(creatorIds),
                userMapper::selectBatchIds
        );
    }

    @Override
    public List<PlaylistVO> getUserPlaylists(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(Playlist::getType, Playlist::getCreateTime);

        List<Playlist> playlists = list(wrapper);

        if (playlists.isEmpty()) {
            return new ArrayList<>();
        }

                                               
        List<PlaylistVO> playlistVOs = convertToVOBatch(playlists, userId);
        applyVisibleSongCounts(playlistVOs);
        return playlistVOs;
    }

    @Override
    public PlaylistVO getFavoritePlaylist(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

                    
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

        Playlist favoritePlaylist = getOne(wrapper);

                         
        if (ObjectUtils.isEmpty(favoritePlaylist)) {
            favoritePlaylist = new Playlist();
            favoritePlaylist.setUserId(userId);
            favoritePlaylist.setName("我喜爱的音乐");
            favoritePlaylist.setType(MusicConstants.PlaylistType.FAVORITE);
            favoritePlaylist.setCover("/images/default-base.png");
            favoritePlaylist.setDescription("我喜爱的音乐收藏");
            favoritePlaylist.setIsPublic(CommonConstants.PUBLIC_PRIVATE);
            favoritePlaylist.setSongCount(0L);
            save(favoritePlaylist);
        }

        return getPlaylistById(favoritePlaylist.getId(), userId, 1, 50);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPlaylist(Long userId, PlaylistCreateDTO dto) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (dto == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "创建歌单");
        validatePlaylistVisibility(dto.getIsPublic());
        List<String> normalizedTags = normalizePlaylistTags(dto.getTags());
        validatePlaylistCover(dto.getCover());

                               
        int vipLevelCode = userVipService.getVipLevel(userId).getCode();
        int maxPlaylists = VipLevel.getMaxPlaylistsByLevel(vipLevelCode);

                     
        LambdaQueryWrapper<Playlist> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.CUSTOM)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

        Long userPlaylistCount = count(countWrapper);
        if (userPlaylistCount >= maxPlaylists) {
            throw new BusinessException("歌单数量已达上限（" + maxPlaylists + "个），升级VIP可创建更多歌单");
        }

               
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);

                    
        SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkName(dto.getName(), "歌单名称");
        if (!nameCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, nameCheck.getMessage());
        }
        playlist.setName(nameCheck.getCleanedValue());

                    
        if (StrUtil.isNotBlank(dto.getDescription())) {
            SecurityCheckUtil.CheckResult descCheck = SecurityCheckUtil.checkDescription(dto.getDescription());
            if (!descCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descCheck.getMessage());
            }
            playlist.setDescription(SecurityCheckUtil.escapeHtml(descCheck.getCleanedValue()));
        } else {
            playlist.setDescription(dto.getDescription());
        }

        playlist.setCover(ObjectUtils.isNotEmpty(dto.getCover()) ? dto.getCover() : "/images/default-base.png");
        playlist.setType(MusicConstants.PlaylistType.CUSTOM);
        playlist.setIsPublic(ObjectUtils.isNotEmpty(dto.getIsPublic()) ? dto.getIsPublic() : CommonConstants.PUBLIC_PUBLIC);
        playlist.setSongCount(0L);
        playlist.setPlayCount(0L);
        playlist.setFavoriteCount(0L);

               
        if (!normalizedTags.isEmpty()) {
            playlist.setTags(JSON.toJSONString(normalizedTags));
        }

        save(playlist);

                          
        if (CollUtil.isNotEmpty(dto.getSongIds())) {
            addSongsToPlaylist(userId, playlist.getId(), dto.getSongIds());
        }

        log.info("用户创建歌单: userId={}, playlistId={}, vipLevel={}, maxPlaylists={}", 
                 userId, playlist.getId(), 
                 vipLevelCode > VipLevel.FREE.getCode() ? "VIP" : "FREE",
                 maxPlaylists);

        return playlist.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePlaylist(Long userId, PlaylistUpdateDTO dto) {
        if (ObjectUtils.isEmpty(userId) || dto == null || ObjectUtils.isEmpty(dto.getId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "修改歌单");
        validatePlaylistVisibility(dto.getIsPublic());
        validatePlaylistCover(dto.getCover());
        List<String> normalizedTags = dto.getTags() == null ? null : normalizePlaylistTags(dto.getTags());
        Playlist playlist = baseMapper.selectByIdForUpdate(dto.getId());
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        if (!isPlaylistOwner(playlist, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有歌单所有者可以修改资料和公开性");
        }

                        
        boolean isFavorite = isFavoritePlaylistType(playlist);
        if (isFavorite && StrUtil.isNotBlank(dto.getName())) {
            throw new BusinessException("收藏歌单名称不可修改");
        }
        String beforeMetadataSummary = playlistMetadataSummary(playlist);

        if (StrUtil.isNotBlank(dto.getName()) && !isFavorite) {
                        
            SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkName(dto.getName(), "歌单名称");
            if (!nameCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, nameCheck.getMessage());
            }
            playlist.setName(nameCheck.getCleanedValue());
        }
        if (StrUtil.isNotBlank(dto.getDescription())) {
                        
            SecurityCheckUtil.CheckResult descCheck = SecurityCheckUtil.checkDescription(dto.getDescription());
            if (!descCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descCheck.getMessage());
            }
            playlist.setDescription(SecurityCheckUtil.escapeHtml(descCheck.getCleanedValue()));
        }
        if (ObjectUtils.isNotEmpty(dto.getCover())) {
            playlist.setCover(dto.getCover());
        }
        if (ObjectUtils.isNotEmpty(dto.getIsPublic()) && !isFavorite) {
            playlist.setIsPublic(dto.getIsPublic());
        }
        if (normalizedTags != null && !isFavorite) {
            playlist.setTags(JSON.toJSONString(normalizedTags));
        }

        updateById(playlist);
        recordCollaborationOperationIfEnabled(playlist.getId(), userId, "update_metadata", null,
                "修改了歌单资料", beforeMetadataSummary, playlistMetadataSummary(playlist));

        String cacheKey = RedisConstants.PLAYLIST_PREFIX + playlist.getId();
        redisUtils.delete(cacheKey);

        log.info("用户更新歌单: userId={}, playlistId={}", userId, playlist.getId());

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deletePlaylist(Long userId, Long playlistId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "删除歌单");
        Playlist playlist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        if (!isPlaylistOwner(playlist, userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此歌单");
        }

                   
        if (isFavoritePlaylistType(playlist)) {
            throw new BusinessException("收藏歌单不可删除");
        }

        List<Long> affectedCollaborators = playlistCollaboratorMapper.selectActiveNonOwnerUserIds(playlistId);
        String collaborationEventId = recordCollaborationOperationIfEnabled(
                playlistId, userId, "delete_playlist", null, "删除了协作歌单");
        playlistCollaboratorMapper.closeAllByPlaylistId(playlistId);

        if (ObjectUtils.isNotEmpty(collaborationEventId) && CollUtil.isNotEmpty(affectedCollaborators)) {
            for (Long collaboratorId : affectedCollaborators) {
                notificationService.sendSystemNotificationOnce(collaboratorId, "协作歌单已关闭",
                        "歌单《" + playlist.getName() + "》已删除，协作关系同步关闭", null,
                        "playlist-collaboration:" + collaborationEventId + ":" + collaboratorId);
            }
        }

               
        removeById(playlistId);

               
        String cacheKey = RedisConstants.PLAYLIST_PREFIX + playlistId;
        redisUtils.delete(cacheKey);

        log.info("用户删除歌单: userId={}, playlistId={}", userId, playlistId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addSongsToPlaylist(Long userId, Long playlistId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId) || CollUtil.isEmpty(songIds)) {
            return 0;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "添加歌单歌曲");
        List<Long> distinctSongIds = songIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinctSongIds.isEmpty() || distinctSongIds.size() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多添加200首歌曲");
        }

        Playlist playlist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        requirePlaylistPermission(playlist, userId, "add", "无权修改此歌单");

                                       
        boolean isFavoritePlaylist = isFavoritePlaylistType(playlist);
        long currentSongCount = playlist.getSongCount() != null ? playlist.getSongCount() : 0L;

        Integer maxSortOrder = playlistSongMapper.selectMaxSortOrder(playlistId);
        int nextSortOrder = maxSortOrder != null ? maxSortOrder + 1 : 0;

                    
        LambdaQueryWrapper<PlaylistSong> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(PlaylistSong::getPlaylistId, playlistId)
                .in(PlaylistSong::getSongId, distinctSongIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .select(PlaylistSong::getSongId);

        List<PlaylistSong> existSongs = playlistSongMapper.selectList(existWrapper);
        Set<Long> existSongIds = existSongs.stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());

        Map<Long, Song> candidateSongs = filterPublicSongs(songMapper.selectBatchIds(distinctSongIds)).stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));

                  
        List<PlaylistSong> toAdd = new ArrayList<>();
        for (int i = 0; i < distinctSongIds.size(); i++) {
            Long songId = distinctSongIds.get(i);
            if (!existSongIds.contains(songId)) {
                Song song = candidateSongs.get(songId);
                if (ObjectUtils.isNotEmpty(song)) {
                    PlaylistSong ps = new PlaylistSong();
                    ps.setPlaylistId(playlistId);
                    ps.setSongId(songId);
                    ps.setSortOrder(nextSortOrder + i);
                    toAdd.add(ps);
                }
            }
        }

        if (CollUtil.isNotEmpty(toAdd)
                && !isFavoritePlaylist
                && currentSongCount + toAdd.size() > CommonConstants.PLAYLIST_MAX_SONGS) {
            throw new BusinessException("歌单歌曲数量已达上限（" + CommonConstants.PLAYLIST_MAX_SONGS + "首）");
        }

        if (CollUtil.isNotEmpty(toAdd)) {
            playlistSongMapper.deleteDeletedHistoryBySongIds(playlistId,
                    toAdd.stream().map(PlaylistSong::getSongId).collect(Collectors.toList()));
            int addedCount = 0;
            for (PlaylistSong ps : toAdd) {
                ps.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                int inserted = playlistSongMapper.insertIgnore(ps);
                if (inserted <= 0) {
                    continue;
                }
                addedCount += inserted;
                recordCollaborationOperationIfEnabled(
                        playlistId, userId, "add_song", ps.getSongId(), "添加了歌曲");
            }

                       
            if (addedCount > 0) {
                baseMapper.adjustSongCount(playlistId, addedCount);
                syncPlaylistIndex(playlistId);
            }

                   
            String cacheKey = RedisConstants.PLAYLIST_PREFIX + playlistId;
            redisUtils.delete(cacheKey);

            log.info("用户添加歌曲到歌单: userId={}, playlistId={}, count={}", userId, playlistId, addedCount);
            return addedCount;
        }

        log.info("用户添加歌曲到歌单: userId={}, playlistId={}, count=0", userId, playlistId);

        return 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer removeSongsFromPlaylist(Long userId, Long playlistId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId) || CollUtil.isEmpty(songIds)) {
            return 0;
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "移除歌单歌曲");
        List<Long> distinctSongIds = songIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctSongIds.isEmpty() || distinctSongIds.size() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多移除200首歌曲");
        }

        Playlist playlist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        requirePlaylistPermission(playlist, userId, "remove", "无权修改此歌单");

                   
        playlistSongMapper.deleteDeletedHistoryBySongIds(playlistId, distinctSongIds);
        int deletedCount = playlistSongMapper.deleteActiveBySongIds(playlistId, distinctSongIds);
        if (deletedCount > 0) {
            for (Long songId : distinctSongIds) {
                recordCollaborationOperationIfEnabled(
                        playlistId, userId, "remove_song", songId, "移除了歌曲");
            }
        }

                   
        if (deletedCount > 0) {
            baseMapper.adjustSongCount(playlistId, -deletedCount);
            syncPlaylistIndex(playlistId);
        }

               
        String cacheKey = RedisConstants.PLAYLIST_PREFIX + playlistId;
        redisUtils.delete(cacheKey);

        log.info("用户从歌单移除歌曲: userId={}, playlistId={}, count={}", userId, playlistId, deletedCount);

        return deletedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSongOrders(Long userId, Long playlistId, List<Long> songIds,
                                    String expectedOrderVersion) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId) || CollUtil.isEmpty(songIds)
                || StrUtil.isBlank(expectedOrderVersion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "调整歌单顺序");
        if (songIds.size() > 200 || songIds.stream().anyMatch(songId -> songId == null || songId <= 0L)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排序项不合法或超过200项");
        }
        if (songIds.stream().distinct().count() != songIds.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排序歌曲不能重复");
        }

        Playlist playlist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        requirePlaylistPermission(playlist, userId, "edit", "无权修改此歌单");
        List<Long> activeSongIds = playlistSongMapper.selectActiveSongIds(playlistId);
        List<Long> safeActiveSongIds = activeSongIds == null ? Collections.emptyList() : activeSongIds;
        String beforeOrderSummary = playlistOrderSummary(playlistId);
        if (!beforeOrderSummary.equals(expectedOrderVersion)) {
            throw new BusinessException(409, "歌单内容或顺序已变化，请刷新后重试");
        }
        if (safeActiveSongIds.size() != songIds.size()
                || !new HashSet<>(safeActiveSongIds).equals(new HashSet<>(songIds))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排序列表必须包含歌单全部活动歌曲且不能包含外来歌曲");
        }
        playlistSongMapper.updateSortOrdersBatch(playlistId, songIds);
        recordCollaborationOperationIfEnabled(playlistId, userId, "reorder", null,
                "调整了" + songIds.size() + "首歌曲的顺序",
                beforeOrderSummary, playlistOrderSummary(playlistId));

               
        String cacheKey = RedisConstants.PLAYLIST_PREFIX + playlistId;
        redisUtils.delete(cacheKey);

        log.info("用户更新歌单歌曲排序: userId={}, playlistId={}", userId, playlistId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean favoritePlaylist(Long userId, Long playlistId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "收藏歌单");

        Playlist targetPlaylist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(targetPlaylist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        if (String.valueOf(targetPlaylist.getUserId()).equals(String.valueOf(userId))) {
            throw new BusinessException("不能收藏自己的歌单");
        }

        if (CommonConstants.PUBLIC_PRIVATE.equals(targetPlaylist.getIsPublic())) {
            throw new BusinessException("不能收藏私密歌单");
        }
        if (!canPublicCreator(targetPlaylist.getUserId())) {
            throw new BusinessException("歌单创建者账号异常，无法收藏");
        }

        if (playlistFavoriteMapper.selectActive(userId, playlistId) != null) {
                       
            log.info("用户已收藏该歌单: userId={}, playlistId={}", userId, playlistId);
            return true;
        }

        int changed = playlistFavoriteMapper.restoreDeleted(userId, playlistId);
        if (changed <= 0) {
            changed = playlistFavoriteMapper.insertIgnoreActive(userId, playlistId);
        }

        if (changed > 0 && UserAccountStatusUtil.canContributePublicStats(user)) {
            baseMapper.adjustFavoriteCount(playlistId, 1);
            syncPlaylistIndex(playlistId);
        }

        log.info("收藏歌单成功: userId={}, playlistId={}", userId, playlistId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfavoritePlaylist(Long userId, Long playlistId) {
                           
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        Playlist targetPlaylist = baseMapper.selectByIdForUpdate(playlistId);
        if (ObjectUtils.isEmpty(targetPlaylist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

        if (playlistFavoriteMapper.selectActive(userId, playlistId) == null) {
            removeFavoriteGrouping(userId, "playlist", playlistId);
                       
            log.info("用户未收藏该歌单: userId={}, playlistId={}", userId, playlistId);
            return true;
        }

        playlistFavoriteMapper.deleteDeletedHistory(userId, playlistId);
        int changed = playlistFavoriteMapper.logicalDeleteActive(userId, playlistId);
        removeFavoriteGrouping(userId, "playlist", playlistId);

        if (changed > 0 && UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            baseMapper.adjustFavoriteCount(playlistId, -1);
            syncPlaylistIndex(playlistId);
        }

        log.info("取消收藏成功: userId={}, playlistId={}", userId, playlistId);
        return true;
    }

    private void removeFavoriteGrouping(Long userId, String resourceType, Long resourceId) {
        if (favoriteCollectionItemMapper != null) {
            favoriteCollectionItemMapper.deleteResource(userId, resourceType, resourceId);
        }
    }
    public List<PlaylistVO> getHotPlaylists(String type, Integer limit, Long userId) {
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsPublic, CommonConstants.PUBLIC_PUBLIC)
                .eq(Playlist::getType, MusicConstants.PlaylistType.CUSTOM);

                         
                                    
        if (StrUtil.isNotBlank(type) && !"all".equalsIgnoreCase(type)) {
                                     
            wrapper.like(Playlist::getTags, type);
        }

        int actualLimit = limit != null ? limit : 20;
        wrapper.orderByDesc(Playlist::getPlayCount, Playlist::getFavoriteCount)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));

        List<Playlist> playlists = filterPublicCreatorPlaylists(list(wrapper), null, actualLimit);

        if (playlists.isEmpty()) {
            return new ArrayList<>();
        }

                 
        return convertToVOBatch(playlists, userId);
    }

       
                         
      
                            
                           
                   
       
    private List<PlaylistVO> convertToVOBatch(List<Playlist> playlists, Long userId) {
        if (playlists.isEmpty()) {
            return new ArrayList<>();
        }

        List<PlaylistVO> voList = new ArrayList<>();

                      
        Set<Long> creatorIds = playlists.stream()
                .map(Playlist::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = getUserInfos(creatorIds);

                                  
        Set<Long> favoritePlaylistIds = Collections.emptySet();
        if (ObjectUtils.isNotEmpty(userId)) {
            favoritePlaylistIds = getFavoritePlaylistIds(userId, playlists);
        }

        Map<Long, Map<String, Integer>> languageStats = getPlaylistLanguageStats(playlists);

                   
        for (Playlist playlist : playlists) {
            PlaylistVO vo = convertToVO(playlist);

                      
            User creator = userMap.get(playlist.getUserId());
            if (creator != null) {
                vo.setCreatorName(creator.getNickname());
                vo.setCreatorAvatar(creator.getAvatar());
            }

                     
            vo.setIsFavorite(favoritePlaylistIds.contains(playlist.getId()));
            vo.setPrimaryLanguage(playlist.getLanguage());
            Map<String, Integer> counts = languageStats.getOrDefault(playlist.getId(), Collections.emptyMap());
            vo.setLanguageCounts(counts);
            vo.setContentLanguages(new ArrayList<>(counts.keySet()));

            voList.add(vo);
        }

        return voList;
    }

    private Map<Long, Map<String, Integer>> getPlaylistLanguageStats(List<Playlist> playlists) {
        List<Long> ids = playlists.stream().map(Playlist::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, Map<String, Integer>> result = new HashMap<>();
        List<Map<String, Object>> rows = baseMapper.selectLanguageStats(ids);
        if (rows == null) return Collections.emptyMap();
        for (Map<String, Object> row : rows) {
            Object playlistIdValue = row.get("playlistId");
            Object languageValue = row.get("language");
            if (!(playlistIdValue instanceof Number) || languageValue == null || StrUtil.isBlank(languageValue.toString())) continue;
            Long playlistId = ((Number) playlistIdValue).longValue();
            int count = row.get("songCount") instanceof Number ? ((Number) row.get("songCount")).intValue() : 0;
            result.computeIfAbsent(playlistId, ignored -> new java.util.LinkedHashMap<>())
                    .put(languageValue.toString(), count);
        }
        return result;
    }

    private void applyVisibleSongCounts(List<PlaylistVO> playlists) {
        if (playlists == null || playlists.isEmpty()) return;
        List<Long> playlistIds = playlists.stream()
                .map(PlaylistVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (playlistIds.isEmpty()) return;

        Map<Long, Integer> counts = new HashMap<>();
        List<Map<String, Object>> rows = baseMapper.selectVisibleSongCounts(playlistIds);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object playlistId = row.get("playlistId");
                Object songCount = row.get("songCount");
                if (playlistId instanceof Number && songCount instanceof Number) {
                    counts.put(((Number) playlistId).longValue(),
                            (int) Math.min(Integer.MAX_VALUE, Math.max(0L, ((Number) songCount).longValue())));
                }
            }
        }
        playlists.forEach(playlist -> playlist.setSongCount(counts.getOrDefault(playlist.getId(), 0)));
    }

       
                         
      
                                 
                           
                     
       
    private IPage<PlaylistVO> convertToVOBatch(IPage<Playlist> playlistPage, Long userId) {
        List<Playlist> playlists = playlistPage.getRecords();
        if (playlists.isEmpty()) {
            return playlistPage.convert(playlist -> new PlaylistVO());
        }

        List<PlaylistVO> voList = convertToVOBatch(playlists, userId);

        Page<PlaylistVO> voPage = new Page<>(playlistPage.getCurrent(), playlistPage.getSize(), playlistPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    private int numberValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private java.math.BigDecimal decimalValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) value;
        }
        if (value instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return java.math.BigDecimal.ZERO;
    }

       
               
      
                            
                              
       
    private Map<Long, User> getUserInfos(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds)
                    .select(User::getId, User::getNickname, User::getAvatar,
                            User::getStatus, User::getDeleted, User::getIsBanned,
                            User::getUserType, User::getRiskScore, User::getCreditScore,
                            User::getCreatorStatus);

            List<User> users = userMapper.selectList(wrapper);
            return users.stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}", userIds);
            return Collections.emptyMap();
        }
    }

    private boolean canExposePlaylistToViewer(Playlist playlist, Long viewerId) {
        if (playlist == null || playlist.getUserId() == null) {
            return false;
        }
        if (viewerId != null && viewerId.equals(playlist.getUserId())) {
            return true;
        }
        if (!CommonConstants.PUBLIC_PUBLIC.equals(playlist.getIsPublic())) {
            return false;
        }
        return canPublicCreator(playlist.getUserId());
    }

    private boolean canPublicCreator(Long userId) {
        if (userId == null) {
            return false;
        }
        return UserAccountStatusUtil.canRetainPublicContent(userId, userMapper::selectById);
    }

       
                                    
       
    private List<Song> filterPublicSongs(Collection<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<User> uploaders = uploaderIds.isEmpty()
                ? Collections.emptyList()
                : userMapper.selectBatchIds(uploaderIds);
        Set<Long> publicUploaderIds = uploaders == null
                ? Collections.emptySet()
                : uploaders.stream()
                .filter(UserAccountStatusUtil::canExposePublicContent)
                .map(User::getId)
                .collect(Collectors.toSet());
        return songs.stream()
                .filter(Objects::nonNull)
                .filter(song -> Objects.equals(song.getStatus(), CommonConstants.STATUS_NORMAL))
                .filter(song -> Objects.equals(song.getDeleted(), CommonConstants.NOT_DELETED))
                .filter(song -> song.getUploaderId() == null || publicUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

       
                           
       
    private Set<Long> selectPublicSongIds(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptySet();
        }
        return filterPublicSongs(songMapper.selectBatchIds(songIds)).stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
    }

       
                            
       
    private List<Long> validatePlaylistMutationSongIds(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty() || songIds.size() > 200
                || songIds.stream().anyMatch(songId -> songId == null || songId <= 0L)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID列表不合法或超过200项");
        }
        List<Long> distinctSongIds = songIds.stream().distinct().collect(Collectors.toList());
        if (distinctSongIds.size() != songIds.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能重复");
        }
        return distinctSongIds;
    }

       
                              
       
    private void validatePlaylistVisibility(Integer visibility) {
        if (visibility != null
                && !CommonConstants.PUBLIC_PRIVATE.equals(visibility)
                && !CommonConstants.PUBLIC_PUBLIC.equals(visibility)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "公开性参数不合法");
        }
    }

       
                       
       
    private List<String> normalizePlaylistTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        if (tags.size() > 20) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单标签不能超过20个");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim();
            if (value.isEmpty() || value.length() > 20) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "歌单标签不合法");
            }
            SecurityCheckUtil.CheckResult check = SecurityCheckUtil.checkName(value, "歌单标签");
            if (!check.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, check.getMessage());
            }
            normalized.add(check.getCleanedValue());
        }
        return new ArrayList<>(normalized);
    }

       
                                         
       
    private void validatePlaylistCover(String cover) {
        if (StrUtil.isBlank(cover)) {
            return;
        }
        String value = cover.trim();
        if (value.length() > 500 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单封面地址不合法");
        }
        if (value.startsWith("/") && !value.startsWith("//")) {
            return;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("invalid cover URL");
            }
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单封面地址不合法");
        }
    }

    private List<Playlist> filterPublicCreatorPlaylists(List<Playlist> playlists, Long keepOwnerId, int limit) {
        if (playlists == null || playlists.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> creatorIds = playlists.stream()
                .map(Playlist::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = getUserInfos(creatorIds);

        java.util.stream.Stream<Playlist> stream = playlists.stream()
                .filter(playlist -> keepOwnerId != null && keepOwnerId.equals(playlist.getUserId())
                        || UserAccountStatusUtil.canRetainPublicContent(userMap.get(playlist.getUserId())));
        if (limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.collect(Collectors.toList());
    }

    private int expandedPublicQueryLimit(int limit) {
        return limit > 0 ? limit * 3 : limit;
    }

       
                      
      
                         
                            
                        
       
    private Set<Long> getFavoritePlaylistIds(Long userId, List<Playlist> playlists) {
        if (userId == null || playlists == null || playlists.isEmpty()) {
            return Collections.emptySet();
        }

                       
        Set<Long> playlistIds = playlists.stream()
                .map(Playlist::getId)
                .collect(Collectors.toSet());

        try {
                                                              
            LambdaQueryWrapper<PlaylistFavorite> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PlaylistFavorite::getUserId, userId)
                    .in(PlaylistFavorite::getPlaylistId, playlistIds)
                    .eq(PlaylistFavorite::getDeleted, 0)                  
                    .select(PlaylistFavorite::getPlaylistId);

            List<PlaylistFavorite> favoriteList = playlistFavoriteMapper.selectList(wrapper);
            return favoriteList.stream()
                    .map(PlaylistFavorite::getPlaylistId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("批量获取收藏歌单ID失败: userId={}, playlists={}", userId, playlists.size());
            return Collections.emptySet();
        }
    }

       
                      
      
                         
                        
                        
       
    private Set<Long> getFavoriteSongIds(Long userId, List<Song> songs) {
        if (userId == null || songs == null || songs.isEmpty()) {
            return Collections.emptySet();
        }

        try {
                     
            List<Long> songIds = songs.stream()
                    .map(Song::getId)
                    .collect(Collectors.toList());

                                                   
            LambdaQueryWrapper<com.haoran.music.entity.SongLike> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(com.haoran.music.entity.SongLike::getUserId, userId)
                    .eq(com.haoran.music.entity.SongLike::getIsFavorite, 1)
                    .in(com.haoran.music.entity.SongLike::getSongId, songIds)
                    .select(com.haoran.music.entity.SongLike::getSongId);

            List<com.haoran.music.entity.SongLike> songLikes = songLikeMapper.selectList(wrapper);
            return songLikes.stream()
                    .map(com.haoran.music.entity.SongLike::getSongId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("批量获取收藏歌曲ID失败: userId={}, songs={}", userId, songs.size());
            return Collections.emptySet();
        }
    }

       
               
      
                             
                             
                                 
       
    private Boolean checkIsFavorite(Long userId, Long playlistId) {
                                            
        LambdaQueryWrapper<PlaylistFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlaylistFavorite::getUserId, userId)
                .eq(PlaylistFavorite::getPlaylistId, playlistId)
                .eq(PlaylistFavorite::getDeleted, 0);                  

        PlaylistFavorite favorite = playlistFavoriteMapper.selectOne(wrapper);
        return favorite != null;
    }

       
                          
      
                      
                                    
       
    private boolean isFavoritePlaylistType(Playlist playlist) {
        return playlist != null && MusicConstants.PlaylistType.FAVORITE.equals(playlist.getType());
    }

       
                  
                                                
                                                              
      
                    
                         
                                 
       
    public Boolean checkSongIsFavorited(Long userId, Long songId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }

                      
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getUserId, userId)
                .eq(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .select(Playlist::getId);

        Playlist favoritePlaylist = getOne(wrapper);
        if (ObjectUtils.isEmpty(favoritePlaylist)) {
            return false;
        }

                       
        LambdaQueryWrapper<PlaylistSong> songWrapper = new LambdaQueryWrapper<>();
        songWrapper.eq(PlaylistSong::getPlaylistId, favoritePlaylist.getId())
                .eq(PlaylistSong::getSongId, songId)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);

        Long count = playlistSongMapper.selectCount(songWrapper);
        return count != null && count > 0;
    }


       
           
      
                            
                            
                            
       
    private void handleSort(LambdaQueryWrapper<Playlist> wrapper, String sortField, String sortOrder) {
        if (StrUtil.isBlank(sortField)) {
            wrapper.orderByDesc(Playlist::getCreateTime)
                    .orderByDesc(Playlist::getId);
            return;
        }

        boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);

        switch (sortField) {
            case "time":
                wrapper.orderBy(true, isAsc, Playlist::getCreateTime)
                        .orderBy(true, isAsc, Playlist::getId);
                break;
            case "hot":
                wrapper.orderBy(true, isAsc, Playlist::getPlayCount)
                        .orderBy(true, isAsc, Playlist::getFavoriteCount)
                        .orderBy(true, isAsc, Playlist::getCreateTime)
                        .orderBy(true, isAsc, Playlist::getId);
                break;
            case "name":
                wrapper.orderBy(true, isAsc, Playlist::getName)
                        .orderBy(true, isAsc, Playlist::getId);
                break;
            case "favoriteTime":
                wrapper.orderBy(true, isAsc, Playlist::getUpdateTime)
                        .orderBy(true, isAsc, Playlist::getId);
                break;
            default:
                wrapper.orderByDesc(Playlist::getCreateTime)
                        .orderByDesc(Playlist::getId);
                break;
        }
    }

       
                             
       
                            
                           
                       
       
       
                
                     
      
                       
                                         
                       
       
    public List<Playlist> filterPlaylistsByLanguage(List<Playlist> playlists, String language) {
        if (playlists == null || playlists.isEmpty() || StrUtil.isBlank(language)) {
            return playlists;
        }

        List<Playlist> filtered = new ArrayList<>();
        for (Playlist playlist : playlists) {
                      
            LambdaQueryWrapper<PlaylistSong> psWrapper = new LambdaQueryWrapper<>();
            psWrapper.eq(PlaylistSong::getPlaylistId, playlist.getId())
                    .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                    .select(PlaylistSong::getSongId);

            List<PlaylistSong> playlistSongs = playlistSongMapper.selectList(psWrapper);
            if (playlistSongs.isEmpty()) {
                continue;
            }

            List<Long> songIds = playlistSongs.stream()
                    .map(PlaylistSong::getSongId)
                    .collect(Collectors.toList());

                     
            LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<>();
            songWrapper.in(Song::getId, songIds)
                    .eq(Song::getLanguage, language)
                    .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                    .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                    .last("LIMIT 1");

            Long count = songMapper.selectCount(songWrapper);
            if (count != null && count > 0) {
                filtered.add(playlist);
            }
        }
        return filtered;
    }


       
                 
      
                               
                                    
                                     
                                     
                      
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistCopyMoveResult copySongsToPlaylist(Long userId, Long sourcePlaylistId, List<Long> songIds, Long targetPlaylistId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(401, "用户未登录");
        }
        if (ObjectUtils.isEmpty(sourcePlaylistId) || ObjectUtils.isEmpty(targetPlaylistId)) {
            throw new BusinessException(400, "歌单ID不能为空");
        }
        if (ObjectUtils.isEmpty(songIds) || songIds.isEmpty()) {
            throw new BusinessException(400, "歌曲ID列表不能为空");
        }
        if (sourcePlaylistId.equals(targetPlaylistId)) {
            throw new BusinessException(400, "源歌单和目标歌单不能相同");
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "复制歌单歌曲");
        List<Long> distinctSongIds = validatePlaylistMutationSongIds(songIds);

        Map<Long, Playlist> lockedPlaylists = lockPlaylistsForUpdate(sourcePlaylistId, targetPlaylistId);
        Playlist sourcePlaylist = lockedPlaylists.get(sourcePlaylistId);
        Playlist targetPlaylist = lockedPlaylists.get(targetPlaylistId);
        requirePlaylistReadable(sourcePlaylist, userId, "无权操作源歌单");
        requirePlaylistPermission(targetPlaylist, userId, "add", "无权操作目标歌单");

                      
        LambdaQueryWrapper<PlaylistSong> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(PlaylistSong::getPlaylistId, sourcePlaylistId)
                .in(PlaylistSong::getSongId, distinctSongIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);
        List<PlaylistSong> existingSongs = playlistSongMapper.selectList(sourceWrapper);
        Set<Long> deliverableSongIds = selectPublicSongIds(
                existingSongs.stream().map(PlaylistSong::getSongId).collect(Collectors.toList()));
        existingSongs = existingSongs.stream()
                .filter(relation -> deliverableSongIds.contains(relation.getSongId()))
                .collect(Collectors.toList());

                  
        PlaylistCopyMoveResult result = new PlaylistCopyMoveResult();
        result.setTotalRequested(distinctSongIds.size());

        if (existingSongs.isEmpty()) {
            result.setDuplicateCount(0);
            result.setActualCount(0);
            return result;
        }

        Integer maxSortOrder = playlistSongMapper.selectMaxSortOrder(targetPlaylistId);
        int nextSortOrder = maxSortOrder != null ? maxSortOrder + 1 : 0;

                        
        LambdaQueryWrapper<PlaylistSong> targetWrapper = new LambdaQueryWrapper<>();
        targetWrapper.eq(PlaylistSong::getPlaylistId, targetPlaylistId)
                .in(PlaylistSong::getSongId, distinctSongIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .select(PlaylistSong::getSongId);
        List<PlaylistSong> targetExistingSongs = playlistSongMapper.selectList(targetWrapper);
        Set<Long> targetExistingSongIds = targetExistingSongs.stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());

                 
        int duplicateCount = 0;
        List<PlaylistSong> songsToAdd = new ArrayList<>();
        for (PlaylistSong ps : existingSongs) {
            if (targetExistingSongIds.contains(ps.getSongId())) {
                duplicateCount++;
            } else {
                PlaylistSong newPs = new PlaylistSong();
                newPs.setPlaylistId(targetPlaylistId);
                newPs.setSongId(ps.getSongId());
                newPs.setSortOrder(nextSortOrder++);
                songsToAdd.add(newPs);
            }
        }

        if (songsToAdd.isEmpty()) {
            result.setDuplicateCount(duplicateCount);
            result.setActualCount(0);
            return result;
        }

        long targetSongCount = targetPlaylist.getSongCount() != null ? targetPlaylist.getSongCount() : 0L;
        if (!isFavoritePlaylistType(targetPlaylist)
                && targetSongCount + songsToAdd.size() > CommonConstants.PLAYLIST_MAX_SONGS) {
            throw new BusinessException("歌单歌曲数量已达上限（" + CommonConstants.PLAYLIST_MAX_SONGS + "首）");
        }

        int addedCount = 0;
        playlistSongMapper.deleteDeletedHistoryBySongIds(targetPlaylistId,
                songsToAdd.stream().map(PlaylistSong::getSongId).collect(Collectors.toList()));
        for (PlaylistSong ps : songsToAdd) {
            ps.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            addedCount += playlistSongMapper.insertIgnore(ps);
        }

        if (addedCount > 0) {
            baseMapper.adjustSongCount(targetPlaylistId, addedCount);
            syncPlaylistIndex(targetPlaylistId);
            redisUtils.delete(RedisConstants.PLAYLIST_PREFIX + targetPlaylistId);
        }

        result.setDuplicateCount(duplicateCount);
        result.setActualCount(addedCount);
        return result;
    }

       
                                 
      
                               
                                    
                                     
                                     
                      
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaylistCopyMoveResult moveSongsToPlaylist(Long userId, Long sourcePlaylistId, List<Long> songIds, Long targetPlaylistId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(401, "用户未登录");
        }
        if (ObjectUtils.isEmpty(sourcePlaylistId) || ObjectUtils.isEmpty(targetPlaylistId)) {
            throw new BusinessException(400, "歌单ID不能为空");
        }
        if (ObjectUtils.isEmpty(songIds) || songIds.isEmpty()) {
            throw new BusinessException(400, "歌曲ID列表不能为空");
        }
        if (sourcePlaylistId.equals(targetPlaylistId)) {
            throw new BusinessException(400, "源歌单和目标歌单不能相同");
        }
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "移动歌单歌曲");
        List<Long> distinctSongIds = validatePlaylistMutationSongIds(songIds);

        Map<Long, Playlist> lockedPlaylists = lockPlaylistsForUpdate(sourcePlaylistId, targetPlaylistId);
        Playlist sourcePlaylist = lockedPlaylists.get(sourcePlaylistId);
        Playlist targetPlaylist = lockedPlaylists.get(targetPlaylistId);
        requirePlaylistPermission(sourcePlaylist, userId, "remove", "无权操作源歌单");
        requirePlaylistPermission(targetPlaylist, userId, "add", "无权操作目标歌单");

                      
        LambdaQueryWrapper<PlaylistSong> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(PlaylistSong::getPlaylistId, sourcePlaylistId)
                .in(PlaylistSong::getSongId, distinctSongIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED);
        List<PlaylistSong> existingSongs = playlistSongMapper.selectList(sourceWrapper);
        Set<Long> deliverableSongIds = selectPublicSongIds(
                existingSongs.stream().map(PlaylistSong::getSongId).collect(Collectors.toList()));
        existingSongs = existingSongs.stream()
                .filter(relation -> deliverableSongIds.contains(relation.getSongId()))
                .collect(Collectors.toList());

                  
        PlaylistCopyMoveResult result = new PlaylistCopyMoveResult();
        result.setTotalRequested(distinctSongIds.size());

        if (existingSongs.isEmpty()) {
            result.setDuplicateCount(0);
            result.setActualCount(0);
            return result;
        }

        Integer maxSortOrder = playlistSongMapper.selectMaxSortOrder(targetPlaylistId);
        int nextSortOrder = maxSortOrder != null ? maxSortOrder + 1 : 0;

                        
        LambdaQueryWrapper<PlaylistSong> targetWrapper = new LambdaQueryWrapper<>();
        targetWrapper.eq(PlaylistSong::getPlaylistId, targetPlaylistId)
                .in(PlaylistSong::getSongId, distinctSongIds)
                .eq(PlaylistSong::getDeleted, CommonConstants.NOT_DELETED)
                .select(PlaylistSong::getSongId);
        List<PlaylistSong> targetExistingSongs = playlistSongMapper.selectList(targetWrapper);
        Set<Long> targetExistingSongIds = targetExistingSongs.stream()
                .map(PlaylistSong::getSongId)
                .collect(Collectors.toSet());

                        
        int duplicateCount = 0;
        List<PlaylistSong> songsToAdd = new ArrayList<>();
        Map<Long, Long> sourceRowIdBySongId = new HashMap<>();

        for (PlaylistSong ps : existingSongs) {
            if (targetExistingSongIds.contains(ps.getSongId())) {
                duplicateCount++;
            } else {
                          
                PlaylistSong newPs = new PlaylistSong();
                newPs.setPlaylistId(targetPlaylistId);
                newPs.setSongId(ps.getSongId());
                newPs.setSortOrder(nextSortOrder++);
                songsToAdd.add(newPs);
                         
                sourceRowIdBySongId.put(ps.getSongId(), ps.getId());
            }
        }

        if (songsToAdd.isEmpty()) {
            result.setDuplicateCount(duplicateCount);
            result.setActualCount(0);
            return result;
        }

        long targetSongCount = targetPlaylist.getSongCount() != null ? targetPlaylist.getSongCount() : 0L;
        if (!isFavoritePlaylistType(targetPlaylist)
                && targetSongCount + songsToAdd.size() > CommonConstants.PLAYLIST_MAX_SONGS) {
            throw new BusinessException("歌单歌曲数量已达上限（" + CommonConstants.PLAYLIST_MAX_SONGS + "首）");
        }

        int addedCount = 0;
        List<Long> sourceRowIdsToDelete = new ArrayList<>();
        List<Long> sourceSongIdsToDelete = new ArrayList<>();
        playlistSongMapper.deleteDeletedHistoryBySongIds(targetPlaylistId,
                songsToAdd.stream().map(PlaylistSong::getSongId).collect(Collectors.toList()));
        for (PlaylistSong ps : songsToAdd) {
            ps.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            int inserted = playlistSongMapper.insertIgnore(ps);
            if (inserted > 0) {
                addedCount += inserted;
                Long sourceRowId = sourceRowIdBySongId.get(ps.getSongId());
                if (sourceRowId != null) {
                    sourceRowIdsToDelete.add(sourceRowId);
                    sourceSongIdsToDelete.add(ps.getSongId());
                }
            }
        }

        int deletedCount = 0;
        if (!sourceRowIdsToDelete.isEmpty()) {
            playlistSongMapper.deleteDeletedHistoryBySongIds(sourcePlaylistId, sourceSongIdsToDelete);
            deletedCount = playlistSongMapper.deleteActiveByIds(sourceRowIdsToDelete);
        }
        if (deletedCount > 0) {
            baseMapper.adjustSongCount(sourcePlaylistId, -deletedCount);
            syncPlaylistIndex(sourcePlaylistId);
            redisUtils.delete(RedisConstants.PLAYLIST_PREFIX + sourcePlaylistId);
        }
        if (addedCount > 0) {
            baseMapper.adjustSongCount(targetPlaylistId, addedCount);
            syncPlaylistIndex(targetPlaylistId);
            redisUtils.delete(RedisConstants.PLAYLIST_PREFIX + targetPlaylistId);
        }

        result.setDuplicateCount(duplicateCount);
        result.setActualCount(addedCount);
        return result;
    }

       
                   
      
                           
                           
                   
       
    private PlaylistVO convertToVOWithUser(Playlist playlist, Long userId) {
        PlaylistVO vo = convertToVO(playlist);

                                                   
        if (ObjectUtils.isNotEmpty(playlist.getUserId())) {
            User creator = userMapper.selectById(playlist.getUserId());
            if (ObjectUtils.isNotEmpty(creator)) {
                vo.setCreatorName(StrUtil.blankToDefault(creator.getNickname(), creator.getUsername()));
                vo.setCreatorAvatar(creator.getAvatar());
            }
        }

        if (ObjectUtils.isNotEmpty(userId)) {
                     
            vo.setIsFavorite(checkIsFavorite(userId, playlist.getId()));
        }

        return vo;
    }

       
            
      
                           
                   
       
       
                            
                           
                   
       
    private PlaylistVO convertToVO(Playlist playlist) {
        PlaylistVO vo = BeanUtil.copyProperties(playlist, PlaylistVO.class);
                                        
        if (ObjectUtils.isEmpty(vo.getCover())) {
            vo.setCover("/images/default-cover.png");
        }
        return vo;
    }

       
                           
      
                         
                      
       
    @Override
    public List<PlaylistVO> getFavoritePlaylists(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

                      
        LambdaQueryWrapper<PlaylistFavorite> favWrapper = new LambdaQueryWrapper<>();
        favWrapper.eq(PlaylistFavorite::getUserId, userId)
                .eq(PlaylistFavorite::getDeleted, 0)                  
                .orderByDesc(PlaylistFavorite::getCreateTime);

        List<PlaylistFavorite> favorites = playlistFavoriteMapper.selectList(favWrapper);
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }

                 
        List<Long> playlistIds = favorites.stream()
                .map(PlaylistFavorite::getPlaylistId)
                .collect(Collectors.toList());

                 
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Playlist::getId, playlistIds)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);

        List<Playlist> playlists = list(wrapper);
        if (playlists.isEmpty()) {
            return new ArrayList<>();
        }

                
        return convertToVOBatch(playlists, userId);
    }

       
                           
      
                         
                              
       
    @Override
    public List<PlaylistVO> getAllUserPlaylists(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        List<PlaylistVO> result = new ArrayList<>();

                       
        List<PlaylistVO> myPlaylists = getUserPlaylists(userId);
        result.addAll(myPlaylists);

                       
        List<PlaylistVO> favoritePlaylists = getFavoritePlaylists(userId);
        result.addAll(favoritePlaylists);

        return result;
    }



       
             
      
                        
                     
       
    @Override
    public List<PlaylistVO> getFeaturedPlaylists(Integer limit) {
        int actualLimit = limit != null ? limit : 10;
        
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsFeatured, 1)            
                .orderByDesc(Playlist::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Playlist> playlists = filterPublicCreatorPlaylists(list(wrapper), null, actualLimit);
        return convertToVOBatch(playlists, null);
    }

       
                 
      
                         
                        
                   
       
    @Override
    public List<PlaylistVO> getPlaylistsByCategory(String category, Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);
        
        if (StrUtil.isNotBlank(category)) {
            wrapper.like(Playlist::getTags, category);
        }
        
        wrapper.orderByDesc(Playlist::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Playlist> playlists = filterPublicCreatorPlaylists(list(wrapper), null, actualLimit);
        return convertToVOBatch(playlists, null);
    }

       
               
      
                   
       
    @Override
    public List<String> getPlaylistCategories() {
                     
        List<String> categories = new ArrayList<>();
        categories.add("流行");
        categories.add("摇滚");
        categories.add("民谣");
        categories.add("电子");
        categories.add("说唱");
        categories.add("爵士");
        categories.add("古典");
        categories.add("轻音乐");
        categories.add("影视原声");
        categories.add("ACG");
        categories.add("欧美");
        categories.add("日语");
        categories.add("韩语");
        categories.add("粤语");
        return categories;
    }

       
           
      
                         
                            
                   
       
    @Override
    public IPage<PlaylistVO> searchPlaylists(String keyword, PageQuery pageQuery) {
        Page<Playlist> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED);
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(Playlist::getName, keyword)
                              .or()
                              .like(Playlist::getDescription, keyword));
        }
        
        wrapper.orderByDesc(Playlist::getCreateTime);
        
        IPage<Playlist> playlistPage = page(page, wrapper);
        playlistPage.setRecords(filterPublicCreatorPlaylists(playlistPage.getRecords(), null, 0));
        return convertToVOBatch(playlistPage, null);
    }

       
             
      
                        
                     
       
    @Override
    public List<PlaylistVO> getLatestPlaylists(Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsPublic, CommonConstants.YES)
                .orderByDesc(Playlist::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Playlist> playlists = filterPublicCreatorPlaylists(list(wrapper), null, actualLimit);
        return convertToVOBatch(playlists, null);
    }

       
                
      
                        
                        
       
    @Override
    public List<PlaylistVO> getUserCreatedPlaylists(Integer limit) {
        int actualLimit = limit != null ? limit : 50;
        
        LambdaQueryWrapper<Playlist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Playlist::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Playlist::getDeleted, CommonConstants.NOT_DELETED)
                .eq(Playlist::getIsPublic, CommonConstants.YES)
                .ne(Playlist::getType, MusicConstants.PlaylistType.FAVORITE)
                .orderByDesc(Playlist::getCreateTime)
                .last("LIMIT " + expandedPublicQueryLimit(actualLimit));
        
        List<Playlist> playlists = filterPublicCreatorPlaylists(list(wrapper), null, actualLimit);
        return convertToVOBatch(playlists, null);
    }
       
               
       
    @Override
    public com.haoran.music.vo.playlist.PlaylistSubscriptionDataVO getSubscriptionData(Long playlistId, Long userId) {
        com.haoran.music.vo.playlist.PlaylistSubscriptionDataVO vo = new com.haoran.music.vo.playlist.PlaylistSubscriptionDataVO();

                   
        Playlist playlist = getById(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

                             
        if (ObjectUtils.isEmpty(userId) || !userId.equals(playlist.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有歌单创建者可以查看订阅数据");
        }

        vo.setPlaylistId(playlistId);
        vo.setPlaylistName(playlist.getName());

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startDate = now.toLocalDate().minusDays(29).atStartOfDay();
        java.time.LocalDateTime endDate = now.toLocalDate().plusDays(1).atStartOfDay();
        java.time.LocalDateTime thirtyDaysAgo = now.minusDays(30);

        Map<String, Object> statistics = playlistSubscribeMapper
                .selectSubscriptionStatistics(playlistId, thirtyDaysAgo);
        int totalSubscribers = numberValue(statistics, "totalSubscribers");
        int activeSubscribers = numberValue(statistics, "activeSubscribers");
        vo.setTotalSubscribers(totalSubscribers);
        vo.setActiveSubscribers(activeSubscribers);
        vo.setMonthlyRevenue(decimalValue(statistics, "monthlyRevenue"));
        vo.setTotalRevenue(decimalValue(statistics, "totalRevenue"));

        Map<String, Integer> growthByDate = new HashMap<>();
        for (Map<String, Object> row : playlistSubscribeMapper
                .selectSubscriberGrowth(playlistId, startDate, endDate)) {
            growthByDate.put(String.valueOf(row.get("growthDate")),
                    numberValue(row, "subscriberCount"));
        }

        List<Integer> subscriberGrowth = new ArrayList<>(30);
        for (int i = 29; i >= 0; i--) {
            subscriberGrowth.add(growthByDate.getOrDefault(
                    now.toLocalDate().minusDays(i).toString(), 0));
        }
        vo.setSubscriberGrowth(subscriberGrowth);

        log.info("获取歌单订阅数据: playlistId={}, totalSubscribers={}, activeSubscribers={}",
            playlistId, totalSubscribers, activeSubscribers);

        return vo;
    }

       
                
       
    @Override
    public java.util.List<com.haoran.music.vo.playlist.PlaylistSubscriberVO> getSubscribers(Long playlistId, Long userId, Integer limit) {
                   
        Playlist playlist = getById(playlistId);
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
        }

                              
        if (ObjectUtils.isEmpty(userId) || !userId.equals(playlist.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有歌单创建者可以查看订阅者列表");
        }

                 
        if (limit == null || limit <= 0 || limit > 100) {
            limit = 20;
        }

                 
        LambdaQueryWrapper<com.haoran.music.entity.PlaylistSubscribe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.PlaylistSubscribe::getPlaylistId, playlistId)
                .orderByDesc(com.haoran.music.entity.PlaylistSubscribe::getStartTime)
                .last("LIMIT " + limit);

        List<com.haoran.music.entity.PlaylistSubscribe> subscribes = playlistSubscribeMapper.selectList(wrapper);

        Set<Long> subscriberIds = subscribes.stream()
                .map(com.haoran.music.entity.PlaylistSubscribe::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> subscriberMap = getUserInfos(subscriberIds);

                 
        java.util.List<com.haoran.music.vo.playlist.PlaylistSubscriberVO> result = new java.util.ArrayList<>();
        for (com.haoran.music.entity.PlaylistSubscribe subscribe : subscribes) {
            com.haoran.music.vo.playlist.PlaylistSubscriberVO vo = new com.haoran.music.vo.playlist.PlaylistSubscriberVO();
            vo.setId(subscribe.getId());
            vo.setUserId(subscribe.getUserId());
            vo.setSubscribeType(subscribe.getSubscribeType());
            vo.setPrice(subscribe.getAmount() != null ? subscribe.getAmount().multiply(new java.math.BigDecimal("100")).intValue() : 0);

                            
            User user = subscriberMap.get(subscribe.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }

                   
            if (subscribe.getStartTime() != null) {
                vo.setSubscribeTime(java.sql.Timestamp.valueOf(subscribe.getStartTime()));
            }
            if (subscribe.getEndTime() != null) {
                vo.setExpireTime(java.sql.Timestamp.valueOf(subscribe.getEndTime()));
                         
                vo.setStatus(subscribe.getEndTime().isAfter(java.time.LocalDateTime.now()) ? "active" : "expired");
            } else {
                vo.setStatus("active");
            }

            result.add(vo);
        }

        log.info("获取歌单订阅者列表: playlistId={}, count={}", playlistId, result.size());

        return result;
    }

       
               
      
                         
                      
                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updatePaidSettings(Long userId, com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        if (ObjectUtils.isEmpty(dto) || ObjectUtils.isEmpty(dto.getPlaylistId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单ID不能为空");
        }

        creatorEligibilityService.requireEligible(userId, "更新付费歌单设置");

        Playlist playlist = baseMapper.selectByIdForUpdate(dto.getPlaylistId());
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "歌单不存在");
        }
        if (!Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能更新自己创建的歌单付费设置");
        }
        if (playlist.getIsPaid() == null || playlist.getIsPaid() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该歌单不是付费歌单，请先设为付费");
        }

        validateMonthlyPrice(dto.getMonthlyPrice());

        com.haoran.music.entity.PaidResource paidResource = paidResourceMapper.selectOne(
            new LambdaQueryWrapper<com.haoran.music.entity.PaidResource>()
                .eq(com.haoran.music.entity.PaidResource::getResourceType, "playlist")
                .eq(com.haoran.music.entity.PaidResource::getResourceId, dto.getPlaylistId())
                .eq(com.haoran.music.entity.PaidResource::getOwnerId, userId)
                .eq(com.haoran.music.entity.PaidResource::getStatus, "approved")
                .eq(com.haoran.music.entity.PaidResource::getIsEnabled, 1)
                .last("LIMIT 1")
        );
        if (paidResource == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "生效中的付费配置不存在，请刷新后重试");
        }
        if ("pending".equals(paidResource.getChangeReviewStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "付费配置变更正在审核中，暂不能覆盖");
        }

        java.math.BigDecimal monthlyPrice = new java.math.BigDecimal(dto.getMonthlyPrice());
        if (paidResource.getPrice() != null
                && paidResource.getPrice().compareTo(monthlyPrice) == 0
                && Integer.valueOf(30).equals(paidResource.getSubscribePeriod())) {
            log.info("event=playlist_paid_change_unchanged playlistId={} userId={}",
                    dto.getPlaylistId(), userId);
            return true;
        }
        int paidUpdated = paidResourceMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.haoran.music.entity.PaidResource>()
                        .eq(com.haoran.music.entity.PaidResource::getId, paidResource.getId())
                        .eq(com.haoran.music.entity.PaidResource::getOwnerId, userId)
                        .eq(com.haoran.music.entity.PaidResource::getStatus, "approved")
                        .eq(com.haoran.music.entity.PaidResource::getIsEnabled, 1)
                        .and(condition -> condition
                                .isNull(com.haoran.music.entity.PaidResource::getChangeReviewStatus)
                                .or()
                                .ne(com.haoran.music.entity.PaidResource::getChangeReviewStatus, "pending"))
                        .set(com.haoran.music.entity.PaidResource::getCandidatePrice, monthlyPrice)
                        .set(com.haoran.music.entity.PaidResource::getCandidateSubscribePeriod, 30)
                        .set(com.haoran.music.entity.PaidResource::getCandidateChangeType, "price_update")
                        .set(com.haoran.music.entity.PaidResource::getCandidateChangeReason,
                                "创作者更新付费歌单设置")
                        .set(com.haoran.music.entity.PaidResource::getChangeReviewStatus, "pending")
                        .setSql("candidate_submitted_at = NOW()")
                        .set(com.haoran.music.entity.PaidResource::getCandidateReviewerId, null)
                        .set(com.haoran.music.entity.PaidResource::getCandidateReviewTime, null)
                        .set(com.haoran.music.entity.PaidResource::getCandidateReviewReason, null));
        if (paidUpdated != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "付费配置状态已变化，请刷新后重试");
        }

        log.info("event=playlist_paid_change_submitted playlistId={} userId={} candidatePrice={}",
            dto.getPlaylistId(), userId, dto.getMonthlyPrice());

        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean setPlaylistPaid(Long userId, com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        if (ObjectUtils.isEmpty(dto) || ObjectUtils.isEmpty(dto.getPlaylistId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌单ID不能为空");
        }

        creatorEligibilityService.requireEligible(userId, "设置付费歌单");

        Playlist playlist = baseMapper.selectByIdForUpdate(dto.getPlaylistId());
        if (ObjectUtils.isEmpty(playlist)) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST, "歌单不存在");
        }
        if (!Objects.equals(playlist.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能设置自己创建的歌单为付费");
        }
        if (playlist.getType() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "我喜爱的音乐歌单不能设置付费");
        }
        if (playlist.getIsPaid() != null && playlist.getIsPaid() == 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该歌单已经是付费状态，请使用更新接口");
        }

        validateMonthlyPrice(dto.getMonthlyPrice());

        com.haoran.music.entity.PaidResource paidResource = paidResourceMapper.selectOne(
                new LambdaQueryWrapper<com.haoran.music.entity.PaidResource>()
                        .eq(com.haoran.music.entity.PaidResource::getResourceType, "playlist")
                        .eq(com.haoran.music.entity.PaidResource::getResourceId, dto.getPlaylistId())
                        .last("LIMIT 1"));
        java.math.BigDecimal monthlyPrice = new java.math.BigDecimal(dto.getMonthlyPrice());
        if (paidResource != null) {
            if (!Objects.equals(paidResource.getOwnerId(), userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "付费配置归属异常，无法重新提交");
            }
            if ("pending".equals(paidResource.getStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该歌单的付费申请正在审核中");
            }
            if ("approved".equals(paidResource.getStatus())
                    && Integer.valueOf(1).equals(paidResource.getIsEnabled())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "该歌单已有生效中的付费配置");
            }
            int updated = paidResourceMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.haoran.music.entity.PaidResource>()
                            .eq(com.haoran.music.entity.PaidResource::getId, paidResource.getId())
                            .eq(com.haoran.music.entity.PaidResource::getOwnerId, userId)
                            .eq(com.haoran.music.entity.PaidResource::getStatus, paidResource.getStatus())
                            .set(com.haoran.music.entity.PaidResource::getPrice, monthlyPrice)
                            .set(com.haoran.music.entity.PaidResource::getPriceType, "subscription")
                            .set(com.haoran.music.entity.PaidResource::getSubscribePeriod, 30)
                            .set(com.haoran.music.entity.PaidResource::getIsEnabled, 1)
                            .set(com.haoran.music.entity.PaidResource::getStatus, "pending")
                            .set(com.haoran.music.entity.PaidResource::getChangeType, "resubmit")
                            .set(com.haoran.music.entity.PaidResource::getChangeReason, "创作者重新提交付费歌单"));
            if (updated != 1) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "付费申请状态已变化，请刷新后重试");
            }
        } else {
            paidResource = new com.haoran.music.entity.PaidResource();
            paidResource.setResourceType("playlist");
            paidResource.setResourceId(dto.getPlaylistId());
            paidResource.setOwnerId(userId);
            paidResource.setOwnerType("creator");
            paidResource.setPrice(monthlyPrice);
            paidResource.setPriceType("subscription");
            paidResource.setSubscribePeriod(30);
            paidResource.setIsEnabled(1);
            paidResource.setStatus("pending");
            paidResource.setSalesCount(0);
            paidResource.setTotalEarnings(java.math.BigDecimal.ZERO);
            paidResource.setPlatformFeeRate(new java.math.BigDecimal("0.02"));
            paidResource.setChangeType("create");
            paidResource.setChangeReason("创作者设置付费歌单");
            if (paidResourceMapper.insert(paidResource) != 1) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "付费申请保存失败");
            }
        }
        log.info("event=playlist_paid_application_submitted playlistId={} userId={} price={} status=pending",
            dto.getPlaylistId(), userId, dto.getMonthlyPrice());

                                               
                      
        LambdaQueryWrapper<User> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(User::getRole, "admin")
            .or()
            .eq(User::getIsModerator, 1);
        java.util.List<User> admins = userMapper.selectList(adminWrapper);

                     
        for (User admin : admins) {
            try {
                notificationService.sendSystemNotification(
                    admin.getId(),
                    "歌单付费审核",
                    "用户 " + userId + " 提交了歌单付费审核，歌单ID: " + dto.getPlaylistId() + "，请及时处理",
                    "/playlist/" + dto.getPlaylistId()
                );
            } catch (Exception e) {
                log.warn("event=playlist_paid_review_notification_failed adminId={} errorType={}",
                        admin.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("event=playlist_paid_review_requested playlistId={} userId={} adminCount={}",
            dto.getPlaylistId(), userId, admins.size());

        return true;
    }

       
                              
                               
                                            
       
    private void validateMonthlyPrice(Integer monthlyPrice) {
        if (monthlyPrice == null || monthlyPrice < 5 || monthlyPrice > 30) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                "月度价格必须在5-30元之间");
        }
    }


    private Map<Long, Playlist> lockPlaylistsForUpdate(Long firstPlaylistId, Long secondPlaylistId) {
        List<Long> playlistIds = Arrays.asList(firstPlaylistId, secondPlaylistId);
        playlistIds.sort(Long::compareTo);

        Map<Long, Playlist> result = new HashMap<>();
        for (Long playlistId : playlistIds) {
            Playlist playlist = baseMapper.selectByIdForUpdate(playlistId);
            if (ObjectUtils.isEmpty(playlist)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "歌单不存在");
            }
            result.put(playlistId, playlist);
        }
        return result;
    }

    private void requirePlaylistReadable(Playlist playlist, Long userId, String message) {
        if (isPlaylistOwner(playlist, userId)) {
            return;
        }
        PlaylistCollaborator collaborator = playlistCollaboratorMapper.selectAccepted(playlist.getId(), userId);
        if (collaborator == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    private void requirePlaylistPermission(Playlist playlist, Long userId, String permission, String message) {
        if (isPlaylistOwner(playlist, userId)) {
            return;
        }
        User owner = userMapper.selectById(playlist.getUserId());
        if (UserAccountStatusUtil.isBanned(owner) || UserAccountStatusUtil.isFrozen(owner)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "歌单协作已冻结");
        }
        PlaylistCollaborator collaborator = playlistCollaboratorMapper.selectAccepted(playlist.getId(), userId);
        if (collaborator == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }

        Integer allowed;
        if ("add".equals(permission)) {
            allowed = collaborator.getCanAdd();
        } else if ("remove".equals(permission)) {
            allowed = collaborator.getCanRemove();
        } else {
            allowed = collaborator.getCanEdit();
        }

        if (!Integer.valueOf(1).equals(allowed)) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    private boolean isPlaylistOwner(Playlist playlist, Long userId) {
        return playlist != null && Objects.equals(playlist.getUserId(), userId);
    }

    private String recordCollaborationOperationIfEnabled(Long playlistId, Long userId,
                                                         String operationType, Long songId, String description) {
        return recordCollaborationOperationIfEnabled(playlistId, userId, operationType, songId, description,
                collaborationStateSummary(operationType, songId, true),
                collaborationStateSummary(operationType, songId, false));
    }

       
                               
       
    private String recordCollaborationOperationIfEnabled(Long playlistId, Long userId,
                                                         String operationType, Long songId, String description,
                                                         String beforeSummary, String afterSummary) {
        PlaylistCollaborator member = playlistCollaboratorMapper.selectAccepted(playlistId, userId);
        if (member == null) {
            return null;
        }
        String eventId = playlistCollaborationAuditService.record(playlistId, userId, null,
                operationType, beforeSummary, afterSummary, description);
        PlaylistOperationLog operation = new PlaylistOperationLog();
        operation.setPlaylistId(playlistId);
        operation.setUserId(userId);
        operation.setOperationType(operationType);
        operation.setSongId(songId);
        operation.setDescription(description);
        operation.setCreateTime(LocalDateTime.now());
        playlistOperationLogMapper.insert(operation);
        return eventId;
    }

       
                                
       
    private String playlistMetadataSummary(Playlist playlist) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", playlist.getName());
        state.put("description", playlist.getDescription());
        state.put("cover", playlist.getCover());
        state.put("isPublic", playlist.getIsPublic());
        state.put("tags", playlist.getTags());
        return "summaryVersion=v1;metadataSha256=" + sha256(JSON.toJSONString(state));
    }

       
                             
       
    private String playlistOrderSummary(Long playlistId) {
        List<String> tokens = playlistSongMapper.selectActiveOrderTokens(playlistId);
        List<String> safeTokens = ObjectUtils.isEmpty(tokens) ? Collections.emptyList() : tokens;
        return "summaryVersion=v1;orderSha256=" + sha256(String.join(",", safeTokens))
                + ";songCount=" + safeTokens.size();
    }

       
                            
       
    private String playlistOrderSummary(List<PlaylistSong> playlistSongs) {
        List<String> tokens = new ArrayList<>();
        if (playlistSongs != null) {
            for (PlaylistSong relation : playlistSongs) {
                tokens.add(relation.getSongId() + ":" + relation.getSortOrder());
            }
        }
        return "summaryVersion=v1;orderSha256=" + sha256(String.join(",", tokens))
                + ";songCount=" + tokens.size();
    }

       
                             
       
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256算法不可用", exception);
        }
    }

    private String collaborationStateSummary(String operationType, Long songId, boolean before) {
        if ("add_song".equals(operationType)) {
            return "songId=" + songId + ";present=" + !before;
        }
        if ("remove_song".equals(operationType)) {
            return "songId=" + songId + ";present=" + before;
        }
        if ("delete_playlist".equals(operationType)) {
            return before ? "playlist=active;collaboration=open"
                    : "playlist=deleted;collaboration=closed";
        }
        if ("update_metadata".equals(operationType)) {
            return before ? "metadata=previous" : "metadata=updated";
        }
        if ("reorder".equals(operationType)) {
            return before ? "order=previous" : "order=updated";
        }
        return before ? "state=before" : "state=after";
    }

    private void syncPlaylistIndex(Long playlistId) {
        if (playlistId != null) {
            searchIndexService.sync("playlist", playlistId);
        }
    }

       
                                            
      
                            
                                           
       
    @Override
    public Integer getCollaboratePlaylistCount(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }
                                                              
        LambdaQueryWrapper<com.haoran.music.entity.PlaylistCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.haoran.music.entity.PlaylistCollaborator::getUserId, userId)
                .eq(com.haoran.music.entity.PlaylistCollaborator::getStatus, "accepted")
                .eq(com.haoran.music.entity.PlaylistCollaborator::getDeleted, CommonConstants.NOT_DELETED);
        Long count = playlistCollaboratorMapper.selectCount(wrapper);
        return count != null ? count.intValue() : 0;
    }
}
