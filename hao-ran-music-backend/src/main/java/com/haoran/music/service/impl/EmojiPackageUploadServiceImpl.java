



package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.EmojiUploadUtil;
import com.haoran.music.common.util.FileSecurityUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.entity.EmojiUploadBatch;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.EmojiMapper;
import com.haoran.music.mapper.EmojiPackageMapper;
import com.haoran.music.mapper.EmojiUploadBatchMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.EmojiPackageUploadService;
import com.haoran.music.service.EmojiService;
import com.haoran.music.vo.EmojiUploadBatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EmojiPackageUploadServiceImpl implements EmojiPackageUploadService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() { };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<List<Long>>() { };

    private final EmojiUploadBatchMapper batchMapper;
    private final EmojiPackageMapper packageMapper;
    private final EmojiMapper emojiMapper;
    private final UserMapper userMapper;
    private final EmojiService emojiService;
    private final EmojiUploadUtil uploadUtil;
    private final MusicUploadConfig config;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Semaphore uploadSlots;

    public EmojiPackageUploadServiceImpl(EmojiUploadBatchMapper batchMapper,
                                         EmojiPackageMapper packageMapper,
                                         EmojiMapper emojiMapper,
                                         UserMapper userMapper,
                                         EmojiService emojiService,
                                         EmojiUploadUtil uploadUtil,
                                         MusicUploadConfig config,
                                         ObjectMapper objectMapper,
                                         PlatformTransactionManager transactionManager) {
        this.batchMapper = batchMapper;
        this.packageMapper = packageMapper;
        this.emojiMapper = emojiMapper;
        this.userMapper = userMapper;
        this.emojiService = emojiService;
        this.uploadUtil = uploadUtil;
        this.config = config;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.uploadSlots = new Semaphore(Math.max(1, config.getEmojiMaxConcurrentUploads()), true);
    }

    @Override
    public EmojiUploadBatchResult upload(Long packageId, List<MultipartFile> files,
                                         String idempotencyKey, Long creatorId) {
        validateRequest(packageId, files, idempotencyKey, creatorId);
        String fingerprint = fingerprint(packageId, files);
        Reservation reservation = transactionTemplate.execute(status ->
                reserve(packageId, files, idempotencyKey, creatorId, fingerprint));
        if (reservation == null) {
            throw new BusinessException("上传批次登记失败");
        }
        if (reservation.replayResult != null) {
            return reservation.replayResult;
        }
        if (!uploadSlots.tryAcquire()) {
            failBatch(reservation.batch.getId(), "SERVER_BUSY", true);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "上传队伍已满，请稍后再试");
        }

        List<String> completedPaths = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                uploadUtil.uploadEmoji(files.get(i), creatorId, null,
                        fileName(reservation.paths.get(i)));
                completedPaths.add(reservation.paths.get(i));
            }
            return transactionTemplate.execute(status -> finalizeBatch(
                    reservation.batch.getId(), packageId, files, reservation.paths,
                    reservation.category, creatorId));
        } catch (RuntimeException e) {
            cleanupFailedBatch(reservation.batch.getId(), completedPaths, "UPLOAD_FAILED");
            throw e;
        } catch (Exception e) {
            cleanupFailedBatch(reservation.batch.getId(), completedPaths, "UPLOAD_FAILED");
            log.error("event=emoji_secure_upload_failed batchId={} errorType={}",
                    reservation.batch.getId(), e.getClass().getSimpleName());
            throw new BusinessException("上传失败，文件已进入自动清理队列，请稍后重试");
        } finally {
            uploadSlots.release();
        }
    }

    private Reservation reserve(Long packageId, List<MultipartFile> files, String key,
                                Long creatorId, String fingerprint) {
        EmojiUploadBatch existing = batchMapper.selectByCreatorAndKeyForUpdate(creatorId, key);
        if (existing != null) {
            if (!fingerprint.equals(existing.getRequestFingerprint())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "同一个上传凭证不能用于不同文件");
            }
            if ("SUCCEEDED".equals(existing.getStatus())) {
                List<Long> ids = readList(existing.getEmojiIdsJson(), LONG_LIST);
                return new Reservation(existing,
                        new EmojiUploadBatchResult(existing.getId(), ids.size(), ids),
                        readList(existing.getUploadedPathsJson(), STRING_LIST), null);
            }
            if ("PROCESSING".equals(existing.getStatus())) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "这批表情正在处理，请不要重复提交");
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "这批上传已结束，请重新确认后再上传");
        }

        User user = userMapper.selectByIdForUpdate(creatorId);
        UserAccountStatusUtil.requireCanInteract(user, "上传表情");
        EmojiPackage emojiPackage = packageMapper.selectActiveByIdForUpdate(packageId);
        requireOwnedEditablePackage(emojiPackage, creatorId);
        long currentCount = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId).eq(Emoji::getEnabled, 1));
        int remaining = Math.max(0, effectiveLimit(emojiPackage) - (int) currentCount);
        if (files.size() > remaining) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    remaining == 0 ? "这套表情包已经装满了"
                            : "这套表情包还可添加 " + remaining + " 个表情");
        }
        if (batchMapper.countActiveProcessingByCreator(creatorId)
                >= config.getEmojiMaxProcessingBatchesPerUser()) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "已有一批表情正在上传，请等待完成");
        }
        long totalBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        if (batchMapper.sumReservedFilesSince(creatorId, dayStart) + files.size()
                > config.getEmojiDailyMaxFilesPerUser()
                || batchMapper.sumReservedBytesSince(creatorId, dayStart) + totalBytes
                > config.getEmojiDailyMaxBytesPerUser()) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "今天的表情上传额度已经用完，请明天再来");
        }

        LocalDateTime now = LocalDateTime.now();
        EmojiUploadBatch batch = new EmojiUploadBatch();
        batch.setCreatorId(creatorId);
        batch.setPackageId(packageId);
        batch.setIdempotencyKey(key);
        batch.setRequestFingerprint(fingerprint);
        batch.setStatus("PROCESSING");
        batch.setFileCount(files.size());
        batch.setTotalBytes(totalBytes);
        batch.setUploadedPathsJson("[]");
        batch.setEmojiIdsJson("[]");
        batch.setExpiresAt(now.plusMinutes(config.getEmojiBatchProcessingMinutes()));
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchMapper.insert(batch);

        List<String> paths = expectedPaths(batch.getId(), creatorId, files);
        batch.setUploadedPathsJson(writeJson(paths));
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return new Reservation(batch, null, paths, emojiPackage.getCategory());
    }

    private EmojiUploadBatchResult finalizeBatch(Long batchId, Long packageId,
                                                  List<MultipartFile> files,
                                                  List<String> paths, String category,
                                                  Long creatorId) {
        EmojiUploadBatch batch = batchMapper.selectByIdForUpdate(batchId);
        if (batch == null || !"PROCESSING".equals(batch.getStatus())) {
            throw new BusinessException("上传批次状态已经变化，请刷新后查看");
        }
        List<Emoji> emojis = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Emoji emoji = new Emoji();
            emoji.setPackageId(packageId);
            emoji.setCode("custom_" + batchId + "_" + i);
            emoji.setName(displayName(files.get(i).getOriginalFilename(), i));
            emoji.setImageUrl(paths.get(i));
            emoji.setCategory(category == null ? "custom" : category);
            emojis.add(emoji);
        }
        List<Long> ids = emojiService.createCustomEmojis(emojis, creatorId);
        batch.setStatus("SUCCEEDED");
        batch.setEmojiIdsJson(writeJson(ids));
        batch.setErrorCode(null);
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return new EmojiUploadBatchResult(batchId, ids.size(), ids);
    }

    private void validateRequest(Long packageId, List<MultipartFile> files,
                                 String key, Long creatorId) {
        if (packageId == null || creatorId == null || key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上传参数或上传凭证无效");
        }
        if (files == null || files.isEmpty() || files.size() > config.getEmojiMaxFiles()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "每次请选择 1 到 " + config.getEmojiMaxFiles() + " 个表情");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty() || file.getSize() > config.getEmojiMaxFileSize()) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "每个表情必须有内容且不能超过 " + config.getEmojiMaxFileSize() + " 字节");
            }
            String extension = FileSecurityUtil.getFileExtension(file.getOriginalFilename())
                    .replace(".", "").toLowerCase(Locale.ROOT);
            boolean allowed = config.getAllowedImageExtensions().stream()
                    .filter(value -> value != null)
                    .map(value -> value.replace(".", "").toLowerCase(Locale.ROOT))
                    .anyMatch(extension::equals);
            if (!allowed) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 JPG、PNG、GIF 或 WebP 图片");
            }
        }
    }

    private void requireOwnedEditablePackage(EmojiPackage emojiPackage, Long creatorId) {
        if (emojiPackage == null || !"custom".equals(emojiPackage.getType())
                || !creatorId.equals(emojiPackage.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能向自己创建的表情包上传");
        }
        if (!("draft".equals(emojiPackage.getReviewStatus())
                || "rejected".equals(emojiPackage.getReviewStatus()))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "待审核或已上架表情包不能修改");
        }
    }

    private int effectiveLimit(EmojiPackage emojiPackage) {
        return emojiPackage.getItemLimit() == null ? 16 : emojiPackage.getItemLimit();
    }

    private List<String> expectedPaths(Long batchId, Long creatorId, List<MultipartFile> files) {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String extension = FileSecurityUtil.getFileExtension(files.get(i).getOriginalFilename())
                    .toLowerCase(Locale.ROOT);
            paths.add("/emojis/custom/emoji_" + creatorId + "_" + batchId + "_" + i + "_"
                    + UUID.randomUUID().toString().replace("-", "") + extension);
        }
        return paths;
    }

    private String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String displayName(String originalName, int index) {
        String value = originalName == null ? "" : originalName.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "").trim();
        if (value.isEmpty()) {
            value = "表情" + (index + 1);
        }
        value = value.length() > 40 ? value.substring(0, 40) : value;
        SecurityCheckUtil.CheckResult checked = SecurityCheckUtil.checkDescription(value);
        if (!checked.isSafe() || checked.getCleanedValue() == null
                || checked.getCleanedValue().trim().isEmpty()) {
            return "表情" + (index + 1);
        }
        return SecurityCheckUtil.escapeHtml(checked.getCleanedValue().trim());
    }

    private String fingerprint(Long packageId, List<MultipartFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(packageId).getBytes(StandardCharsets.UTF_8));
            for (MultipartFile file : files) {
                digest.update(("|" + file.getOriginalFilename() + "|" + file.getSize() + "|"
                        + file.getContentType()).getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder value = new StringBuilder();
            for (byte item : digest.digest()) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception e) {
            throw new IllegalStateException("cannot calculate upload fingerprint", e);
        }
    }

    private void cleanupFailedBatch(Long batchId, List<String> paths, String errorCode) {
        boolean cleaned = true;
        for (String path : paths) {
            if (!uploadUtil.deleteEmoji(path)) {
                cleaned = false;
            }
        }
        failBatch(batchId, errorCode, cleaned);
    }

    private void failBatch(Long batchId, String errorCode, boolean cleaned) {
        transactionTemplate.executeWithoutResult(status -> {
            EmojiUploadBatch batch = batchMapper.selectByIdForUpdate(batchId);
            if (batch != null && !"SUCCEEDED".equals(batch.getStatus())) {
                batch.setStatus(cleaned ? "CLEANED" : "FAILED");
                batch.setErrorCode(errorCode);
                batch.setUpdatedAt(LocalDateTime.now());
                batchMapper.updateById(batch);
            }
        });
    }

    @Override
    public int cleanupRecoverableBatches() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        List<EmojiUploadBatch> candidates = batchMapper.selectCleanupCandidates(staleBefore, 50);
        int cleanedCount = 0;
        for (EmojiUploadBatch candidate : candidates) {
            if (batchMapper.claimForCleanup(candidate.getId(), staleBefore) != 1) {
                continue;
            }
            boolean cleaned = true;
            for (String path : readList(candidate.getUploadedPathsJson(), STRING_LIST)) {
                long references = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                        .eq(Emoji::getImageUrl, path).eq(Emoji::getEnabled, 1));
                long coverReferences = packageMapper.selectCount(new LambdaQueryWrapper<EmojiPackage>()
                        .eq(EmojiPackage::getCoverUrl, path));
                if (references == 0 && coverReferences == 0 && !uploadUtil.deleteEmoji(path)) {
                    cleaned = false;
                }
            }
            failBatch(candidate.getId(), candidate.getErrorCode(), cleaned);
            if (cleaned) {
                cleanedCount++;
            }
        }
        batchMapper.deleteExpiredTerminal(
                LocalDateTime.now().minusDays(config.getEmojiRetentionDays()), 500);
        return cleanedCount;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize upload batch", e);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("event=emoji_upload_batch_json_invalid errorType={}", e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private static final class Reservation {
        private final EmojiUploadBatch batch;
        private final EmojiUploadBatchResult replayResult;
        private final List<String> paths;
        private final String category;

        private Reservation(EmojiUploadBatch batch, EmojiUploadBatchResult replayResult,
                            List<String> paths, String category) {
            this.batch = batch;
            this.replayResult = replayResult;
            this.paths = paths;
            this.category = category;
        }
    }
}
