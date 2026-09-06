package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.FeedbackRewardService;
import com.haoran.music.service.CreditService;
import com.haoran.music.service.UserPointsService;
import com.haoran.music.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;





@Slf4j
@Service
public class FeedbackRewardServiceImpl implements FeedbackRewardService {

    @Resource
    private FeedbackRewardMapper feedbackRewardMapper;

    @Resource
    private UserFeedbackMapper userFeedbackMapper;

    @Resource
    private UserCreditMapper userCreditMapper;

    @Resource
    private UserPointsRecordMapper userPointsRecordMapper;

    @Resource
    private UserService userService;

    @Resource
    private UserPointsService userPointsService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CreditService creditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReward(Long feedbackId, String rewardLevel, String rewardDescription) {
        if (feedbackId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈ID不能为空");
        }

        UserFeedback feedback = userFeedbackMapper.selectByIdForUpdate(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈不存在");
        }
        if (feedback.getUserId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈用户不存在");
        }

        FeedbackReward existingReward = feedbackRewardMapper.selectOne(
                new LambdaQueryWrapper<FeedbackReward>()
                        .eq(FeedbackReward::getFeedbackId, feedbackId)
                        .last("LIMIT 1"));
        if (existingReward != null) {
            return existingReward.getId();
        }

        FeedbackReward reward = new FeedbackReward();
        reward.setFeedbackId(feedbackId);
        reward.setUserId(feedback.getUserId());
        reward.setRewardLevel(rewardLevel);
        reward.setRewardDescription(rewardDescription);
        reward.setStatus("pending");
        reward.setIsGranted(0);
        reward.setCreateTime(LocalDateTime.now());

        if ("normal".equals(rewardLevel)) {
            reward.setPoints(10);
            reward.setCreditScore(1);
        } else if ("important".equals(rewardLevel)) {
            reward.setPoints(30);
            reward.setCreditScore(3);
            reward.setVipDays(3);
        } else if ("critical".equals(rewardLevel)) {
            reward.setPoints(100);
            reward.setCreditScore(10);
            reward.setVipDays(7);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励等级不正确");
        }

        feedbackRewardMapper.insert(reward);
        return reward.getId();
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean grantReward(Long rewardId, Long grantorId) {
        if (rewardId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励ID不能为空");
        }

        FeedbackReward reward = feedbackRewardMapper.selectById(rewardId);
        if (reward == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励不存在");
        }
        if ("granted".equals(reward.getStatus())) {
            return true;
        }
        if (!"pending".equals(reward.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励已发放或取消");
        }
        if (reward.getUserId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(
                userMapper.selectById(reward.getUserId()), "领取反馈奖励");
        if (feedbackRewardMapper.transitionStatus(rewardId, "pending", "processing") != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励已被其他操作处理");
        }

        if (reward.getPoints() != null && reward.getPoints() > 0) {
            grantPointsDirectly(reward.getUserId(), reward.getPoints(), "反馈奖励", rewardId);
        }
        if (reward.getCreditScore() != null && reward.getCreditScore() > 0) {
            grantCreditDirectly(reward.getUserId(), reward.getCreditScore(), "反馈奖励", rewardId);
        }
        if (reward.getVipDays() != null && reward.getVipDays() > 0) {
            userService.addVipDays(reward.getUserId(), reward.getVipDays(), "反馈奖励");
        }

        if (feedbackRewardMapper.completeGrant(rewardId, grantorId, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("反馈奖励完成状态更新失败");
        }
        return true;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelReward(Long rewardId, String cancelReason) {
        if (rewardId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励ID不能为空");
        }

        FeedbackReward reward = feedbackRewardMapper.selectById(rewardId);
        if (reward == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励不存在");
        }
        if (!"pending".equals(reward.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只有待发放奖励可以取消");
        }

        if (feedbackRewardMapper.cancelPending(rewardId, cancelReason, LocalDateTime.now()) != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励已被其他操作处理");
        }
        return true;
    }
    @Override
    public Map<String, Object> getMyRewards(Long userId, Integer page, Integer size) {
        Page<FeedbackReward> pageInfo = new Page<>(page, size);
        IPage<FeedbackReward> result = feedbackRewardMapper.selectPage(pageInfo,
                new LambdaQueryWrapper<FeedbackReward>()
                        .eq(FeedbackReward::getUserId, userId)
                        .orderByDesc(FeedbackReward::getCreateTime));

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("list", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("current", result.getCurrent());
        response.put("size", size);
        response.put("pages", result.getPages());
        return response;
    }

    @Override
    public Map<String, Object> getPendingRewards(Integer page, Integer size) {
        Page<FeedbackReward> pageInfo = new Page<>(page, size);
        IPage<FeedbackReward> result = feedbackRewardMapper.selectPage(pageInfo,
                new LambdaQueryWrapper<FeedbackReward>()
                        .eq(FeedbackReward::getStatus, "pending")
                        .orderByDesc(FeedbackReward::getCreateTime));

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("list", result.getRecords());
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("current", result.getCurrent());
        response.put("size", size);
        response.put("pages", result.getPages());
        return response;
    }

    @Override
    public Map<String, Object> getRewardLevels() {
        Map<String, Object> levels = new HashMap<>();

        Map<String, Object> normal = new HashMap<>();
        normal.put("level", "normal");
        normal.put("name", "普通奖励");
        normal.put("points", 10);
        normal.put("creditScore", 1);
        normal.put("vipDays", 0);

        Map<String, Object> important = new HashMap<>();
        important.put("level", "important");
        important.put("name", "重要奖励");
        important.put("points", 30);
        important.put("creditScore", 3);
        important.put("vipDays", 3);

        Map<String, Object> critical = new HashMap<>();
        critical.put("level", "critical");
        critical.put("name", "重大奖励");
        critical.put("points", 100);
        critical.put("creditScore", 10);
        critical.put("vipDays", 7);

        List<Map<String, Object>> levelList = Arrays.asList(normal, important, critical);
        levels.put("levels", levelList);

        return levels;
    }

    @Override
    public Map<String, Object> getRewardDetail(Long rewardId) {
        if (rewardId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励ID不能为空");
        }

        FeedbackReward reward = feedbackRewardMapper.selectById(rewardId);
        if (reward == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励不存在");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", reward.getId());
        detail.put("feedbackId", reward.getFeedbackId());
        detail.put("userId", reward.getUserId());
        detail.put("rewardLevel", reward.getRewardLevel());
        detail.put("rewardDescription", reward.getRewardDescription());
        detail.put("points", reward.getPoints());
        detail.put("creditScore", reward.getCreditScore());
        detail.put("vipDays", reward.getVipDays());
        detail.put("status", reward.getStatus());
        detail.put("createTime", reward.getCreateTime());
        detail.put("grantedTime", reward.getGrantedTime());

        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchGrantRewards(Long[] rewardIds, Long grantorId) {
        if (rewardIds == null || rewardIds.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "奖励ID不能为空");
        }
        if (rewardIds.length > 50) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多发放50条奖励");
        }
        int count = 0;
        for (Long rewardId : rewardIds) {
            try {
                grantReward(rewardId, grantorId);
                count++;
            } catch (Exception e) {
                log.error("批量发放失败: rewardId={}", rewardId);
            }
        }
        return count;
    }

    @Override
    public Map<String, Object> getRewardStatistics(Long userId) {
        Map<String, Object> aggregate = feedbackRewardMapper.selectRewardStatistics(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRewards", intValue(aggregate, "totalRewards"));
        stats.put("pendingRewards", intValue(aggregate, "pendingRewards"));
        stats.put("grantedRewards", intValue(aggregate, "grantedRewards"));
        stats.put("totalPoints", intValue(aggregate, "totalPoints"));
        stats.put("totalVipDays", intValue(aggregate, "totalVipDays"));

        return stats;
    }

    private int intValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0;
        }
        Object value = row.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse feedback reward statistic {}={}", key, value);
            return 0;
        }
    }








    private void grantPointsDirectly(Long userId, Integer points, String reason, Long rewardId) {
        try {
            userPointsService.addPoints(userId, "admin", points, reason, rewardId, "feedback_reward");
            log.info("发放积分成功: userId={}, points={}, reason={}, rewardId={}", userId, points, reason, rewardId);
        } catch (Exception e) {
            log.error("发放积分失败: userId={}, reason={}, rewardId={}", userId, reason, rewardId);
            throw e;
        }
    }

    private void grantCreditDirectly(Long userId, Integer creditScore, String reason, Long rewardId) {
        try {
            creditService.addCreditRecord(userId, "feedback_reward", creditScore, reason, null);
            log.info("发放信用分成功: userId={}, score={}, reason={}, rewardId={}", userId, creditScore, reason, rewardId);
        } catch (Exception e) {
            log.error("发放信用分失败: userId={}, reason={}, rewardId={}", userId, reason, rewardId);
            throw e;
        }
    }}
