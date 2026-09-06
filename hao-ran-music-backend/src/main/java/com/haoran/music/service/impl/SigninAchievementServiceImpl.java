




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.SigninAchievement;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserSigninAchievement;
import com.haoran.music.mapper.SigninAchievementMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserSigninAchievementMapper;
import com.haoran.music.service.SigninAchievementService;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.UserBadgeService;
import com.haoran.music.service.UserDecorationService;
import com.haoran.music.service.UserVipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;




@Slf4j
@Service
public class SigninAchievementServiceImpl extends ServiceImpl<SigninAchievementMapper, SigninAchievement>
        implements SigninAchievementService {

    @Resource
    private UserSigninAchievementMapper userSigninAchievementMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserActivityPointsService activityPointsService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private UserDecorationService userDecorationService;

    @Resource
    private UserBadgeService userBadgeService;

    @Override
    public List<SigninAchievement> getAllEnabledAchievements() {
        return baseMapper.selectAllEnabled();
    }

    @Override
    public List<Map<String, Object>> getUserAchievementProgress(Long userId, Integer continuousDays) {
        List<SigninAchievement> allAchievements = getAllEnabledAchievements();
        List<UserSigninAchievement> userAchievements = userSigninAchievementMapper.selectByUserId(userId);
        User user = ObjectUtils.isEmpty(userId) ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);


        Map<Integer, UserSigninAchievement> userAchievementMap = new HashMap<>();
        for (UserSigninAchievement ua : userAchievements) {
            userAchievementMap.put(ua.getDays(), ua);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (SigninAchievement achievement : allAchievements) {
            Map<String, Object> item = new HashMap<>();
            item.put("achievement", achievement);
            boolean unlocked;
            boolean rewarded;

            UserSigninAchievement userAchievement = userAchievementMap.get(achievement.getDays());
            if (userAchievement != null) {
                unlocked = true;
                rewarded = userAchievement.getIsRewarded() == 1;
                item.put("unlocked", unlocked);
                item.put("rewarded", rewarded);
                item.put("rewardTime", userAchievement.getRewardTime());
            } else {
                unlocked = continuousDays >= achievement.getDays();
                rewarded = false;
                item.put("unlocked", unlocked);
                item.put("rewarded", rewarded);
            }
            item.put("canClaim", canInteract && unlocked && !rewarded);
            item.put("accountUnavailable", !canInteract);
            if (!canInteract) {
                item.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
            }


            if (continuousDays >= achievement.getDays()) {
                item.put("progress", 100);
            } else {
                item.put("progress", (continuousDays * 100.0 / achievement.getDays()));
            }

            result.add(item);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndUnlockAchievement(Long userId, Integer continuousDays) {
        UserAccountStatusUtil.requireCanInteract(
                ObjectUtils.isEmpty(userId) ? null : userMapper.selectByIdForUpdate(userId),
                "解锁签到成就");

        LambdaQueryWrapper<SigninAchievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SigninAchievement::getDays, continuousDays)
                .eq(SigninAchievement::getStatus, 1);

        SigninAchievement achievement = getOne(wrapper);
        if (achievement == null) {
            return false;
        }


        UserSigninAchievement userAchievement = userSigninAchievementMapper.selectByUserIdAndDays(userId, continuousDays);
        if (userAchievement != null) {
            return false;
        }


        userAchievement = new UserSigninAchievement();
        userAchievement.setUserId(userId);
        userAchievement.setAchievementId(achievement.getId());
        userAchievement.setDays(continuousDays);
        userAchievement.setIsRewarded(0);
        userSigninAchievementMapper.insert(userAchievement);

        log.info("用户签到成就解锁: userId={}, days={}, achievement={}", userId, continuousDays, achievement.getAchievementName());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claimAchievementReward(Long userId, Long achievementId, Integer continuousDays) {
        UserAccountStatusUtil.requireCanInteract(
                ObjectUtils.isEmpty(userId) ? null : userMapper.selectByIdForUpdate(userId),
                "领取签到成就奖励");
        Map<String, Object> result = new HashMap<>();

        SigninAchievement achievement = getById(achievementId);
        if (achievement == null) {
            result.put("success", false);
            result.put("message", "成就不存在");
            return result;
        }
        if (!Integer.valueOf(1).equals(achievement.getStatus())) {
            result.put("success", false);
            result.put("message", "该成就暂未开放");
            return result;
        }
        if (continuousDays == null || continuousDays < achievement.getDays()) {
            result.put("success", false);
            result.put("message", "连续签到天数尚未达到领取条件");
            return result;
        }

        UserSigninAchievement userAchievement =
                userSigninAchievementMapper.selectByUserIdAndAchievementId(userId, achievementId);
        if (userAchievement == null) {


            userAchievement = new UserSigninAchievement();
            userAchievement.setUserId(userId);
            userAchievement.setAchievementId(achievementId);
            userAchievement.setDays(achievement.getDays());
            userAchievement.setIsRewarded(0);
            if (userSigninAchievementMapper.insert(userAchievement) != 1) {
                throw new IllegalStateException("签到成就解锁记录创建失败");
            }
        }

        if (userAchievement.getIsRewarded() == 1) {
            result.put("success", false);
            result.put("message", "已领取过奖励");
            return result;
        }


        List<String> rewards = new ArrayList<>();


        if (achievement.getRewardPoints() != null && achievement.getRewardPoints() > 0) {
            activityPointsService.addPoints(userId, achievement.getRewardPoints(), "achievement",
                    "签到成就奖励：" + achievement.getAchievementName());
            rewards.add(achievement.getRewardPoints() + "活跃值");
        }


        if (achievement.getRewardVipDays() != null && achievement.getRewardVipDays() > 0) {
            userVipService.grantVip(userId, 1, achievement.getRewardVipDays(), "achievement");
            rewards.add(achievement.getRewardVipDays() + "天VIP");
        }


        if (achievement.getRewardBadgeId() != null && achievement.getRewardBadgeId() > 0) {
            String badgeType = "signin_" + achievement.getDays();
            String badgeName = achievement.getDays() + "天签到成就";
            userBadgeService.grantSystemBadge(userId, badgeType,
                    "signin_achievement", String.valueOf(achievementId),
                    "continuousDays=" + achievement.getDays());
            rewards.add(badgeName);
            log.info("发放签到成就徽章: userId={}, days={}", userId, achievement.getDays());
        }


        if (achievement.getRewardDecorationId() != null && achievement.getRewardDecorationId() > 0) {
            if (!userDecorationService.addDecoration(
                    userId, String.valueOf(achievement.getRewardDecorationId()), "achievement")) {
                throw new IllegalStateException("签到成就装饰发放失败");
            }
            rewards.add("成就装饰");
        }


        if (userSigninAchievementMapper.markRewarded(
                userAchievement.getId(), userId, achievementId) != 1) {
            throw new IllegalStateException("签到成就领取状态更新失败");
        }

        result.put("success", true);
        result.put("message", "领取成功");
        result.put("rewards", rewards);

        log.info("用户领取签到成就奖励: userId={}, achievement={}, rewards={}", userId, achievement.getAchievementName(), rewards);
        return result;
    }
}
