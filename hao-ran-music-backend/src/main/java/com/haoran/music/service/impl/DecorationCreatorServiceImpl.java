   
                      
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.decoration.DecorationCreatorRequest;
import com.haoran.music.entity.DecorationConfig;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.DecorationConfigMapper;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.DecorationCreatorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DecorationCreatorServiceImpl implements DecorationCreatorService {

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "avatar_frame", "comment_bar", "player", "dialog_box", "theme", "badge"));
    private static final Set<String> RARITIES = new HashSet<>(Arrays.asList(
            "common", "rare", "epic", "legendary"));

    private final DecorationConfigMapper decorationConfigMapper;
    private final ModerationRecordMapper moderationRecordMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public DecorationCreatorServiceImpl(DecorationConfigMapper decorationConfigMapper,
                                        ModerationRecordMapper moderationRecordMapper,
                                        UserMapper userMapper,
                                        ObjectMapper objectMapper) {
        this.decorationConfigMapper = decorationConfigMapper;
        this.moderationRecordMapper = moderationRecordMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DecorationConfig> getMyDecorations(Long creatorId) {
        return decorationConfigMapper.selectList(new LambdaQueryWrapper<DecorationConfig>()
                .eq(DecorationConfig::getCreatorId, creatorId)
                .eq(DecorationConfig::getDeleted, 0)
                .orderByDesc(DecorationConfig::getUpdateTime));
    }

    @Override
    public DecorationConfig getForReview(Long decorationId) {
        DecorationConfig value = decorationConfigMapper.selectById(decorationId);
        if (value == null || !"custom".equals(value.getSourceType())) {
            throw new BusinessException("装饰作品不存在");
        }
        if ("draft".equals(value.getReviewStatus()) || value.getSubmitTime() == null) {
            throw new BusinessException("装饰作品尚未提交审核");
        }
        return value;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(DecorationCreatorRequest request, Long creatorId) {
        User creator = requireCreatorCanInteract(creatorId, "创作装饰");
        DecorationConfig value = new DecorationConfig();
        value.setDecorationId("creative-" + creatorId + "-" + UUID.randomUUID().toString().replace("-", ""));
        value.setCreatorId(creatorId);
        value.setSourceType("custom");
        value.setReviewStatus("draft");
        value.setContentVersion(1);
        value.setIsEnabled(0);
        value.setSortOrder(0);
        value.setDeleted(0);
        applyRequest(value, request, creator);
        if (decorationConfigMapper.insert(value) != 1) {
            throw new BusinessException("装饰草稿保存失败");
        }
        return value.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long decorationId, DecorationCreatorRequest request, Long creatorId) {
        User creator = requireCreatorCanInteract(creatorId, "修改装饰");
        DecorationConfig current = requireOwnedEditable(decorationId, creatorId);
        if ("rejected".equals(current.getReviewStatus())) {
            current.setReviewStatus("draft");
            current.setSubmitTime(null);
            current.setReviewerId(null);
            current.setReviewTime(null);
        }
        applyRequest(current, request, creator);
        current.setReviewReason(null);
        current.setContentVersion((current.getContentVersion() == null ? 0 : current.getContentVersion()) + 1);
        if (decorationConfigMapper.updateById(current) != 1) {
            throw new BusinessException("装饰草稿保存失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long decorationId, Long creatorId) {
        requireCreatorCanInteract(creatorId, "提交装饰审核");
        DecorationConfig current = requireOwnedEditable(decorationId, creatorId);
        LocalDateTime now = LocalDateTime.now();
        current.setReviewStatus("pending");
        current.setIsEnabled(0);
        current.setSubmitTime(now);
        current.setReviewerId(null);
        current.setReviewTime(null);
        current.setReviewReason(null);
        if (decorationConfigMapper.updateById(current) != 1) {
            throw new BusinessException("装饰提交状态更新失败");
        }

        ModerationRecord record = new ModerationRecord();
        record.setTargetType("decoration");
        record.setTargetId(decorationId);
        record.setSubmitterId(creatorId);
        record.setSubmitterSource("user");
        record.setPriority(5);
        record.setStatus("pending");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (moderationRecordMapper.insert(record) != 1) {
            throw new BusinessException("装饰审核任务创建失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long decorationId, boolean approved, String reason, Long reviewerId) {
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        DecorationConfig current = decorationConfigMapper.selectActiveByIdForUpdate(decorationId);
        if (current == null || !"custom".equals(current.getSourceType())
                || !"pending".equals(current.getReviewStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "装饰作品不在待审核状态");
        }
        if (approved && "cash".equals(current.getObtainType())
                && !isActiveCreator(userMapper.selectById(current.getCreatorId()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作者当前不具备有效创作者资格，不能上架现金装饰");
        }
        current.setReviewStatus(approved ? "approved" : "rejected");
        current.setIsEnabled(approved ? 1 : 0);
        current.setReviewerId(reviewerId);
        current.setReviewTime(LocalDateTime.now());
        current.setReviewReason(trim(reason));
        if (decorationConfigMapper.updateById(current) != 1) {
            throw new BusinessException("装饰审核状态更新失败");
        }
    }

    private DecorationConfig requireOwnedEditable(Long decorationId, Long creatorId) {
        DecorationConfig current = decorationConfigMapper.selectActiveByIdForUpdate(decorationId);
        if (current == null || creatorId == null || !creatorId.equals(current.getCreatorId())
                || !"custom".equals(current.getSourceType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己创建的装饰");
        }
        if (!"draft".equals(current.getReviewStatus()) && !"rejected".equals(current.getReviewStatus())) {
            throw new BusinessException("只有草稿或被退回的装饰可以修改");
        }
        return current;
    }

    private User requireCreatorCanInteract(Long creatorId, String action) {
        if (creatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        User creator = userMapper.selectById(creatorId);
        UserAccountStatusUtil.requireCanInteract(creator, action);
        return creator;
    }

    private void applyRequest(DecorationConfig target, DecorationCreatorRequest request, User creator) {
        String type = trim(request.getDecorationType());
        if (!TYPES.contains(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "装饰类型不正确");
        }
        String rarity = trim(request.getRarity());
        if (rarity.isEmpty()) rarity = "common";
        if (!RARITIES.contains(rarity)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "装饰稀有度不正确");
        }
        String obtainType = trim(request.getObtainType());
        if (!"points".equals(obtainType) && !"cash".equals(obtainType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "创作装饰只支持活跃值或现金定价");
        }
        if ("cash".equals(obtainType) && !isActiveCreator(creator)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有有效创作者可以发布现金装饰");
        }
        if ("points".equals(obtainType) && (request.getPointsCost() == null || request.getPointsCost() <= 0)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写大于0的活跃值价格");
        }
        if ("points".equals(obtainType) && request.getPointsCost() > 100000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "活跃值价格不能超过100000");
        }
        if ("cash".equals(obtainType) && (request.getCashPrice() == null || request.getCashPrice() <= 0)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写大于0的现金价格");
        }
        if ("cash".equals(obtainType) && request.getCashPrice() > 10000000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "现金价格不能超过10000000分");
        }
        String styleConfig = trim(request.getStyleConfig());
        if (styleConfig.isEmpty()) styleConfig = "{}";
        try {
            if (!objectMapper.readTree(styleConfig).isObject()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "样式配置必须是 JSON 对象");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "样式配置不是有效的 JSON");
        }

        target.setDecorationName(trim(request.getDecorationName()));
        target.setDecorationType(type);
        target.setDescription(trim(request.getDescription()));
        target.setIconUrl(requireManagedImagePath(request.getIconUrl(), creator.getId(), "小图标"));
        target.setPreviewUrl(requireManagedImagePath(request.getPreviewUrl(), creator.getId(), "预览图"));
        target.setStyleConfig(styleConfig);
        target.setRarity(rarity);
        target.setObtainType(obtainType);
        target.setObtainCondition("{}");
        target.setPointsCost("points".equals(obtainType) ? request.getPointsCost() : null);
        target.setCashPrice("cash".equals(obtainType) ? request.getCashPrice() : null);
        int permanent = Integer.valueOf(0).equals(request.getIsPermanent()) ? 0 : 1;
        if (permanent == 0 && (request.getDurationDays() == null || request.getDurationDays() <= 0)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "限时装饰需要填写有效天数");
        }
        if (permanent == 0 && request.getDurationDays() > 3650) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "装饰有效期不能超过3650天");
        }
        target.setIsPermanent(permanent);
        target.setDurationDays(permanent == 1 ? null : request.getDurationDays());
    }

    private boolean isActiveCreator(User user) {
        return user != null && Integer.valueOf(1).equals(user.getIsCreator())
                && "active".equalsIgnoreCase(user.getCreatorStatus());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireManagedImagePath(String value, Long creatorId, String fieldName) {
        String path = trim(value);
        String prefix = "/emojis/custom/emoji_" + creatorId + "_";
        if (!path.startsWith(prefix) || path.indexOf('\\') >= 0 || path.contains("..")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "必须使用本人上传的受管图片");
        }
        return path;
    }
}
