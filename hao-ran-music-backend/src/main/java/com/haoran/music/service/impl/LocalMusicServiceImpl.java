package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.MusicConstants;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ExternalUrlGuard;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UrlHelper;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.entity.LocalMusic;
import com.haoran.music.entity.MV;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.LocalMusicMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.service.SongLikeService;
import com.haoran.music.service.LocalMusicService;
import com.haoran.music.service.LocalProxyService;
import com.haoran.music.vo.song.LocalMusicVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

   
                      
                         
   
@Slf4j
@Service
public class LocalMusicServiceImpl extends ServiceImpl<LocalMusicMapper, LocalMusic> implements LocalMusicService {

    private static final int MAX_LYRIC_CONTENT_LENGTH = 20000;

    private static final int MAX_BATCH_LOCAL_MUSIC_COUNT = 50;
    private static final int MAX_BATCH_LOCAL_MUSIC_BYTES = 64 * 1024;

    @Resource
    private SongMapper songMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private SongLikeService songLikeService;

    @Resource
    private LocalProxyService localProxyService;

    @Resource
    private SecurityConfig securityConfig;

       
                                 
                                      
       
    @Override
    public IPage<LocalMusicVO> scanSongsByPath(String path, Long userId, PageQuery pageQuery, String nginxUrlPrefix) {
        log.info("event=local_song_scan_started userId={}", userId);

                                      
        LambdaQueryWrapper<LocalMusic> userMusicWrapper = new LambdaQueryWrapper<>();
        userMusicWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        List<LocalMusic> userLocalMusicList = list(userMusicWrapper);
        Set<String> addedPaths = userLocalMusicList.stream()
                .map(LocalMusic::getFilePath)
                .collect(Collectors.toSet());

                                           
        Page<Song> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNotNull(Song::getUrlStandard)
                        .or()
                        .isNotNull(Song::getUrlHigh)
                        .or()
                        .isNotNull(Song::getUrlLossless))
                .orderByDesc(Song::getCreateTime);

        IPage<Song> songPage = songMapper.selectPage(page, wrapper);

                
        final Set<String> finalAddedPaths = addedPaths;
        return songPage.convert(song -> {
            LocalMusicVO vo = new LocalMusicVO();
            vo.setId(song.getId());
            vo.setName(song.getName());
            vo.setArtistName(song.getArtistNames());
            vo.setAlbumName(song.getAlbumName());
            vo.setDuration(song.getDuration());
                                                     
            String songUrl = song.getUrlLossless() != null ? song.getUrlLossless() :
                            song.getUrlHigh() != null ? song.getUrlHigh() :
                            song.getUrlStandard();
            
                                
            String relativePath = extractRelativePathFromUrl(songUrl);
            vo.setFileSize(localProxyService.getFileSize(relativePath));
            vo.setFileFormat(getFileExtensionFromUrl(songUrl));
            vo.setPlayCount(song.getPlayCount() != null ? song.getPlayCount().intValue() : 0);
            vo.setCreateTime(song.getCreateTime());

                      
            vo.setPlayUrl(nginxUrlPrefix + relativePath);

                   
            vo.setCoverUrl(song.getCover());

                        
            vo.setAdded(finalAddedPaths.contains(relativePath));

            return vo;
        });
    }

       
                                        
      
                         
                            
                                        
                        
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LocalMusicVO> addBySongIds(Long userId, List<Long> songIds, String nginxUrlPrefix) {
        List<LocalMusicVO> results = new ArrayList<>();

        if (ObjectUtils.isEmpty(songIds)) {
            return results;
        }
        requireBatchSize(songIds.size());

                      
        List<Song> songs = songMapper.selectBatchIds(songIds);
        Map<Long, Song> songMap = songs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));

                           
        LambdaQueryWrapper<LocalMusic> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        List<LocalMusic> existingLocalMusic = list(existWrapper);
        Set<String> existingPaths = existingLocalMusic.stream()
                .map(LocalMusic::getFilePath)
                .collect(Collectors.toSet());

        List<LocalMusic> newLocalMusicList = new ArrayList<>();

        for (Long songId : songIds) {
            try {
                Song song = songMap.get(songId);
                if (song == null) {
                    log.warn("歌曲不存在: songId={}", songId);
                    continue;
                }

                         
                String relativePath = extractRelativePathFromUrl(song.getUrlStandard());

                          
                if (existingPaths.contains(relativePath)) {
                    log.info("歌曲已添加，跳过: songId={}", songId);
                    continue;
                }

                            
                LocalMusic localMusic = new LocalMusic();
                localMusic.setUserId(userId);
                localMusic.setName(song.getName());
                localMusic.setArtistName(song.getArtistNames());
                localMusic.setAlbumName(song.getAlbumName());
                localMusic.setFileFormat(getFileExtensionFromUrl(song.getUrlStandard()));
                localMusic.setFilePath(relativePath);
                localMusic.setFileSize(localProxyService.getFileSize(relativePath));
                                            
                Integer duration = song.getDuration();
                if (duration == null || duration == 0) {
                    duration = localProxyService.getAudioDuration(relativePath);
                }
                localMusic.setDuration(duration != null ? duration : 0);
                localMusic.setPlayCount(0);
                localMusic.setSongId(songId);
                localMusic.setResourceType(MusicConstants.ResourceType.SONG);
                                 
                localMusic.setQuality(determineQualityFromFileFormat(getFileExtensionFromUrl(song.getUrlStandard())));

                newLocalMusicList.add(localMusic);
                existingPaths.add(relativePath);           

            } catch (Exception e) {
                log.error("准备添加本地音乐失败: songId={}, error={}", songId, e.getClass().getSimpleName());
            }
        }

                  
        if (!newLocalMusicList.isEmpty()) {
            saveBatch(newLocalMusicList);

                     
            for (LocalMusic localMusic : newLocalMusicList) {
                LocalMusicVO vo = new LocalMusicVO();
                vo.setId(localMusic.getId());
                vo.setName(localMusic.getName());
                vo.setArtistName(localMusic.getArtistName());
                vo.setAlbumName(localMusic.getAlbumName());
                vo.setDuration(localMusic.getDuration());
                vo.setFileSize(localMusic.getFileSize());
                vo.setFileFormat(localMusic.getFileFormat());
                vo.setPlayCount(localMusic.getPlayCount());
                vo.setCreateTime(localMusic.getCreateTime());
                vo.setPlayUrl(nginxUrlPrefix + localMusic.getFilePath());
                results.add(vo);

                log.info("event=local_song_added userId={} songId={}", userId, localMusic.getSongId());
            }
        }

        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalMusicVO addLocalMusic(Long userId, String filePath, String name, String artist, String album, String nginxUrlPrefix) {
               
        if (StrUtil.isBlank(filePath)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径不能为空");
        }
        validateStoredLocalMediaPath(filePath);

                         
        LambdaQueryWrapper<LocalMusic> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getFilePath, filePath)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        LocalMusic existing = getOne(existWrapper);
        if (existing != null) {
            log.info("event=local_music_add_skipped userId={} reason=already_exists", userId);
                                              
            return convertToVO(existing, nginxUrlPrefix);
        }

                         
        boolean isNetworkUrl = filePath.startsWith("http://") || filePath.startsWith("https://");

                     
        String fileName = extractFileName(filePath);
        String fileExtension = extractFileExtension(fileName);

                    
                       
        List<String> allowedFormats = Arrays.asList(
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "ape",
            "mp4", "m4p", "aiff", "aif", "aifc", "caf", "wv", "tta",
            "opus", "spx", "amr", "3gp", "ra", "dts", "ac3", "mpc",
            "dsd", "dsf", "dff", "mid", "midi", "rmi", "kar", "mod",
            "it", "s3m", "xm", "669", "med", "mtm", "psm", "umx"
        );
        if (!fileExtension.isEmpty() && !allowedFormats.contains(fileExtension.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的音频格式：" + fileExtension);
        }

                 
        LocalMusic localMusic = new LocalMusic();
        localMusic.setUserId(userId);
        localMusic.setName(StrUtil.blankToDefault(name, extractFileNameWithoutExt(fileName)));
        localMusic.setArtistName(StrUtil.blankToDefault(artist, "未知艺术家"));
        localMusic.setAlbumName(StrUtil.blankToDefault(album, "本地音乐"));
        localMusic.setFileFormat(fileExtension);
        localMusic.setFilePath(filePath);
        localMusic.setFileSize(0L);                     
        localMusic.setDuration(0);
        localMusic.setPlayCount(0);
                  
        localMusic.setResourceType(MusicConstants.ResourceType.SONG);
                         
        localMusic.setQuality(determineQualityFromFileFormat(fileExtension));

        save(localMusic);

        log.info("用户添加本地音乐: userId={}, networkUrl={}", userId, isNetworkUrl);

        return convertToVO(localMusic, nginxUrlPrefix);
    }

       
                         
      
                         
                              
                                        
                        
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LocalMusicVO> addBatchLocalMusic(Long userId, List<String> filePaths, String nginxUrlPrefix) {
        List<LocalMusicVO> results = new ArrayList<>();

        if (ObjectUtils.isEmpty(filePaths)) {
            return results;
        }
        if (filePaths.size() > MAX_BATCH_LOCAL_MUSIC_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多添加50首本地音乐");
        }

                                                      
        List<String> normalizedPaths = new ArrayList<>(filePaths.size());
        int totalBytes = 0;
        for (String filePath : filePaths) {
            if (StrUtil.isBlank(filePath)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "文件路径不能为空");
            }
            String normalizedPath = filePath.trim();
            validateStoredLocalMediaPath(normalizedPath);
            totalBytes += normalizedPath.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > MAX_BATCH_LOCAL_MUSIC_BYTES) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "批量媒体路径总长度过大");
            }
            normalizedPaths.add(normalizedPath);
        }

                     
        LambdaQueryWrapper<LocalMusic> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        List<LocalMusic> existingLocalMusic = list(existWrapper);
        Set<String> existingPaths = existingLocalMusic.stream()
                .map(LocalMusic::getFilePath)
                .collect(Collectors.toSet());

        List<LocalMusic> newLocalMusicList = new ArrayList<>();

        for (String filePath : normalizedPaths) {
            try {
                          
                if (existingPaths.contains(filePath)) {
                    log.info("event=local_music_batch_item_skipped reason=already_exists");
                    continue;
                }

                                               
                String fileName = extractFileName(filePath);
                String name = null;
                String artist = null;

                String fileNameWithoutExt = extractFileNameWithoutExt(fileName);
                String[] parts = fileNameWithoutExt.split(" - ");
                if (parts.length >= 2) {
                    artist = parts[0].trim();
                    name = parts[1].trim();
                }

                           
                LocalMusic localMusic = new LocalMusic();
                localMusic.setUserId(userId);
                localMusic.setName(StrUtil.blankToDefault(name, extractFileNameWithoutExt(fileName)));
                localMusic.setArtistName(StrUtil.blankToDefault(artist, "未知艺术家"));
                localMusic.setAlbumName("本地音乐");
                localMusic.setFileFormat(extractFileExtension(fileName));
                localMusic.setFilePath(filePath);
                localMusic.setFileSize(0L);                     
                localMusic.setDuration(0);
                localMusic.setPlayCount(0);
                localMusic.setResourceType(MusicConstants.ResourceType.SONG);
                                 
                localMusic.setQuality(determineQualityFromFileFormat(extractFileExtension(fileName)));

                newLocalMusicList.add(localMusic);
                existingPaths.add(filePath);           

            } catch (Exception e) {
                log.warn("event=local_music_batch_item_failed errorType={}", e.getClass().getSimpleName());
            }
        }

                  
        if (!newLocalMusicList.isEmpty()) {
            saveBatch(newLocalMusicList);

                     
            for (LocalMusic localMusic : newLocalMusicList) {
                results.add(convertToVO(localMusic, nginxUrlPrefix));
            }

            log.info("批量添加本地音乐成功: userId={}, count={}", userId, newLocalMusicList.size());
        }

        return results;
    }

       
                               
      
                         
                            
                                        
                       
       
    @Override
    public IPage<LocalMusicVO> getUserLocalMusic(Long userId, PageQuery pageQuery, String nginxUrlPrefix, Integer resourceType) {
        Page<LocalMusic> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<LocalMusic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);

                                         
        if (resourceType != null) {
            wrapper.eq(LocalMusic::getResourceType, resourceType);
        }

        String keyword = StrUtil.trim(pageQuery.getKeyword());
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(query -> query.like(LocalMusic::getName, keyword)
                    .or().like(LocalMusic::getArtistName, keyword)
                    .or().like(LocalMusic::getAlbumName, keyword));
        }

        applyLocalMusicSort(wrapper, pageQuery.getSortField(), pageQuery.getSortOrder());

        IPage<LocalMusic> localMusicPage = page(page, wrapper);

                                                                                               
                                                                                                  
        List<LocalMusic> toRemove = new ArrayList<>();
        for (LocalMusic music : localMusicPage.getRecords()) {
            if (isClientManagedLocalPath(music.getFilePath())) {
                continue;
            }
            String playUrl = convertToVO(music, nginxUrlPrefix).getPlayUrl();
            if (!isFileAccessible(playUrl)) {
                log.warn("event=local_music_inaccessible_removed localMusicId={} userId={}",
                        music.getId(), userId);
                toRemove.add(music);
            }
        }

                    
        for (LocalMusic music : toRemove) {
            removeById(music.getId());
            localMusicPage.getRecords().remove(music);
        }

                 
        localMusicPage.setTotal(localMusicPage.getTotal() - toRemove.size());

                            
        IPage<LocalMusicVO> voPage = localMusicPage.convert(music -> convertToVO(music, nginxUrlPrefix));

                      
        List<Long> songIds = voPage.getRecords().stream()
                .map(LocalMusicVO::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<Long> favoriteSongIds = Collections.emptySet();
        if (!songIds.isEmpty()) {
            favoriteSongIds = getFavoriteSongIdsBatch(userId, songIds);
        }

        final Set<Long> finalFavoriteSongIds = favoriteSongIds;
                           
        for (LocalMusicVO vo : voPage.getRecords()) {
            if (vo.getSongId() != null) {
                vo.setIsFavorite(finalFavoriteSongIds.contains(vo.getSongId()));
            }
        }

        return voPage;
    }

       
                                             
       
    private void applyLocalMusicSort(
            LambdaQueryWrapper<LocalMusic> wrapper,
            String sortField,
            String sortOrder) {
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);
        String field = StrUtil.blankToDefault(sortField, "created").toLowerCase(Locale.ROOT);

        switch (field) {
            case "name":
                wrapper.orderBy(true, ascending, LocalMusic::getName)
                        .orderBy(true, ascending, LocalMusic::getId);
                break;
            case "artist":
                wrapper.orderBy(true, ascending, LocalMusic::getArtistName)
                        .orderBy(true, ascending, LocalMusic::getName)
                        .orderBy(true, ascending, LocalMusic::getId);
                break;
            case "duration":
                wrapper.orderBy(true, ascending, LocalMusic::getDuration)
                        .orderBy(true, ascending, LocalMusic::getName)
                        .orderBy(true, ascending, LocalMusic::getId);
                break;
            case "playcount":
                wrapper.orderBy(true, ascending, LocalMusic::getPlayCount)
                        .orderBy(true, ascending, LocalMusic::getId);
                break;
            default:
                wrapper.orderBy(true, ascending, LocalMusic::getCreateTime)
                        .orderBy(true, ascending, LocalMusic::getId);
                break;
        }
    }

       
                      
      
                         
                            
                        
       
    private Set<Long> getFavoriteSongIdsBatch(Long userId, List<Long> songIds) {
        if (songIds.isEmpty()) {
            return Collections.emptySet();
        }

        try {
                                                 
            return songLikeService.getFavoriteSongIdsBatch(userId, songIds);
        } catch (Exception e) {
            log.warn("批量查询收藏状态失败: userId={}, songIds={}", userId, songIds.size());
            return Collections.emptySet();
        }
    }

    @Override
    public LocalMusicVO getLocalMusicDetail(Long id, Long userId, String nginxUrlPrefix) {
        LocalMusic localMusic = getById(id);
        if (ObjectUtils.isEmpty(localMusic)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "本地音乐不存在");
        }

        if (!localMusic.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该音乐");
        }

        return convertToVO(localMusic, nginxUrlPrefix);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteLocalMusic(Long id, Long userId) {
        LocalMusic localMusic = getById(id);
        if (ObjectUtils.isEmpty(localMusic)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "本地音乐不存在");
        }

        if (!localMusic.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该音乐");
        }

                                            
        return removeById(id);
    }

       

       
               
      
                       
                         
                     
                             
                            
                       
       
    @Override
    public LocalMusicVO updateLocalMusic(Long id, Long userId, String name, String artistName, String albumName, String versionType, String versionName, String nginxUrlPrefix) {
        LocalMusic localMusic = getById(id);
        if (ObjectUtils.isEmpty(localMusic)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "本地音乐不存在");
        }

        if (!localMusic.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改该音乐");
        }

               
        localMusic.setName(name);
        localMusic.setArtistName(artistName);
        if (albumName != null) {
            localMusic.setAlbumName(albumName);
        }
        if (versionType != null) {
            localMusic.setVersionType(versionType);
        }
        if (versionName != null) {
            localMusic.setVersionName(versionName);
        }

        boolean updated = updateById(localMusic);
        if (!updated) {
            throw new BusinessException(ResultCode.ERROR, "更新失败");
        }

        log.info("event=local_music_updated userId={} localMusicId={}", userId, id);

                   
        return getLocalMusicDetail(id, userId, nginxUrlPrefix);
    }

       
                       
       
    @Override
    public Boolean updateLocalMusicLyric(Long id, Long userId, String lyric) {
        if (lyric == null || lyric.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词内容不能为空");
        }
        String safeLyric = lyric.trim();
        if (safeLyric.length() > MAX_LYRIC_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "歌词内容不能超过" + MAX_LYRIC_CONTENT_LENGTH + "个字符");
        }

        LocalMusic localMusic = getById(id);
        if (ObjectUtils.isEmpty(localMusic)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "本地音乐不存在");
        }

        if (!localMusic.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权修改该音乐");
        }

                 
        localMusic.setLyricText(safeLyric);
        boolean updated = updateById(localMusic);

        if (!updated) {
            throw new BusinessException(ResultCode.ERROR, "更新歌词失败");
        }

        log.info("event=local_music_lyric_updated userId={} localMusicId={} lyricLength={}",
                userId, id, safeLyric.length());

        return true;
    }

       
                         
      
                              
                         
                     
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchDeleteLocalMusic(List<Long> ids, Long userId) {
        if (ObjectUtils.isEmpty(ids)) {
            return 0;
        }
        requireBatchSize(ids.size());

                               
        LambdaQueryWrapper<LocalMusic> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LocalMusic::getId, ids)
                .eq(LocalMusic::getUserId, userId);

        List<LocalMusic> toDelete = list(wrapper);

        if (toDelete.isEmpty()) {
            log.warn("没有找到可删除的本地音乐: ids={}, userId={}", ids, userId);
            return 0;
        }

                     
        List<Long> validIds = toDelete.stream()
                .map(LocalMusic::getId)
                .collect(Collectors.toList());

                  
        boolean success = removeByIds(validIds);

        if (success) {
            log.info("批量删除本地音乐成功: userId={}, count={}", userId, validIds.size());
            return validIds.size();
        }

        return 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean clearUserLocalMusic(Long userId) {
                  
        LambdaQueryWrapper<LocalMusic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocalMusic::getUserId, userId);
        return remove(wrapper);
    }
    @Override
    public Boolean incrementPlayCount(Long userId, Long id) {
        if (userId == null || id == null) {
            return false;
        }
        LocalMusic localMusic = getById(id);
        if (localMusic == null || !userId.equals(localMusic.getUserId())) {
            log.warn("本地音乐记录不存在或不属于当前用户: userId={}, id={}", userId, id);
            return false;
        }

                               
        Integer newQuality = null;
        if (localMusic.getQuality() == null || localMusic.getQuality() == 0) {
            newQuality = determineQualityFromFileFormat(localMusic.getFileFormat());
        }

                           
        int rows;
        if (newQuality != null) {
            rows = baseMapper.incrementPlayCountWithQuality(userId, id, newQuality);
        } else {
            rows = baseMapper.incrementPlayCount(userId, id);
        }
        
        boolean success = rows > 0;
        log.info("本地音乐播放次数更新: userId={}, id={}, rows={}, success={}", userId, id, rows, success);
        return success;
    }

       
            
      
                               
                                         
                           
       
    private LocalMusicVO convertToVO(LocalMusic localMusic, String nginxUrlPrefix) {
        LocalMusicVO vo = new LocalMusicVO();
        vo.setId(localMusic.getId());
        vo.setName(localMusic.getName());
        vo.setArtistName(localMusic.getArtistName());
        vo.setAlbumName(localMusic.getAlbumName());
        vo.setDuration(localMusic.getDuration());
        vo.setFileSize(localMusic.getFileSize());
        vo.setFileFormat(localMusic.getFileFormat());
        vo.setPlayCount(localMusic.getPlayCount());
        vo.setCreateTime(localMusic.getCreateTime());

                  
        String filePath = localMusic.getFilePath();

        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                                    
            if (securityConfig.isHideIp()) {
                                         
                vo.setPlayUrl(UrlHelper.toRelativePath(filePath));
            } else {
                vo.setPlayUrl(filePath);
            }
        } else {
                   
            if (securityConfig.isHideIp()) {
                                            
                vo.setPlayUrl(filePath.startsWith("/") ? filePath : "/" + filePath);
            } else {
                               
                String normalizedPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
                String prefix = nginxUrlPrefix.endsWith("/") ? nginxUrlPrefix : nginxUrlPrefix + "/";
                vo.setPlayUrl(prefix + normalizedPath);
            }
        }

                         
        vo.setSongId(localMusic.getSongId());

                                       
        if (localMusic.getResourceType() != null) {
            vo.setResourceType(localMusic.getResourceType());
        } else if ("MV".equals(localMusic.getAlbumName())) {
            vo.setResourceType(MusicConstants.ResourceType.MV);
        } else {
            vo.setResourceType(MusicConstants.ResourceType.SONG);
        }

                               
        vo.setFilePath(filePath);

        return vo;
    }
    private String extractFileName(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return "";
        }
                            
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

       
                  
       
    private String extractFileExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot + 1) : "";
    }

       
                 
       
    private String extractFileNameWithoutExt(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }

       
                  
       
    private String extractRelativePathFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
                      
                                                                                       
        try {
            int idx = url.indexOf("/songs/");
            if (idx > 0) {
                return url.substring(idx + 1);           
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }

       
                   
       
    private String getFileExtensionFromUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        int lastDot = url.lastIndexOf('.');
        int lastSlash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
        if (lastDot > lastSlash) {
            return url.substring(lastDot + 1);
        }
        return "";
    }

       
                   
                                    
                                
       
    private boolean isClientManagedLocalPath(String path) {
        if (StrUtil.isBlank(path)) {
            return false;
        }
        String value = path.trim();
        if (value.startsWith("local:") || value.startsWith("/local:")) {
            return true;
        }
        if (value.matches("^/?[A-Za-z]:[\\\\/].*")) {
            return true;
        }
        return value.startsWith("\\\\");
    }


    private void validateStoredLocalMediaPath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.length() > 2048) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "媒体路径过长");
        }
        if ((value.startsWith("http://") || value.startsWith("https://"))
                && !ExternalUrlGuard.validate(value).isAllowed()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "媒体URL不安全");
        }
    }

    private void requireBatchSize(int size) {
        if (size > MAX_BATCH_LOCAL_MUSIC_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多处理50条本地媒体记录");
        }
    }

    private boolean isFileAccessible(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return false;
        }

        String value = fileUrl.trim();
        if (isClientManagedLocalPath(value)) {
            return true;
        }

        if (value.startsWith("local:")) {
            return true;
        }

                             
        if (value.startsWith("/local:")) {
            return true;
        }

        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return true;
        }
        if (!ExternalUrlGuard.validate(value).isAllowed()) {
            return false;
        }
                                                                                                      
        return true;
    }

       
                          
                               
       
    @Override
    public IPage<LocalMusicVO> scanMVsByPath(String path, Long userId, PageQuery pageQuery, String nginxUrlPrefix) {
        log.info("event=local_mv_scan_started userId={}", userId);

        LambdaQueryWrapper<LocalMusic> userMusicWrapper = new LambdaQueryWrapper<>();
        userMusicWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        List<LocalMusic> userLocalMusicList = list(userMusicWrapper);
        Set<String> addedPaths = userLocalMusicList.stream()
                .map(LocalMusic::getFilePath)
                .collect(Collectors.toSet());

                                  
        Page<MV> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.isNotNull(MV::getUrl360p)
                        .or()
                        .isNotNull(MV::getUrl720p)
                        .or()
                        .isNotNull(MV::getUrl1080p))
                .eq(MV::getStatus, 1)
                .orderByDesc(MV::getCreateTime);

        IPage<MV> mvPage = mvMapper.selectPage(page, wrapper);

        final Set<String> finalAddedPaths = addedPaths;
        return mvPage.convert(mv -> {
            LocalMusicVO vo = new LocalMusicVO();
            vo.setId(mv.getId());
            vo.setName(mv.getName());
            vo.setArtistName(mv.getArtistNames());
            vo.setAlbumName("MV");
            vo.setDuration(mv.getDuration());
                                               
            String mvUrl = mv.getUrl1080p() != null ? mv.getUrl1080p() :
                          mv.getUrl720p() != null ? mv.getUrl720p() :
                          mv.getUrl360p();

            String relativePath = extractRelativePathFromUrl(mvUrl);
            vo.setFileSize(localProxyService.getFileSize(relativePath));
            vo.setFileFormat("mp4");
            vo.setPlayCount(mv.getPlayCount() != null ? mv.getPlayCount().intValue() : 0);
            vo.setCreateTime(mv.getCreateTime());

                      
            vo.setPlayUrl(nginxUrlPrefix + relativePath);
            vo.setCoverUrl(mv.getCover());
            vo.setAdded(finalAddedPaths.contains(relativePath));

            return vo;
        });
    }

       
                                         
      
                         
                           
                                        
                        
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LocalMusicVO> addMVsByMvIds(Long userId, List<Long> mvIds, String nginxUrlPrefix) {
        List<LocalMusicVO> results = new ArrayList<>();

        if (ObjectUtils.isEmpty(mvIds)) {
            return results;
        }
        requireBatchSize(mvIds.size());

                      
        List<MV> mvs = mvMapper.selectBatchIds(mvIds);
        Map<Long, MV> mvMap = mvs.stream()
                .collect(Collectors.toMap(MV::getId, s -> s, (a, b) -> a));

                           
        LambdaQueryWrapper<LocalMusic> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        List<LocalMusic> existingLocalMusic = list(existWrapper);
        Set<String> existingPaths = existingLocalMusic.stream()
                .map(LocalMusic::getFilePath)
                .collect(Collectors.toSet());

        List<LocalMusic> newLocalMusicList = new ArrayList<>();

        for (Long mvId : mvIds) {
            try {
                MV mv = mvMap.get(mvId);
                if (mv == null) {
                    log.warn("MV不存在: mvId={}", mvId);
                    continue;
                }

                String mvUrl = mv.getUrl720p() != null ? mv.getUrl720p() : mv.getUrl360p();
                if (mvUrl == null) {
                    log.warn("MV没有可用的播放URL: mvId={}", mvId);
                    continue;
                }

                String relativePath = extractRelativePathFromUrl(mvUrl);

                          
                if (existingPaths.contains(relativePath)) {
                    log.info("MV已添加，跳过: mvId={}", mvId);
                    continue;
                }

                LocalMusic localMusic = new LocalMusic();
                localMusic.setUserId(userId);
                localMusic.setName(mv.getName());
                localMusic.setArtistName(mv.getArtistNames());
                localMusic.setAlbumName("MV");
                localMusic.setFileFormat("mp4");
                localMusic.setFilePath(relativePath);
                localMusic.setFileSize(localProxyService.getFileSize(relativePath));
                localMusic.setResourceType(MusicConstants.ResourceType.MV);
                localMusic.setQuality(0);           
                localMusic.setDuration(mv.getDuration());
                localMusic.setPlayCount(0);

                newLocalMusicList.add(localMusic);
                existingPaths.add(relativePath);           

            } catch (Exception e) {
                log.error("准备添加本地MV失败: mvId={}, error={}", mvId, e.getClass().getSimpleName());
            }
        }

                  
        if (!newLocalMusicList.isEmpty()) {
            saveBatch(newLocalMusicList);

                     
            for (LocalMusic localMusic : newLocalMusicList) {
                LocalMusicVO vo = new LocalMusicVO();
                vo.setId(localMusic.getId());
                vo.setName(localMusic.getName());
                vo.setArtistName(localMusic.getArtistName());
                vo.setAlbumName(localMusic.getAlbumName());
                vo.setDuration(localMusic.getDuration());
                vo.setFileSize(localMusic.getFileSize());
                vo.setFileFormat(localMusic.getFileFormat());
                vo.setPlayCount(localMusic.getPlayCount());
                vo.setCreateTime(localMusic.getCreateTime());
                vo.setPlayUrl(nginxUrlPrefix + localMusic.getFilePath());
                results.add(vo);

                log.info("event=local_mv_added userId={} mvId={}", userId, localMusic.getId());
            }
        }

        return results;
    }

       
               
      
                         
                       
                         
                       
                             
                                         
                       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalMusicVO addManualMV(Long userId, String name, String artist, String url, String cover, String nginxUrlPrefix, Long fileSize, Integer duration, Integer quality) {
               
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "MV名称不能为空");
        }
        if (StrUtil.isBlank(artist)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌手名称不能为空");
        }
        if (StrUtil.isBlank(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "视频URL不能为空");
        }
        validateStoredLocalMediaPath(url);

                         
        boolean isNetworkUrl = url.startsWith("http://") || url.startsWith("https://");

                  
        String fileExtension = extractFileExtension(extractFileName(url));

                    
        List<String> allowedFormats = Arrays.asList(
            "mp4", "flv", "avi", "mkv", "mov", "wmv", "webm", "m4v",
            "3gp", "3g2", "mpeg", "mpg", "ts", "m2ts", "f4v"
        );
        if (!fileExtension.isEmpty() && !allowedFormats.contains(fileExtension.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的视频格式：" + fileExtension);
        }

                  
        LambdaQueryWrapper<LocalMusic> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getFilePath, url)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        long count = count(checkWrapper);

        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该MV已添加");
        }

                   
        LocalMusic localMusic = new LocalMusic();
        localMusic.setUserId(userId);
        localMusic.setName(name);
        localMusic.setArtistName(artist);
        localMusic.setAlbumName("MV");
        localMusic.setFileFormat(fileExtension.isEmpty() ? "mp4" : fileExtension);
        localMusic.setFilePath(url);
        localMusic.setFileSize(fileSize != null ? fileSize : 0L);
        localMusic.setDuration(duration != null ? duration : 0);
        localMusic.setPlayCount(0);
                  
        localMusic.setResourceType(MusicConstants.ResourceType.MV);
                          
        if (quality != null) {
            localMusic.setQuality(quality);
        } else {
            localMusic.setQuality(0);         
        }

        save(localMusic);

        log.info("event=local_mv_manual_add userId={} isNetworkUrl={}", userId, isNetworkUrl);

        return convertToVO(localMusic, nginxUrlPrefix);
    }

       
             
      
                         
                       
                         
                       
                             
                                         
                       
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalMusicVO addManualSong(Long userId, String name, String artist, String url, String cover, String nginxUrlPrefix, Long fileSize, Integer duration, Integer quality) {
               
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲名称不能为空");
        }
        if (StrUtil.isBlank(artist)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌手名称不能为空");
        }
        if (StrUtil.isBlank(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "音频URL不能为空");
        }
        validateStoredLocalMediaPath(url);

                         
        boolean isNetworkUrl = url.startsWith("http://") || url.startsWith("https://");

                  
        String fileExtension = extractFileExtension(extractFileName(url));

                    
        List<String> allowedFormats = Arrays.asList(
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "ape",
            "mp4", "m4p", "aiff", "aif", "aifc", "caf", "wv", "tta",
            "opus", "spx", "amr", "3gp", "ra", "dts", "ac3", "mpc",
            "dsd", "dsf", "dff", "mid", "midi", "rmi", "kar", "mod",
            "it", "s3m", "xm", "669", "med", "mtm", "psm", "umx"
        );
        if (!fileExtension.isEmpty() && !allowedFormats.contains(fileExtension.toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的音频格式：" + fileExtension);
        }

                  
        LambdaQueryWrapper<LocalMusic> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getFilePath, url)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED);
        long count = count(checkWrapper);

        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该歌曲已添加");
        }

                   
        LocalMusic localMusic = new LocalMusic();
        localMusic.setUserId(userId);
        localMusic.setName(name);
        localMusic.setArtistName(artist);
                                                      
        localMusic.setAlbumName(StrUtil.isBlank(cover) ? "本地音乐" : cover.trim());
        localMusic.setFileFormat(fileExtension.isEmpty() ? "mp3" : fileExtension);
        localMusic.setFilePath(url);
        localMusic.setFileSize(fileSize != null ? fileSize : 0L);
        localMusic.setDuration(duration != null ? duration : 0);
        localMusic.setPlayCount(0);
                  
        localMusic.setResourceType(MusicConstants.ResourceType.SONG);
                                        
        localMusic.setQuality(quality != null ? quality : determineQualityFromFileFormat(fileExtension));

        save(localMusic);

        log.info("event=local_song_manual_add userId={} isNetworkUrl={}", userId, isNetworkUrl);

        return convertToVO(localMusic, nginxUrlPrefix);
    }

     
               
                               
                                           
     
  private Integer determineQualityFromFileFormat(String fileExtension) {
    if (StrUtil.isBlank(fileExtension)) {
      return 0;
    }
    String ext = fileExtension.toLowerCase();
           
    if (ext.matches("flac|wav|ape|wv|tta|aiff|aif|aifc|caf|dsf|dff")) {
      return 2;             
    }
                            
    if (ext.matches("m4a")) {
      return 1;                      
    }
    return 0;             
  }

       
                      
      
                                         
                                 
                         
                            
                                         
                     
       
    @Override
    public IPage<LocalMusicVO> searchBySongName(String songName, Long excludeId, Long userId, PageQuery pageQuery, String nginxUrlPrefix) {
        log.info("[LocalMusicService] 按歌曲名搜索版本: songName={}, excludeId={}, userId={}", songName, excludeId, userId);

        Page<LocalMusic> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<LocalMusic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LocalMusic::getUserId, userId)
                .eq(LocalMusic::getDeleted, CommonConstants.NOT_DELETED)
                                       
                .like(LocalMusic::getName, songName)
                         
                .ne(excludeId != null, LocalMusic::getId, excludeId)
                          
                .eq(LocalMusic::getResourceType, MusicConstants.ResourceType.SONG)
                .orderByDesc(LocalMusic::getCreateTime);

        IPage<LocalMusic> localMusicPage = page(page, wrapper);

                
        IPage<LocalMusicVO> voPage = localMusicPage.convert(music -> {
            LocalMusicVO vo = convertToVO(music, nginxUrlPrefix);
                     
            if (StrUtil.isNotBlank(music.getLyricText())) {
                vo.setLyric(music.getLyricText());
            }
            return vo;
        });

        log.info("[LocalMusicService] 搜索结果: total={}, records={}", voPage.getTotal(), voPage.getRecords().size());

        return voPage;
    }

}
