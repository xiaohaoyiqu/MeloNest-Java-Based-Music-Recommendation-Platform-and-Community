package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.Moderation;
import com.haoran.music.mapper.ModerationMapper;
import com.haoran.music.service.ModerationService;
import com.haoran.music.service.RejectionPostProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;





@Slf4j
@Service
public class ModerationServiceImpl extends ServiceImpl<ModerationMapper, Moderation> implements ModerationService {

    @Autowired
    private RejectionPostProcessService rejectionPostProcessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForModeration(String contentType, Long contentId, String title,
                                   String description, Long submitterId, String submitterName) {
        LambdaQueryWrapper<Moderation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Moderation::getContentType, contentType)
                .eq(Moderation::getContentId, contentId)
                .eq(Moderation::getStatus, 0)
                .eq(Moderation::getDeleted, 0);

        Moderation existing = getOne(wrapper);
        if (existing != null) {
            return existing.getId();
        }

        Moderation moderation = new Moderation();
        moderation.setContentType(contentType);
        moderation.setContentId(contentId);
        moderation.setTitle(title);
        moderation.setDescription(description);
        moderation.setSubmitterId(submitterId);
        moderation.setSubmitterName(submitterName);
        moderation.setStatus(0);

        save(moderation);
        log.info("提交审核: contentType={}, contentId={}, submitterId={}", contentType, contentId, submitterId);
        return moderation.getId();
    }

    @Override
    public IPage<Moderation> getModerationList(PageQuery pageQuery, Integer status, String contentType) {
        Page<Moderation> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());

        LambdaQueryWrapper<Moderation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Moderation::getDeleted, 0);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(Moderation::getStatus, status);
        }
        if (ObjectUtils.isNotEmpty(contentType)) {
            wrapper.eq(Moderation::getContentType, contentType);
        }

        wrapper.orderByDesc(Moderation::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long moderationId, Long reviewerId, String reviewerName) {
        Moderation moderation = getById(moderationId);
        if (ObjectUtils.isEmpty(moderation)) {
            throw new RuntimeException("审核记录不存在");
        }

        moderation.setStatus(1);
        moderation.setReviewerId(reviewerId);
        moderation.setReviewerName(reviewerName);
        moderation.setReviewTime(LocalDateTime.now());
        updateById(moderation);

        log.info("审核通过: moderationId={}, contentId={}, reviewerId={}",
                moderationId, moderation.getContentId(), reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long moderationId, Long reviewerId, String reviewerName, String reason) {
        Moderation moderation = getById(moderationId);
        if (ObjectUtils.isEmpty(moderation)) {
            throw new RuntimeException("审核记录不存在");
        }

        moderation.setStatus(2);
        moderation.setReviewerId(reviewerId);
        moderation.setReviewerName(reviewerName);
        moderation.setReviewTime(LocalDateTime.now());
        moderation.setReviewReason(reason);
        updateById(moderation);

        log.info("审核拒绝: moderationId={}, contentId={}, reason={}",
                moderationId, moderation.getContentId(), reason);


        try {
            rejectionPostProcessService.processRejection(moderationId, reason);
        } catch (Exception e) {
            log.error("拒绝后处理失败: moderationId={}, error={}", moderationId, e.getClass().getSimpleName());
        }
    }

    @Override
    public Long getPendingCount() {
        LambdaQueryWrapper<Moderation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Moderation::getStatus, 0)
                .eq(Moderation::getDeleted, 0);
        return count(wrapper);
    }
}
