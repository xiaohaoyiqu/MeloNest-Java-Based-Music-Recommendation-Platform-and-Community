package com.haoran.music.service.impl;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ConvertHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.ValidPlayPolicy;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.ListenHistoryMapper;
import com.haoran.music.mapper.LocalMusicMapper;
import com.haoran.music.entity.LocalMusic;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.entity.User;
import com.haoran.music.service.ListenHistoryService;
import com.haoran.music.service.LocalMusicService;
import com.haoran.music.service.PlayEventReceiptService;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.vo.song.ListenHistoryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

   
                      
                         
   
@Slf4j
@Service
public class ListenHistoryServiceImpl extends ServiceImpl<ListenHistoryMapper, ListenHistory> implements ListenHistoryService {

    private static final int MAX_QUERY_SIZE = 100;
    private static final int MAX_IMPORT_SIZE = 500;

       
                  
                      
       
    private static final int MAX_HISTORY_COUNT = 560;

    @Resource
    private SongMapper songMapper;
    @Resource
    private LocalMusicMapper localMusicMapper;
    @Resource
    private LocalMusicService localMusicService;

    @Resource
    private ArtistMapper artistMapper;
    @Resource
    private AlbumMapper albumMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PlayEventReceiptService playEventReceiptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addListenRecord(Long userId, String songId, Integer progress, String quality, Integer isLocal) {
        addListenRecord(userId, songId, progress, quality, isLocal, UUID.randomUUID().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addListenRecord(Long userId, String songId, Integer progress, String quality,
                                Integer isLocal, String eventId) {
        String normalizedEventId = ObjectUtils.isEmpty(eventId)
                ? UUID.randomUUID().toString()
                : eventId.trim();
        int normalizedIsLocal = Objects.equals(isLocal, 1) ? 1 : 0;
        final Long actualSongId;
        try {
            actualSongId = Long.parseLong(songId);
        } catch (Exception e) {
            log.warn("忽略非法播放事件歌曲ID: userId={}, songId={}", userId, songId);
            return;
        }
        if (!playEventReceiptService.tryClaim(normalizedEventId, userId, songId, normalizedIsLocal)) {
            return;
        }

        boolean processed = false;
        try {
            Song song = null;
            LocalMusic localMusic = null;
            if (normalizedIsLocal == 1) {
                                                      
                                      
                if (!UserAccountStatusUtil.canInteract(userId, userMapper::selectById)) {
                    log.debug("跳过不可互动账号的本地播放事件: userId={}, songId={}", userId, songId);
                    processed = true;
                    return;
                }
                localMusic = localMusicMapper.selectById(actualSongId);
                if (localMusic == null || !userId.equals(localMusic.getUserId())) {
                    log.warn("忽略非归属本地播放事件: userId={}, songId={}", userId, songId);
                    processed = true;
                    return;
                }
            } else {
                if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
                    log.debug("跳过非公共统计账号播放历史和热度: userId={}, songId={}", userId, songId);
                    processed = true;
                    return;
                }
                song = songMapper.selectById(actualSongId);
                if (!canContributeSongStats(song)) {
                    log.debug("跳过公开作者异常歌曲播放历史和热度: userId={}, songId={}", userId, songId);
                    processed = true;
                    return;
                }
            }
            boolean qualified = ValidPlayPolicy.isQualified(progress,
                    normalizedIsLocal == 1
                            ? localMusic.getDuration()
                            : (song != null ? song.getDuration() : null));

            LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ListenHistory::getUserId, userId)
                    .eq(ListenHistory::getSongId, actualSongId)
                    .eq(ListenHistory::getIsLocal, normalizedIsLocal)
                    .orderByDesc(ListenHistory::getCreateTime)
                    .last("LIMIT 1");

            ListenHistory existing = getOne(queryWrapper);
            boolean isNewRecord = false;
            if (existing != null) {
                existing.setProgress(progress);
                existing.setQuality(quality);
                existing.setIsCompleted(qualified ? 1 : 0);
                updateById(existing);
            } else {
                ListenHistory history = new ListenHistory();
                history.setUserId(userId);
                history.setSongId(actualSongId);
                history.setIsLocal(normalizedIsLocal);
                history.setProgress(progress);
                history.setQuality(quality);
                history.setIsCompleted(qualified ? 1 : 0);
                save(history);
                isNewRecord = true;
            }

            if (!qualified) {
                log.debug("播放进度未达到有效播放阈值，仅更新历史: userId={}, songId={}, progress={}",
                        userId, songId, progress);
            } else if (normalizedIsLocal == 1) {
                localMusicService.incrementPlayCount(userId, actualSongId);
            } else {
                songMapper.incrementPlayCount(actualSongId);

                for (Long artistId : resolveArtistIds(song)) {
                    artistMapper.incrementPlayCount(artistId);
                }

                if (song != null && ObjectUtils.isNotEmpty(song.getAlbumId())) {
                    albumMapper.incrementPlayCount(song.getAlbumId());
                }
            }

            if (isNewRecord) {
                cleanOldHistoryIfNeeded(userId);
            }

            log.debug("Add listen history: userId={}, songId={}, isLocal={}, progress={}",
                    userId, songId, normalizedIsLocal, progress);
            processed = true;
        } finally {
            if (processed) {
                playEventReceiptService.markProcessed(normalizedEventId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateListenProgress(Long userId, String songId, Integer progress,
                                        String quality, Integer isLocal) {
        if (userId == null || StrUtil.isBlank(songId)) {
            return false;
        }
        final long actualSongId;
        try {
            actualSongId = Long.parseLong(songId);
        } catch (NumberFormatException exception) {
            return false;
        }

        int normalizedIsLocal = Objects.equals(isLocal, 1) ? 1 : 0;
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getSongId, actualSongId)
                .eq(ListenHistory::getIsLocal, normalizedIsLocal)
                .orderByDesc(ListenHistory::getCreateTime)
                .last("LIMIT 1");
        ListenHistory existing = getOne(wrapper);
        if (existing == null) {
            return false;
        }

        int safeProgress = Math.max(0, Math.min(progress == null ? 0 : progress, 24 * 60 * 60));
        Integer songDuration = null;
        if (normalizedIsLocal == 0) {
            Song song = songMapper.selectById(actualSongId);
            songDuration = song == null ? null : song.getDuration();
        }
        if (songDuration != null && songDuration > 0) {
            safeProgress = Math.min(safeProgress, songDuration);
        }

        existing.setProgress(safeProgress);
        if (StrUtil.isNotBlank(quality)
                && ("standard".equals(quality) || "high".equals(quality) || "lossless".equals(quality))) {
            existing.setQuality(quality);
        }
        existing.setIsCompleted(ValidPlayPolicy.isQualified(safeProgress, songDuration) ? 1 : 0);
        return updateById(existing);
    }

    private boolean canContributeSongStats(Song song) {
        if (song == null || song.getUploaderId() == null) {
            return true;
        }
        return UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById);
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
                    log.warn("Invalid artist id: {}", artistIdStr);
                }
            }
        }
        return artistIds;
    }
    private void cleanOldHistoryIfNeeded(Long userId) {
        LambdaQueryWrapper<ListenHistory> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ListenHistory::getUserId, userId);
        Long totalCount = count(countWrapper);

        if (totalCount > MAX_HISTORY_COUNT) {
            int deleteCount = (int) (totalCount - MAX_HISTORY_COUNT) + 50;

            LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ListenHistory::getUserId, userId)
                    .orderByAsc(ListenHistory::getCreateTime)
                    .last("LIMIT " + deleteCount);

            List<ListenHistory> oldHistories = list(queryWrapper);
            if (!oldHistories.isEmpty()) {
                List<Long> idsToDelete = oldHistories.stream()
                        .map(ListenHistory::getId)
                        .collect(Collectors.toList());
                removeByIds(idsToDelete);
                log.info("清理用户过旧播放历史: userId={}, 删除记录数={}", userId, idsToDelete.size());
            }
        }
    }

       
                                     
      
                         
                          
                        
       
    @Override
    public List<SongVO> getRecentSongs(Long userId, Integer limit) {
        int actualLimit = limit == null ? 50 : Math.max(1, Math.min(limit, MAX_QUERY_SIZE));
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getUserId, userId)
                .orderByDesc(ListenHistory::getCreateTime)
                .last("LIMIT " + actualLimit);

        List<ListenHistory> histories = list(queryWrapper);

                             
        Set<Long> songIdSet = new LinkedHashSet<>();
        for (ListenHistory history : histories) {
            songIdSet.add(history.getSongId());
            if (songIdSet.size() >= actualLimit) {
                break;
            }
        }

                               
        if (songIdSet.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> songIds = new ArrayList<>(songIdSet);
        List<Song> songs = songMapper.selectBatchIds(songIds);

                                      
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));

                           
        List<SongVO> result = new ArrayList<>();
        for (Long songId : songIds) {
            Song song = songMap.get(songId);
            if (ObjectUtils.isNotEmpty(song)) {
                SongVO vo = new SongVO();
                BeanUtils.copyProperties(song, vo);
                result.add(vo);
            }
        }

        return result;
    }

    @Override
    public List<ListenHistory> getUserHistory(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, MAX_QUERY_SIZE));
        int offset = (safePage - 1) * safeSize;
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getUserId, userId)
                .orderByDesc(ListenHistory::getCreateTime)
                .last("LIMIT " + safeSize + " OFFSET " + offset);

        return list(queryWrapper);
    }

       
                                    
      
                         
                           
                       
                       
       
    @Override
    public List<ListenHistoryVO> getUserHistoryWithDetails(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, MAX_QUERY_SIZE));
        int offset = (safePage - 1) * safeSize;
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getUserId, userId)
                .orderByDesc(ListenHistory::getCreateTime)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<ListenHistory> histories = list(queryWrapper);

        if (histories.isEmpty()) {
            return new ArrayList<>();
        }

                               
        List<Long> regularSongIds = new ArrayList<>();
        List<Long> localSongIds = new ArrayList<>();

        for (ListenHistory history : histories) {
            if (history.getIsLocal() != null && history.getIsLocal() == 1) {
                localSongIds.add(history.getSongId());
            } else {
                regularSongIds.add(history.getSongId());
            }
        }

                        
        Map<Long, Song> songMap = new HashMap<>();
        if (!regularSongIds.isEmpty()) {
            List<Song> songs = songMapper.selectBatchIds(regularSongIds);
            songMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));
        }

                        
        Map<Long, LocalMusic> localMusicMap = new HashMap<>();
        if (!localSongIds.isEmpty()) {
            List<LocalMusic> localMusicList = localMusicMapper.selectBatchIds(localSongIds);
            localMusicMap = localMusicList.stream()
                    .collect(Collectors.toMap(LocalMusic::getId, s -> s, (a, b) -> a));
        }

                               
        List<ListenHistoryVO> result = new ArrayList<>();

        for (ListenHistory history : histories) {
            Long songId = history.getSongId();
            Integer isLocalFlag = history.getIsLocal();
            ListenHistoryVO vo = new ListenHistoryVO();
            vo.setId(history.getId());
            vo.setSongId(songId);
            vo.setProgress(history.getProgress());
            vo.setQuality(history.getQuality());
            vo.setListenTime(history.getCreateTime());

                                   
            if (isLocalFlag != null && isLocalFlag == 1) {
                LocalMusic localMusic = localMusicMap.get(songId);
                if (ObjectUtils.isNotEmpty(localMusic)) {
                    vo.setSongName(localMusic.getName());
                    vo.setArtistNames(localMusic.getArtistName());
                    vo.setAlbumName(localMusic.getAlbumName());
                                         
                    if (localMusic.getSongId() != null) {
                        Song relatedSong = songMap.get(localMusic.getSongId());
                        vo.setCover(relatedSong != null ? relatedSong.getCover() : null);
                    } else {
                        vo.setCover("/images/default-cover.png");
                    }
                    vo.setDuration(localMusic.getDuration());
                                              
                    vo.setUrlStandard(localMusic.getFilePath());
                    vo.setIsLocal(true);
                    result.add(vo);
                }
            } else {
                Song song = songMap.get(songId);
                if (ObjectUtils.isNotEmpty(song)) {
                    vo.setSongName(song.getName());
                    vo.setArtistNames(song.getArtistNames());
                    vo.setAlbumName(song.getAlbumName());
                    vo.setCover(UrlHelper.buildRelativeCoverUrl(song.getCover()));
                    vo.setDuration(song.getDuration());
                    vo.setUrlStandard(UrlHelper.buildRelativeAudioUrl(song.getUrlStandard()));
                    vo.setUrlHigh(UrlHelper.buildRelativeAudioUrl(song.getUrlHigh()));
                    vo.setUrlLossless(UrlHelper.buildRelativeAudioUrl(song.getUrlLossless()));
                    vo.setIsLocal(false);
                    result.add(vo);
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearUserHistory(Long userId) {
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getUserId, userId);
        remove(queryWrapper);
        log.info("清空用户播放历史: userId={}", userId);
    }

    @Override
    public Long getUserHistoryCount(Long userId) {
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getUserId, userId);
        return count(queryWrapper);
    }

       
                                  
      
                         
                            
                     
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importHistory(Long userId, List<Long> songIds) {
        if (ObjectUtils.isEmpty(songIds)) {
            return 0;
        }
        if (songIds.size() > MAX_IMPORT_SIZE) {
            throw new BusinessException("单次最多导入" + MAX_IMPORT_SIZE + "条播放历史");
        }
        LinkedHashSet<Long> uniqueSongIds = songIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueSongIds.isEmpty()) {
            return 0;
        }

                          
        List<Song> existingSongs = songMapper.selectBatchIds(uniqueSongIds);
        Set<Long> validSongIds = existingSongs.stream()
                .map(Song::getId)
                .collect(Collectors.toSet());
        if (validSongIds.isEmpty()) {
            return 0;
        }

                           
        LambdaQueryWrapper<ListenHistory> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ListenHistory::getUserId, userId)
                .in(ListenHistory::getSongId, validSongIds);
        List<ListenHistory> existingHistories = list(existWrapper);
        Set<Long> existingSongIds = existingHistories.stream()
                .map(ListenHistory::getSongId)
                .collect(Collectors.toSet());

                  
        int importCount = 0;
        List<ListenHistory> newHistories = new ArrayList<>();

        for (Long songId : uniqueSongIds) {
            if (!validSongIds.contains(songId) || existingSongIds.contains(songId)) {
                continue;
            }

            ListenHistory history = new ListenHistory();
            history.setUserId(userId);
            history.setSongId(songId);
            history.setProgress(0);
            history.setQuality("standard");
            history.setIsCompleted(0);
            newHistories.add(history);
            importCount++;
        }

                  
        if (!newHistories.isEmpty()) {
            saveBatch(newHistories);
        }

        log.info("批量导入播放历史: userId={}, 导入数量={}", userId, importCount);
        return importCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteHistory(Long historyId, Long userId) {
                       
        LambdaQueryWrapper<ListenHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ListenHistory::getId, historyId)
                .eq(ListenHistory::getUserId, userId);

        ListenHistory history = getOne(queryWrapper);
        if (ObjectUtils.isEmpty(history)) {
            log.warn("尝试删除不属于该用户的播放历史记录: historyId={}, userId={}", historyId, userId);
            return false;
        }

        boolean result = removeById(historyId);
        if (result) {
            log.info("删除播放历史记录: historyId={}, userId={}", historyId, userId);
        }
        return result;
    }
}
