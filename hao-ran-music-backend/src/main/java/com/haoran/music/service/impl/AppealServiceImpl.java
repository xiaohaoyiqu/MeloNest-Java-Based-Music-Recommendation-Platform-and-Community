   
                      
   
package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.appeal.AppealCreateDTO;
import com.haoran.music.dto.appeal.AppealQueryDTO;
import com.haoran.music.dto.appeal.AppealReviewDTO;
import com.haoran.music.entity.Appeal;
import com.haoran.music.entity.CreatorApply;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.AppealMapper;
import com.haoran.music.mapper.CreatorApplyMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AppealRecordService;
import com.haoran.music.service.AppealService;
import com.haoran.music.service.PrivateAttachmentService;
import com.haoran.music.enums.PrivateAttachmentPurpose;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.vo.appeal.AppealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AppealServiceImpl extends ServiceImpl<AppealMapper, Appeal> implements AppealService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final Set<String> APPEAL_TYPES = new HashSet<String>(Arrays.asList(
            "BAN", "CREATOR", "CONTENT"));
    private static final Set<String> REVIEW_RESULTS = new HashSet<String>(Arrays.asList(
            STATUS_APPROVED, STATUS_REJECTED));

    @Resource
    private UserMapper userMapper;

    @Resource
    private CreatorApplyMapper creatorApplyMapper;

    @Resource
    private AppealRecordService appealRecordService;

    @Autowired
    private PrivateAttachmentService privateAttachmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAppeal(Long userId, AppealCreateDTO dto) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (dto == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉参数不能为空");
        }

        String appealType = normalizeAppealType(dto.getAppealType());
        if (!APPEAL_TYPES.contains(appealType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉类型不合法");
        }
        String appealReason = dto.getAppealReason() == null ? "" : dto.getAppealReason().trim();
        if (appealReason.length() < 10 || appealReason.length() > 2000) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉理由应为10至2000字");
        }
        if (StrUtil.isNotBlank(dto.getEvidenceUrls())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "新申诉不再接受证据URL，请先上传私有附件并提交evidenceAssetIds");
        }

                                                                                             
                                                                                            
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        Long authoritativeRelatedId = resolveAppealTarget(user, appealType, dto.getRelatedId());
        if (hasPendingAppeal(userId, appealType, authoritativeRelatedId)) {
            throw new BusinessException("同一事项已有待处理的申诉，请勿重复提交");
        }

        LocalDateTime now = LocalDateTime.now();
        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setAppealType(appealType);
        appeal.setAppealReason(appealReason);
        appeal.setRelatedId(authoritativeRelatedId);
        appeal.setEvidenceUrls(null);
        appeal.setAppealStatus(STATUS_PENDING);
        appeal.setCreateTime(now);
        appeal.setUpdateTime(now);

        baseMapper.insert(appeal);
        if (ObjectUtils.isNotEmpty(dto.getEvidenceAssetIds())) {
            privateAttachmentService.bindAssets(userId,
                    PrivateAttachmentPurpose.APPEAL_EVIDENCE.name(), dto.getEvidenceAssetIds(),
                    PrivateAttachmentPurpose.APPEAL_EVIDENCE.getTargetType(), appeal.getId());
        }
        recordAppealAction(appeal.getId(), userId, "USER", "CREATE", "用户创建申诉");
        return appeal.getId();
    }

    @Override
    public IPage<AppealVO> pageAppeals(AppealQueryDTO dto) {
        AppealQueryDTO query = dto == null ? new AppealQueryDTO() : dto;
        Page<Appeal> page = new Page<Appeal>(query.getPage(), query.getSize());

        String appealType = normalizeAppealType(query.getAppealType());
        String appealStatus = normalizeAppealStatus(query.getAppealStatus());
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();

        LambdaQueryWrapper<Appeal> wrapper = new LambdaQueryWrapper<Appeal>();
        wrapper.eq(query.getUserId() != null, Appeal::getUserId, query.getUserId())
                .eq(StrUtil.isNotBlank(appealType), Appeal::getAppealType, appealType)
                .eq(StrUtil.isNotBlank(appealStatus), Appeal::getAppealStatus, appealStatus)
                .eq(query.getReviewerId() != null, Appeal::getReviewerId, query.getReviewerId());

        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(item -> item.like(Appeal::getAppealReason, keyword)
                    .or()
                    .like(Appeal::getEvidenceUrls, keyword));
        }

        wrapper.orderByDesc(Appeal::getCreateTime);
        IPage<Appeal> appealPage = baseMapper.selectPage(page, wrapper);
        return appealPage.convert(this::convertToVO);
    }

    @Override
    public AppealVO getAppealDetail(Long appealId) {
        if (appealId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉ID不能为空");
        }
        Appeal appeal = baseMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        }
        return convertToVO(appeal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAppeal(Long reviewerId, AppealReviewDTO dto) {
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (dto == null || dto.getAppealId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉ID不能为空");
        }

        String reviewResult = normalizeAppealStatus(dto.getReviewResult());
        if (!REVIEW_RESULTS.contains(reviewResult)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "申诉审核结果不合法");
        }

        Appeal appeal = baseMapper.selectById(dto.getAppealId());
        if (appeal == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        }
        if (!STATUS_PENDING.equals(appeal.getAppealStatus())) {
            throw new BusinessException("该申诉已处理，无法重复审核");
        }
        User appellant = userMapper.selectById(appeal.getUserId());
        AdminAccountOperationGuard.requireCanOperateAccount(
                userMapper.selectById(reviewerId), appellant, "审核用户申诉");

        LocalDateTime now = LocalDateTime.now();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, appeal.getId())
                .eq(Appeal::getAppealStatus, STATUS_PENDING)
                .set(Appeal::getAppealStatus, reviewResult)
                .set(Appeal::getReviewerId, reviewerId)
                .set(Appeal::getReviewResult, reviewResult)
                .set(Appeal::getReviewTime, now)
                .set(Appeal::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("该申诉已被其他审核人处理");
        }
        appeal.setAppealStatus(reviewResult);
        appeal.setReviewerId(reviewerId);
        appeal.setReviewResult(reviewResult);
        appeal.setReviewTime(now);
        appeal.setUpdateTime(now);

        String action = STATUS_APPROVED.equals(reviewResult) ? "APPROVE" : "REJECT";
        recordAppealAction(appeal.getId(), reviewerId, "ADMIN", action, dto.getReviewRemark());

        if (STATUS_APPROVED.equals(reviewResult)) {
            handleApprovedAppeal(appeal);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAppeal(Long userId, Long appealId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Appeal appeal = baseMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        }
        if (!userId.equals(appeal.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能取消自己的申诉");
        }
        if (!STATUS_PENDING.equals(appeal.getAppealStatus())) {
            throw new BusinessException("只能取消待审核的申诉");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<Appeal>()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getUserId, userId)
                .eq(Appeal::getAppealStatus, STATUS_PENDING)
                .set(Appeal::getAppealStatus, STATUS_CANCELLED)
                .set(Appeal::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("该申诉已被其他操作处理");
        }
        recordAppealAction(appeal.getId(), userId, "USER", "CANCEL", "用户取消申诉");
        privateAttachmentService.releaseTargetReferences(
                PrivateAttachmentPurpose.APPEAL_EVIDENCE.getTargetType(), appeal.getId());
    }

    @Override
    public IPage<AppealVO> getMyAppeals(Long userId, AppealQueryDTO dto) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        AppealQueryDTO query = dto == null ? new AppealQueryDTO() : dto;
        query.setUserId(userId);
        return pageAppeals(query);
    }

    @Override
    public Map<String, Long> getAppealStats() {
        Map<String, Long> stats = new HashMap<String, Long>();
        stats.put("total", baseMapper.selectCount(null));
        stats.put("pending", countByStatus(STATUS_PENDING));
        stats.put("approved", countByStatus(STATUS_APPROVED));
        stats.put("rejected", countByStatus(STATUS_REJECTED));
        stats.put("cancelled", countByStatus(STATUS_CANCELLED));
        return stats;
    }

    private Long countByStatus(String status) {
        return baseMapper.selectCount(new LambdaQueryWrapper<Appeal>().eq(Appeal::getAppealStatus, status));
    }

    private boolean hasPendingAppeal(Long userId, String appealType, Long relatedId) {
        LambdaQueryWrapper<Appeal> wrapper = new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getUserId, userId)
                .eq(Appeal::getAppealType, appealType)
                .eq(Appeal::getAppealStatus, STATUS_PENDING);
        if (relatedId == null) {
            wrapper.isNull(Appeal::getRelatedId);
        } else {
            wrapper.eq(Appeal::getRelatedId, relatedId);
        }
        Long count = baseMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

       
                                      
       
    private Long resolveAppealTarget(User user, String appealType, Long requestedRelatedId) {
        if ("BAN".equals(appealType)) {
            if (UserAccountStatusUtil.canInteract(user)) {
                throw new BusinessException("当前账号未受限，无需提交账号状态申诉");
            }
            return null;
        }
        if ("CONTENT".equals(appealType)) {
            throw new BusinessException("作品处理复核请从本人作品的审核结果发起");
        }

        CreatorApply application = creatorApplyMapper.selectOne(
                new LambdaQueryWrapper<CreatorApply>()
                        .eq(CreatorApply::getUserId, user.getId())
                        .eq(CreatorApply::getStatus, "rejected")
                        .orderByDesc(CreatorApply::getCreateTime)
                        .last("LIMIT 1"));
        if (application == null) {
            throw new BusinessException("没有可申诉的创作者登记记录");
        }
        if (requestedRelatedId != null && !requestedRelatedId.equals(application.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "关联记录不属于当前可申诉事项");
        }
        return application.getId();
    }

    private void handleApprovedAppeal(Appeal appeal) {
        String appealType = appeal.getAppealType();
        if ("BAN".equals(appealType)) {
            User user = userMapper.selectById(appeal.getUserId());
            if (user != null) {
                user.setStatus(CommonConstants.STATUS_NORMAL);
                user.setIsBanned(CommonConstants.NO);
                user.setBanReason(null);
                user.setBanStartTime(null);
                user.setBanEndTime(null);
                if (UserType.BANNED.getCode().equals(user.getUserType())
                        || UserType.BOT.getCode().equals(user.getUserType())) {
                    user.setUserType(UserType.NORMAL.getCode());
                }
                userMapper.updateById(user);
            }
            return;
        }

        if ("CREATOR".equals(appealType)) {
            int updated = creatorApplyMapper.update(null, new LambdaUpdateWrapper<CreatorApply>()
                    .eq(CreatorApply::getId, appeal.getRelatedId())
                    .eq(CreatorApply::getUserId, appeal.getUserId())
                    .eq(CreatorApply::getStatus, "rejected")
                    .set(CreatorApply::getStatus, "pending")
                    .set(CreatorApply::getReviewerId, null)
                    .set(CreatorApply::getReviewTime, null)
                    .set(CreatorApply::getReviewReason, null)
                    .set(CreatorApply::getUpdateTime, LocalDateTime.now()));
            if (updated != 1) {
                throw new BusinessException("关联的创作者登记状态已变化，请重新核对");
            }
            log.info("event=creator_appeal_reopened appealId={} applicationId={} userId={}",
                    appeal.getId(), appeal.getRelatedId(), appeal.getUserId());
            return;
        }

        if ("CONTENT".equals(appealType)) {
            log.info("event=content_appeal_approved appealId={} relatedId={} userId={}",
                    appeal.getId(), appeal.getRelatedId(), appeal.getUserId());
        }
    }

    private AppealVO convertToVO(Appeal appeal) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setUserId(appeal.getUserId());
        vo.setAppealType(appeal.getAppealType());
        vo.setAppealTypeName(getAppealTypeName(appeal.getAppealType()));
        vo.setAppealReason(appeal.getAppealReason());
        vo.setAppealStatus(appeal.getAppealStatus());
        vo.setAppealStatusName(getAppealStatusName(appeal.getAppealStatus()));
        vo.setRelatedId(appeal.getRelatedId());
        vo.setEvidenceUrls(appeal.getEvidenceUrls());
        vo.setEvidenceAssetIds(privateAttachmentService.listTargetAssetIds(
                PrivateAttachmentPurpose.APPEAL_EVIDENCE.getTargetType(), appeal.getId()));
        vo.setReviewerId(appeal.getReviewerId());
        vo.setReviewResult(appeal.getReviewResult());
        vo.setCreateTime(appeal.getCreateTime());
        vo.setReviewTime(appeal.getReviewTime());

        User user = userMapper.selectById(appeal.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }

        if (appeal.getReviewerId() != null) {
            User reviewer = userMapper.selectById(appeal.getReviewerId());
            if (reviewer != null) {
                vo.setReviewerName(reviewer.getUsername());
            }
        }

        return vo;
    }

    private String normalizeAppealType(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        return type.trim().toUpperCase();
    }

    private String normalizeAppealStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        String value = status.trim().toUpperCase();
        if ("0".equals(value)) {
            return STATUS_PENDING;
        }
        if ("1".equals(value)) {
            return STATUS_APPROVED;
        }
        if ("2".equals(value)) {
            return STATUS_REJECTED;
        }
        return value;
    }

    private String getAppealTypeName(String type) {
        if ("BAN".equals(type)) {
            return "账号限制复核";
        }
        if ("CREATOR".equals(type)) {
            return "创作者登记复核";
        }
        if ("CONTENT".equals(type)) {
            return "作品处理复核";
        }
        return "其他复核";
    }

    private String getAppealStatusName(String status) {
        if (STATUS_PENDING.equals(status)) {
            return "待审核";
        }
        if (STATUS_APPROVED.equals(status)) {
            return "已通过";
        }
        if (STATUS_REJECTED.equals(status)) {
            return "已拒绝";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "已取消";
        }
        return "未知";
    }

    private void recordAppealAction(Long appealId, Long operatorId, String operatorType, String action, String remark) {
        try {
            appealRecordService.recordAction(appealId, operatorId, operatorType, action, remark);
        } catch (Exception e) {
            log.warn("event=appeal_action_log_failed appealId={} action={} error={}",
                    appealId, action, e.getClass().getSimpleName());
        }
    }
}
