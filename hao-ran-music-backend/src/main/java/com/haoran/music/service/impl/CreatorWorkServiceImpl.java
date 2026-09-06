package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.config.WorkSubmissionConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.dto.creator.CreatorWorkDTO;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.CreatorWorkService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.LyricService;
import com.haoran.music.service.ModerationIntegrationService;
import com.haoran.music.service.OfficialMediaDerivativeService;
import com.haoran.music.service.SubmissionAlbumPublishService;
import com.haoran.music.service.SubmissionFileSecurityService;
import com.haoran.music.common.util.AudioQualityDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

   
                      
                          
  
                                                      
                    
                            
                         
                 
                                                               
   
@Slf4j
@Service
public class CreatorWorkServiceImpl extends ServiceImpl<CreatorWorkMapper, CreatorWork> implements CreatorWorkService {

    private static final java.util.Set<Integer> ALLOWED_WORK_SUBSCRIBE_PERIODS =
            new java.util.HashSet<>(java.util.Arrays.asList(0, 1, 3, 12));
    private static final int MAX_WORK_PRICE_CENTS = 1_000_000;

    @Autowired
    private CreatorWorkMapper creatorWorkMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private LyricService lyricService;

    @Autowired
    private UserExtensionMapper userExtensionMapper;

    @Autowired
    private AudioQualityDetector audioQualityDetector;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WorkProcessingUtil workProcessingUtil;

    @Autowired
    private CreatorWorkPurchaseMapper creatorWorkPurchaseMapper;

    @Autowired
    private CreatorEarningsMapper creatorEarningsMapper;

    @Autowired
    private WorkSubmissionConfig workSubmissionConfig;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private ModerationIntegrationService moderationIntegrationService;

    @Autowired
    private com.haoran.music.service.NotificationService notificationService;

    @Autowired
    private OfficialMediaDerivativeService officialMediaDerivativeService;

    @Autowired
    private SubmissionAlbumPublishService submissionAlbumPublishService;

    @Autowired
    private SubmissionFileSecurityService submissionFileSecurityService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.haoran.music.service.CreatorEligibilityService creatorEligibilityService;

    @Autowired
    private com.haoran.music.service.ArtistProfileService artistProfileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitWork(Long userId, CreatorWorkDTO dto) throws Exception {
        creatorEligibilityService.requireEligible(userId, "提交创作者作品");

                 
        if (!canSubmit(userId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "creator work daily submit limit reached: " + workSubmissionConfig.getCreatorWorkDailyLimit());
        }

                                     
        checkForXSS(dto);
        validatePaidSettings(dto.getIsPaid() == null ? 0 : dto.getIsPaid(),
                dto.getPrice(), dto.getSubscribePeriod());
        validateSubmissionFiles(userId, dto.getWorkType(), dto.getFileUrl(), dto.getFileUrls(), false);

               
        AudioQualityDetector.AudioInfo audioInfo = null;
        if (StrUtil.isNotBlank(dto.getFileUrl())) {
            try {
                audioInfo = audioQualityDetector.detectAudioInfo(dto.getFileUrl());
                log.info("event=creator_work_audio_quality_detected quality={}", audioInfo.getQualityName());
            } catch (Exception e) {
                log.warn("event=creator_work_audio_quality_detection_failed errorType={}",
                        e.getClass().getSimpleName());
                audioInfo = new AudioQualityDetector.AudioInfo();
            }
        }

        CreatorWork work = new CreatorWork();
        work.setUserId(userId);
        work.setWorkType(dto.getWorkType());
        work.setWorkName(dto.getWorkName());
        work.setCoverUrl(dto.getCoverUrl());
        work.setFileUrl(dto.getFileUrl());
        work.setDescription(SecurityCheckUtil.escapeHtml(dto.getDescription()));
        work.setSubmittedLyric(StrUtil.isBlank(dto.getLyric()) ? null : dto.getLyric().trim());
        work.setTags(dto.getTags());
        work.setLanguage(dto.getLanguage());
        work.setStatus(0);       
        work.setCreateTime(LocalDateTime.now());
        work.setPlayCount(0L);
        work.setLikeCount(0);
        work.setCollectCount(0);

               
        work.setAllowDownload(dto.getAllowDownload() != null ? dto.getAllowDownload() : 1);
        work.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
        work.setAllowShare(dto.getAllowShare() != null ? dto.getAllowShare() : 1);

               
        int paidFlag = dto.getIsPaid() != null ? dto.getIsPaid() : 0;
        work.setIsPaid(paidFlag);
        if (paidFlag == 1) {
            work.setPrice(new BigDecimal(dto.getPrice()));
            work.setSubscribePeriod(dto.getSubscribePeriod());
        } else {
            work.setPrice(null);
            work.setSubscribePeriod(null);
        }

                 
        if (audioInfo != null) {
            work.setDetectedQuality(audioInfo.getQualityLevel());
            work.setFileSize(audioInfo.getFileSize());
            work.setDuration(audioInfo.getDuration());
            work.setBitrate(audioInfo.getBitrate());
            work.setSampleRate(audioInfo.getSampleRate());
            work.setAudioFormat(audioInfo.getFormat());
        }

                  
        work.setUploadType(resolveUploadType(dto));
        work.setFileUrls(dto.getFileUrls());
        work.setZipFileUrl(dto.getZipFileUrl());

        if (creatorWorkMapper.insert(work) != 1) {
            throw new BusinessException(ResultCode.ERROR, "creator work save failed, please try again later");
        }
        registerMediaAssets(work);
        moderationIntegrationService.submitForModeration(
                "creator_work", work.getId(), userId, "creator");

                 
        String submitKey = workSubmissionConfig.getCreatorWorkPrefix() + userId;
        Long count = redisTemplate.opsForValue().increment(submitKey);
        if (count != null && count == 1) {
            redisTemplate.expire(submitKey, workSubmissionConfig.getSubmitWindowDays(), TimeUnit.DAYS);
        }

        log.info("event=creator_work_submitted userId={} workId={} quality={}",
                userId, work.getId(), audioInfo != null ? audioInfo.getQualityName() : "unknown");
        return work.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewWork(Long workId, Long reviewerId, Integer status, String reviewReason) {
        if (workId == null || reviewerId == null || (status == null || (status != 1 && status != 2))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核参数不合法");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核原因不能超过1000字");
        }
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作品记录不存在");
        }
        if (!Integer.valueOf(0).equals(work.getStatus())) {
            throw new BusinessException(409, "作品已被审核");
        }
        if (Integer.valueOf(1).equals(status)) {
            creatorEligibilityService.requireEligible(work.getUserId(), "发布创作者作品");
        }

        LocalDateTime reviewTime = LocalDateTime.now();
        work.setStatus(status);
        work.setReviewerId(reviewerId);
        work.setReviewTime(reviewTime);
        work.setReviewReason(reviewReason);
        int claimed = creatorWorkMapper.reviewPendingWork(work);
        if (claimed != 1) {
            throw new BusinessException(409, "作品已被其他审核人处理");
        }
                      
        if (status.equals(1)) {
            work.setPublishTime(LocalDateTime.now());

            if (isSingleLikeWork(work.getWorkType())) {
                submissionFileSecurityService.validateSingleAudioFile(work.getUserId(), work.getFileUrl(), true);
                Long songId = createSongFromWork(work);
                if (songId == null) {
                    throw new BusinessException(ResultCode.ERROR, "作品发布失败，请稍后重试");
                }
                work.setAutoSongId(songId);
                publishSubmittedLyric(work, songId, reviewerId);
            } else if (isAlbumLikeWork(work.getWorkType())) {
                SubmissionAlbumPublishService.AlbumPublishResult result =
                        submissionAlbumPublishService.publishCreatorAlbum(work);
                if (result.getFirstSongId() != null) {
                    work.setAutoSongId(result.getFirstSongId());
                }
            }
        } else if (status.equals(2)) {
            mediaAssetService.releaseTargetReferences("creator_work", workId);
        }

        if (creatorWorkMapper.updateById(work) != 1) {
            throw new BusinessException(ResultCode.ERROR, "作品审核结果保存失败，请稍后重试");
        }
        moderationIntegrationService.completeTargetReview(
                "creator_work", workId, reviewerId,
                Integer.valueOf(1).equals(status) ? "approved" : "rejected", reviewReason);
        notificationService.sendModerationResultNotificationOnce(
                work.getUserId(), "创作者作品", work.getWorkName(), workId,
                Integer.valueOf(1).equals(status), reviewReason,
                "moderation:creator_work:" + workId);
        log.info("event=creator_work_reviewed workId={} reviewerId={} status={} autoSongId={}",
                workId, reviewerId, status, work.getAutoSongId());
    }

       
                
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateWork(Long workId, Long userId, CreatorWorkDTO dto) {
        creatorEligibilityService.requireEligible(userId, "修改创作者作品");
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null || (work.getDeleted() != null && work.getDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "work does not exist");
        }
        if (!work.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "permission denied");
        }
        if (Integer.valueOf(1).equals(work.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "published work cannot be edited directly");
        }

        checkForXSS(dto);
        int targetPaidFlag = dto.getIsPaid() != null ? dto.getIsPaid()
                : (work.getIsPaid() == null ? 0 : work.getIsPaid());
        Integer targetPrice = dto.getPrice() != null ? dto.getPrice()
                : (work.getPrice() == null ? null : work.getPrice().intValue());
        Integer targetPeriod = dto.getSubscribePeriod() != null
                ? dto.getSubscribePeriod() : work.getSubscribePeriod();
        validatePaidSettings(targetPaidFlag, targetPrice, targetPeriod);

        boolean submissionFileChanged = dto.getWorkType() != null
                || dto.getFileUrl() != null
                || dto.getFileUrls() != null
                || dto.getZipFileUrl() != null;
        if (submissionFileChanged) {
            Integer targetWorkType = dto.getWorkType() != null ? dto.getWorkType() : work.getWorkType();
            String targetFileUrl = dto.getFileUrl() != null ? dto.getFileUrl() : work.getFileUrl();
            String targetFileUrls = dto.getFileUrls() != null ? dto.getFileUrls() : work.getFileUrls();
            validateSubmissionFiles(userId, targetWorkType, targetFileUrl, targetFileUrls, false);
        }
        boolean fileChanged = StrUtil.isNotBlank(dto.getFileUrl()) && !dto.getFileUrl().equals(work.getFileUrl());
        AudioQualityDetector.AudioInfo audioInfo = null;
        if (fileChanged) {
            try {
                audioInfo = audioQualityDetector.detectAudioInfo(dto.getFileUrl());
                log.info("event=creator_work_audio_quality_redetected workId={} quality={}",
                        workId, audioInfo.getQualityName());
            } catch (Exception e) {
                log.warn("event=creator_work_audio_quality_update_detection_failed errorType={}",
                        e.getClass().getSimpleName());
                audioInfo = new AudioQualityDetector.AudioInfo();
            }

        }

        if (dto.getWorkType() != null) {
            work.setWorkType(dto.getWorkType());
        }
        if (StrUtil.isNotBlank(dto.getWorkName())) {
            work.setWorkName(dto.getWorkName());
        }
        if (dto.getCoverUrl() != null) {
            work.setCoverUrl(dto.getCoverUrl());
        }
        if (dto.getFileUrl() != null) {
            work.setFileUrl(dto.getFileUrl());
        }
        if (dto.getDescription() != null) {
            work.setDescription(SecurityCheckUtil.escapeHtml(dto.getDescription()));
        }
        if (dto.getTags() != null) {
            work.setTags(dto.getTags());
        }
        if (dto.getLanguage() != null) {
            work.setLanguage(dto.getLanguage());
        }
        if (dto.getAllowDownload() != null) {
            work.setAllowDownload(dto.getAllowDownload());
        }
        if (dto.getAllowComment() != null) {
            work.setAllowComment(dto.getAllowComment());
        }
        if (dto.getAllowShare() != null) {
            work.setAllowShare(dto.getAllowShare());
        }
        if (dto.getIsPaid() != null) {
            work.setIsPaid(dto.getIsPaid());
            if (Integer.valueOf(0).equals(dto.getIsPaid())) {
                work.setPrice(null);
                work.setSubscribePeriod(null);
            }
        }
        if (targetPaidFlag == 1 && dto.getPrice() != null) {
            work.setPrice(new BigDecimal(dto.getPrice()));
        }
        if (targetPaidFlag == 1 && dto.getSubscribePeriod() != null) {
            work.setSubscribePeriod(dto.getSubscribePeriod());
        }
        if (dto.getUploadType() != null) {
            work.setUploadType(dto.getUploadType());
        }
        if (dto.getFileUrls() != null) {
            work.setFileUrls(dto.getFileUrls());
        }
        if (dto.getZipFileUrl() != null) {
            work.setZipFileUrl(dto.getZipFileUrl());
        }

        boolean mediaChanged = dto.getFileUrl() != null
                || dto.getFileUrls() != null
                || dto.getZipFileUrl() != null
                || dto.getCoverUrl() != null;
        if (mediaChanged) {
            mediaAssetService.releaseTargetReferences("creator_work", workId);
        }

        if (audioInfo != null) {
            work.setDetectedQuality(audioInfo.getQualityLevel());
            work.setFileSize(audioInfo.getFileSize());
            work.setDuration(audioInfo.getDuration());
            work.setBitrate(audioInfo.getBitrate());
            work.setSampleRate(audioInfo.getSampleRate());
            work.setAudioFormat(audioInfo.getFormat());
        }

        work.setStatus(0);
        work.setReviewerId(null);
        work.setReviewTime(null);
        work.setReviewReason(null);
        work.setUpdateTime(LocalDateTime.now());

        boolean updated = creatorWorkMapper.updateById(work) > 0;
        if (!updated) {
            throw new BusinessException(ResultCode.ERROR, "work update failed, please try again later");
        }
        if (mediaChanged) {
            registerMediaAssets(creatorWorkMapper.selectById(workId));
        }
        return true;
    }

       
                     
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        CreatorWork work = creatorWorkMapper.selectById(id);
        boolean removed = super.removeById(id);
        if (removed && work != null) {
            mediaAssetService.releaseTargetReferences("creator_work", work.getId());
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWork(Long workId, Long userId) {
        creatorEligibilityService.requireEligible(userId, "删除创作者作品");
        if (workId == null || workId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作品ID不合法");
        }
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null || Integer.valueOf(1).equals(work.getDeleted())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作品不存在");
        }
        if (!userId.equals(work.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限删除此作品");
        }
        if (Integer.valueOf(1).equals(work.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "已发布作品不能直接删除");
        }
        return removeById(workId);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSongFromWork(CreatorWork work) {
        if (!isSingleLikeWork(work.getWorkType())) {
            log.info("event=creator_work_song_publish_skipped workId={} workType={}",
                    work.getId(), work.getWorkType());
            return null;
        }
        if (StrUtil.isBlank(work.getFileUrl())) {
            log.info("event=creator_work_song_publish_skipped workId={} reason=file_url_missing", work.getId());
            return null;
        }

        try {
            User owner = userMapper.selectById(work.getUserId());
            String artistName = owner == null ? null
                    : StrUtil.blankToDefault(owner.getNickname(), owner.getUsername());
            Artist artist = artistProfileService.resolveOwnedProfile(
                    work.getUserId(), artistName, "creator_work");

                     
            Song song = new Song();
            song.setName(work.getWorkName());
            song.setCover(StrUtil.isNotBlank(work.getCoverUrl()) ? work.getCoverUrl() : "/default-cover.png");

                                         
            Integer quality = work.getDetectedQuality() != null ? work.getDetectedQuality() : 2;
            WorkProcessingUtil.setSongQualityField(song, quality, work.getFileUrl(), work.getFileSize());

            song.setDuration(work.getDuration() != null ? work.getDuration() : 0);
            song.setArtistId(artist.getId());
            song.setArtistIds(String.valueOf(artist.getId()));
            song.setArtistNames(artist.getName());
            song.setUploaderId(work.getUserId());
            song.setDescription(work.getDescription());
            song.setTags(work.getTags());
            song.setLanguage(mapLanguage(work.getLanguage()));
            song.setPlayCount(0L);
            song.setFavoriteCount(0L);
            song.setCommentCount(0L);
            song.setLikeCount(0L);
            song.setReplyCount(0L);
            song.setShareCount(0L);
            song.setDownloadCount(0L);
            song.setAvgRating(BigDecimal.ZERO);
            song.setPriority(0);
            song.setIsVipOnly(CommonConstants.NO);
                                                                                     
                                                                            
            song.setIsPaid(CommonConstants.NO);
            song.setIsSingle(CommonConstants.YES);
            song.setIsNew(CommonConstants.YES);
            song.setIsHot(CommonConstants.NO);
            song.setStatus(CommonConstants.STATUS_NORMAL);
            song.setDeleted(CommonConstants.NOT_DELETED);
            song.setVersionType("原创");
            song.setCreateTime(LocalDateTime.now());
            song.setUpdateTime(LocalDateTime.now());

            songMapper.insert(song);
            officialMediaDerivativeService.submitSongDerivativeJob(song.getId(), work.getFileUrl(),
                    work.getFileSize(), work.getDetectedQuality());

                                         
            workProcessingUtil.createSongArtistRelation(song.getId(), artist.getId(), artist.getName());

            log.info("event=creator_work_song_published workId={} songId={} quality={}",
                    work.getId(), song.getId(), work.getDetectedQuality());

            return song.getId();

        } catch (Exception e) {
            log.error("event=creator_work_song_auto_create_failed workId={} errorType={}",
                    work.getId(), e.getClass().getSimpleName());
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ResultCode.ERROR, "作品发布失败，请稍后重试");
        }
    }

    @Override
    public CreatorWork getById(Serializable id) {
        CreatorWork work = super.getById(id);
        submissionAlbumPublishService.enrichCreatorWorks(work != null
                ? Collections.singletonList(work)
                : Collections.emptyList());
        return work;
    }

    @Override
    public IPage<CreatorWork> pageWorks(Integer current, Integer size, Integer status, Long userId) {
        Page<CreatorWork> page = new Page<>(current, size);
        LambdaQueryWrapper<CreatorWork> wrapper = new LambdaQueryWrapper<CreatorWork>()
                .eq(CreatorWork::getDeleted, 0)
                .orderByDesc(CreatorWork::getCreateTime);

        if (status != null) {
            wrapper.eq(CreatorWork::getStatus, status);
        }

        if (userId != null) {
            wrapper.eq(CreatorWork::getUserId, userId);
        }

        IPage<CreatorWork> result = creatorWorkMapper.selectPage(page, wrapper);
        submissionAlbumPublishService.enrichCreatorWorks(result.getRecords());
        return result;
    }

    @Override
    public List<CreatorWork> getCreatorWorks(Long userId) {
        List<CreatorWork> works = creatorWorkMapper.selectList(
            new LambdaQueryWrapper<CreatorWork>()
                .eq(CreatorWork::getUserId, userId)
                .eq(CreatorWork::getDeleted, 0)
                .orderByDesc(CreatorWork::getCreateTime)
        );
        submissionAlbumPublishService.enrichCreatorWorks(works);
        return works;
    }

    @Override
    public Long getPendingCount() {
        return creatorWorkMapper.selectCount(
            new LambdaQueryWrapper<CreatorWork>()
                .eq(CreatorWork::getStatus, 0)
                .eq(CreatorWork::getDeleted, 0)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePolicyUpdate(Long workId, String reason) {
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作品记录不存在");
        }

        if (!work.getStatus().equals(2)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只有被拒绝的作品才能重新审核");
        }

        work.setStatus(0);
        work.setReviewTime(null);
        work.setReviewReason("平台规则更新，重新审核。原因: " + reason);
        work.setUpdateTime(LocalDateTime.now());

        creatorWorkMapper.updateById(work);

        log.info("作品{}因平台规则更新重新进入审核流程", workId);
    }

    @Override
    public Boolean canSubmit(Long userId) {
        String submitKey = workSubmissionConfig.getCreatorWorkPrefix() + userId;
        String count = redisTemplate.opsForValue().get(submitKey);
        if (count != null && Integer.parseInt(count) >= workSubmissionConfig.getCreatorWorkDailyLimit()) {
            return false;
        }
        return true;
    }

       
                                   
       
    private void checkForXSS(CreatorWorkDTO dto) {
        if (SecurityCheckUtil.containsXSS(dto.getWorkName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作品名称包含非法字符，请检查输入");
        }
        if (SecurityCheckUtil.containsXSS(dto.getDescription())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作品描述包含非法字符，请检查输入");
        }
        if (SecurityCheckUtil.containsXSS(dto.getTags())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "标签包含非法字符，请检查输入");
        }
        if (SecurityCheckUtil.containsXSS(dto.getLyric())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词包含非法字符，请检查输入");
        }
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

    private Integer resolveUploadType(CreatorWorkDTO dto) {
        if (dto.getUploadType() != null) {
            return dto.getUploadType();
        }
        if (isAlbumLikeWork(dto.getWorkType())) {
            return StrUtil.isNotBlank(dto.getZipFileUrl()) ? 3 : 2;
        }
        return 1;
    }

    private boolean isSingleLikeWork(Integer workType) {
        return Integer.valueOf(1).equals(workType) || Integer.valueOf(4).equals(workType);
    }

    private boolean isAlbumLikeWork(Integer workType) {
        return Integer.valueOf(2).equals(workType) || Integer.valueOf(3).equals(workType);
    }

    private void registerMediaAssets(CreatorWork work) {
        if (work == null || work.getId() == null) {
            return;
        }
        mediaAssetService.registerSubmissionAssets(
                "audio", "creator_work", work.getId(), work.getFileUrl(),
                work.getFileUrls(), work.getZipFileUrl(), work.getFileSize());
        mediaAssetService.registerSubmissionAssets(
                "image", "creator_work", work.getId(), work.getCoverUrl(),
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

    private void publishSubmittedLyric(CreatorWork work, Long songId, Long reviewerId) {
        if (StrUtil.isBlank(work.getSubmittedLyric())) {
            return;
        }
        lyricService.saveLyric(songId, work.getSubmittedLyric(), "zh-CN",
                CommonConstants.LYRIC_TYPE_ORIGINAL, CommonConstants.LYRIC_SOURCE_CREATOR, reviewerId);
    }

                                                                           

       
              
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setWorkPaid(Long creatorId, Long workId, BigDecimal price, Integer subscribePeriod) {
        creatorEligibilityService.requireEligible(creatorId, "设置作品付费");
        if (price == null || price.scale() > 0 || price.compareTo(BigDecimal.ONE) < 0
                || price.compareTo(BigDecimal.valueOf(MAX_WORK_PRICE_CENTS)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作品价格必须是1到1000000之间的整数分值");
        }
        if (subscribePeriod == null || !ALLOWED_WORK_SUBSCRIBE_PERIODS.contains(subscribePeriod)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订阅周期仅支持一次性、月、季或年");
        }
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作品不存在");
        }

        if (!work.getUserId().equals(creatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限设置此作品为付费");
        }
        work.setIsPaid(1);
        work.setPrice(price);
        work.setSubscribePeriod(subscribePeriod);
        work.setUpdateTime(LocalDateTime.now());

        creatorWorkMapper.updateById(work);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("workId", workId);
        result.put("price", price);
        result.put("subscribePeriod", subscribePeriod);

        log.info("作品{}设置为付费，价格: {}，周期: {}天", workId, price, subscribePeriod);
        return result;
    }

       
             
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelWorkPaid(Long creatorId, Long workId) {
        creatorEligibilityService.requireEligible(creatorId, "取消作品付费");
        CreatorWork work = creatorWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "作品不存在");
        }

        if (!work.getUserId().equals(creatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限取消此作品的付费设置");
        }

        work.setIsPaid(0);
        work.setPrice(null);
        work.setSubscribePeriod(null);
        work.setUpdateTime(LocalDateTime.now());

        creatorWorkMapper.updateById(work);

        log.info("作品{}取消付费设置", workId);
        return true;
    }

    private void validatePaidSettings(Integer isPaid, Integer price, Integer subscribePeriod) {
        if (isPaid == null || (isPaid != 0 && isPaid != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "付费开关仅支持0或1");
        }
        if (isPaid == 0) {
            if ((price != null && price != 0) || (subscribePeriod != null && subscribePeriod != 0)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "免费作品不能携带价格或订阅周期");
            }
            return;
        }
        if (price == null || price < 1 || price > MAX_WORK_PRICE_CENTS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "付费作品价格必须在1到1000000分之间");
        }
        if (subscribePeriod == null || !ALLOWED_WORK_SUBSCRIBE_PERIODS.contains(subscribePeriod)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "订阅周期仅支持一次性、月、季或年");
        }
    }

       
                 
       
    @Override
    public Map<String, Object> getMyPaidWorks(Long creatorId, Integer page, Integer size) {
        Page<CreatorWork> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<CreatorWork> wrapper = new LambdaQueryWrapper<CreatorWork>()
                .eq(CreatorWork::getUserId, creatorId)
                .eq(CreatorWork::getIsPaid, 1)
                .eq(CreatorWork::getDeleted, 0)
                .orderByDesc(CreatorWork::getCreateTime);

        Page<CreatorWork> resultPage = creatorWorkMapper.selectPage(pageParam, wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("current", page);
        result.put("size", size);

        return result;
    }

       
               
       
    @Override
    public Map<String, Object> getWorkEarnings(Long creatorId, Long workId) {
        Map<String, Object> result = new HashMap<>();

        if (workId != null) {
                        
            CreatorWork work = creatorWorkMapper.selectById(workId);
            if (work == null || !work.getUserId().equals(creatorId)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "作品不存在或无权限查看");
            }

                           
            LambdaQueryWrapper<CreatorWorkPurchase> purchaseWrapper = new LambdaQueryWrapper<>();
            purchaseWrapper.eq(CreatorWorkPurchase::getWorkId, workId)
                    .eq(CreatorWorkPurchase::getStatus, "success");
            Long purchaseCount = creatorWorkPurchaseMapper.selectCount(purchaseWrapper);

                    
            List<CreatorWorkPurchase> purchases = creatorWorkPurchaseMapper.selectList(purchaseWrapper);
            BigDecimal totalRevenue = BigDecimal.ZERO;
            for (CreatorWorkPurchase purchase : purchases) {
                if (purchase.getPurchaseAmount() != null) {
                    totalRevenue = totalRevenue.add(purchase.getPurchaseAmount());
                }
            }

                                                    
            LambdaQueryWrapper<CreatorEarnings> earningsWrapper = new LambdaQueryWrapper<>();
            earningsWrapper.eq(CreatorEarnings::getUserId, creatorId)
                    .eq(CreatorEarnings::getWorkId, workId);
            List<CreatorEarnings> earningsList = creatorEarningsMapper.selectList(earningsWrapper);

            BigDecimal actualEarnings = BigDecimal.ZERO;
            for (CreatorEarnings earnings : earningsList) {
                if (earnings.getEarningsAmount() != null) {
                                                                
                    actualEarnings = actualEarnings.add(BigDecimal.valueOf(earnings.getEarningsAmount()).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP));
                }
            }

                                 
            BigDecimal platformFees = totalRevenue.subtract(actualEarnings);

            result.put("workId", workId);
            result.put("workName", work.getWorkName());
            result.put("price", work.getPrice());
            result.put("purchaseCount", purchaseCount != null ? purchaseCount.intValue() : 0);
            result.put("totalRevenue", totalRevenue);
            result.put("actualEarnings", actualEarnings);
            result.put("platformFees", platformFees);
        } else {
                        
            List<CreatorWork> works = creatorWorkMapper.selectList(
                    new LambdaQueryWrapper<CreatorWork>()
                            .eq(CreatorWork::getUserId, creatorId)
                            .eq(CreatorWork::getIsPaid, 1)
                            .eq(CreatorWork::getDeleted, 0)
            );

                         
            BigDecimal totalRevenue = BigDecimal.ZERO;
            int totalPurchases = 0;

            Set<Long> workIds = works.stream()
                    .map(CreatorWork::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (!workIds.isEmpty()) {
                LambdaQueryWrapper<CreatorWorkPurchase> purchaseWrapper = new LambdaQueryWrapper<>();
                purchaseWrapper.in(CreatorWorkPurchase::getWorkId, workIds)
                        .eq(CreatorWorkPurchase::getStatus, "success");
                List<CreatorWorkPurchase> purchases = creatorWorkPurchaseMapper.selectList(purchaseWrapper);
                totalPurchases = purchases.size();
                for (CreatorWorkPurchase purchase : purchases) {
                    if (purchase.getPurchaseAmount() != null) {
                        totalRevenue = totalRevenue.add(purchase.getPurchaseAmount());
                    }
                }
            }

                                                     
            LambdaQueryWrapper<CreatorEarnings> earningsWrapper = new LambdaQueryWrapper<>();
            earningsWrapper.eq(CreatorEarnings::getUserId, creatorId);
            List<CreatorEarnings> earningsList = creatorEarningsMapper.selectList(earningsWrapper);

            BigDecimal actualEarnings = BigDecimal.ZERO;
            for (CreatorEarnings earnings : earningsList) {
                if (earnings.getEarningsAmount() != null) {
                                                                
                    actualEarnings = actualEarnings.add(BigDecimal.valueOf(earnings.getEarningsAmount()).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP));
                }
            }

            result.put("totalWorks", works.size());
            result.put("totalPurchases", totalPurchases);
            result.put("totalRevenue", totalRevenue);
            result.put("actualEarnings", actualEarnings);
        }

        return result;
    }

       
                 
                             
                   
       
    @Override
    public Map<String, Object> getCreatorTotalEarnings(Long creatorId) {
                                
        LambdaQueryWrapper<CreatorEarnings> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(CreatorEarnings::getUserId, creatorId);

                     
        List<CreatorEarnings> allEarnings = creatorEarningsMapper.selectList(allWrapper);

                         
        Map<String, Long> earningsByType = new HashMap<>();
        for (CreatorEarnings earnings : allEarnings) {
            String type = earnings.getEarningsType();
            Long amount = earnings.getEarningsAmount();
            if (type != null && amount != null) {
                earningsByType.put(type, earningsByType.getOrDefault(type, 0L) + amount);
            }
        }

              
        Long totalEarnings = earningsByType.values().stream()
                .mapToLong(Long::longValue).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("creatorId", creatorId);
        result.put("totalEarnings", totalEarnings);
        result.put("earningsByType", earningsByType);
                              
        result.put("availableBalance", totalEarnings);
        result.put("pendingWithdrawal", 0L);
        result.put("totalWithdrawn", 0L);

        return result;
    }
}
