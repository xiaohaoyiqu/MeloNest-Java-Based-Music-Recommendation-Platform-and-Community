package com.haoran.music.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.DeepSeekConfig;
import com.haoran.music.common.config.LyricTranslationConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Lyric;
import com.haoran.music.entity.LyricTranslation;
import com.haoran.music.entity.MusicLanguage;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.LyricMapper;
import com.haoran.music.mapper.LyricTranslationMapper;
import com.haoran.music.mapper.MusicLanguageMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.LyricService;
import com.haoran.music.service.ContentAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

   
                      
                       
   
@Slf4j
@Service
public class LyricServiceImpl extends ServiceImpl<LyricMapper, Lyric> implements LyricService {

    private static final int MAX_LYRIC_CONTENT_LENGTH = 20000;
    private static final int MAX_LANGUAGE_LENGTH = 32;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int DEFAULT_DEEPSEEK_TIMEOUT_SECONDS = 600;
    private static final int MAX_DEEPSEEK_RETRIES = 5;
    private static final long DEEPSEEK_RETRY_BACKOFF_MILLIS = 200L;
    private static final Pattern LANGUAGE_CODE_PATTERN =
            Pattern.compile("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$");

    @Resource
    private DeepSeekConfig deepSeekConfig;

    @Resource
    private LyricTranslationMapper lyricTranslationMapper;

    @Resource
    private MusicLanguageMapper musicLanguageMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private ContentAccessService contentAccessService;

    @Resource
    private UserMapper userMapper;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Resource
    private LyricTranslationConfig lyricTranslationConfig;

    @Resource(name = CommonConstants.LYRIC_TRANSLATOR_EXECUTOR)
    private Executor lyricTranslatorExecutor;

    @Resource
    private com.haoran.music.common.util.SshUtil sshUtil;

    @Override
    public Lyric getSongLyric(Long songId, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }

                   
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);

        String preferredLanguage = firstNonBlank(
                song.getLyricLanguage(), song.getLanguage(), CommonConstants.DEFAULT_LYRIC_LANGUAGE);
        Lyric lyric = getSongLyric(songId, preferredLanguage, CommonConstants.LYRIC_TYPE_ORIGINAL);
        if (isEmptyContent(lyric) && !CommonConstants.DEFAULT_LYRIC_LANGUAGE.equals(preferredLanguage)) {
            lyric = getSongLyric(songId, CommonConstants.DEFAULT_LYRIC_LANGUAGE,
                    CommonConstants.LYRIC_TYPE_ORIGINAL);
        }
        return lyric;
    }

    @Override
    public Lyric getSongLyric(Long songId, String language, Integer lyricType) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }

        String safeLanguage = normalizeLanguage(language);
        Integer safeLyricType = normalizeLyricType(lyricType, CommonConstants.LYRIC_TYPE_ORIGINAL);

               
        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lyric::getSongId, songId)
                .eq(Lyric::getLanguage, safeLanguage)
                .eq(Lyric::getLyricType, safeLyricType)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Lyric::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Lyric::getCreateTime)
                .last("LIMIT 1");

        Lyric lyric = getOne(wrapper);

                                     
        if (ObjectUtils.isEmpty(lyric)) {
            lyric = new Lyric();
            lyric.setSongId(songId);
            lyric.setContent("");
            lyric.setLanguage(safeLanguage);
            lyric.setLyricType(safeLyricType);

            Song song = songMapper.selectById(songId);
            if (song != null) {
                String localContent = sshUtil.readRemoteLyricFile(
                        song.getName(), song.getArtistNames(), safeLyricType);
                if (localContent != null && !localContent.trim().isEmpty()) {
                    lyric.setContent(localContent.trim());
                    lyric.setSource(CommonConstants.LYRIC_SOURCE_NODE3);
                    lyric.setStatus(CommonConstants.STATUS_NORMAL);
                    lyric.setLanguage(resolveLocalLanguage(song, safeLyricType, safeLanguage));
                }
            }
        }

        return lyric;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveLyric(Long songId, String content, String language, Integer lyricType, Long userId) {
        return saveLyric(songId, content, language, lyricType, CommonConstants.LYRIC_SOURCE_USER, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveLyric(Long songId, String content, String language, Integer lyricType, String source, Long userId) {
        if (ObjectUtils.isEmpty(songId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }
        String safeContent = normalizeContent(content);
        String safeLanguage = normalizeLanguage(language);
        Integer safeLyricType = normalizeLyricType(lyricType, CommonConstants.LYRIC_TYPE_ORIGINAL);
        String safeSource = normalizeSource(source);

                   
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);

        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "保存歌词");
        if (!UserRole.canModerate(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "正式歌词只能由审核人员发布");
        }

        upsertLyric(song, safeContent, safeLanguage, safeLyricType, safeSource, userId);

        log.info("保存歌词成功: songId={}, language={}, lyricType={}, source={}, userId={}",
                songId, safeLanguage, safeLyricType, safeSource, userId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteLyric(Long lyricId, Long userId) {
        if (ObjectUtils.isEmpty(lyricId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词ID不能为空");
        }

        Lyric lyric = getById(lyricId);
        if (ObjectUtils.isEmpty(lyric)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌词不存在");
        }

        User user = userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, "删除歌词");
        if (!UserRole.canModerate(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "正式歌词只能由审核人员删除");
        }

        removeById(lyricId);

                     
        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lyric::getSongId, lyric.getSongId());
        long count = count(wrapper);

        if (count == 0) {
            Song song = songMapper.selectById(lyric.getSongId());
            if (song != null) {
                song.setHasLyric(CommonConstants.NO);
                songMapper.updateById(song);
            }
        }

        return true;
    }

    @Override
    public List<Lyric> getSongLyrics(Long songId, Long userId) {
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);
        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lyric::getSongId, songId)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .orderBy(true, true, Lyric::getLyricType)
                .orderByAsc(Lyric::getLanguage);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long requestTranslation(Long songId, String targetLanguage, Integer lyricType, Long userId) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(targetLanguage)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID和目标语言不能为空");
        }

        UserAccountStatusUtil.requireCanInteract(userMapper.selectById(userId), "请求歌词翻译");
        Song song = songMapper.selectById(songId);
        if (ObjectUtils.isEmpty(song)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, userId);
        String safeTargetLanguage = normalizeLanguage(targetLanguage);
        requireSupportedLanguage(safeTargetLanguage);
        Integer safeLyricType = normalizeTranslationType(lyricType);

                   
        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lyric::getSongId, songId)
                .eq(Lyric::getLyricType, safeLyricType)
                .eq(Lyric::getLanguage, safeTargetLanguage)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Lyric::getDeleted, CommonConstants.NOT_DELETED);
        Lyric existingTranslation = getOne(wrapper);

        if (ObjectUtils.isNotEmpty(existingTranslation)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该翻译已存在");
        }

        LyricTranslation reusableTask = findReusableTranslationTask(
                songId, safeTargetLanguage, safeLyricType, userId);
        if (reusableTask != null) {
            return reusableTask.getId();
        }
        if (hasActiveTranslationTask(songId, safeTargetLanguage, safeLyricType)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "这首歌的同语种翻译正在排队，请稍后查看");
        }

        String lockKey = translationLockKey(songId, safeTargetLanguage, safeLyricType);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey, String.valueOf(userId), lyricTranslationConfig.getLockMinutes(), TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "这首歌的同语种翻译正在排队，请稍后查看");
        }
        if (hasActiveTranslationTask(songId, safeTargetLanguage, safeLyricType)) {
            releaseTranslationLock(lockKey);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "这首歌的同语种翻译正在排队，请稍后查看");
        }

        boolean quotaReserved = false;
        boolean taskCreated = false;
        try {
            reserveTranslationQuota(userId);
            quotaReserved = true;

                                                
            reusableTask = findReusableTranslationTask(songId, safeTargetLanguage, safeLyricType, userId);
            if (reusableTask != null) {
                releaseTranslationQuota(userId);
                releaseTranslationLock(lockKey);
                return reusableTask.getId();
            }

                                                   
                                                       
            Lyric originalLyric = resolveTranslationSource(song);

                     
            LyricTranslation task = new LyricTranslation();
            task.setSongId(songId);
                                                     
            task.setSourceLyricId(originalLyric.getId());
            task.setTargetLanguage(safeTargetLanguage);
            task.setLyricType(safeLyricType);
            task.setStatus(CommonConstants.TRANSLATION_STATUS_PENDING);
            task.setCreatorId(userId);
            task.setRequestTime(LocalDateTime.now());
            task.setModelName(resolveDeepSeekModelNameForAudit());
            task.setAttemptCount(0);
            task.setDeleted(CommonConstants.NOT_DELETED);
            lyricTranslationMapper.insert(task);
            taskCreated = true;

            final Long taskId = task.getId();
            submitTranslationAfterCommit(taskId);

            return taskId;
        } catch (RuntimeException e) {
            if (!taskCreated && quotaReserved) {
                releaseTranslationQuota(userId);
            }
            if (!taskCreated) {
                releaseTranslationLock(lockKey);
            }
            throw e;
        }
    }

    private void submitTranslationExecution(Long taskId) {
        try {
            lyricTranslatorExecutor.execute(() -> executeTranslation(taskId));
        } catch (RejectedExecutionException e) {
            deferPendingTranslation(taskId, "QUEUE_BUSY", resolveRetryDelaySeconds(1));
            log.warn("event=lyric_translation_queue_busy taskId={}", taskId);
        }
    }

    private void submitTranslationAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTranslationExecution(taskId);
                }
            });
            return;
        }
        submitTranslationExecution(taskId);
    }

    @Override
    public Boolean executeTranslation(Long taskId) {
        LocalDateTime claimTime = LocalDateTime.now();
        String processingToken = UUID.randomUUID().toString();
        int claimed = lyricTranslationMapper.update(null, new LambdaUpdateWrapper<LyricTranslation>()
                .eq(LyricTranslation::getId, taskId)
                .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PENDING)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED)
                .and(wrapper -> wrapper.isNull(LyricTranslation::getNextRetryTime)
                        .or().le(LyricTranslation::getNextRetryTime, claimTime))
                .set(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PROCESSING)
                .set(LyricTranslation::getProcessingToken, processingToken)
                .set(LyricTranslation::getErrorCode, null)
                .set(LyricTranslation::getErrorMessage, null)
                .set(LyricTranslation::getNextRetryTime, null)
                .set(LyricTranslation::getUpdateTime, claimTime)
                .setSql("attempt_count = COALESCE(attempt_count, 0) + 1"));
        if (claimed != 1) {
            log.info("event=lyric_translation_duplicate_execution_skipped taskId={}", taskId);
            return false;
        }

        LyricTranslation task = lyricTranslationMapper.selectById(taskId);
        if (ObjectUtils.isEmpty(task)) {
            log.error("翻译任务不存在: taskId={}", taskId);
            return false;
        }
        task.setStatus(CommonConstants.TRANSLATION_STATUS_PROCESSING);
        task.setProcessingToken(processingToken);
        int attemptCount = Math.max(1, task.getAttemptCount() == null ? 1 : task.getAttemptCount());
        boolean releaseLock = false;

        try {
            Lyric originalLyric = resolveTranslationExecutionSource(task);

            String prompt = buildTranslationPrompt(originalLyric.getContent(),
                    task.getTargetLanguage(), task.getLyricType());

            String translatedContent = callDeepSeekAPI(prompt);
            String lrcTranslated = normalizeContent(
                    extractLRCTranslation(originalLyric.getContent(), translatedContent));

            Song song = songMapper.selectById(task.getSongId());
            if (ObjectUtils.isEmpty(song)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
            }
                                               
            LocalDateTime completedAt = LocalDateTime.now();
            int completed = updateClaimedTranslation(taskId, processingToken,
                    new LambdaUpdateWrapper<LyricTranslation>()
                            .set(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_COMPLETED)
                            .set(LyricTranslation::getResultContent, lrcTranslated)
                            .set(LyricTranslation::getErrorCode, null)
                            .set(LyricTranslation::getErrorMessage, null)
                            .set(LyricTranslation::getNextRetryTime, null)
                            .set(LyricTranslation::getProcessingToken, null)
                            .set(LyricTranslation::getCompleteTime, completedAt)
                            .set(LyricTranslation::getUpdateTime, completedAt));
            if (completed != 1) {
                log.info("event=lyric_translation_stale_result_skipped taskId={}", taskId);
                return false;
            }
            task.setStatus(CommonConstants.TRANSLATION_STATUS_COMPLETED);
            task.setResultContent(lrcTranslated);
            task.setCompleteTime(completedAt);
            releaseLock = true;

            log.info("event=lyric_translation_completed taskId={} songId={} language={} attempt={}",
                    taskId, task.getSongId(), task.getTargetLanguage(), attemptCount);
            return true;

        } catch (Exception e) {
            String errorCode = resolveTranslationFailureCode(e.getMessage());
            if ("AI_SERVICE_UNAVAILABLE".equals(errorCode) && attemptCount < resolveMaxTranslationAttempts()) {
                LocalDateTime retryAt = LocalDateTime.now().plusSeconds(resolveRetryDelaySeconds(attemptCount));
                int requeued = updateClaimedTranslation(taskId, processingToken,
                        new LambdaUpdateWrapper<LyricTranslation>()
                                .set(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PENDING)
                                .set(LyricTranslation::getErrorCode, errorCode)
                                .set(LyricTranslation::getErrorMessage, errorCode)
                                .set(LyricTranslation::getNextRetryTime, retryAt)
                                .set(LyricTranslation::getProcessingToken, null)
                                .set(LyricTranslation::getCompleteTime, null)
                                .set(LyricTranslation::getUpdateTime, LocalDateTime.now()));
                if (requeued == 1) {
                    log.warn("event=lyric_translation_retry_scheduled taskId={} attempt={} maxAttempts={}",
                            taskId, attemptCount, resolveMaxTranslationAttempts());
                }
                return false;
            }

            LocalDateTime failedAt = LocalDateTime.now();
            int failed = updateClaimedTranslation(taskId, processingToken,
                    new LambdaUpdateWrapper<LyricTranslation>()
                            .set(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_FAILED)
                            .set(LyricTranslation::getErrorCode, errorCode)
                            .set(LyricTranslation::getErrorMessage, errorCode)
                            .set(LyricTranslation::getNextRetryTime, null)
                            .set(LyricTranslation::getProcessingToken, null)
                            .set(LyricTranslation::getCompleteTime, failedAt)
                            .set(LyricTranslation::getUpdateTime, failedAt));
            releaseLock = failed == 1;
            log.warn("event=lyric_translation_failed taskId={} errorCode={} attempt={}",
                    taskId, errorCode, attemptCount);
            return false;
        } finally {
            if (releaseLock) {
                releaseTranslationLock(translationLockKey(
                        task.getSongId(), task.getTargetLanguage(), task.getLyricType()));
            }
        }
    }

    private void releaseTranslationLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (RuntimeException e) {
                                                       
            log.warn("event=lyric_translation_lock_release_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private int updateClaimedTranslation(Long taskId, String processingToken,
                                          LambdaUpdateWrapper<LyricTranslation> changes) {
        changes.eq(LyricTranslation::getId, taskId)
                .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PROCESSING)
                .eq(LyricTranslation::getProcessingToken, processingToken)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED);
        return lyricTranslationMapper.update(null, changes);
    }

    @Override
    public Map<String, Object> getTranslationStatus(Long taskId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "翻译任务ID不能为空");
        }
        LyricTranslation task = lyricTranslationMapper.selectOne(new LambdaQueryWrapper<LyricTranslation>()
                .eq(LyricTranslation::getId, taskId)
                .eq(LyricTranslation::getCreatorId, userId)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED));
        if (ObjectUtils.isEmpty(task)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "翻译任务不存在");
        }

        return toTranslationStatus(task);
    }

    @Override
    public Map<String, Object> getLatestTranslationStatus(Long songId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (songId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID不能为空");
        }
        LyricTranslation task = lyricTranslationMapper.selectOne(new LambdaQueryWrapper<LyricTranslation>()
                .eq(LyricTranslation::getSongId, songId)
                .eq(LyricTranslation::getCreatorId, userId)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(LyricTranslation::getRequestTime)
                .last("LIMIT 1"));
        return task == null ? Collections.emptyMap() : toTranslationStatus(task);
    }

    private Map<String, Object> toTranslationStatus(LyricTranslation task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("songId", task.getSongId());
        result.put("status", task.getStatus());
        result.put("targetLanguage", task.getTargetLanguage());
        result.put("lyricType", task.getLyricType());
        result.put("requestTime", task.getRequestTime());
        result.put("completeTime", task.getCompleteTime());
        result.put("resultContent", task.getResultContent());
        result.put("updatedTime", task.getUpdateTime());
        result.put("attemptCount", task.getAttemptCount() == null ? 0 : task.getAttemptCount());
        result.put("maxAttempts", resolveMaxTranslationAttempts());
        result.put("nextRetryTime", task.getNextRetryTime());
        String errorCode = task.getErrorCode();
        if (errorCode == null && Objects.equals(CommonConstants.TRANSLATION_STATUS_FAILED, task.getStatus())) {
            errorCode = resolveTranslationFailureCode(task.getErrorMessage());
        }
        result.put("errorCode", errorCode);
        result.put("errorMessage", errorCode == null ? null : resolveTranslationFailureMessage(errorCode));

        return result;
    }

    private String resolveTranslationFailureCode(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
        if (message.contains("不能为空") || message.contains("empty") || message.contains("choices")) {
            return "AI_RESPONSE_EMPTY";
        }
        if (message.contains("deepseek") || message.contains("翻译服务") || message.contains("connection")
                || message.contains("connect") || message.contains("timeout") || message.contains("timed out")
                || message.contains("refused") || message.contains("模型未配置") || message.contains("服务地址未配置")) {
            return "AI_SERVICE_UNAVAILABLE";
        }
        if (message.contains("歌词不存在") || message.contains("歌曲不存在")) {
            return "SOURCE_UNAVAILABLE";
        }
        return "TRANSLATION_FAILED";
    }

    private String resolveTranslationFailureMessage(String errorCode) {
        switch (errorCode) {
            case "AI_RESPONSE_EMPTY":
                return "AI 翻译铺没有交回可用译稿，请稍后再试";
            case "AI_SERVICE_UNAVAILABLE":
                return "AI 翻译服务暂时没有接通，请稍后再试";
            case "SOURCE_UNAVAILABLE":
                return "原歌词或歌曲已经不可用，这张译稿无法继续";
            case "QUEUE_BUSY":
                return "翻译铺眼下正忙，这张委托单会自动等下一班";
            case "TASK_RECOVERING":
                return "上一次整理意外停下，这张委托单正在重新排队";
            default:
                return "这张译稿没有生成成功，请稍后再试";
        }
    }

    @Override
    public List<MusicLanguage> getSupportedLanguages() {
        LambdaQueryWrapper<MusicLanguage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicLanguage::getIsEnabled, CommonConstants.STATUS_NORMAL)
                .orderByAsc(MusicLanguage::getSortOrder);
        return musicLanguageMapper.selectList(wrapper);
    }

    private Lyric resolveTranslationSource(Song song) {
        String preferredLanguage = firstNonBlank(
                song.getLyricLanguage(), song.getLanguage(), CommonConstants.DEFAULT_LYRIC_LANGUAGE);
        Lyric stored = findActiveLyric(song.getId(), preferredLanguage, CommonConstants.LYRIC_TYPE_ORIGINAL);
        if (stored == null && !CommonConstants.DEFAULT_LYRIC_LANGUAGE.equals(preferredLanguage)) {
            stored = findActiveLyric(song.getId(), CommonConstants.DEFAULT_LYRIC_LANGUAGE,
                    CommonConstants.LYRIC_TYPE_ORIGINAL);
        }
        if (stored != null) {
            return stored;
        }
        return readNode3TranslationSource(song, preferredLanguage);
    }

    private Lyric resolveTranslationExecutionSource(LyricTranslation task) {
        if (task.getSourceLyricId() != null) {
            Lyric stored = getById(task.getSourceLyricId());
            if (stored == null
                    || !Objects.equals(stored.getSongId(), task.getSongId())
                    || !Objects.equals(stored.getLyricType(), CommonConstants.LYRIC_TYPE_ORIGINAL)
                    || !Objects.equals(stored.getStatus(), CommonConstants.STATUS_NORMAL)
                    || !Objects.equals(stored.getDeleted(), CommonConstants.NOT_DELETED)
                    || isEmptyContent(stored)) {
                throw new BusinessException(ResultCode.NOT_FOUND, CommonConstants.ERROR_LYRIC_NOT_FOUND);
            }
            log.info("event=lyric_translation_source_resolved taskId={} songId={} source=formal lyricId={}",
                    task.getId(), task.getSongId(), stored.getId());
            return stored;
        }

        Song song = songMapper.selectById(task.getSongId());
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        contentAccessService.requireSongAccess(song, task.getCreatorId());
        String preferredLanguage = firstNonBlank(
                song.getLyricLanguage(), song.getLanguage(), CommonConstants.DEFAULT_LYRIC_LANGUAGE);
        Lyric node3Lyric = readNode3TranslationSource(song, preferredLanguage);
        log.info("event=lyric_translation_source_resolved taskId={} songId={} source=node3",
                task.getId(), task.getSongId());
        return node3Lyric;
    }

    private Lyric readNode3TranslationSource(Song song, String preferredLanguage) {
        String localContent = sshUtil.readRemoteLyricFile(
                song.getName(), song.getArtistNames(), CommonConstants.LYRIC_TYPE_ORIGINAL);
        if (localContent == null || localContent.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原文歌词不存在，无法翻译");
        }

        Lyric lyric = new Lyric();
        lyric.setSongId(song.getId());
        lyric.setContent(normalizeContent(localContent));
        lyric.setLanguage(resolveLocalLanguage(
                song, CommonConstants.LYRIC_TYPE_ORIGINAL, preferredLanguage));
        lyric.setLyricType(CommonConstants.LYRIC_TYPE_ORIGINAL);
        lyric.setSource(CommonConstants.LYRIC_SOURCE_NODE3);
        lyric.setStatus(CommonConstants.STATUS_NORMAL);
        lyric.setDeleted(CommonConstants.NOT_DELETED);
        return lyric;
    }

    private Lyric findActiveLyric(Long songId, String language, Integer lyricType) {
        return getOne(new LambdaQueryWrapper<Lyric>()
                .eq(Lyric::getSongId, songId)
                .eq(Lyric::getLanguage, normalizeLanguage(language))
                .eq(Lyric::getLyricType, lyricType)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Lyric::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Lyric::getCreateTime)
                .last("LIMIT 1"), false);
    }

    private Lyric upsertLyric(Song song, String content, String language, Integer lyricType, String source, Long creatorId) {
        String safeContent = normalizeContent(content);
        String safeLanguage = normalizeLanguage(language);
        Integer safeLyricType = normalizeLyricType(lyricType, CommonConstants.LYRIC_TYPE_ORIGINAL);
        String safeSource = normalizeSource(source);

        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Lyric::getSongId, song.getId())
                .eq(Lyric::getLyricType, safeLyricType)
                .eq(Lyric::getLanguage, safeLanguage)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Lyric::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(Lyric::getCreateTime)
                .last("LIMIT 1");
        Lyric existing = getOne(wrapper);

        if (ObjectUtils.isNotEmpty(existing)) {
            existing.setContent(safeContent);
            existing.setSource(safeSource);
            existing.setUpdateTime(LocalDateTime.now());
            updateById(existing);
            markSongHasLyric(song, safeLanguage);
            return existing;
        } else {
            Lyric lyric = new Lyric();
            lyric.setSongId(song.getId());
            lyric.setContent(safeContent);
            lyric.setLanguage(safeLanguage);
            lyric.setLyricType(safeLyricType);
            lyric.setSource(safeSource);
            lyric.setCreatorId(creatorId);
            lyric.setStatus(CommonConstants.STATUS_NORMAL);
            lyric.setDeleted(CommonConstants.NOT_DELETED);
            lyric.setCreateTime(LocalDateTime.now());
            lyric.setUpdateTime(LocalDateTime.now());
            save(lyric);
            markSongHasLyric(song, safeLanguage);
            return lyric;
        }
    }

    private void markSongHasLyric(Song song, String language) {
        song.setHasLyric(CommonConstants.YES);
        if (ObjectUtils.isEmpty(song.getLyricLanguage())) {
            song.setLyricLanguage(language);
        }
        songMapper.updateById(song);
    }

    private String normalizeContent(String content) {
        if (ObjectUtils.isEmpty(content)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_LYRIC_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词内容不能超过" + MAX_LYRIC_CONTENT_LENGTH + "个字符");
        }
        return trimmed;
    }

    private String normalizeLanguage(String language) {
        String safeLanguage = ObjectUtils.isEmpty(language) ? CommonConstants.DEFAULT_LYRIC_LANGUAGE : language.trim();
        if (safeLanguage.isEmpty() || safeLanguage.length() > MAX_LANGUAGE_LENGTH
                || !LANGUAGE_CODE_PATTERN.matcher(safeLanguage).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词语言参数不合法");
        }
        return safeLanguage;
    }

    private void requireSupportedLanguage(String language) {
        Long count = musicLanguageMapper.selectCount(new LambdaQueryWrapper<MusicLanguage>()
                .eq(MusicLanguage::getCode, language)
                .eq(MusicLanguage::getIsEnabled, CommonConstants.STATUS_NORMAL)
                .eq(MusicLanguage::getDeleted, CommonConstants.NOT_DELETED));
        if (count == null || count <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "暂不支持这个目标语种");
        }
    }

    private LyricTranslation findReusableTranslationTask(Long songId, String language,
                                                           Integer lyricType, Long userId) {
        return lyricTranslationMapper.selectOne(new LambdaQueryWrapper<LyricTranslation>()
                .eq(LyricTranslation::getSongId, songId)
                .eq(LyricTranslation::getTargetLanguage, language)
                .eq(LyricTranslation::getLyricType, lyricType)
                .eq(LyricTranslation::getCreatorId, userId)
                .in(LyricTranslation::getStatus,
                        CommonConstants.TRANSLATION_STATUS_PENDING,
                        CommonConstants.TRANSLATION_STATUS_PROCESSING,
                        CommonConstants.TRANSLATION_STATUS_COMPLETED)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(LyricTranslation::getRequestTime)
                .last("LIMIT 1"));
    }

    private boolean hasActiveTranslationTask(Long songId, String language, Integer lyricType) {
        Long count = lyricTranslationMapper.selectCount(new LambdaQueryWrapper<LyricTranslation>()
                .eq(LyricTranslation::getSongId, songId)
                .eq(LyricTranslation::getTargetLanguage, language)
                .eq(LyricTranslation::getLyricType, lyricType)
                .in(LyricTranslation::getStatus,
                        CommonConstants.TRANSLATION_STATUS_PENDING,
                        CommonConstants.TRANSLATION_STATUS_PROCESSING)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED));
        return count != null && count > 0;
    }

    private void reserveTranslationQuota(Long userId) {
        String quotaKey = lyricTranslationConfig.getQuotaPrefix() + userId;
        Long count = redisTemplate.opsForValue().increment(quotaKey);
        if (count != null && count == 1) {
            redisTemplate.expire(quotaKey,
                    lyricTranslationConfig.getLimitPeriodHours(), TimeUnit.HOURS);
        }
        if (count == null || count > lyricTranslationConfig.getLimitPerUser()) {
            if (count != null) {
                redisTemplate.opsForValue().decrement(quotaKey);
            }
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "今天的 AI 歌词翻译次数已经用完了，明天再来看看吧");
        }
    }

    private void releaseTranslationQuota(Long userId) {
        String quotaKey = lyricTranslationConfig.getQuotaPrefix() + userId;
        Long remaining = redisTemplate.opsForValue().decrement(quotaKey);
        if (remaining != null && remaining <= 0) {
            redisTemplate.delete(quotaKey);
        }
    }

    private String translationLockKey(Long songId, String language, Integer lyricType) {
        return lyricTranslationConfig.getLockPrefix()
                + songId + ":" + lyricType + ":" + language.toLowerCase(Locale.ROOT);
    }

       
                                        
                                              
       
    @Scheduled(fixedDelayString = "${lyric.translation.recovery-delay-ms:60000}")
    public void recoverTranslationTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now
                .minusMinutes(lyricTranslationConfig.getStaleProcessingMinutes());
        List<LyricTranslation> candidates = lyricTranslationMapper.selectList(
                new LambdaQueryWrapper<LyricTranslation>()
                        .and(wrapper -> wrapper
                                .and(pending -> pending
                                        .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PENDING)
                                        .and(due -> due.isNull(LyricTranslation::getNextRetryTime)
                                                .or().le(LyricTranslation::getNextRetryTime, now)))
                                .or(stale -> stale
                                        .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PROCESSING)
                                        .le(LyricTranslation::getUpdateTime, staleBefore)))
                        .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED)
                        .orderByAsc(LyricTranslation::getRequestTime)
                        .last("LIMIT " + Math.max(1,
                                Math.min(100, lyricTranslationConfig.getRecoveryBatchSize()))));
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        int submitted = 0;
        for (LyricTranslation task : candidates) {
            if (Objects.equals(task.getStatus(), CommonConstants.TRANSLATION_STATUS_PROCESSING)) {
                int reset = lyricTranslationMapper.update(null, new LambdaUpdateWrapper<LyricTranslation>()
                        .eq(LyricTranslation::getId, task.getId())
                        .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PROCESSING)
                        .le(LyricTranslation::getUpdateTime, staleBefore)
                        .set(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PENDING)
                        .set(LyricTranslation::getErrorCode, "TASK_RECOVERING")
                        .set(LyricTranslation::getErrorMessage, "TASK_RECOVERING")
                        .set(LyricTranslation::getNextRetryTime, now)
                        .set(LyricTranslation::getProcessingToken, null)
                        .set(LyricTranslation::getUpdateTime, LocalDateTime.now()));
                if (reset != 1) {
                    continue;
                }
            }
            redisTemplate.opsForValue().setIfAbsent(
                    translationLockKey(task.getSongId(), task.getTargetLanguage(), task.getLyricType()),
                    String.valueOf(task.getCreatorId()),
                    lyricTranslationConfig.getLockMinutes(), TimeUnit.MINUTES);
            submitTranslationExecution(task.getId());
            submitted++;
        }
        if (submitted > 0) {
            log.info("event=lyric_translation_recovery submitted={}", submitted);
        }
    }

    private Integer normalizeLyricType(Integer lyricType, Integer defaultType) {
        Integer safeType = ObjectUtils.isEmpty(lyricType) ? defaultType : lyricType;
        if (!Objects.equals(CommonConstants.LYRIC_TYPE_ORIGINAL, safeType)
                && !Objects.equals(CommonConstants.LYRIC_TYPE_TRANSLATE, safeType)
                && !Objects.equals(3, safeType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词类型参数不合法");
        }
        return safeType;
    }

    private Integer normalizeTranslationType(Integer lyricType) {
        Integer safeType = ObjectUtils.isEmpty(lyricType) ? CommonConstants.LYRIC_TYPE_TRANSLATE : lyricType;
        if (!Objects.equals(CommonConstants.LYRIC_TYPE_TRANSLATE, safeType) && !Objects.equals(3, safeType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "翻译类型参数不合法");
        }
        return safeType;
    }

    private String normalizeSource(String source) {
        String safeSource = ObjectUtils.isEmpty(source) ? CommonConstants.LYRIC_SOURCE_USER : source.trim();
        if (!CommonConstants.LYRIC_SOURCE_USER.equals(safeSource)
                && !CommonConstants.LYRIC_SOURCE_ADMIN.equals(safeSource)
                && !CommonConstants.LYRIC_SOURCE_NODE3.equals(safeSource)
                && !CommonConstants.LYRIC_SOURCE_DEEPSEEK.equals(safeSource)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌词来源参数不合法，应为user/admin/node3/deepseek之一");
        }
        return safeSource;
    }

    private void deferPendingTranslation(Long taskId, String errorCode, long delaySeconds) {
        LocalDateTime now = LocalDateTime.now();
        lyricTranslationMapper.update(null, new LambdaUpdateWrapper<LyricTranslation>()
                .eq(LyricTranslation::getId, taskId)
                .eq(LyricTranslation::getStatus, CommonConstants.TRANSLATION_STATUS_PENDING)
                .eq(LyricTranslation::getDeleted, CommonConstants.NOT_DELETED)
                .set(LyricTranslation::getErrorCode, errorCode)
                .set(LyricTranslation::getErrorMessage, errorCode)
                .set(LyricTranslation::getNextRetryTime, now.plusSeconds(Math.max(1L, delaySeconds)))
                .set(LyricTranslation::getUpdateTime, now));
    }

    private int resolveMaxTranslationAttempts() {
        return Math.max(1, Math.min(10, lyricTranslationConfig.getMaxAttempts()));
    }

    private long resolveRetryDelaySeconds(int completedAttempts) {
        long base = Math.max(1L, lyricTranslationConfig.getRetryDelaySeconds());
        long maximum = Math.max(base, lyricTranslationConfig.getMaxRetryDelaySeconds());
        int exponent = Math.max(0, Math.min(10, completedAttempts - 1));
        long multiplier = 1L << exponent;
        return Math.min(maximum, base * multiplier);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

       
                        
       
    protected String callDeepSeekAPI(String prompt) {
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", requireDeepSeekModelName());
        requestBody.set("temperature", deepSeekConfig.getTemperature());
        requestBody.set("max_tokens", deepSeekConfig.getMaxTokens());

        List<JSONObject> messages = new ArrayList<>();
        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", "你是一位专业的歌词翻译专家。请按照用户要求翻译歌词，保持原有的LRC时间标签格式。");
        messages.add(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", prompt);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        int maxAttempts = resolveDeepSeekMaxRetries() + 1;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeDeepSeekRequest(requestBody);
            } catch (RetryableDeepSeekException e) {
                lastFailure = e;
                if (attempt < maxAttempts) {
                    log.warn("event=deepseek_request_retry attempt={} maxAttempts={} errorType={}",
                            attempt, maxAttempts, e.getClass().getSimpleName());
                    waitBeforeDeepSeekRetry(attempt);
                }
            } catch (BusinessException e) {
                throw e;
            }
        }

        String errorMessage = lastFailure == null ? "未知错误" : lastFailure.getMessage();
        log.error("event=deepseek_request_failed attempts={} errorType={}", maxAttempts,
                lastFailure == null ? "Unknown" : lastFailure.getClass().getSimpleName());
        throw new BusinessException(ResultCode.ERROR,
                CommonConstants.ERROR_TRANSLATION_FAILED + ": " + errorMessage);
    }

    private String executeDeepSeekRequest(JSONObject requestBody) throws RetryableDeepSeekException {
        try {
            try (cn.hutool.http.HttpResponse httpResponse = buildDeepSeekRequest(requestBody).execute()) {
                int status = httpResponse.getStatus();
                String response = httpResponse.body();
                if (status >= 500) {
                    throw new RetryableDeepSeekException("翻译服务HTTP状态异常: " + status);
                }
                if (status < 200 || status >= 300) {
                    throw new BusinessException(ResultCode.ERROR,
                            "翻译服务HTTP状态异常: " + status);
                }
                return DeepSeekResponseParser.parseContent(response);
            }
        } catch (RetryableDeepSeekException e) {
            throw e;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryableDeepSeekException(
                    truncate(e.getMessage(), MAX_ERROR_MESSAGE_LENGTH), e);
        }
    }

    private HttpRequest buildDeepSeekRequest(JSONObject requestBody) {
        if (deepSeekConfig == null
                || deepSeekConfig.getBaseUrl() == null
                || deepSeekConfig.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "DeepSeek服务地址未配置");
        }

        String baseUrl = deepSeekConfig.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        HttpRequest request = HttpRequest.post(baseUrl + "/v1/chat/completions")
                .body(requestBody.toString())
                .contentType("application/json")
                .timeout(resolveDeepSeekTimeoutSeconds() * 1000);
        if (deepSeekConfig.getApiKey() != null && !deepSeekConfig.getApiKey().trim().isEmpty()) {
            request.header("Authorization", "Bearer " + deepSeekConfig.getApiKey().trim());
        }
        return request;
    }

    private String requireDeepSeekModelName() {
        String model = deepSeekConfig == null ? null : deepSeekConfig.getModel();
        if (model == null || model.trim().isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "DeepSeek模型未配置");
        }
        return model.trim();
    }

    private String resolveDeepSeekModelNameForAudit() {
        String model = deepSeekConfig == null ? null : deepSeekConfig.getModel();
        return model == null || model.trim().isEmpty() ? "unknown" : model.trim();
    }

    private int resolveDeepSeekTimeoutSeconds() {
        Integer timeout = deepSeekConfig == null ? null : deepSeekConfig.getTimeout();
        return timeout == null ? DEFAULT_DEEPSEEK_TIMEOUT_SECONDS : Math.max(1, timeout);
    }

    private int resolveDeepSeekMaxRetries() {
        Integer retries = deepSeekConfig == null ? null : deepSeekConfig.getMaxRetries();
        if (retries == null) {
            return 0;
        }
        return Math.min(MAX_DEEPSEEK_RETRIES, Math.max(0, retries));
    }

    private void waitBeforeDeepSeekRetry(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, DEEPSEEK_RETRY_BACKOFF_MILLIS * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.ERROR, "DeepSeek翻译重试被中断");
        }
    }

       
              
       
    private String buildTranslationPrompt(String lrcContent, String targetLanguage, Integer lyricType) {
        StringBuilder prompt = new StringBuilder();

        String languageName = getLanguageName(targetLanguage);
        String typeDesc = lyricType == CommonConstants.LYRIC_TYPE_TRANSLATE ? "翻译" : "音译（注音）";

        prompt.append("请将以下歌词").append(typeDesc).append("为").append(languageName);
        prompt.append("。请保持原有的LRC时间标签格式[mm:ss.xx]，只翻译歌词文本内容。\n\n");
        prompt.append("原文歌词：\n");
        prompt.append(lrcContent);
        prompt.append("\n\n请直接输出翻译后的歌词内容，保持LRC格式。");

        return prompt.toString();
    }

       
                   
       
    private String extractLRCTranslation(String originalLRC, String translatedText) {
        return DeepSeekResponseParser.extractLrcTranslation(originalLRC, translatedText);
    }

       
             
       
    private String getLanguageName(String code) {
        Map<String, String> languageNames = new HashMap<>();
        languageNames.put("zh-CN", "简体中文");
        languageNames.put("zh-TW", "繁体中文");
        languageNames.put("en", "英语");
        languageNames.put("ja", "日语");
        languageNames.put("ko", "韩语");
        languageNames.put("fr", "法语");
        languageNames.put("de", "德语");
        languageNames.put("es", "西班牙语");
        languageNames.put("it", "意大利语");
        languageNames.put("pt", "葡萄牙语");
        languageNames.put("ru", "俄语");
        languageNames.put("th", "泰语");
        languageNames.put("vi", "越南语");
        languageNames.put("id", "印尼语");
        languageNames.put("ms", "马来语");

        return languageNames.getOrDefault(code, code);
    }

    @Override
    public Map<String, Object> testDeepSeekConnection() {
        Map<String, Object> result = new HashMap<>();

               
        result.put("baseUrl", deepSeekConfig.getBaseUrl());
        result.put("model", deepSeekConfig.getModel());
        result.put("timeout", deepSeekConfig.getTimeout() + "s");

        boolean connected = false;
        String message = "";
        String responseContent = "";

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", requireDeepSeekModelName());
            requestBody.set("temperature", 0.3);
            requestBody.set("max_tokens", 50);

            List<JSONObject> messages = new ArrayList<>();
            JSONObject testMessage = new JSONObject();
            testMessage.set("role", "user");
            testMessage.set("content", "Hello");
            messages.add(testMessage);
            requestBody.set("messages", messages);

            long startTime = System.currentTimeMillis();
            String response;
            int status;
            try (cn.hutool.http.HttpResponse httpResponse = buildDeepSeekRequest(requestBody).execute()) {
                status = httpResponse.getStatus();
                response = httpResponse.body();
            }
            long endTime = System.currentTimeMillis();

            JSONObject jsonResponse = JSONUtil.parseObj(response);
            responseContent = jsonResponse.toString();

            connected = status >= 200 && status < 300
                    && jsonResponse.getJSONArray("choices") != null
                    && jsonResponse.getJSONArray("choices").size() > 0;

            message = connected
                    ? "连接成功 (耗时: " + (endTime - startTime) + "ms)"
                    : "HTTP状态或响应格式异常: " + status;

        } catch (Exception e) {
            message = "连接失败: " + e.getMessage();
            log.error("DeepSeek连接测试失败");
        }

        result.put("connected", connected);
        result.put("message", message);
        result.put("response", responseContent);

        return result;
    }

       
       
    @Override
    public String getLocalLyricFromFile(Long songId) {
        try {
                                               
            Song song = songMapper.selectById(songId);
            if (song == null) {
                log.warn("Song not found: {}", songId);
                return null;
            }

            String lyric = sshUtil.readRemoteLyricFile(
                    song.getName(), song.getArtistNames(), CommonConstants.LYRIC_TYPE_ORIGINAL);
            if (lyric != null && !lyric.trim().isEmpty()) {
                log.info("Got lyric from node3 for song {}, length: {}", songId, lyric.length());
                return lyric;
            }
            log.warn("Local lyric file not found for song: {}", songId);
            return null;
        } catch (Exception e) {
            log.error("Failed to read local lyric: songId={}", songId);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean syncLocalLyric(Long songId, Long operatorId) {
        return syncLocalLyric(songId, operatorId, CommonConstants.LYRIC_TYPE_ORIGINAL, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean syncLocalLyric(Long songId, Long operatorId, Integer lyricType, String language) {
        if (ObjectUtils.isEmpty(songId) || ObjectUtils.isEmpty(operatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "歌曲ID和操作人不能为空");
        }
        Integer safeLyricType = normalizeLyricType(lyricType, CommonConstants.LYRIC_TYPE_ORIGINAL);
        Song song = songMapper.selectById(songId);
        if (song == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "歌曲不存在");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !UserRole.canModerate(operator.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权同步node3歌词");
        }
        String content = sshUtil.readRemoteLyricFile(
                song.getName(), song.getArtistNames(), safeLyricType);
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "node3未找到歌词文件");
        }
        String targetLanguage = firstNonBlank(
                language,
                song.getLyricLanguage(),
                song.getLanguage(),
                CommonConstants.DEFAULT_LYRIC_LANGUAGE);
        return saveLyric(songId, content, targetLanguage, safeLyricType,
                CommonConstants.LYRIC_SOURCE_NODE3, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> calibrateNode3Lyrics(Long operatorId, Integer limit,
                                                    Boolean dryRun, Boolean includeTranslations) {
        if (ObjectUtils.isEmpty(operatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "操作人不能为空");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !UserRole.canModerate(operator.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权校准node3歌词");
        }

        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), 500);
        boolean preview = dryRun == null || dryRun;
        boolean translations = Boolean.TRUE.equals(includeTranslations);

        LambdaQueryWrapper<Song> songWrapper = new LambdaQueryWrapper<Song>()
                .eq(Song::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Song::getDeleted, CommonConstants.NOT_DELETED)
                .orderByAsc(Song::getId)
                .last("LIMIT " + safeLimit);
        List<Song> songs = songMapper.selectList(songWrapper);
        if (songs == null) {
            songs = Collections.emptyList();
        }

        Map<Long, List<LyricCandidate>> candidatesBySong = new LinkedHashMap<>();
        Set<String> requestedPaths = new LinkedHashSet<>();
        for (Song song : songs) {
            List<LyricCandidate> candidates = new ArrayList<>();
            addLyricCandidates(song, CommonConstants.LYRIC_TYPE_ORIGINAL,
                    resolveLocalLanguage(song, CommonConstants.LYRIC_TYPE_ORIGINAL,
                            CommonConstants.DEFAULT_LYRIC_LANGUAGE), candidates, requestedPaths);
            if (translations) {
                addLyricCandidates(song, CommonConstants.LYRIC_TYPE_TRANSLATE,
                        CommonConstants.DEFAULT_LYRIC_LANGUAGE, candidates, requestedPaths);
            }
            candidatesBySong.put(song.getId(), candidates);
        }

        Map<String, String> remoteFiles = sshUtil.readRemoteLyricFiles(requestedPaths);
        int matched = 0;
        int applied = 0;
        int skippedExisting = 0;
        int missing = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (Song song : songs) {
            List<LyricCandidate> candidates = candidatesBySong.get(song.getId());
            if (candidates == null) {
                continue;
            }
            for (LyricCandidate candidate : candidates) {
                String content = firstMatchingContent(candidate.paths, remoteFiles);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("songId", song.getId());
                item.put("songName", song.getName());
                item.put("lyricType", candidate.lyricType);
                item.put("language", candidate.language);

                if (content == null || content.trim().isEmpty()) {
                    missing++;
                    item.put("action", "missing");
                    items.add(item);
                    continue;
                }

                matched++;
                item.put("path", firstMatchingPath(candidate.paths, remoteFiles));
                if (hasActiveLyric(song.getId(), candidate.lyricType, candidate.language)) {
                    skippedExisting++;
                    item.put("action", "skipped_existing");
                } else if (preview) {
                    item.put("action", "preview");
                } else {
                    saveLyric(song.getId(), content, candidate.language, candidate.lyricType,
                            CommonConstants.LYRIC_SOURCE_NODE3, operatorId);
                    applied++;
                    item.put("action", "applied");
                }
                items.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", preview);
        result.put("includeTranslations", translations);
        result.put("limit", safeLimit);
        result.put("scannedSongs", songs.size());
        result.put("requestedFiles", requestedPaths.size());
        result.put("matched", matched);
        result.put("applied", applied);
        result.put("skippedExisting", skippedExisting);
        result.put("missing", missing);
        result.put("remoteFilesRead", remoteFiles.size());
        result.put("items", items);
        return result;
    }

    private void addLyricCandidates(Song song, Integer lyricType, String language,
                                    List<LyricCandidate> target, Set<String> requestedPaths) {
        List<String> paths = sshUtil.getLyricFileCandidates(song.getName(), song.getArtistNames(), lyricType);
        requestedPaths.addAll(paths);
        target.add(new LyricCandidate(lyricType, language, paths));
    }

    private String firstMatchingContent(List<String> paths, Map<String, String> remoteFiles) {
        for (String path : paths) {
            String content = remoteFiles.get(path);
            if (content != null && !content.trim().isEmpty()) {
                return content;
            }
        }
        return null;
    }

    private String firstMatchingPath(List<String> paths, Map<String, String> remoteFiles) {
        for (String path : paths) {
            if (remoteFiles.containsKey(path)) {
                return path;
            }
        }
        return null;
    }

    private boolean hasActiveLyric(Long songId, Integer lyricType, String language) {
        LambdaQueryWrapper<Lyric> wrapper = new LambdaQueryWrapper<Lyric>()
                .eq(Lyric::getSongId, songId)
                .eq(Lyric::getLyricType, lyricType)
                .eq(Lyric::getLanguage, language)
                .eq(Lyric::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(Lyric::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 1");
        return baseMapper.selectOne(wrapper) != null;
    }

    private static final class LyricCandidate {
        private final Integer lyricType;
        private final String language;
        private final List<String> paths;

        private LyricCandidate(Integer lyricType, String language, List<String> paths) {
            this.lyricType = lyricType;
            this.language = language;
            this.paths = paths;
        }
    }

    private boolean isEmptyContent(Lyric lyric) {
        return lyric == null || lyric.getContent() == null || lyric.getContent().trim().isEmpty();
    }

    private String resolveLocalLanguage(Song song, Integer lyricType, String requestedLanguage) {
        if (lyricType != null && lyricType == CommonConstants.LYRIC_TYPE_TRANSLATE) {
            return requestedLanguage;
        }
        return firstNonBlank(song.getLyricLanguage(), song.getLanguage(), requestedLanguage);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return CommonConstants.DEFAULT_LYRIC_LANGUAGE;
    }

    private static final class RetryableDeepSeekException extends Exception {

        private static final long serialVersionUID = 1L;

        private RetryableDeepSeekException(String message) {
            super(message);
        }

        private RetryableDeepSeekException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
