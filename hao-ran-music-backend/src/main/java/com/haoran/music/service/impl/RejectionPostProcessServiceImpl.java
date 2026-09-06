package com.haoran.music.service.impl;

import com.haoran.music.entity.Moderation;
import com.haoran.music.mapper.ModerationMapper;
import com.haoran.music.service.RejectionPostProcessService;
import com.haoran.music.service.UserViolationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;





@Slf4j
@Service
public class RejectionPostProcessServiceImpl implements RejectionPostProcessService {

    @Autowired
    private ModerationMapper moderationMapper;

    @Autowired
    private UserViolationService violationService;




    @Getter
    private enum RejectionCategory {
        COPYRIGHT_VIOLATION("版权问题", false, true, 3),
        CONTENT_VIOLATION("内容违规", false, true, 3),
        QUALITY_ISSUE("质量问题", true, false, 1),
        INCOMPLETE_INFO("信息不全", true, false, 1),
        OTHER("其他", true, false, 1);

        private final String name;
        private final boolean modifiable;
        private final boolean violation;
        private final int level;

        RejectionCategory(String name, boolean modifiable, boolean violation, int level) {
            this.name = name;
            this.modifiable = modifiable;
            this.violation = violation;
            this.level = level;
        }

    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRejection(Long moderationId, String rejectReason) {
        log.info("开始处理审核拒绝: moderationId={}, reason={}", moderationId, rejectReason);

        Moderation moderation = moderationMapper.selectById(moderationId);
        if (moderation == null) {
            log.error("审核记录不存在: moderationId={}", moderationId);
            throw new RuntimeException("审核记录不存在");
        }


        moderation.setReviewReason(rejectReason);
        moderation.setReviewTime(LocalDateTime.now());


        String reapplyTimeStr = calculateReapplyTime(rejectReason);
        LocalDateTime reapplyTime = parseReapplyTime(reapplyTimeStr);
        moderation.setReapplyAvailableTime(reapplyTime);


        String categoryCode = categorizeRejection(rejectReason);
        RejectionCategory category = getRejectionCategory(categoryCode);


        moderation.setCanModify(category.isModifiable() ? 1 : 0);


        if (category.isViolation()) {
            violationService.recordViolation(
                moderation.getSubmitterId(),
                moderation.getContentType(),
                moderation.getContentId(),
                category.getName(),
                category.getLevel(),
                "审核拒绝: " + rejectReason
            );
        }


        String suggestions = generateSuggestions(rejectReason);
        log.info("审核拒绝改进建议: moderationId={}, suggestions={}", moderationId, suggestions);



        moderationMapper.updateById(moderation);

        log.info("审核拒绝处理完成: moderationId={}, canModify={}, reapplyTime={}",
                moderationId, category.isModifiable(), reapplyTimeStr);
    }




    @Override
    public String calculateReapplyTime(String rejectReason) {
        if (rejectReason == null) {
            rejectReason = "";
        }

        String lowerReason = rejectReason.toLowerCase();

        if (lowerReason.contains("版权") || lowerReason.contains("侵权")) {
            return "30天后";
        }
        if (lowerReason.contains("违规") || lowerReason.contains("敏感") || lowerReason.contains("违法")) {
            return "7天后";
        }
        if (lowerReason.contains("垃圾") || lowerReason.contains("广告")) {
            return "7天后";
        }

        return "24小时后";
    }




    private LocalDateTime parseReapplyTime(String timeStr) {
        LocalDateTime now = LocalDateTime.now();

        if (timeStr.contains("30天")) {
            return now.plusDays(30);
        } else if (timeStr.contains("7天")) {
            return now.plusDays(7);
        } else if (timeStr.contains("24小时")) {
            return now.plusHours(24);
        } else if (timeStr.contains("3天")) {
            return now.plusDays(3);
        } else if (timeStr.contains("48小时")) {
            return now.plusHours(48);
        }

        return now.plusHours(24);
    }




    @Override
    public String categorizeRejection(String rejectReason) {
        if (!StringUtils.hasText(rejectReason)) {
            return "OTHER";
        }

        String lowerReason = rejectReason.toLowerCase();

        if (lowerReason.contains("版权") || lowerReason.contains("侵权")) {
            return "COPYRIGHT_VIOLATION";
        }
        if (lowerReason.contains("违规") || lowerReason.contains("敏感")
            || lowerReason.contains("违法") || lowerReason.contains("色情")
            || lowerReason.contains("暴力") || lowerReason.contains("恐怖")) {
            return "CONTENT_VIOLATION";
        }
        if (lowerReason.contains("音质") || lowerReason.contains("质量")
            || lowerReason.contains("音频") || lowerReason.contains("杂音")) {
            return "QUALITY_ISSUE";
        }
        if (lowerReason.contains("信息") || lowerReason.contains("描述")
            || lowerReason.contains("不全") || lowerReason.contains("不完整")) {
            return "INCOMPLETE_INFO";
        }

        return "OTHER";
    }




    private RejectionCategory getRejectionCategory(String categoryCode) {
        try {
            return RejectionCategory.valueOf(categoryCode);
        } catch (IllegalArgumentException e) {
            return RejectionCategory.OTHER;
        }
    }




    @Override
    public String generateSuggestions(String rejectReason) {
        if (!StringUtils.hasText(rejectReason)) {
            return "请检查提交内容是否符合平台规范。";
        }

        String lowerReason = rejectReason.toLowerCase();
        List<String> suggestions = new ArrayList<>();

        if (lowerReason.contains("版权") || lowerReason.contains("侵权")) {
            suggestions.add("请确保您拥有内容的完整版权或已获得合法授权");
            suggestions.add("如使用他人作品，请提供版权证明文件");
        }

        if (lowerReason.contains("违规") || lowerReason.contains("敏感")) {
            suggestions.add("请仔细阅读平台内容规范，确保内容符合法律法规");
            suggestions.add("避免使用敏感词汇和不当表述");
        }

        if (lowerReason.contains("音质") || lowerReason.contains("质量")) {
            suggestions.add("建议使用专业录音设备，确保音频质量清晰");
            suggestions.add("音频比特率建议不低于320kbps");
        }

        if (lowerReason.contains("信息") || lowerReason.contains("描述")) {
            suggestions.add("请完善作品的标题、描述等元信息");
            suggestions.add("确保信息准确、详实，便于审核和用户了解");
        }

        if (lowerReason.contains("垃圾") || lowerReason.contains("广告")) {
            suggestions.add("请勿发布垃圾内容或纯广告信息");
            suggestions.add("确保内容具有真实价值和意义");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("请仔细阅读拒绝原因，进行相应修改后重新提交");
            suggestions.add("如有疑问，请联系客服或申请申诉");
        }

        return String.join("；", suggestions);
    }
}
