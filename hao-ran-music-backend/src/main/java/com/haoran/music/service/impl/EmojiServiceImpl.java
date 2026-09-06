




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.constant.EmojiPackageCapacity;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.EmojiUploadUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserEmoji;
import com.haoran.music.mapper.EmojiMapper;
import com.haoran.music.mapper.EmojiPackageMapper;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserEmojiMapper;
import com.haoran.music.service.EmojiService;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.vo.emoji.EmojiDisplayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




@Slf4j
@Service
public class EmojiServiceImpl extends ServiceImpl<EmojiPackageMapper, EmojiPackage> implements EmojiService {

    private static final int MAX_POINTS_PRICE = 100000;
    private static final BigDecimal MAX_CASH_PRICE = new BigDecimal("9999.99");
    private static final int MAX_DISPLAY_CODES = 50;
    private static final int MAX_CONTENT_EMOJI_COUNT = 20;
    private static final Pattern EMOJI_CODE = Pattern.compile("[A-Za-z0-9_-]{1,50}");
    private static final Pattern CONTENT_EMOJI = Pattern.compile("\\[emoji:([^\\]]{1,80})\\]");

    private final EmojiMapper emojiMapper;
    private final EmojiPackageMapper emojiPackageMapper;
    private final UserMapper userMapper;
    private final UserEmojiMapper userEmojiMapper;
    private final ModerationRecordMapper moderationRecordMapper;
    private final StoreProductPolicyService storeProductPolicyService;
    private final EmojiUploadUtil emojiUploadUtil;
    private final MusicUploadConfig musicUploadConfig;

    public EmojiServiceImpl(EmojiMapper emojiMapper,
                             EmojiPackageMapper emojiPackageMapper,
                             UserMapper userMapper,
                             UserEmojiMapper userEmojiMapper,
                             ModerationRecordMapper moderationRecordMapper,
                             StoreProductPolicyService storeProductPolicyService,
                             EmojiUploadUtil emojiUploadUtil,
                             MusicUploadConfig musicUploadConfig) {
        this.emojiMapper = emojiMapper;
        this.emojiPackageMapper = emojiPackageMapper;
        this.userMapper = userMapper;
        this.userEmojiMapper = userEmojiMapper;
        this.moderationRecordMapper = moderationRecordMapper;
        this.storeProductPolicyService = storeProductPolicyService;
        this.emojiUploadUtil = emojiUploadUtil;
        this.musicUploadConfig = musicUploadConfig;
    }

    @Override
    public List<EmojiPackage> getAllPackages(Long userId) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(EmojiPackage::getType)
                .orderByDesc(EmojiPackage::getSortOrder);
        return enrichPackageQuantities(filterAccessiblePackages(emojiPackageMapper.selectList(wrapper), userId));
    }

    @Override
    public List<EmojiPackage> getEnabledPackages(Long userId) {
        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .orderByDesc(EmojiPackage::getDownloadCount);
        return enrichPackageQuantities(filterAccessiblePackages(emojiPackageMapper.selectList(wrapper), userId));
    }

    @Override
    public EmojiPackage getPackageById(Long packageId, Long userId) {
        if (ObjectUtils.isEmpty(packageId)) {
            return null;
        }
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(packageId);
        if (!canAccessPackage(emojiPackage, userId, purchasedPackageIds(userId))) {
            return null;
        }
        enrichPackageQuantities(Collections.singletonList(emojiPackage));
        return emojiPackage;
    }

    @Override
    public EmojiPackage getPackageForReview(Long packageId) {
        if (ObjectUtils.isEmpty(packageId)) {
            return null;
        }
        EmojiPackage emojiPackage = emojiPackageMapper.selectById(packageId);
        if (emojiPackage == null || !"custom".equals(emojiPackage.getType())
                || "draft".equals(emojiPackage.getReviewStatus())
                || emojiPackage.getSubmitTime() == null) {
            return null;
        }
        enrichPackageQuantities(Collections.singletonList(emojiPackage));
        emojiPackage.setEmojis(emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId)
                .eq(Emoji::getEnabled, 1)
                .orderByAsc(Emoji::getSortOrder)
                .orderByAsc(Emoji::getId)));
        return emojiPackage;
    }

    @Override
    public List<Emoji> getEmojisByPackage(Long packageId, Long userId) {
        if (ObjectUtils.isEmpty(packageId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Emoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Emoji::getPackageId, packageId)
                .eq(Emoji::getEnabled, 1)
                .orderByAsc(Emoji::getSortOrder)
                .orderByAsc(Emoji::getId);

        EmojiPackage emojiPackage = emojiPackageMapper.selectById(packageId);
        if (!canAccessPackage(emojiPackage, userId, purchasedPackageIds(userId))) {
            return Collections.emptyList();
        }
        return emojiMapper.selectList(wrapper);
    }

    @Override
    public List<Emoji> getEmojisByCategory(String category, Long userId) {
        LambdaQueryWrapper<Emoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Emoji::getEnabled, 1);
        if (ObjectUtils.isNotEmpty(category)) {
            wrapper.eq(Emoji::getCategory, category);
        }
        wrapper.orderByAsc(Emoji::getSortOrder);

        return filterAccessibleEmojis(emojiMapper.selectList(wrapper), userId);
    }

    @Override
    public List<Emoji> getSystemEmojis(Long userId) {

        return getEmojisByPackage(0L, userId);
    }

    @Override
    public List<Emoji> searchEmojis(String keyword, Long userId) {
        if (ObjectUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Emoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Emoji::getEnabled, 1)
                .and(w -> w.like(Emoji::getName, keyword)
                        .or()
                        .like(Emoji::getCode, keyword));
        wrapper.orderByDesc(Emoji::getUsageCount);

        return filterAccessibleEmojis(emojiMapper.selectList(wrapper), userId);
    }

    @Override
    public List<EmojiDisplayVO> resolveDisplayEmojis(List<String> codes) {
        LinkedHashSet<String> safeCodes = normalizeEmojiCodes(codes);
        if (safeCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Emoji> emojis = emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                .in(Emoji::getCode, safeCodes)
                .eq(Emoji::getEnabled, 1));
        Set<Long> packageIds = emojis.stream()
                .map(Emoji::getPackageId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        Map<Long, EmojiPackage> packagesById = packageIds.isEmpty()
                ? Collections.emptyMap()
                : emojiPackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(EmojiPackage::getId, item -> item));

        Map<String, Emoji> visibleByCode = emojis.stream()
                .filter(emoji -> canRenderInPublishedContent(emoji, packagesById.get(emoji.getPackageId())))
                .collect(Collectors.toMap(Emoji::getCode, item -> item, (left, right) -> left));
        return safeCodes.stream()
                .map(visibleByCode::get)
                .filter(Objects::nonNull)
                .map(EmojiDisplayVO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void requireContentEmojiAccess(String content, Long userId) {
        if (content == null || !content.contains("[emoji:")) {
            return;
        }
        Matcher matcher = CONTENT_EMOJI.matcher(content);
        List<String> codes = new ArrayList<>();
        while (matcher.find()) {
            String code = matcher.group(1);
            if (!EMOJI_CODE.matcher(code).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "表情标记不完整，请从表情抽屉重新选择");
            }
            codes.add(code);
            if (codes.size() > MAX_CONTENT_EMOJI_COUNT) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "一条评论最多放 20 个表情");
            }
        }
        if (codes.isEmpty()) {
            return;
        }

        LinkedHashSet<String> requestedCodes = new LinkedHashSet<>(codes);
        List<Emoji> candidates = emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                .in(Emoji::getCode, requestedCodes)
                .eq(Emoji::getEnabled, 1));
        Set<String> accessibleCodes = filterAccessibleEmojis(candidates, userId).stream()
                .map(Emoji::getCode)
                .collect(Collectors.toSet());
        if (!accessibleCodes.containsAll(requestedCodes)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "这枚表情暂时不能使用，请从表情抽屉重新选择");
        }
    }

    private LinkedHashSet<String> normalizeEmojiCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (codes.size() > MAX_DISPLAY_CODES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "一次最多查看 50 枚表情");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            String safeCode = code == null ? "" : code.trim();
            if (!EMOJI_CODE.matcher(safeCode).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "表情代码不合法");
            }
            result.add(safeCode);
        }
        return result;
    }

    private boolean canRenderInPublishedContent(Emoji emoji, EmojiPackage emojiPackage) {
        if (isSystemEmoji(emoji)) {
            return true;
        }
        return emojiPackage != null
                && "approved".equals(emojiPackage.getReviewStatus())
                && (isSystemPackage(emojiPackage)
                || UserAccountStatusUtil.canRetainPublicContent(emojiPackage.getCreatorId(), userMapper::selectById));
    }

    @Override
    public void recordUsage(Long emojiId, Long userId) {
        if (ObjectUtils.isEmpty(emojiId)) {
            return;
        }

        Emoji emoji = emojiMapper.selectById(emojiId);
        if (emoji == null) {
            return;
        }
        if (!filterAccessibleEmojis(Collections.singletonList(emoji), userId).isEmpty()) {
            Long count = emoji.getUsageCount();
            emoji.setUsageCount(count == null ? 1L : count + 1);
            emojiMapper.updateById(emoji);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCustomPackage(EmojiPackage emojiPackage, Long creatorId) {
        if (ObjectUtils.isEmpty(emojiPackage)) {
            return null;
        }
        User creator = userMapper.selectByIdForUpdate(creatorId);
        UserAccountStatusUtil.requireCanInteract(creator, "创建表情包");
        long editableCount = emojiPackageMapper.countEditableByCreator(creatorId);
        if (editableCount >= musicUploadConfig.getEmojiMaxEditablePackagesPerUser()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "最多同时保留 " + musicUploadConfig.getEmojiMaxEditablePackagesPerUser()
                            + " 份制作中的表情包，请先完成或删除一份草稿");
        }

        int itemLimit = emojiPackage.getItemLimit() == null
                ? EmojiPackageCapacity.DEFAULT : emojiPackage.getItemLimit();
        if (!EmojiPackageCapacity.isSupported(itemLimit)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "表情包容量仅支持 8、16、24、32 或 40 个");
        }

        normalizePackageCopy(emojiPackage);

        emojiPackage.setType("custom");
        emojiPackage.setCreatorId(creatorId);
        applyPurchaseRules(emojiPackage, creator);
        emojiPackage.setStatus(0);
        emojiPackage.setReviewStatus("draft");
        emojiPackage.setDownloadCount(0);
        emojiPackage.setItemLimit(itemLimit);
        emojiPackage.setCoverEmojiId(null);
        emojiPackage.setCoverUrl(null);

        emojiPackageMapper.insert(emojiPackage);
        log.info("event=emoji_package_created packageId={} creatorId={}", emojiPackage.getId(), creatorId);

        return emojiPackage.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCustomPackage(Long packageId, EmojiPackage changes, Long creatorId) {
        if (changes == null || packageId == null || creatorId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包资料不完整");
        }
        User creator = userMapper.selectById(creatorId);
        UserAccountStatusUtil.requireCanInteract(creator, "修改表情包");
        EmojiPackage current = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (current == null || !"custom".equals(current.getType())
                || !creatorId.equals(current.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己创建的表情包");
        }
        requireEditablePackage(current);
        beginPrivateRevision(current);
        int itemLimit = changes.getItemLimit() == null
                ? effectiveItemLimit(current) : changes.getItemLimit();
        if (!EmojiPackageCapacity.isSupported(itemLimit)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包容量仅支持 8、16、24、32 或 40 个");
        }
        long itemCount = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId)
                .eq(Emoji::getEnabled, 1));
        if (itemCount > itemLimit) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "小箱子已经装了 " + itemCount + " 个表情，容量不能改成 " + itemLimit + " 个");
        }
        normalizePackageCopy(changes);
        EmojiPackage pricing = new EmojiPackage();
        pricing.setPurchaseMode(changes.getPurchaseMode());
        pricing.setPrice(changes.getPrice());
        pricing.setCashPrice(changes.getCashPrice());
        applyPurchaseRules(pricing, creator);

        current.setName(changes.getName());
        current.setDescription(changes.getDescription());
        current.setCategory(changes.getCategory());
        current.setItemLimit(itemLimit);
        current.setPurchaseMode(pricing.getPurchaseMode());
        current.setPrice(pricing.getPrice());
        current.setCashPrice(pricing.getCashPrice());
        current.setIsFree(pricing.getIsFree());
        current.setReviewReason(null);
        if (emojiPackageMapper.updateById(current) != 1) {
            throw new BusinessException("表情包资料保存失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCustomEmoji(Emoji emoji, Long creatorId) {
        List<Long> ids = createCustomEmojisInternal(Collections.singletonList(emoji), creatorId);
        return ids.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createCustomEmojis(List<Emoji> emojis, Long creatorId) {
        return createCustomEmojisInternal(emojis, creatorId);
    }

    private List<Long> createCustomEmojisInternal(List<Emoji> emojis, Long creatorId) {
        if (emojis == null || emojis.isEmpty() || emojis.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情列表不能为空");
        }
        UserAccountStatusUtil.requireCanInteract(creatorId, userMapper::selectById, "创建自定义表情");

        Long packageId = emojis.get(0).getPackageId();
        if (packageId == null || emojis.stream().anyMatch(emoji -> !packageId.equals(emoji.getPackageId()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "一批表情只能添加到同一个表情包");
        }

        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (emojiPackage == null
                || !"custom".equals(emojiPackage.getType())
                || !creatorId.equals(emojiPackage.getCreatorId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能向自己创建的自定义表情包添加表情");
        }
        requireEditablePackage(emojiPackage);
        if (beginPrivateRevision(emojiPackage) && emojiPackageMapper.updateById(emojiPackage) != 1) {
            throw new BusinessException("表情包草稿状态更新失败");
        }

        int itemLimit = effectiveItemLimit(emojiPackage);
        long itemCount = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId)
                .eq(Emoji::getEnabled, 1));
        int remainingCount = Math.max(0, itemLimit - (int) itemCount);
        if (emojis.size() > remainingCount) {
            String message = remainingCount == 0
                    ? "该表情包已达到 " + itemLimit + " 个的容量上限"
                    : "该表情包还可添加 " + remainingCount + " 个表情，本次提交了 " + emojis.size() + " 个";
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }

        List<Long> ids = new ArrayList<>(emojis.size());
        for (Emoji emoji : emojis) {
            emoji.setCreatorId(creatorId);
            emoji.setEnabled(1);
            emoji.setUsageCount(0L);
            if (emoji.getSortOrder() == null) {
                emoji.setSortOrder((int) itemCount + ids.size());
            }

            emojiMapper.insert(emoji);
            ids.add(emoji.getId());
        }
        if (emojiPackage.getCoverEmojiId() == null && !emojis.isEmpty()) {
            updatePackageCover(emojiPackage, emojis.get(0));
        }
        log.info("添加自定义表情: packageId={}, count={}, creatorId={}", packageId, emojis.size(), creatorId);

        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCustomPackageCover(Long packageId, Long emojiId, Long creatorId) {
        if (ObjectUtils.isEmpty(packageId) || ObjectUtils.isEmpty(emojiId) || ObjectUtils.isEmpty(creatorId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择封面表情");
        }
        UserAccountStatusUtil.requireCanInteract(creatorId, userMapper::selectById, "设置表情包封面");

        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (emojiPackage == null
                || !"custom".equals(emojiPackage.getType())
                || !creatorId.equals(emojiPackage.getCreatorId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能修改自己创建的自定义表情包封面");
        }
        requireEditablePackage(emojiPackage);
        if (beginPrivateRevision(emojiPackage) && emojiPackageMapper.updateById(emojiPackage) != 1) {
            throw new BusinessException("表情包草稿状态更新失败");
        }

        Emoji emoji = emojiMapper.selectById(emojiId);
        if (emoji == null
                || !packageId.equals(emoji.getPackageId())
                || !Integer.valueOf(1).equals(emoji.getEnabled())
                || ObjectUtils.isEmpty(emoji.getImageUrl())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "封面必须选择该表情包中的可用表情");
        }
        updatePackageCover(emojiPackage, emoji);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPackage(Long packageId, Long creatorId) {
        User creator = userMapper.selectByIdForUpdate(creatorId);
        UserAccountStatusUtil.requireCanInteract(creator, "提交表情包审核");
        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (emojiPackage == null
                || !"custom".equals(emojiPackage.getType())
                || !creatorId.equals(emojiPackage.getCreatorId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能提交自己创建的表情包");
        }
        requireEditablePackage(emojiPackage);

        long pendingCount = emojiPackageMapper.countPendingByCreator(creatorId);
        if (pendingCount >= musicUploadConfig.getEmojiMaxPendingPackagesPerUser()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "最多同时有 " + musicUploadConfig.getEmojiMaxPendingPackagesPerUser()
                            + " 套表情包等待审核，请等一套处理完成后再提交");
        }

        int itemLimit = effectiveItemLimit(emojiPackage);
        long itemCount = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId)
                .eq(Emoji::getEnabled, 1));
        if (itemCount != itemLimit) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "表情包需要达到所选容量 " + itemLimit + " 个后才能提交审核，当前为 " + itemCount + " 个");
        }

        LocalDateTime now = LocalDateTime.now();
        emojiPackage.setStatus(0);
        emojiPackage.setReviewStatus("pending");
        emojiPackage.setSubmitTime(now);
        emojiPackage.setReviewerId(null);
        emojiPackage.setReviewTime(null);
        emojiPackage.setReviewReason(null);
        if (emojiPackageMapper.updateById(emojiPackage) != 1) {
            throw new BusinessException("表情包提交状态更新失败");
        }

        ModerationRecord record = new ModerationRecord();
        record.setTargetType("emoji_package");
        record.setTargetId(packageId);
        record.setSubmitterId(creatorId);
        record.setSubmitterSource("user");
        record.setPriority(5);
        record.setStatus("pending");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (moderationRecordMapper.insert(record) != 1) {
            throw new BusinessException("表情包审核任务创建失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPackage(Long packageId, boolean approved, String reason, Long reviewerId) {
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (emojiPackage == null || !"pending".equals(emojiPackage.getReviewStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包不在待审核状态");
        }
        if (approved && "cash".equals(emojiPackage.getPurchaseMode())) {
            User creator = userMapper.selectById(emojiPackage.getCreatorId());
            if (!isActiveCreator(creator)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "作者当前不具备有效创作者资格，不能上架现金表情包");
            }
        }

        emojiPackage.setReviewStatus(approved ? "approved" : "rejected");
        emojiPackage.setStatus(approved ? 1 : 0);
        emojiPackage.setReviewerId(reviewerId);
        emojiPackage.setReviewTime(LocalDateTime.now());
        emojiPackage.setReviewReason(reason);
        if (emojiPackageMapper.updateById(emojiPackage) != 1) {
            throw new BusinessException("表情包审核状态更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCustomEmoji(Long emojiId, Long creatorId) {
        if (ObjectUtils.isEmpty(emojiId) || ObjectUtils.isEmpty(creatorId)) {
            return false;
        }

        Emoji emoji = emojiMapper.selectById(emojiId);
        if (emoji == null || !creatorId.equals(emoji.getCreatorId())) {
            return false;
        }
        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(emoji.getPackageId());
        if (emojiPackage == null || !creatorId.equals(emojiPackage.getCreatorId())) {
            return false;
        }
        requireEditablePackage(emojiPackage);
        if (beginPrivateRevision(emojiPackage) && emojiPackageMapper.updateById(emojiPackage) != 1) {
            throw new BusinessException("表情包草稿状态更新失败");
        }

        int result = emojiMapper.deleteById(emojiId);
        if (result > 0 && Objects.equals(emojiPackage.getCoverEmojiId(), emojiId)) {
            Emoji nextCover = emojiMapper.selectOne(new LambdaQueryWrapper<Emoji>()
                    .eq(Emoji::getPackageId, emojiPackage.getId())
                    .eq(Emoji::getEnabled, 1)
                    .orderByAsc(Emoji::getSortOrder)
                    .orderByAsc(Emoji::getId)
                    .last("LIMIT 1"));
            updatePackageCover(emojiPackage, nextCover);
        }
        if (result > 0) {
            deleteManagedImagesAfterCommit(Collections.singleton(emoji.getImageUrl()));
        }
        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCustomPackage(Long packageId, Long creatorId) {
        if (ObjectUtils.isEmpty(packageId) || ObjectUtils.isEmpty(creatorId)) {
            return false;
        }

        EmojiPackage emojiPackage = emojiPackageMapper.selectActiveByIdForUpdate(packageId);
        if (emojiPackage == null || !"custom".equals(emojiPackage.getType())
                || !creatorId.equals(emojiPackage.getCreatorId())) {
            return false;
        }
        requireEditablePackage(emojiPackage);

        List<Emoji> emojis = emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                .eq(Emoji::getPackageId, packageId));
        Set<String> imageUrls = emojis.stream()
                .map(Emoji::getImageUrl)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ObjectUtils.isNotEmpty(emojiPackage.getCoverUrl())) {
            imageUrls.add(emojiPackage.getCoverUrl());
        }

        emojiMapper.delete(new LambdaQueryWrapper<Emoji>().eq(Emoji::getPackageId, packageId));
        if (emojiPackageMapper.deleteById(packageId) != 1) {
            throw new BusinessException("表情包草稿删除失败");
        }
        deleteManagedImagesAfterCommit(imageUrls);
        return true;
    }

    private void deleteManagedImagesAfterCommit(Collection<String> imageUrls) {
        List<String> managedUrls = imageUrls.stream()
                .filter(Objects::nonNull)
                .filter(url -> url.startsWith("/emojis/custom/") && url.indexOf('?', 16) < 0)
                .distinct()
                .collect(Collectors.toList());
        if (managedUrls.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> managedUrls.forEach(this::deleteManagedImageIfUnreferenced);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void deleteManagedImageIfUnreferenced(String imageUrl) {
        try {
            long emojiReferences = emojiMapper.selectCount(new LambdaQueryWrapper<Emoji>()
                    .eq(Emoji::getImageUrl, imageUrl));
            long coverReferences = emojiPackageMapper.selectCount(new LambdaQueryWrapper<EmojiPackage>()
                    .eq(EmojiPackage::getCoverUrl, imageUrl));
            if (emojiReferences == 0 && coverReferences == 0 && !emojiUploadUtil.deleteEmoji(imageUrl)) {
                log.warn("event=emoji_image_cleanup_deferred");
            }
        } catch (Exception error) {
            log.error("event=emoji_image_cleanup_failed errorType={}", error.getClass().getSimpleName());
        }
    }

    @Override
    public IPage<EmojiPackage> pagePackages(PageQuery pageQuery, Long userId) {
        Page<EmojiPackage> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getStatus, 1)
                .orderByDesc(EmojiPackage::getDownloadCount);

        IPage<EmojiPackage> result = emojiPackageMapper.selectPage(page, wrapper);
        result.setRecords(enrichPackageQuantities(filterAccessiblePackages(result.getRecords(), userId)));
        return result;
    }

    @Override
    public List<EmojiPackage> getUserPackages(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<EmojiPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmojiPackage::getCreatorId, creatorId)
                .orderByDesc(EmojiPackage::getCreateTime);

        return enrichPackageQuantities(emojiPackageMapper.selectList(wrapper));
    }

    @Override
    public List<Emoji> getHotEmojis(Integer limit, Long userId) {
        int actualLimit = limit != null ? limit : 50;

        LambdaQueryWrapper<Emoji> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Emoji::getEnabled, 1)
                .orderByDesc(Emoji::getUsageCount)
                .last("LIMIT " + publicCandidateLimit(actualLimit));

        return filterAccessibleEmojis(emojiMapper.selectList(wrapper), userId).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getEmojiStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<EmojiPackage> publicPackages = filterPublicPackages(emojiPackageMapper.selectList(null));
        List<Emoji> allEmojis = filterPublicEmojis(emojiMapper.selectList(null));


        stats.put("packageCount", (long) publicPackages.size());


        stats.put("systemEmojiCount", allEmojis.stream().filter(this::isSystemEmoji).count());


        stats.put("customEmojiCount", allEmojis.stream().filter(emoji -> !isSystemEmoji(emoji)).count());


        Map<String, Long> categoryStats = allEmojis.stream()
                .collect(Collectors.groupingBy(emoji -> {
                    String cat = emoji.getCategory();
                    return ObjectUtils.isNotEmpty(cat) ? cat : "uncategorized";
                }, Collectors.counting()));
        stats.put("categoryStats", categoryStats);


        long totalUsage = allEmojis.stream()
                .mapToLong(emoji -> emoji.getUsageCount() != null ? emoji.getUsageCount() : 0L)
                .sum();
        stats.put("totalUsage", totalUsage);

        return stats;
    }

    private int publicCandidateLimit(int limit) {
        return Math.min(Math.max(limit * 3, limit), 300);
    }

    private List<EmojiPackage> enrichPackageQuantities(List<EmojiPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return packages == null ? Collections.emptyList() : packages;
        }
        List<Long> packageIds = packages.stream()
                .map(EmojiPackage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<Emoji>> emojisByPackage = new HashMap<>();
        if (!packageIds.isEmpty()) {
            for (Emoji emoji : emojiMapper.selectList(new LambdaQueryWrapper<Emoji>()
                    .in(Emoji::getPackageId, packageIds)
                    .eq(Emoji::getEnabled, 1)
                    .orderByAsc(Emoji::getPackageId)
                    .orderByAsc(Emoji::getSortOrder)
                    .orderByAsc(Emoji::getId))) {
                if (emoji.getPackageId() != null) {
                    emojisByPackage.computeIfAbsent(emoji.getPackageId(), ignored -> new ArrayList<>()).add(emoji);
                }
            }
        }
        for (EmojiPackage emojiPackage : packages) {
            List<Emoji> packageEmojis = emojisByPackage.getOrDefault(emojiPackage.getId(), Collections.emptyList());
            int itemCount = packageEmojis.size();
            int itemLimit = effectiveItemLimit(emojiPackage);
            emojiPackage.setItemLimit(itemLimit);
            emojiPackage.setItemCount(itemCount);
            emojiPackage.setRemainingCount(Math.max(0, itemLimit - itemCount));
            Emoji effectiveCover = packageEmojis.stream()
                    .filter(emoji -> Objects.equals(emoji.getId(), emojiPackage.getCoverEmojiId()))
                    .findFirst()
                    .orElse(packageEmojis.isEmpty() ? null : packageEmojis.get(0));
            if (effectiveCover != null) {
                emojiPackage.setCoverEmojiId(effectiveCover.getId());
                emojiPackage.setCoverUrl(effectiveCover.getImageUrl());
            }
        }
        return packages;
    }

    private void updatePackageCover(EmojiPackage emojiPackage, Emoji emoji) {
        Long coverEmojiId = emoji == null ? null : emoji.getId();
        String coverUrl = emoji == null ? null : emoji.getImageUrl();
        if (Objects.equals(emojiPackage.getCoverEmojiId(), coverEmojiId)
                && Objects.equals(emojiPackage.getCoverUrl(), coverUrl)) {
            return;
        }
        if (emojiPackageMapper.updateCover(emojiPackage.getId(), coverEmojiId, coverUrl) != 1) {
            throw new BusinessException("表情包封面更新失败");
        }
        emojiPackage.setCoverEmojiId(coverEmojiId);
        emojiPackage.setCoverUrl(coverUrl);
    }

    private int effectiveItemLimit(EmojiPackage emojiPackage) {
        Integer configured = emojiPackage.getItemLimit();
        return EmojiPackageCapacity.effective(configured);
    }

    private void requireEditablePackage(EmojiPackage emojiPackage) {
        String reviewStatus = emojiPackage.getReviewStatus();
        if (!"draft".equals(reviewStatus) && !"rejected".equals(reviewStatus)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "待审核或已上架表情包不能修改");
        }
    }

    private boolean beginPrivateRevision(EmojiPackage emojiPackage) {
        if (!"rejected".equals(emojiPackage.getReviewStatus())) {
            return false;
        }
        emojiPackage.setReviewStatus("draft");
        emojiPackage.setStatus(0);
        emojiPackage.setSubmitTime(null);
        emojiPackage.setReviewerId(null);
        emojiPackage.setReviewTime(null);
        emojiPackage.setReviewReason(null);
        return true;
    }

    private void applyPurchaseRules(EmojiPackage emojiPackage, User creator) {
        String purchaseMode = emojiPackage.getPurchaseMode() == null
                ? "points" : emojiPackage.getPurchaseMode().trim().toLowerCase(Locale.ROOT);
        if ("cash".equals(purchaseMode)) {
            if (!isActiveCreator(creator)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅有效创作者可创建人民币付费表情包");
            }
            if (emojiPackage.getCashPrice() == null
                    || emojiPackage.getCashPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "人民币表情包价格必须大于 0");
            }
            if (emojiPackage.getCashPrice().compareTo(MAX_CASH_PRICE) > 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "人民币表情包价格不能超过 9999.99 元");
            }
            emojiPackage.setPrice(0);
            emojiPackage.setIsFree(0);
        } else if ("points".equals(purchaseMode)) {
            if (emojiPackage.getPrice() == null || emojiPackage.getPrice() <= 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活跃值表情包价格必须大于 0");
            }
            if (emojiPackage.getPrice() > MAX_POINTS_PRICE) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "活跃值表情包价格不能超过 100000");
            }
            emojiPackage.setCashPrice(null);
            emojiPackage.setIsFree(0);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户投稿表情包仅支持活跃值或人民币购买");
        }
        emojiPackage.setPurchaseMode(purchaseMode);
    }

    private void normalizePackageCopy(EmojiPackage emojiPackage) {
        String name = emojiPackage.getName() == null ? "" : emojiPackage.getName().trim();
        String description = emojiPackage.getDescription() == null ? "" : emojiPackage.getDescription().trim();
        if (name.isEmpty() || name.length() > 40) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包名称需要填写，且不能超过 40 个字");
        }
        if (description.length() > 200) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包简介不能超过 200 个字");
        }
        SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkDescription(name);
        SecurityCheckUtil.CheckResult descriptionCheck = SecurityCheckUtil.checkDescription(description);
        if (!nameCheck.isSafe() || !descriptionCheck.isSafe()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表情包资料里有不能使用的内容");
        }
        emojiPackage.setName(SecurityCheckUtil.escapeHtml(nameCheck.getCleanedValue()));
        emojiPackage.setDescription(SecurityCheckUtil.escapeHtml(descriptionCheck.getCleanedValue()));
        String category = emojiPackage.getCategory() == null ? "custom" : emojiPackage.getCategory().trim();
        if (!Arrays.asList("emotion", "animal", "food", "object", "symbol", "custom").contains(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择合适的表情包分类");
        }
        emojiPackage.setCategory(category);
    }

    private boolean isActiveCreator(User user) {
        return UserAccountStatusUtil.canInteract(user)
                && Integer.valueOf(1).equals(user.getIsCreator())
                && "active".equalsIgnoreCase(user.getCreatorStatus());
    }

    private List<EmojiPackage> filterPublicPackages(List<EmojiPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> creatorIds = packages.stream()
                .map(EmojiPackage::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedCreatorIds = UserAccountStatusUtil.filterPublicContentUserIds(
                creatorIds,
                ids -> userMapper.selectBatchIds(ids)
        );
        return packages.stream()
                .filter(pkg -> isSystemPackage(pkg) || allowedCreatorIds.contains(pkg.getCreatorId()))
                .collect(Collectors.toList());
    }

    private List<EmojiPackage> filterAccessiblePackages(List<EmojiPackage> packages, Long userId) {
        Set<Long> ownedPackageIds = purchasedPackageIds(userId);
        return packages.stream()
                .filter(pkg -> canAccessPackage(pkg, userId, ownedPackageIds))
                .collect(Collectors.toList());
    }

    private List<Emoji> filterAccessibleEmojis(List<Emoji> emojis, Long userId) {
        Set<Long> packageIds = emojis.stream()
                .map(Emoji::getPackageId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
        if (packageIds.isEmpty()) {
            return filterPublicEmojis(emojis);
        }

        Map<Long, EmojiPackage> packagesById = emojiPackageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(EmojiPackage::getId, pkg -> pkg));
        Set<Long> ownedPackageIds = purchasedPackageIds(userId);
        return emojis.stream()
                .filter(emoji -> emoji.getPackageId() == null || emoji.getPackageId() <= 0
                        || canAccessPackage(packagesById.get(emoji.getPackageId()), userId, ownedPackageIds))
                .collect(Collectors.toList());
    }

    private Set<Long> purchasedPackageIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return userEmojiMapper.selectList(new LambdaQueryWrapper<UserEmoji>()
                        .eq(UserEmoji::getUserId, userId)
                        .eq(UserEmoji::getIsPurchased, 1))
                .stream()
                .map(UserEmoji::getEmojiPackageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean canAccessPackage(EmojiPackage emojiPackage,
                                     Long userId,
                                     Set<Long> ownedPackageIds) {
        if (emojiPackage != null && userId != null && userId.equals(emojiPackage.getCreatorId())) {
            return true;
        }
        if (emojiPackage == null) {
            return false;
        }
        boolean owned = userId != null && ownedPackageIds.contains(emojiPackage.getId());
        if (owned) {
            return storeProductPolicyService.canUseOwnedEntitlement("emoji_package", emojiPackage.getId());
        }
        if (!storeProductPolicyService.isDirectlyVisible("emoji_package", emojiPackage.getId())) {
            return false;
        }
        if (!requiresOwnership(emojiPackage)) {
            return true;
        }
        return false;
    }

    private boolean requiresOwnership(EmojiPackage emojiPackage) {
        return emojiPackage != null && Integer.valueOf(0).equals(emojiPackage.getIsFree());
    }

    private List<Emoji> filterPublicEmojis(List<Emoji> emojis) {
        if (emojis == null || emojis.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> creatorIds = emojis.stream()
                .map(Emoji::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedCreatorIds = UserAccountStatusUtil.filterPublicContentUserIds(
                creatorIds,
                ids -> userMapper.selectBatchIds(ids)
        );
        return emojis.stream()
                .filter(emoji -> isSystemEmoji(emoji) || allowedCreatorIds.contains(emoji.getCreatorId()))
                .collect(Collectors.toList());
    }

    private boolean isPublicPackage(EmojiPackage emojiPackage) {
        return emojiPackage != null
                && (isSystemPackage(emojiPackage)
                || UserAccountStatusUtil.canExposePublicContent(emojiPackage.getCreatorId(), userMapper::selectById));
    }

    private boolean isPublicEmoji(Emoji emoji) {
        return emoji != null
                && (isSystemEmoji(emoji)
                || UserAccountStatusUtil.canExposePublicContent(emoji.getCreatorId(), userMapper::selectById));
    }

    private boolean isSystemPackage(EmojiPackage emojiPackage) {
        return emojiPackage != null
                && (emojiPackage.getCreatorId() == null || "system".equals(emojiPackage.getType()));
    }

    private boolean isSystemEmoji(Emoji emoji) {
        return emoji != null
                && (emoji.getCreatorId() == null || Long.valueOf(0L).equals(emoji.getPackageId()));
    }
}
