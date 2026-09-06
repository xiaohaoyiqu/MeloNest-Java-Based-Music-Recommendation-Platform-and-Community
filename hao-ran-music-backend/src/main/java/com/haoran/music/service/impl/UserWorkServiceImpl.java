package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.config.UserWorkRewardConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Lyric;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserWork;
import com.haoran.music.mapper.LyricMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserWorkMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.IUserWorkService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.ModerationIntegrationService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import com.haoran.music.service.SubmissionAlbumPublishService;
import com.haoran.music.service.SubmissionFileSecurityService;
import com.haoran.music.service.UserPrivateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

   
                      
                         
   
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWorkServiceImpl extends ServiceImpl<UserWorkMapper, UserWork> implements IUserWorkService {

    private final UserWorkRewardConfig rewardConfig;
    private final SongMapper songMapper;
    private final LyricMapper lyricMapper;
    private final WorkProcessingUtil workProcessingUtil;
    private final MediaAssetService mediaAssetService;
    private final ModerationIntegrationService moderationIntegrationService;
    private final OfficialMediaDerivativeService officialMediaDerivativeService;
    private final SubmissionAlbumPublishService submissionAlbumPublishService;
    private final SubmissionFileSecurityService submissionFileSecurityService;
    private final UserMapper userMapper;

    @javax.annotation.Resource
    private com.haoran.music.service.NotificationService notificationService;

    @javax.annotation.Resource
    private com.haoran.music.service.ArtistProfileService artistProfileService;

    @javax.annotation.Resource
    private UserPrivateService userPrivateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserWork submitWork(UserWork userWork) {
        User actor = userMapper.selectById(userWork.getUserId());
        UserAccountStatusUtil.requireCanInteract(actor, "提交用户投稿");

        userWork.setId(null);
        userWork.setNickname(actor.getNickname());
        userWork.setAvatar(actor.getAvatar());
        userWork.setShowRealName(normalizeRealNameDisplay(userWork.getUserId(), userWork.getShowRealName()));
        userWork.setSource(1);
        userWork.setStatus(0);       
        userWork.setReviewerId(null);
        userWork.setReviewerName(null);
        userWork.setReviewTime(null);
        userWork.setReviewReason(null);
        userWork.setPublishTime(null);
        userWork.setSongId(null);
        userWork.setPlayCount(0L);
        userWork.setLikeCount(0);
        userWork.setCollectCount(0);
        userWork.setShareCount(0);
        userWork.setDownloadCount(0);
        userWork.setVirusScanned(false);
        userWork.setVirusScanResult(null);
        userWork.setRewardPoints(0);
        userWork.setCreateTime(null);
        userWork.setUpdateTime(null);
        userWork.setDeleted(0);
        if (!save(userWork)) {
            throw new BusinessException(ResultCode.ERROR, "投稿保存失败，请稍后重试");
        }
        registerMediaAssets(userWork);
        moderationIntegrationService.submitForModeration(
                "user_work", userWork.getId(), userWork.getUserId(), "user");
        return userWork;
    }

    @Override
    public List<UserWork> getMyWorks(Long userId) {
        LambdaQueryWrapper<UserWork> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserWork::getUserId, userId)
                .eq(UserWork::getDeleted, 0)
                .orderByDesc(UserWork::getCreateTime);
        List<UserWork> works = list(wrapper);
        submissionAlbumPublishService.enrichUserWorks(works);
        return works;
    }

    @Override
    public UserWork getWorkDetail(Long workId) {
        UserWork work = getById(workId);
        submissionAlbumPublishService.enrichUserWorks(work != null
                ? Collections.singletonList(work)
                : Collections.emptyList());
        return work;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWork(UserWork userWork) {
                     
        UserWork existing = getById(userWork.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能编辑待审核的投稿");
        }
        if (!existing.getUserId().equals(userWork.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权编辑此投稿");
        }

        boolean submissionFileChanged = userWork.getWorkType() != null
                || userWork.getFileUrl() != null
                || userWork.getFileUrls() != null
                || userWork.getZipFileUrl() != null;
        if (submissionFileChanged) {
            Integer targetWorkType = userWork.getWorkType() != null ? userWork.getWorkType() : existing.getWorkType();
            String targetFileUrl = userWork.getFileUrl() != null ? userWork.getFileUrl() : existing.getFileUrl();
            String targetFileUrls = userWork.getFileUrls() != null ? userWork.getFileUrls() : existing.getFileUrls();
            validateSubmissionFiles(existing.getUserId(), targetWorkType, targetFileUrl, targetFileUrls, false);
        }

        boolean mediaChanged = userWork.getFileUrl() != null
                || userWork.getFileUrls() != null
                || userWork.getZipFileUrl() != null
                || userWork.getCoverUrl() != null
                || userWork.getLyricFileUrl() != null;
        if (mediaChanged) {
            mediaAssetService.releaseTargetReferences("user_work", existing.getId());
        }

        if (userWork.getShowRealName() != null) {
            userWork.setShowRealName(normalizeRealNameDisplay(existing.getUserId(), userWork.getShowRealName()));
        }

        UserWork editable = buildEditableUpdate(userWork);
        boolean updated = updateById(editable);
        if (!updated) {
            throw new BusinessException(ResultCode.ERROR, "投稿更新失败，请稍后重试");
        }
        if (mediaChanged) {
            registerMediaAssets(baseMapper.selectById(userWork.getId()));
        }
        return true;
    }

    private UserWork buildEditableUpdate(UserWork request) {
        UserWork editable = new UserWork();
        editable.setId(request.getId());
        editable.setWorkType(request.getWorkType());
        editable.setWorkName(request.getWorkName());
        editable.setCoverUrl(request.getCoverUrl());
        editable.setFileUrl(request.getFileUrl());
        editable.setFileUrls(request.getFileUrls());
        editable.setZipFileUrl(request.getZipFileUrl());
        editable.setLyricFileUrl(request.getLyricFileUrl());
        editable.setUploadType(request.getUploadType());
        editable.setVersionType(request.getVersionType());
        editable.setProductionType(request.getProductionType());
        editable.setDescription(request.getDescription());
        editable.setTags(request.getTags());
        editable.setLanguage(request.getLanguage());
        editable.setLyric(request.getLyric());
        editable.setShowRealName(request.getShowRealName());
        editable.setAllowDownload(request.getAllowDownload());
        editable.setAllowComment(request.getAllowComment());
        editable.setAllowShare(request.getAllowShare());
        return editable;
    }

       
                                                  
       
    private Boolean normalizeRealNameDisplay(Long userId, Boolean requested) {
        if (!Boolean.TRUE.equals(requested) || userId == null || userPrivateService == null) {
            return false;
        }
        try {
            UserPrivateService.UserPrivateDTO privateInfo = userPrivateService.getUserPrivateInfo(userId);
            boolean available = privateInfo != null
                    && Boolean.TRUE.equals(privateInfo.getRealNameVerified())
                    && StrUtil.isNotBlank(privateInfo.getRealName());
            if (!available) {
                log.info("event=user_work_real_name_fallback userId={} reason=unverified_or_missing", userId);
            }
            return available;
        } catch (RuntimeException ex) {
            log.warn("event=user_work_real_name_fallback userId={} reason=private_profile_unavailable errorType={}",
                    userId, ex.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWork(Long workId, Long userId) {
        UserWork work = getById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (!work.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除此投稿");
        }
        boolean removed = removeById(workId);
        if (removed) {
            mediaAssetService.releaseTargetReferences("user_work", workId);
        }
        return removed;
    }

    @Override
    public List<UserWork> getPendingWorks() {
        return baseMapper.getPendingWorks();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason) {
        if (workId == null || reviewerId == null || status == null || (status != 1 && status != 2)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核参数不合法");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核原因不能超过1000字");
        }
        UserWork work = getById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (work.getStatus() != 0) {
            throw new BusinessException(409, "该投稿已审核");
        }

        work.setStatus(status);
        work.setReviewerId(reviewerId);
        work.setReviewReason(reviewReason);
        work.setReviewTime(java.time.LocalDateTime.now());

        if (baseMapper.reviewPendingWork(work) != 1) {
            throw new BusinessException(409, "投稿已被其他审核人处理");
        }
        if (status == 1) {
                                       
            work.setPublishTime(java.time.LocalDateTime.now());
            if (isSingleLikeWork(work.getWorkType())) {
                submissionFileSecurityService.validateSingleAudioFile(work.getUserId(), work.getFileUrl(), true);
                Long songId = createSongFromSingleWork(work);
                if (songId == null) {
                    throw new BusinessException(ResultCode.ERROR, "投稿发布失败，请稍后重试");
                }
                work.setSongId(songId);
            } else if (isAlbumLikeWork(work.getWorkType())) {
                SubmissionAlbumPublishService.AlbumPublishResult result =
                        submissionAlbumPublishService.publishUserAlbum(work);
                if (result.getFirstSongId() != null) {
                    work.setSongId(result.getFirstSongId());
                }
            }
                                         
            Integer points = rewardConfig.calculateRewardPoints(
                work.getLikeCount(),
                work.getCollectCount(),
                work.getPlayCount() != null ? work.getPlayCount().intValue() : 0
            );
            work.setRewardPoints(points);
        } else if (status == 2) {
            mediaAssetService.releaseTargetReferences("user_work", workId);
        }

        if (!updateById(work)) {
            throw new BusinessException(ResultCode.ERROR, "投稿审核结果保存失败，请稍后重试");
        }
        moderationIntegrationService.completeTargetReview(
                "user_work", workId, reviewerId,
                status == 1 ? "approved" : "rejected", reviewReason);
        notificationService.sendModerationResultNotificationOnce(
                work.getUserId(), "用户投稿", work.getWorkName(), workId,
                status == 1, reviewReason, "moderation:user_work:" + workId);
        return true;
    }
       
                                                                
      
                                     
                                                              
       
    private Long createSongFromSingleWork(UserWork work) {
        if (!isSingleLikeWork(work.getWorkType())) {
            log.info("[UserWork] skip auto song for non-single work: workId={}, workType={}",
                    work.getId(), work.getWorkType());
            return null;
        }
        if (work.getUploadType() != null && work.getUploadType() != 1) {
            log.info("[UserWork] skip auto song for non-single upload: workId={}, uploadType={}",
                    work.getId(), work.getUploadType());
            return null;
        }
        if (StrUtil.isBlank(work.getFileUrl())) {
            log.info("event=user_work_song_publish_skipped workId={} reason=file_url_missing", work.getId());
            return null;
        }

        try {
            String artistName = StrUtil.isNotBlank(work.getNickname()) ? work.getNickname() : "user_" + work.getUserId();
            Artist artist = artistProfileService.resolveOwnedProfile(
                    work.getUserId(), artistName, "user_work");

            Song song = new Song();
            song.setName(work.getWorkName());
            song.setCover(StrUtil.isNotBlank(work.getCoverUrl()) ? work.getCoverUrl() : "/default-cover.png");
            Integer quality = work.getQualityType() != null ? work.getQualityType() : 2;
            WorkProcessingUtil.setSongQualityField(song, quality, work.getFileUrl(), work.getFileSize());
            song.setDuration(work.getDuration() != null ? work.getDuration() : 0);
            song.setUploaderId(work.getUserId());
            song.setArtistId(artist.getId());
            song.setArtistIds(String.valueOf(artist.getId()));
            song.setArtistNames(artist.getName());
            song.setDescription(work.getDescription());
            song.setTags(work.getTags());
            song.setLanguage(mapLanguage(work.getLanguage()));
            song.setVersionType(StrUtil.blankToDefault(work.getVersionType(), "user_upload"));
            song.setVersionName(work.getProductionType());
            song.setHasLyric(StrUtil.isNotBlank(work.getLyric()) ? CommonConstants.YES : CommonConstants.NO);
            song.setPlayCount(0L);
            song.setFavoriteCount(0L);
            song.setCommentCount(0L);
            song.setLikeCount(0L);
            song.setReplyCount(0L);
            song.setShareCount(0L);
            song.setDownloadCount(0L);
            song.setPriority(0);
            song.setIsVipOnly(CommonConstants.NO);
            song.setIsPaid(0);
            song.setIsSingle(CommonConstants.YES);
            song.setIsNew(CommonConstants.YES);
            song.setIsHot(CommonConstants.NO);
            song.setStatus(CommonConstants.STATUS_NORMAL);
            song.setDeleted(CommonConstants.NOT_DELETED);
            song.setCreateTime(LocalDateTime.now());
            song.setUpdateTime(LocalDateTime.now());

            songMapper.insert(song);
            officialMediaDerivativeService.submitSongDerivativeJob(song.getId(), work.getFileUrl(),
                    work.getFileSize(), work.getQualityType());
            workProcessingUtil.createSongArtistRelation(song.getId(), artist.getId(), artist.getName());
            saveLyricIfPresent(song.getId(), work);

            log.info("event=user_work_song_published workId={} songId={}", work.getId(), song.getId());
            return song.getId();
        } catch (Exception e) {
            log.error("event=user_work_song_publish_failed workId={} errorType={}",
                    work.getId(), e.getClass().getSimpleName());
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ResultCode.ERROR, "投稿发布失败，请稍后重试");
        }
    }

       
                                                              
      
                            
                                     
       
    private void saveLyricIfPresent(Long songId, UserWork work) {
        if (StrUtil.isBlank(work.getLyric())) {
            return;
        }
        Lyric lyric = new Lyric();
        lyric.setSongId(songId);
        lyric.setLyricType(CommonConstants.LYRIC_TYPE_ORIGINAL);
        lyric.setLanguage(StrUtil.blankToDefault(mapLanguage(work.getLanguage()), CommonConstants.DEFAULT_LYRIC_LANGUAGE));
        lyric.setContent(work.getLyric());
        lyric.setSource("user_work");
        lyric.setCreatorId(work.getUserId());
        lyric.setStatus(CommonConstants.STATUS_NORMAL);
        lyric.setCreateTime(LocalDateTime.now());
        lyricMapper.insert(lyric);
    }

    private void validateSubmissionFiles(Long userId, Integer workType, String fileUrl, String fileUrls, boolean scan) {
        if (isAlbumLikeWork(workType)) {
            submissionFileSecurityService.validateAlbumFileUrls(userId, fileUrls, scan);
            return;
        }
        if (isSingleLikeWork(workType)) {
            submissionFileSecurityService.validateSingleAudioFile(userId, fileUrl, scan);
            return;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "invalid workType");
    }

    private boolean isSingleLikeWork(Integer workType) {
        return Integer.valueOf(1).equals(workType) || Integer.valueOf(4).equals(workType);
    }

    private boolean isAlbumLikeWork(Integer workType) {
        return Integer.valueOf(2).equals(workType) || Integer.valueOf(3).equals(workType);
    }

    private void registerMediaAssets(UserWork work) {
        if (work == null || work.getId() == null) {
            return;
        }
        mediaAssetService.registerSubmissionAssets(
                "audio", "user_work", work.getId(), work.getFileUrl(),
                work.getFileUrls(), work.getZipFileUrl(), work.getFileSize());
        mediaAssetService.registerSubmissionAssets(
                "image", "user_work", work.getId(), work.getCoverUrl(),
                null, null, null);
        mediaAssetService.registerSubmissionAssets(
                "lyric", "user_work", work.getId(), work.getLyricFileUrl(),
                null, null, null);
    }

       
                                                            
      
                                                
                            
       
    private String mapLanguage(Integer language) {
        if (language == null) {
            return null;
        }
        switch (language) {
            case 1:
                return "zh";
            case 2:
                return "en";
            case 3:
                return "ja";
            case 4:
                return "ko";
            default:
                return "other";
        }
    }

    @Override
    public Integer getWorkCount(Long userId) {
        return baseMapper.countByUserId(userId);
    }

    @Override
    public boolean updateWorkStats(Long workId, Long playCount, Integer likeCount, Integer collectCount) {
        UserWork work = getById(workId);
        if (work == null) {
            return false;
        }

        if (playCount != null && playCount > 0) {
            work.setPlayCount(work.getPlayCount() + playCount);
        }
        if (likeCount != null && likeCount != 0) {
            work.setLikeCount(work.getLikeCount() + likeCount);
        }
        if (collectCount != null && collectCount != 0) {
            work.setCollectCount(work.getCollectCount() + collectCount);
        }

        return updateById(work);
    }

    @Override
    public UserWorkRewardConfig.RewardProgress getRewardProgress(Long workId) {
        UserWork work = getById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }

        return rewardConfig.getRewardProgress(
            work.getLikeCount(),
            work.getCollectCount(),
            work.getPlayCount() != null ? work.getPlayCount().intValue() : 0
        );
    }

    @Override
    public int checkAndUpdateRewards() {
                              
        List<UserWork> publishedWorks = baseMapper.getPublishedWorks();
        int updatedCount = 0;

        for (UserWork work : publishedWorks) {
            Integer currentPoints = work.getRewardPoints();
            Integer calculatedPoints = rewardConfig.calculateRewardPoints(
                work.getLikeCount(),
                work.getCollectCount(),
                work.getPlayCount() != null ? work.getPlayCount().intValue() : 0
            );

            if (!calculatedPoints.equals(currentPoints)) {
                work.setRewardPoints(calculatedPoints);
                updateById(work);
                updatedCount++;
            }
        }

        return updatedCount;
    }

    @Override
    public List<UserWork> getPublishedWorks() {
        return baseMapper.getPublishedWorks();
    }
    @Override
    public boolean updateLyricFile(Long workId, String lyricFileName, String lyricContent) {
                  
        if (workId == null) {
            return false;
        }

        UserWork work = baseMapper.selectById(workId);
        if (work == null) {
            return false;
        }

        try {
                      
            if (lyricFileName != null && !lyricFileName.trim().isEmpty()) {
                                         
            }
                     
            if (lyricContent != null) {
                work.setLyric(lyricContent);
            }
            work.setUpdateTime(LocalDateTime.now());
            updateById(work);
            return true;
        } catch (Exception e) {
            log.error("更新作品歌词失败: workId={}, error={}", workId, e.getClass().getSimpleName());
            return false;
        }
    }

       
              
       
    @Override
    public UserWork submitZipFile(UserWork userWork, org.springframework.web.multipart.MultipartFile zipFile) {
        UserWork work = submitWork(userWork);
        log.info("event=user_work_archive_submitted workId={} filePresent={}",
                work.getId(), zipFile != null);
        return work;
    }

       
              
       
    @Override
    public UserWork submitMultipleFiles(UserWork userWork, org.springframework.web.multipart.MultipartFile[] files) {
        UserWork work = submitWork(userWork);
        log.info("event=user_work_files_submitted workId={} fileCount={}", work.getId(),
                files != null ? files.length : 0);
        return work;
    }
}
