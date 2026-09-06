package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.common.config.WorkSubmissionConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MusicSquareWorkService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.ModerationIntegrationService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.SubmissionFileSecurityService;
import com.haoran.music.service.VirusScanService;
import com.haoran.music.common.util.AudioQualityDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.haoran.music.common.dto.PageQuery;





@Slf4j
@Service
public class MusicSquareWorkServiceImpl extends ServiceImpl<MusicSquareWorkMapper, MusicSquareWork>
        implements MusicSquareWorkService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_REJECTED = 2;

    @Autowired
    private MusicSquareWorkMapper musicSquareWorkMapper;

    @Autowired
    private MusicSquareWorkLikeMapper musicSquareWorkLikeMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private MVMapper mvMapper;

    @Autowired
    private LyricMapper lyricMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VirusScanService virusScanService;

    @Autowired
    private AudioQualityDetector audioQualityDetector;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserExtensionMapper userExtensionMapper;

    @Autowired
    private WorkProcessingUtil workProcessingUtil;

    @Autowired
    private WorkSubmissionConfig workSubmissionConfig;

    @Autowired
    private OfficialMediaDerivativeService officialMediaDerivativeService;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private ModerationIntegrationService moderationIntegrationService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SubmissionFileSecurityService submissionFileSecurityService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.haoran.music.service.ArtistProfileService artistProfileService;




    @Value("${audio.ffmpeg.path}")
    private String ffprobePath;




    @Value("${audio.ffmpeg.detect-timeout}")
    private int detectTimeout;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitWork(Long userId, MusicSquareWorkDTO dto) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "提交投稿");


        if (!canSubmit(userId)) {
            throw new BusinessException(ResultCode.ERROR,
                    "music square daily submit limit reached: " + workSubmissionConfig.getMusicSquareDailyLimit());
        }


        checkXSS(dto);

        MusicSquareWork work = new MusicSquareWork();
        work.setUserId(userId);



        UserExtension userExt = userExtensionMapper.selectOne(
            new LambdaQueryWrapper<UserExtension>().eq(UserExtension::getUserId, userId)
        );

        User userInfo = userMapper.selectById(userId);
        work.setUserName(userInfo != null && StrUtil.isNotBlank(userInfo.getNickname()) ? userInfo.getNickname() : "未知用户");

        work.setWorkType(dto.getWorkType());
        work.setTitle(dto.getTitle());
        work.setName(dto.getTitle());
        work.setDescription(SecurityCheckUtil.escapeHtml(dto.getDescription()));
        work.setCoverUrl(dto.getCoverUrl());
        work.setArtistNames(dto.getTags());
        work.setStatus(0);       
        work.setCreateTime(LocalDateTime.now());
        work.setViewCount(0L);
        work.setLikeCount(0);
        work.setCommentCount(0);
        work.setShareCount(0);


        if (dto.getAudioUrl() != null) {
            File audioFile = submissionFileSecurityService.resolveSubmissionFile(userId, dto.getAudioUrl());
            work.setAudioUrl(dto.getAudioUrl());

            try {
                AudioQualityDetector.AudioInfo audioInfo = audioQualityDetector.detectAudioInfo(audioFile.getAbsolutePath());
                work.setAudioQuality(audioInfo.getQualityLevel());
                work.setAudioSize(audioInfo.getFileSize());
                work.setAudioDuration(audioInfo.getDuration());
                work.setAudioBitrate(audioInfo.getBitrate());
                work.setAudioSampleRate(audioInfo.getSampleRate());
                work.setAudioFormat(audioInfo.getFormat());
                log.info("[MusicSquare] 音质检测: {} -> {}", dto.getTitle(), audioInfo.getQualityName());
            } catch (Exception e) {
                log.warn("[MusicSquare] 音质检测失败: {}", e.getClass().getSimpleName());
            }


            if (!scanFile(audioFile)) {
                throw new BusinessException(ResultCode.ERROR, "音频文件安全检测未通过");
            }
        }


        if (dto.getVideoUrl() != null) {
            File videoFile = submissionFileSecurityService.resolveSubmissionFile(userId, dto.getVideoUrl());
            work.setVideoUrl(dto.getVideoUrl());

            try {
                VideoInfo videoInfo = detectVideoFile(videoFile);
                work.setVideoSize(videoInfo.getFileSize());
                work.setVideoDuration(videoInfo.getDuration());
                work.setVideoQuality(videoInfo.getQuality());
                work.setVideoFormat(videoInfo.getFormat());
                log.info("[MusicSquare] 视频检测: {} -> {} {}", dto.getTitle(),
                    videoInfo.getQuality(), videoInfo.getFormat());
            } catch (Exception e) {
                log.warn("[MusicSquare] 视频检测失败: {}", e.getClass().getSimpleName());
            }


            if (!scanFile(videoFile)) {
                throw new BusinessException(ResultCode.ERROR, "视频文件安全检测未通过");
            }
        }


        if (StrUtil.isNotBlank(dto.getLyricContent())) {
            work.setLyricContent(SecurityCheckUtil.escapeHtml(dto.getLyricContent()));
        } else if (StrUtil.isNotBlank(dto.getLyricFileUrl())) {
            work.setLyricFileUrl(dto.getLyricFileUrl());
        }
        work.setHasTranslation(StrUtil.isNotBlank(dto.getLyricContent()) ||
                               StrUtil.isNotBlank(dto.getLyricFileUrl()) ? 1 : 0);





        musicSquareWorkMapper.insert(work);
        registerMediaAssets(work);
        moderationIntegrationService.submitForModeration(
                "music_square_work", work.getId(), userId, "user");


        String submitKey = workSubmissionConfig.getMusicSquarePrefix() + userId;
        Long count = redisTemplate.opsForValue().increment(submitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(submitKey, workSubmissionConfig.getSubmitWindowDays(), TimeUnit.DAYS);
        }

        log.info("[MusicSquare] 用户提交投稿: userId={}, title={}, type={}",
            userId, dto.getTitle(), dto.getWorkType());

        return work.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason) {
        if (!isReviewStatus(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "review status must be 1 or 2");
        }

        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }
        if (musicSquareWorkMapper.reviewPendingWork(workId, reviewerId, status, reviewReason) != 1) {
            throw new BusinessException(409, "投稿已被其他审核人处理");
        }


        if (status.equals(STATUS_PUBLISHED)) {
            work.setPublishTime(LocalDateTime.now());


            if (work.getWorkType() == 1 || work.getWorkType() == 3) {

                if (StrUtil.isNotBlank(work.getAudioUrl())) {
                    Long songId = createSongFromWork(work);
                    if (songId != null) {
                        work.setRelatedSongId(songId);
                    }
                }
            } else if (work.getWorkType() == 2) {

                if (StrUtil.isNotBlank(work.getVideoUrl())) {
                    Long mvId = createMVFromWork(work);
                    if (mvId != null) {
                        work.setRelatedMvId(mvId);
                    }
                }
            }


            if (StrUtil.isNotBlank(work.getLyricContent()) && work.getRelatedSongId() != null) {
                saveLyric(work.getRelatedSongId(), work.getLyricContent());
            }
        } else if (status.equals(STATUS_REJECTED)) {
            mediaAssetService.releaseTargetReferences("music_square_work", workId);
        }

        if (status.equals(STATUS_PUBLISHED)
                && musicSquareWorkMapper.updateReviewRelations(
                        workId, work.getRelatedSongId(), work.getRelatedMvId()) != 1) {
            throw new BusinessException(409, "审核派生关联写入失败");
        }
        log.info("[MusicSquare] 审核投稿: workId={}, status={}, songId={}, mvId={}",
            workId, status, work.getRelatedSongId(), work.getRelatedMvId());
    }




    private Long createSongFromWork(MusicSquareWork work) {
        try {

            Song song = new Song();
            song.setName(work.getName());
            song.setCover(StrUtil.isNotBlank(work.getCoverUrl()) ? work.getCoverUrl() : "/default-cover.png");


            Integer quality = work.getAudioQuality() != null ? work.getAudioQuality() : 2;
            WorkProcessingUtil.setSongQualityField(song, quality, work.getAudioUrl(),
                work.getAudioSize());

            song.setDuration(work.getAudioDuration() != null ? work.getAudioDuration() : 0);

            song.setArtistNames(work.getTags());

            song.setCreateTime(LocalDateTime.now());
            song.setUpdateTime(LocalDateTime.now());

            songMapper.insert(song);
            officialMediaDerivativeService.submitSongDerivativeJob(song.getId(), work.getAudioUrl(),
                    work.getAudioSize(), work.getAudioQuality());


            Artist artist = artistProfileService.resolveOwnedProfile(
                    work.getUserId(), work.getUserName(), "music_square_work");
            workProcessingUtil.createSongArtistRelation(song.getId(), artist.getId(), artist.getName());

            log.info("event=music_square_song_published songId={}", song.getId());
            return song.getId();

        } catch (Exception e) {
            log.error("[MusicSquare] 创建歌曲失败: {}", work.getName());
            return null;
        }
    }




    private Long createMVFromWork(MusicSquareWork work) {
        try {

            LambdaQueryWrapper<MV> wrapper = new LambdaQueryWrapper<MV>()
                .eq(MV::getName, work.getName());
            MV existingMV = mvMapper.selectOne(wrapper);
            if (existingMV != null) {
                return existingMV.getId();
            }


            MV mv = new MV();
            mv.setName(work.getName());
            mv.setCover(StrUtil.isNotBlank(work.getCoverUrl()) ? work.getCoverUrl() : "/default-cover.png");


            String quality = work.getVideoQuality() != null ? work.getVideoQuality() : "720p";
            setMVField(mv, quality, work.getVideoUrl());

            mv.setDuration(work.getVideoDuration() != null ? work.getVideoDuration() : 0);
            mv.setDescription(work.getDescription());
            mv.setArtistNames(work.getTags());
            mv.setPublishDate(LocalDateTime.now().toLocalDate());

            mv.setCreateTime(LocalDateTime.now());
            mv.setUpdateTime(LocalDateTime.now());

            mvMapper.insert(mv);
            officialMediaDerivativeService.submitMvDerivativeJob(mv.getId(), work.getVideoUrl());

            log.info("[MusicSquare] 自动创建MV: mvId={}, title={}", mv.getId(), mv.getName());
            return mv.getId();

        } catch (Exception e) {
            log.error("[MusicSquare] 创建MV失败: {}", work.getName());
            return null;
        }
    }




    private void setMVField(MV mv, String quality, String value) {
        try {
            String fieldName = WorkProcessingUtil.getVideoQualityField(quality);
            java.lang.reflect.Field field = mv.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(mv, value);
        } catch (Exception e) {
            log.warn("设置MV字段失败: quality={}, value={}", quality, value);
        }
    }




    private void saveLyric(Long songId, String content) {
        Lyric lyric = new Lyric();
        lyric.setSongId(songId);
        lyric.setContent(content);
        lyric.setLanguage("zh-CN");
        lyric.setLyricType(1);

        lyric.setCreateTime(LocalDateTime.now());
        lyricMapper.insert(lyric);
    }

    @Override
    public IPage<MusicSquareWork> pageWorks(PageQuery pageQuery, Integer workType, Integer status) {
        Page<MusicSquareWork> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<MusicSquareWork> wrapper = new LambdaQueryWrapper<MusicSquareWork>()
            .eq(MusicSquareWork::getDeleted, 0)
            .exists(publicUserExistsSql("user_id"))
            .orderByDesc(MusicSquareWork::getCreateTime);

        if (workType != null) {
            wrapper.eq(MusicSquareWork::getWorkType, workType);
        }
        if (status != null) {
            wrapper.eq(MusicSquareWork::getStatus, status);
        }

        return musicSquareWorkMapper.selectPage(page, wrapper);
    }

    @Override
    public MusicSquareWork getVisibleWorkDetail(Long workId, Long viewerId) {
        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null || Integer.valueOf(1).equals(work.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "music square work not found");
        }

        if (Integer.valueOf(STATUS_PUBLISHED).equals(work.getStatus()) && canPublicAuthor(work.getUserId())) {
            return work;
        }
        if (viewerId != null && viewerId.equals(work.getUserId())) {
            return work;
        }
        if (canModerate(viewerId)) {
            return work;
        }

        throw new BusinessException(ResultCode.NOT_FOUND, "music square work not found");
    }

    @Override
    public IPage<MusicSquareWork> getMyWorks(Long userId, PageQuery pageQuery) {
        Page<MusicSquareWork> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<MusicSquareWork> wrapper = new LambdaQueryWrapper<MusicSquareWork>()
            .eq(MusicSquareWork::getUserId, userId)
            .eq(MusicSquareWork::getDeleted, 0)
            .orderByDesc(MusicSquareWork::getCreateTime);

        return musicSquareWorkMapper.selectPage(page, wrapper);
    }

    @Override
    public Long getPendingCount() {
        return musicSquareWorkMapper.selectCount(
            new LambdaQueryWrapper<MusicSquareWork>()
                .eq(MusicSquareWork::getStatus, 0)
                .eq(MusicSquareWork::getDeleted, 0)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeWork(Long workId, Long userId) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "点赞投稿");

        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null || !Integer.valueOf(1).equals(work.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }
        if (!canPublicAuthor(work.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }

        int liked = musicSquareWorkLikeMapper.insertIgnore(workId, userId);

        if (liked > 0
                && canCurrentUserContributePublicStats(userId)
                && canPublicAuthor(work.getUserId())) {
            musicSquareWorkMapper.incrementLikeCount(workId);
        }
        if (liked > 0) {
            User liker = userMapper.selectById(userId);
            String likerName = liker != null && StrUtil.isNotBlank(liker.getNickname())
                    ? liker.getNickname().trim()
                    : liker != null && StrUtil.isNotBlank(liker.getUsername()) ? liker.getUsername().trim() : "一位镇民";
            notificationService.sendLikeNotification(
                    work.getUserId(), userId, likerName, "music-square-work", workId,
                    StrUtil.isNotBlank(work.getTitle()) ? work.getTitle() : work.getName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeWork(Long workId, Long userId) {
        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }

        int deleted = musicSquareWorkLikeMapper.deleteByWorkAndUser(workId, userId);

        if (deleted > 0
                && canCurrentUserContributePublicStats(userId)
                && canPublicAuthor(work.getUserId())) {
            musicSquareWorkMapper.decrementLikeCount(workId);
        }
        if (deleted > 0) {
            notificationService.revokeLikeNotification(work.getUserId(), userId, workId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(Long workId, Long userId) {
        if (userId != null && !canCurrentUserContributePublicStats(userId)) {
            return;
        }
        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null
                || !Integer.valueOf(STATUS_PUBLISHED).equals(work.getStatus())
                || !canPublicAuthor(work.getUserId())) {
            return;
        }
        musicSquareWorkMapper.incrementViewCount(workId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(Long workId, Long userId) {
        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }

        if (!work.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ERROR, "无权删除他人投稿");
        }


        if (work.getStatus() == STATUS_PUBLISHED) {
            throw new BusinessException(ResultCode.ERROR, "已发布的投稿不能删除");
        }

        musicSquareWorkMapper.deleteById(workId);
        mediaAssetService.releaseTargetReferences("music_square_work", workId);
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWork(Long workId, Long userId, MusicSquareWorkDTO dto) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "编辑投稿");
        MusicSquareWork work = musicSquareWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿记录不存在");
        }

        if (!work.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ERROR, "无权编辑他人投稿");
        }


        if (work.getStatus() != STATUS_PENDING) {
            throw new BusinessException(ResultCode.ERROR, "只有待审核状态的投稿可以编辑");
        }


        checkXSS(dto);

        boolean audioChanged = StrUtil.isNotBlank(dto.getAudioUrl())
                && !dto.getAudioUrl().equals(work.getAudioUrl());
        boolean videoChanged = StrUtil.isNotBlank(dto.getVideoUrl())
                && !dto.getVideoUrl().equals(work.getVideoUrl());
        boolean lyricFileChanged = StrUtil.isNotBlank(dto.getLyricFileUrl())
                && !dto.getLyricFileUrl().equals(work.getLyricFileUrl());
        boolean mediaChanged = audioChanged || videoChanged || lyricFileChanged
                || StrUtil.isNotBlank(dto.getCoverUrl());


        if (StrUtil.isNotBlank(dto.getTitle())) {
            work.setTitle(dto.getTitle());
            work.setName(dto.getTitle());
        }
        if (StrUtil.isNotBlank(dto.getDescription())) {
            work.setDescription(SecurityCheckUtil.escapeHtml(dto.getDescription()));
        }
        if (StrUtil.isNotBlank(dto.getCoverUrl())) {
            work.setCoverUrl(dto.getCoverUrl());
        }
        if (StrUtil.isNotBlank(dto.getTags())) {
            work.setArtistNames(dto.getTags());
        }


        if (StrUtil.isNotBlank(dto.getAudioUrl()) && !dto.getAudioUrl().equals(work.getAudioUrl())) {
            File audioFile = submissionFileSecurityService.resolveSubmissionFile(userId, dto.getAudioUrl());
            work.setAudioUrl(dto.getAudioUrl());

            try {
                AudioQualityDetector.AudioInfo audioInfo = audioQualityDetector.detectAudioInfo(audioFile.getAbsolutePath());
                work.setAudioQuality(audioInfo.getQualityLevel());
                work.setAudioSize(audioInfo.getFileSize());
                work.setAudioDuration(audioInfo.getDuration());
                work.setAudioBitrate(audioInfo.getBitrate());
                work.setAudioSampleRate(audioInfo.getSampleRate());
                work.setAudioFormat(audioInfo.getFormat());
                log.info("[MusicSquare] 编辑后音质检测: {} -> {}", dto.getTitle(), audioInfo.getQualityName());
            } catch (Exception e) {
                log.warn("[MusicSquare] 音质检测失败: {}", e.getClass().getSimpleName());
            }


            if (!scanFile(audioFile)) {
                throw new BusinessException(ResultCode.ERROR, "音频文件安全检测未通过");
            }
        }


        if (StrUtil.isNotBlank(dto.getVideoUrl()) && !dto.getVideoUrl().equals(work.getVideoUrl())) {
            File videoFile = submissionFileSecurityService.resolveSubmissionFile(userId, dto.getVideoUrl());
            work.setVideoUrl(dto.getVideoUrl());

            try {
                VideoInfo videoInfo = detectVideoFile(videoFile);
                work.setVideoSize(videoInfo.getFileSize());
                work.setVideoDuration(videoInfo.getDuration());
                work.setVideoQuality(videoInfo.getQuality());
                work.setVideoFormat(videoInfo.getFormat());
                log.info("[MusicSquare] 编辑后视频检测: {} -> {}", dto.getTitle(),
                    videoInfo.getQuality());
            } catch (Exception e) {
                log.warn("[MusicSquare] 视频检测失败: {}", e.getClass().getSimpleName());
            }


            if (!scanFile(videoFile)) {
                throw new BusinessException(ResultCode.ERROR, "视频文件安全检测未通过");
            }
        }


        if (StrUtil.isNotBlank(dto.getLyricContent())) {
            work.setLyricContent(SecurityCheckUtil.escapeHtml(dto.getLyricContent()));
            work.setHasTranslation(1);
        } else if (StrUtil.isNotBlank(dto.getLyricFileUrl())) {
            work.setLyricFileUrl(dto.getLyricFileUrl());
            work.setHasTranslation(1);
        }

        work.setUpdateTime(LocalDateTime.now());
        if (musicSquareWorkMapper.updateById(work) <= 0) {
            throw new IllegalStateException("投稿更新失败");
        }
        if (mediaChanged) {
            mediaAssetService.releaseTargetReferences("music_square_work", workId);
            registerMediaAssets(work);
        }

        log.info("[MusicSquare] 编辑投稿: workId={}, userId={}, title={}",
            workId, userId, work.getName());
    }

    private void registerMediaAssets(MusicSquareWork work) {
        if (work == null || work.getId() == null) {
            return;
        }
        mediaAssetService.registerSubmissionAssets(
                "audio", "music_square_work", work.getId(), work.getAudioUrl(),
                null, null, work.getAudioSize());
        mediaAssetService.registerSubmissionAssets(
                "video", "music_square_work", work.getId(), work.getVideoUrl(),
                null, null, work.getVideoSize());
        mediaAssetService.registerSubmissionAssets(
                "lyric", "music_square_work", work.getId(), work.getLyricFileUrl(),
                null, null, null);
        mediaAssetService.registerSubmissionAssets(
                "image", "music_square_work", work.getId(), work.getCoverUrl(),
                null, null, null);
    }

    @Override
    public AudioQualityDetector.AudioInfo detectAudioInfo(Long userId, String audioUrl) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "检测音频");
        File ownedFile = submissionFileSecurityService.resolveSubmissionFile(userId, audioUrl);
        return audioQualityDetector.detectAudioInfo(ownedFile.getAbsolutePath());
    }

    @Override
    public VideoInfo detectVideoInfo(Long userId, String videoUrl) {
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "检测视频");
        File ownedFile = submissionFileSecurityService.resolveSubmissionFile(userId, videoUrl);
        return detectVideoFile(ownedFile);
    }

    private VideoInfo detectVideoFile(File ownedFile) {
        VideoInfo info = new VideoInfo();
        String localPath = ownedFile.getAbsolutePath();
        info.setFileSize(ownedFile.length());

        try {
            com.haoran.music.common.util.ProcessExecutionUtil.Result processResult =
                    com.haoran.music.common.util.ProcessExecutionUtil.execute(Arrays.asList(
                            ffprobePath, "-v", "quiet",
                            "-print_format", "json",
                            "-show_streams", "-show_format", localPath),
                            Math.max(1, detectTimeout / 1000));
            if (processResult.isTimedOut()) {
                return info;
            }


            if (processResult.getExitCode() == 0) {
                parseVideoInfo(processResult.getOutput(), info);
            }

        } catch (Exception e) {
            log.warn("[MusicSquare] 视频检测失败: {}", e.getClass().getSimpleName());
        }

        return info;
    }




    private void parseVideoInfo(String jsonOutput, VideoInfo info) {

        Double duration = extractDouble(jsonOutput, "\"duration\"\\s*:\\s*(\\d+\\.?\\d*)");
        if (duration != null) {
            info.setDuration(duration.intValue());
        }


        Integer width = extractInt(jsonOutput, "\"width\"\\s*:\\s*(\\d+)");
        Integer height = extractInt(jsonOutput, "\"height\"\\s*:\\s*(\\d+)");

        if (width != null && height != null) {
            info.setWidth(width);
            info.setHeight(height);
            info.setQuality(determineVideoQuality(width, height));
        }


        String codec = extractString(jsonOutput, "\"codec_name\"\\s*:\\s*\"([^\"]+)\"");
        if (codec != null) {
            info.setFormat(codec);
        }
    }




    private String determineVideoQuality(Integer width, Integer height) {
        int minDim = Math.min(width, height);
        if (minDim >= 2160) return "4k";
        if (minDim >= 1080) return "1080p";
        if (minDim >= 720) return "720p";
        if (minDim >= 480) return "480p";
        return "360p";
    }

    private Integer extractInt(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Double extractDouble(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractString(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean canSubmit(Long userId) {
        String submitKey = workSubmissionConfig.getMusicSquarePrefix() + userId;
        String count = redisTemplate.opsForValue().get(submitKey);
        return count == null || Integer.parseInt(count) < workSubmissionConfig.getMusicSquareDailyLimit();
    }

    private boolean canPublicAuthor(Long userId) {
        if (userId == null) {
            return false;
        }
        return UserAccountStatusUtil.canExposePublicContent(userId, userMapper::selectById);
    }

    private boolean canCurrentUserContributePublicStats(Long userId) {
        return UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById);
    }

    private boolean canModerate(Long userId) {
        return userId != null && permissionService != null && permissionService.isModerator(userId);
    }

    private boolean isReviewStatus(Integer status) {
        return Integer.valueOf(STATUS_PUBLISHED).equals(status)
                || Integer.valueOf(STATUS_REJECTED).equals(status);
    }

    private String publicUserExistsSql(String userIdColumn) {
        return PublicStatsSql.USER_EXISTS_PREFIX + userIdColumn + PublicStatsSql.RETAINED_PUBLIC_CONTENT_FILTER;
    }




    private void checkXSS(MusicSquareWorkDTO dto) {
        if (SecurityCheckUtil.containsXSS(dto.getTitle()) ||
            SecurityCheckUtil.containsXSS(dto.getDescription()) ||
            SecurityCheckUtil.containsXSS(dto.getTags()) ||
            SecurityCheckUtil.containsXSS(dto.getLyricContent())) {
            throw new BusinessException(ResultCode.ERROR, "内容包含非法字符");
        }
    }

    private boolean scanFile(File file) {
        try {
            if (file == null || !file.isFile()) {
                log.warn("owned submission file is unavailable for antivirus scan");
                return false;
            }
            return virusScanService.scanFile(file);
        } catch (Exception e) {
            log.warn("owned submission file antivirus scan failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
