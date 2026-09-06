package com.haoran.music.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.config.SongRequestConfig;
import com.haoran.music.entity.*;
import com.haoran.music.enums.AudioQuality;
import com.haoran.music.enums.SoundQuality;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.ModerationIntegrationService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import com.haoran.music.service.SongResourceRequestService;
import com.haoran.music.service.VirusScanService;
import com.haoran.music.common.util.AudioQualityDetector;
import com.haoran.music.vo.song.SongResourceRequestVO;
import com.haoran.music.dto.SongResourceRequestAddDTO;
import com.haoran.music.dto.SongResourceRequestHandleDTO;
import com.haoran.music.common.dto.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

   
                      
                               
   
@Slf4j
@Service
public class SongResourceRequestServiceImpl implements SongResourceRequestService {

    private static final Set<String> USER_REQUEST_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("pending", "processing", "completed", "rejected"))
    );

    private final SongResourceRequestMapper requestMapper;
    private final SongMapper songMapper;
    private final ArtistMapper artistMapper;
    private final AlbumMapper albumMapper;
    private final UserMapper userMapper;
    private final SongRequestConfig songRequestConfig;
                                                        

    @Autowired
    private AudioQualityDetector audioQualityDetector;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private WorkProcessingUtil workProcessingUtil;

    @Autowired
    private VirusScanService virusScanService;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private ModerationIntegrationService moderationIntegrationService;

    @Autowired
    private OfficialMediaDerivativeService officialMediaDerivativeService;


    public SongResourceRequestServiceImpl(SongResourceRequestMapper requestMapper,
                                          SongMapper songMapper,
                                          ArtistMapper artistMapper,
                                          AlbumMapper albumMapper,
                                          UserMapper userMapper,
                                          SongRequestConfig songRequestConfig) {
        this.requestMapper = requestMapper;
        this.songMapper = songMapper;
        this.artistMapper = artistMapper;
        this.albumMapper = albumMapper;
        this.userMapper = userMapper;
        this.songRequestConfig = songRequestConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRequest(Long userId, String userNickname, SongResourceRequestAddDTO dto) {
        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "提交歌曲资源申请");
        int todayCount = requestMapper.countTodayRequests(userId);
        if (todayCount >= songRequestConfig.getLimits().getDailyMaxRequests()) {
            throw new BusinessException(ResultCode.ERROR,
                    "daily request limit reached: " + songRequestConfig.getLimits().getDailyMaxRequests());
        }

        int duplicate = requestMapper.checkDuplicate(userId, dto.getSongName(), dto.getArtistName());
        if (duplicate > 0) {
            throw new BusinessException(ResultCode.ERROR, "您已申请过该歌曲，请勿重复提交");
        }

        SongResourceRequest request = new SongResourceRequest();
        request.setUserId(userId);
        request.setSongName(dto.getSongName());
        request.setArtistName(dto.getArtistName());
        request.setAlbumName(dto.getAlbumName());
        request.setVersionInfo(StrUtil.isNotBlank(dto.getVersionInfo()) ? dto.getVersionInfo() : "原版");
        request.setSourceDescription(dto.getSourceDescription());
        request.setRemark(dto.getRemark());
        request.setStatus("pending");
        request.setNotified(0);

        requestMapper.insert(request);
        moderationIntegrationService.submitForModeration(
                "song_resource_request", request.getId(), userId, "user");

        log.info("[SongResourceRequest] 用户创建申请: userId={}, song={}, artist={}",
            userId, dto.getSongName(), dto.getArtistName());

        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRequestWithFile(Long userId, String userNickname, String songName, String artistName,
            String albumName, String versionInfo, String sourceDescription, String remark, MultipartFile file) {

        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "提交歌曲资源申请");
        int todayCount = requestMapper.countTodayRequests(userId);
        if (todayCount >= songRequestConfig.getLimits().getDailyMaxRequests()) {
            throw new BusinessException(ResultCode.ERROR,
                    "daily request limit reached: " + songRequestConfig.getLimits().getDailyMaxRequests());
        }

        int duplicate = requestMapper.checkDuplicate(userId, songName, artistName);
        if (duplicate > 0) {
            throw new BusinessException(ResultCode.ERROR, "您已申请过该歌曲，请勿重复提交");
        }

        String fileUrl = null;
        AudioQualityDetector.AudioInfo audioInfo = null;

        if (file != null && !file.isEmpty()) {
                      
            fileUrl = handleFileUpload(file, userId);

                      
            try {
                audioInfo = audioQualityDetector.detectAudioInfo(fileUrl);
                log.info("[SongResourceRequest] 音质检测完成: {} -> {}", songName, audioInfo.getQualityName());
            } catch (Exception e) {
                log.warn("[SongResourceRequest] 音质检测失败，使用默认值: {}", e.getClass().getSimpleName());
                audioInfo = new AudioQualityDetector.AudioInfo();
            }
        }

        SongResourceRequest request = new SongResourceRequest();
        request.setUserId(userId);
        request.setSongName(songName);
        request.setArtistName(artistName);
        request.setAlbumName(albumName);
        request.setVersionInfo(StrUtil.isNotBlank(versionInfo) ? versionInfo : "原版");
        request.setSourceDescription(sourceDescription);
        request.setRemark(remark);
        request.setFileUrl(fileUrl);

                   
        if (audioInfo != null) {
            request.setDetectedQuality(audioInfo.getQualityLevel());
            request.setFileSize(audioInfo.getFileSize());
            request.setDuration(audioInfo.getDuration());
            request.setBitrate(audioInfo.getBitrate());
            request.setSampleRate(audioInfo.getSampleRate());
            request.setFormat(audioInfo.getFormat());
        }
        if (request.getFileSize() == null && file != null) {
            request.setFileSize(file.getSize());
        }

        request.setStatus("pending");
        request.setNotified(0);

        requestMapper.insert(request);
        registerMediaAsset(request);
        moderationIntegrationService.submitForModeration(
                "song_resource_request", request.getId(), userId, "user");

        log.info("event=song_resource_request_created requestId={} userId={} quality={}",
                request.getId(), userId, audioInfo != null ? audioInfo.getQualityLevel() : null);

        return request.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRequest(Long handlerId, String handlerName, SongResourceRequestHandleDTO dto) {
        SongResourceRequest request = requestMapper.selectById(dto.getId());
        if (ObjectUtils.isEmpty(request)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "申请记录不存在");
        }

        if (!"pending".equals(request.getStatus()) && !"processing".equals(request.getStatus())) {
            throw new BusinessException(ResultCode.ERROR, "该申请已处理，无法重复处理");
        }

        request.setStatus(dto.getStatus());
        request.setHandlerId(handlerId);
        request.setHandleTime(LocalDateTime.now());
        request.setMatchedSongId(dto.getMatchedSongId());
        request.setHandleResult(dto.getHandleResult());

                              
        if ("completed".equals(dto.getStatus()) && StrUtil.isNotBlank(request.getFileUrl())) {
            Long autoSongId = autoCreateSongFromRequest(request);
            if (autoSongId == null) {
                throw new BusinessException(ResultCode.ERROR, "歌曲资源发布失败，请稍后重试");
            }
            request.setAutoSongId(autoSongId);
            request.setHandleResult((StrUtil.isNotBlank(dto.getHandleResult()) ?
                dto.getHandleResult() + "；" : "") + "已自动创建歌曲记录(ID:" + autoSongId + ")");
        }
        if ("rejected".equals(dto.getStatus())) {
            mediaAssetService.releaseTargetReferences("song_resource_request", request.getId());
        }

        if (requestMapper.updateById(request) != 1) {
            throw new BusinessException(ResultCode.ERROR, "申请处理结果保存失败，请稍后重试");
        }

        log.info("event=song_resource_request_handled requestId={} status={} handlerId={} autoSongId={}",
                dto.getId(), dto.getStatus(), handlerId, request.getAutoSongId());
    }

       
                  
       
    @Transactional(rollbackFor = Exception.class)
    public Long autoCreateSongFromRequest(SongResourceRequest request) {
        try {
                          
            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<Song>()
                .eq(Song::getName, request.getSongName());
            Song existingSong = songMapper.selectOne(wrapper);
            if (existingSong != null) {
                log.info("event=song_resource_song_publish_reused requestId={} songId={}",
                        request.getId(), existingSong.getId());
                return existingSong.getId();
            }

                                          
            Artist artist = workProcessingUtil.getOrCreateArtist(request.getArtistName());

                      
            Album album = null;
            if (StrUtil.isNotBlank(request.getAlbumName())) {
                album = getOrCreateAlbum(request.getAlbumName(), artist.getId());
            }

                     
            Song song = new Song();
            song.setName(request.getSongName());
            song.setCover(album != null ? album.getCover() : "/default-cover.png");

                                         
            Integer quality = request.getDetectedQuality() != null ? request.getDetectedQuality() : 2;
            WorkProcessingUtil.setSongQualityField(song, quality, request.getFileUrl(), request.getFileSize());

            song.setDuration(request.getDuration() != null ? request.getDuration() : 0);
            song.setAlbumId(album != null ? album.getId() : null);
            song.setVersionType(StrUtil.isNotBlank(request.getVersionInfo()) &&
                !"原版".equals(request.getVersionInfo()) ? request.getVersionInfo() : null);
            song.setCreateTime(LocalDateTime.now());
            song.setUpdateTime(LocalDateTime.now());

            if (songMapper.insert(song) != 1 || song.getId() == null) {
                throw new BusinessException(ResultCode.ERROR, "歌曲资源发布失败，请稍后重试");
            }

                                         
            workProcessingUtil.createSongArtistRelation(song.getId(), artist.getId(), artist.getName());
            officialMediaDerivativeService.submitSongDerivativeJob(song.getId(), request.getFileUrl(),
                    request.getFileSize(), request.getDetectedQuality());

            log.info("event=song_resource_song_published requestId={} songId={} quality={}",
                    request.getId(), song.getId(), request.getDetectedQuality());

            return song.getId();

        } catch (Exception e) {
            log.error("event=song_resource_song_publish_failed requestId={} errorType={}",
                    request.getId(), e.getClass().getSimpleName());
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ResultCode.ERROR, "歌曲资源发布失败，请稍后重试");
        }
    }

       
              
       
    private Album getOrCreateAlbum(String albumName, Long artistId) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<Album>()
            .eq(Album::getName, albumName);
        Album album = albumMapper.selectOne(wrapper);

        if (album == null) {
            album = new Album();
            album.setName(albumName);
            album.setCover("/default-cover.png");
            album.setArtistId(artistId);
            album.setDescription("用户上传歌曲自动创建");
            album.setReleaseDate(LocalDateTime.now().toLocalDate());
            album.setCreateTime(LocalDateTime.now());
            album.setUpdateTime(LocalDateTime.now());
            albumMapper.insert(album);
            log.info("[SongResourceRequest] 自动创建专辑: {}", albumName);
        }

        return album;
    }

    private String handleFileUpload(MultipartFile file, Long userId) {
        if (file.getSize() > songRequestConfig.getLimits().getMaxFileSize()) {
            throw new BusinessException(ResultCode.ERROR, "file size exceeds limit: " + songRequestConfig.getLimits().getMaxFileSize());
        }

        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            throw new BusinessException(ResultCode.ERROR, "文件名不能为空");
        }

                                      
        SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkFileName(originalFilename);
        if (!nameCheck.isSafe()) {
            throw new BusinessException(ResultCode.ERROR, "文件名包含非法字符");
        }

        String extension = FileUtil.extName(originalFilename);
        if (StrUtil.isBlank(extension)) {
            throw new BusinessException(ResultCode.ERROR, "无法识别文件扩展名");
        }
        extension = extension.toLowerCase();

        if (!songRequestConfig.isAllowedFormat(extension)) {
            throw new BusinessException(ResultCode.ERROR, "unsupported audio format: " + extension);
        }

        String safeFilename = IdUtil.simpleUUID() + "." + extension;

        LocalDate now = LocalDate.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String userPath = String.valueOf(userId);

        String fullPath = songRequestConfig.getUpload().getPath() + "/" + datePath + "/" + userPath;

        File targetDir = new File(fullPath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File targetFile = new File(targetDir, safeFilename);
        try {
            file.transferTo(targetFile);
                                                                
            boolean clean = virusScanService.scanFile(targetFile);
            if (!clean) {
                targetFile.delete();
                throw new BusinessException(ResultCode.ERROR, "song resource virus scan failed");
            }
                                                                                                               
            boolean synced = copyToNode2(targetFile.getAbsolutePath(), songRequestConfig.getNode2().getPath() + "/" + datePath + "/" + userPath, safeFilename);
            if (synced) {
                log.info("event=song_resource_file_synced target=node2");
            } else {
                log.warn("event=song_resource_file_sync_failed fallback=local");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("[SongResourceRequest] 文件上传失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ERROR, "文件上传失败");
        } catch (Exception e) {
            targetFile.delete();
            log.error("[SongResourceRequest] 病毒扫描失败: {}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ERROR, "virus scan failed, please try again later");
        }

        String relativePath = datePath.replace("/", "/") + "/" + userPath + "/" + safeFilename;
        if (securityConfig.isHideIp()) {
            return "/" + relativePath;
        } else {
            return songRequestConfig.getNginx().getUrl() + "/" + relativePath;
        }
    }

    private void registerMediaAsset(SongResourceRequest request) {
        if (request == null || request.getId() == null || StrUtil.isBlank(request.getFileUrl())) {
            return;
        }
        String storagePath = resolveRequestFilePath(request.getFileUrl());
        if (StrUtil.isBlank(storagePath)) {
            log.debug("event=song_resource_unmanaged_url_skipped requestId={}", request.getId());
            return;
        }
        mediaAssetService.registerAndRetain(
                "audio", "song_resource_request", request.getId(), "source",
                request.getFileUrl(), MediaAsset.STORAGE_NODE_LOCAL, storagePath,
                request.getFileSize(), "song_resource_request", request.getId());
    }

    private String resolveRequestFilePath(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }
        String cleanPrefix = trimTrailingSlash(songRequestConfig.getNginx().getUrl());
        String relativePath = null;
        if (StrUtil.isNotBlank(cleanPrefix) && fileUrl.startsWith(cleanPrefix + "/")) {
            relativePath = fileUrl.substring(cleanPrefix.length() + 1);
        } else if (fileUrl.startsWith("/")) {
            relativePath = fileUrl.substring(1);
        }
        if (relativePath == null) {
            return normalizeRequestPath(CommonUtil.extractLocalPath(fileUrl));
        }
        try {
            relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8.name())
                    .replace('\\', '/');
            Path basePath = Paths.get(songRequestConfig.getUpload().getPath()).toAbsolutePath().normalize();
            Path targetPath = basePath.resolve(relativePath).normalize();
            return targetPath.startsWith(basePath) ? normalizeRequestPath(targetPath.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeRequestPath(String path) {
        if (StrUtil.isBlank(path) || !WorkProcessingUtil.isPathSafe(path)) {
            return null;
        }
        return path;
    }

    private String trimTrailingSlash(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

       
                                                  
       
    private boolean copyToNode2(String localFilePath, String node2Dir, String filename) {
        try {
            validatePathSafety(localFilePath);
            validatePathSafety(node2Dir);
            validatePathSafety(filename);

            com.haoran.music.common.util.ProcessExecutionUtil.Result mkdirResult =
                    com.haoran.music.common.util.ProcessExecutionUtil.execute(java.util.Arrays.asList(
                            "ssh", songRequestConfig.getNode2().getUser() + "@" + songRequestConfig.getNode2().getHost(),
                            "mkdir", "-p", node2Dir),
                            songRequestConfig.getSync().getMkdirTimeoutSeconds());
            if (mkdirResult.isTimedOut()) {
                log.error("[SongResourceRequest] node2目录创建超时: {}", node2Dir);
                return false;
            }
            if (mkdirResult.getExitCode() != 0) {
                log.error("[SongResourceRequest] node2目录创建失败, exitCode={}, output={}",
                        mkdirResult.getExitCode(), mkdirResult.getOutput());
                return false;
            }

            com.haoran.music.common.util.ProcessExecutionUtil.Result scpResult =
                    com.haoran.music.common.util.ProcessExecutionUtil.execute(java.util.Arrays.asList(
                            "scp", localFilePath,
                            songRequestConfig.getNode2().getUser() + "@" + songRequestConfig.getNode2().getHost()
                                    + ":" + node2Dir + "/"),
                            songRequestConfig.getSync().getScpTimeoutSeconds());
            if (scpResult.isTimedOut()) {
                log.error("event=song_resource_scp_timeout");
                return false;
            }
            if (scpResult.getExitCode() != 0) {
                log.error("event=song_resource_scp_failed exitCode={}", scpResult.getExitCode());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[SongResourceRequest] SCP传输被中断: {}", e.getClass().getSimpleName());
            return false;
        } catch (Exception e) {
            log.error("[SongResourceRequest] SCP传输异常: {}", e.getClass().getSimpleName());
            return false;
        }
    }
       
              
       
    private void validatePathSafety(String path) {
        if (StrUtil.isBlank(path)) {
            throw new SecurityException("路径不能为空");
        }
                   
        if (path.contains("|") || path.contains("&") || path.contains(";") ||
            path.contains("$") || path.contains("`") || path.contains("\n") ||
            path.contains("\r") || path.contains("\\") && !path.contains("/")) {
            throw new SecurityException("路径包含非法字符: " + path);
        }
    }

    @Override
    public IPage<SongResourceRequestVO> getUserRequests(Long userId, PageQuery pageQuery, String status) {
        Page<SongResourceRequest> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        String normalizedStatus = StrUtil.trim(status);
        if (StrUtil.isNotBlank(normalizedStatus) && !USER_REQUEST_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申请状态无效");
        }

        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongResourceRequest::getUserId, userId)
                .eq(SongResourceRequest::getDeleted, 0);
        if (StrUtil.isNotBlank(normalizedStatus)) {
            wrapper.eq(SongResourceRequest::getStatus, normalizedStatus);
        }
        wrapper.orderByDesc(SongResourceRequest::getCreateTime);

        IPage<SongResourceRequest> resultPage = requestMapper.selectPage(page, wrapper);

        return resultPage.convert(this::convertToVO);
    }

    @Override
    public boolean hasRequestedSong(Long userId, String songName, String artistName) {
        int count = requestMapper.checkDuplicate(userId, songName, artistName);
        return count > 0;
    }

    @Override
    public int getTodayRequestCount(Long userId) {
        return requestMapper.countTodayRequests(userId);
    }

    @Override
    public IPage<SongResourceRequestVO> getAllRequests(PageQuery pageQuery, String status) {
        Page<SongResourceRequest> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(SongResourceRequest::getStatus, status);
        }
        wrapper.eq(SongResourceRequest::getDeleted, 0)
                .orderByDesc(SongResourceRequest::getCreateTime);

        IPage<SongResourceRequest> resultPage = requestMapper.selectPage(page, wrapper);

        return resultPage.convert(this::convertToVO);
    }

    @Override
    public long getPendingCount() {
        LambdaQueryWrapper<SongResourceRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SongResourceRequest::getStatus, "pending")
                .eq(SongResourceRequest::getDeleted, 0);
        return requestMapper.selectCount(wrapper);
    }

    private SongResourceRequestVO convertToVO(SongResourceRequest entity) {
        SongResourceRequestVO vo = new SongResourceRequestVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setSongName(entity.getSongName());
        vo.setArtistName(entity.getArtistName());
        vo.setAlbumName(entity.getAlbumName());
        vo.setVersionInfo(entity.getVersionInfo());
        vo.setFileUrl(entity.getFileUrl());
        vo.setSourceDescription(entity.getSourceDescription());
        vo.setRemark(entity.getRemark());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(getStatusDesc(entity.getStatus()));
        vo.setHandlerId(entity.getHandlerId());
        vo.setHandleTime(entity.getHandleTime());
        vo.setHandleResult(entity.getHandleResult());
        vo.setMatchedSongId(entity.getMatchedSongId());
        vo.setNotified(entity.getNotified() == 1);
        vo.setCreateTime(entity.getCreateTime());

                 
        vo.setDetectedQuality(entity.getDetectedQuality());
        vo.setQualityName(entity.getDetectedQuality() != null ?
            audioQualityDetector.getQualityName(entity.getDetectedQuality()) : null);
        vo.setFileSize(entity.getFileSize());
        vo.setDuration(entity.getDuration());
        vo.setBitrate(entity.getBitrate());
        vo.setSampleRate(entity.getSampleRate());
        vo.setFormat(entity.getFormat());
        vo.setAutoSongId(entity.getAutoSongId());

        if (entity.getMatchedSongId() != null) {
            Song song = songMapper.selectById(entity.getMatchedSongId());
            if (song != null) {
                vo.setMatchedSongName(song.getName());
            }
        }
        if (entity.getAutoSongId() != null) {
            Song autoSong = songMapper.selectById(entity.getAutoSongId());
            if (autoSong != null) {
                vo.setAutoSongName(autoSong.getName());
            }
        }

        return vo;
    }

    private String getStatusDesc(String status) {
        switch (status) {
            case "pending": return "待处理";
            case "processing": return "处理中";
            case "completed": return "已完成";
            case "rejected": return "已拒绝";
            default: return status;
        }
    }
}
