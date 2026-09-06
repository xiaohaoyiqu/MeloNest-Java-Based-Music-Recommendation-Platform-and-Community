


package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserStatistics;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.CreditService;
import com.haoran.music.service.OnlineStatusService;
import com.haoran.music.service.SimpleUserClassificationService;
import com.haoran.music.service.UserActivityQuickService;
import com.haoran.music.service.UserMonitoringService;
import com.haoran.music.service.UserPortraitService;
import com.haoran.music.service.UserProfileService;
import com.haoran.music.service.UserStatisticsService;
import com.haoran.music.vo.user.UserProfileVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;




@Service
public class UserMonitoringServiceImpl implements UserMonitoringService {

    private final UserMapper userMapper;
    private final UserActivityQuickService userActivityQuickService;
    private final UserProfileService userProfileService;
    private final UserPortraitService userPortraitService;
    private final UserStatisticsService userStatisticsService;
    private final OnlineStatusService onlineStatusService;
    private final CreditService creditService;
    private final SimpleUserClassificationService userClassificationService;

    public UserMonitoringServiceImpl(UserMapper userMapper,
                                     UserActivityQuickService userActivityQuickService,
                                     UserProfileService userProfileService,
                                     UserPortraitService userPortraitService,
                                     UserStatisticsService userStatisticsService,
                                     OnlineStatusService onlineStatusService,
                                     CreditService creditService,
                                     SimpleUserClassificationService userClassificationService) {
        this.userMapper = userMapper;
        this.userActivityQuickService = userActivityQuickService;
        this.userProfileService = userProfileService;
        this.userPortraitService = userPortraitService;
        this.userStatisticsService = userStatisticsService;
        this.onlineStatusService = onlineStatusService;
        this.creditService = creditService;
        this.userClassificationService = userClassificationService;
    }

    @Override
    public Map<String, Object> getUserMonitoringOverview(Long userId, Integer days) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int safeDays = safeDays(days);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeDays - 1L);
        List<UserStatistics> stats = userStatisticsService.getStatsByDateRange(userId, startDate, endDate);

        Map<String, Object> result = new HashMap<>();
        result.put("user", buildUserBasic(user));
        result.put("window", buildWindow(startDate, endDate, safeDays));
        result.put("activity", userActivityQuickService.getActivityDetail(userId));
        result.put("credit", buildCreditOverview(userId, user));
        result.put("classification", buildClassificationOverview(userId, user));
        result.put("risk", buildRiskOverview(user, stats));
        result.put("online", onlineStatusService.getUserOnlineStatus(userId));
        result.put("statistics", buildStatisticsOverview(stats));
        result.put("portrait", buildPortraitOverview(userId));
        result.put("analysisTime", LocalDateTime.now());
        return result;
    }

    @Override
    public Map<String, Object> getSystemMonitoringOverview(Integer days, Integer limit) {
        int safeDays = safeDays(days);
        int safeLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 100);
        LocalDate statDate = LocalDate.now().minusDays(1);
        List<UserStatistics> yesterdayStats = userStatisticsService.getAllStatsByDate(statDate);

        Map<String, Object> cards = new HashMap<>();
        long totalUsers = countUsers(null);
        long publicUsers = userMapper.selectCount(UserAccountStatusUtil.publicStatsUserQuery());
        cards.put("totalUsers", totalUsers);
        cards.put("publicUsers", publicUsers);
        cards.put("restrictedUsers", Math.max(0L, totalUsers - publicUsers));
        cards.put("onlineUsers", countUsers(wrapper -> wrapper.eq(User::getIsOnline, 1)));
        cards.put("highRiskUsers", countUsers(wrapper -> wrapper.ge(User::getRiskScore, UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD)));
        cards.put("lowCreditUsers", countUsers(wrapper -> wrapper.lt(User::getCreditScore, UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE)));
        cards.put("botUsers", countUsers(wrapper -> wrapper.eq(User::getUserType, UserType.BOT.getCode())));
        cards.put("suspiciousUsers", countUsers(wrapper -> wrapper.eq(User::getUserType, UserType.SUSPICIOUS.getCode())));

        Map<String, Object> yesterday = new HashMap<>();
        yesterday.put("date", statDate);
        yesterday.put("statUsers", yesterdayStats.size());
        yesterday.put("abnormalUsers", yesterdayStats.stream().filter(stat -> Integer.valueOf(1).equals(stat.getIsAbnormal())).count());
        yesterday.put("highRiskUsers", yesterdayStats.stream()
                .filter(stat -> value(stat.getRiskScore()) >= UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD).count());
        yesterday.put("totalPlays", sum(yesterdayStats, UserStatistics::getPlayCount));
        yesterday.put("totalPlayDuration", sum(yesterdayStats, UserStatistics::getPlayDuration));

        List<User> sampleUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .orderByDesc(User::getRiskScore)
                .orderByAsc(User::getCreditScore)
                .orderByDesc(User::getLastActiveTime)
                .last("LIMIT " + safeLimit));
        List<Long> sampleIds = sampleUsers.stream().map(User::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, Integer> activityScores = userActivityQuickService.batchGetActivityScore(sampleIds);

        List<Map<String, Object>> samples = new ArrayList<>();
        for (User user : sampleUsers) {
            samples.add(buildUserSample(user, activityScores.get(user.getId())));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("window", buildWindow(LocalDate.now().minusDays(safeDays - 1L), LocalDate.now(), safeDays));
        result.put("cards", cards);
        result.put("yesterday", yesterday);
        result.put("users", samples);
        result.put("analysisTime", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> buildUserBasic(User user) {
        Map<String, Object> basic = new HashMap<>();
        basic.put("userId", user.getId());
        basic.put("username", user.getUsername());
        basic.put("nickname", user.getNickname());
        basic.put("avatar", user.getAvatar());
        basic.put("role", user.getRole());
        basic.put("status", user.getStatus());
        basic.put("userType", user.getUserType());
        basic.put("riskScore", user.getRiskScore());
        basic.put("creditScore", user.getCreditScore());
        basic.put("lastLoginTime", user.getLastLoginTime());
        basic.put("lastLoginIp", user.getLastLoginIp());
        basic.put("lastActiveTime", user.getLastActiveTime());
        basic.put("lastOnlineTime", user.getLastOnlineTime());
        basic.put("isCreator", user.getIsCreator());
        basic.put("isModerator", user.getIsModerator());
        basic.put("isOfficial", user.getIsOfficial());
        basic.put("createTime", user.getCreateTime());
        return basic;
    }

    private Map<String, Object> buildCreditOverview(Long userId, User user) {
        Integer creditScore = creditService.getUserCredit(userId);
        Map<String, Object> credit = new HashMap<>();
        credit.put("score", creditScore);
        credit.put("userTableScore", user.getCreditScore());
        credit.put("level", resolveCreditLevel(creditScore));
        credit.put("lowCredit", creditScore != null
                && creditScore < UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE);
        return credit;
    }

    private Map<String, Object> buildClassificationOverview(Long userId, User user) {
        UserType userType = userClassificationService.getUserType(userId);
        Map<String, Object> classification = new HashMap<>();
        classification.put("userType", userType.getCode());
        classification.put("userTypeName", userType.name());
        classification.put("description", userType.getDescription());
        classification.put("restricted", userType.shouldRestrict());
        classification.put("interactionRestricted", !UserAccountStatusUtil.canInteract(user));
        classification.put("publicFlowRestricted", !UserAccountStatusUtil.canContributePublicStats(user));
        classification.put("bot", userClassificationService.isBotUser(userId));
        classification.put("userTypeUpdateTime", user.getUserTypeUpdateTime());
        return classification;
    }

    private Map<String, Object> buildRiskOverview(User user, List<UserStatistics> stats) {
        int maxDailyRisk = stats.stream().map(UserStatistics::getRiskScore).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
        int riskScore = Math.max(value(user.getRiskScore()), maxDailyRisk);
        long abnormalDays = stats.stream().filter(stat -> Integer.valueOf(1).equals(stat.getIsAbnormal())).count();
        Map<String, Object> risk = new HashMap<>();
        risk.put("score", riskScore);
        risk.put("level", resolveRiskLevel(riskScore));
        risk.put("maxDailyRiskScore", maxDailyRisk);
        risk.put("abnormalDays", abnormalDays);
        risk.put("latestAbnormalReason", stats.stream()
                .filter(stat -> stat.getAbnormalReason() != null && !stat.getAbnormalReason().isEmpty())
                .findFirst()
                .map(UserStatistics::getAbnormalReason)
                .orElse(null));
        return risk;
    }

    private Map<String, Object> buildStatisticsOverview(List<UserStatistics> stats) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("activeDays", stats.stream()
                .filter(stat -> value(stat.getPlayCount()) > 0 || value(stat.getLoginCount()) > 0)
                .map(UserStatistics::getStatDate)
                .filter(Objects::nonNull)
                .distinct()
                .count());
        summary.put("playCount", sum(stats, UserStatistics::getPlayCount));
        summary.put("playDuration", sum(stats, UserStatistics::getPlayDuration));
        summary.put("uniqueSongCount", sum(stats, UserStatistics::getUniqueSongCount));
        summary.put("completePlayCount", sum(stats, UserStatistics::getCompletePlayCount));
        summary.put("loginCount", sum(stats, UserStatistics::getLoginCount));
        summary.put("searchCount", sum(stats, UserStatistics::getSearchCount));
        summary.put("likeCount", sum(stats, UserStatistics::getLikeCount));
        summary.put("favoriteCount", sum(stats, UserStatistics::getFavoriteCount));
        summary.put("commentCount", sum(stats, UserStatistics::getCommentCount));
        summary.put("shareCount", sum(stats, UserStatistics::getShareCount));
        summary.put("daily", stats.stream().limit(14).map(this::buildDailyStat).collect(Collectors.toList()));
        return summary;
    }

    private Map<String, Object> buildDailyStat(UserStatistics stat) {
        Map<String, Object> day = new HashMap<>();
        day.put("date", stat.getStatDate());
        day.put("playCount", value(stat.getPlayCount()));
        day.put("playDuration", value(stat.getPlayDuration()));
        day.put("loginCount", value(stat.getLoginCount()));
        day.put("searchCount", value(stat.getSearchCount()));
        day.put("riskScore", value(stat.getRiskScore()));
        day.put("isAbnormal", Integer.valueOf(1).equals(stat.getIsAbnormal()));
        return day;
    }

    private Map<String, Object> buildPortraitOverview(Long userId) {
        Map<String, Object> portrait = new HashMap<>();
        UserProfileVO profile = userProfileService.getUserProfile(userId);
        portrait.put("profile", profile);
        portrait.put("tags", userProfileService.getUserPreferenceTags(userId));
        portrait.put("behaviorSummary", userPortraitService.getUserBehaviorSummary(userId));
        portrait.put("interestTags", userPortraitService.getUserInterestTags(userId));
        return portrait;
    }

    private Map<String, Object> buildUserSample(User user, Integer activityScore) {
        UserType userType = UserType.fromCode(user.getUserType());
        Map<String, Object> sample = new HashMap<>();
        sample.put("userId", user.getId());
        sample.put("username", user.getUsername());
        sample.put("nickname", user.getNickname());
        sample.put("status", user.getStatus());
        sample.put("userType", userType.getCode());
        sample.put("userTypeName", userType.name());
        sample.put("restricted", !UserAccountStatusUtil.canContributePublicStats(user));
        sample.put("interactionRestricted", !UserAccountStatusUtil.canInteract(user));
        sample.put("activityScore", activityScore == null ? 0 : activityScore);
        sample.put("riskScore", value(user.getRiskScore()));
        sample.put("creditScore", user.getCreditScore());
        sample.put("online", Integer.valueOf(1).equals(user.getIsOnline()));
        sample.put("lastActiveTime", user.getLastActiveTime());
        return sample;
    }

    private Map<String, Object> buildWindow(LocalDate startDate, LocalDate endDate, int days) {
        Map<String, Object> window = new HashMap<>();
        window.put("days", days);
        window.put("startDate", startDate);
        window.put("endDate", endDate);
        return window;
    }

    private long countUsers(Function<LambdaQueryWrapper<User>, LambdaQueryWrapper<User>> customizer) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getDeleted, 0);
        if (customizer != null) {
            wrapper = customizer.apply(wrapper);
        }
        return userMapper.selectCount(wrapper);
    }

    private int safeDays(Integer days) {
        return days == null ? 30 : Math.min(Math.max(days, 1), 90);
    }

    private int sum(List<UserStatistics> stats, Function<UserStatistics, Integer> getter) {
        return stats.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveRiskLevel(int riskScore) {
        if (riskScore >= UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD) {
            return "high";
        }
        if (riskScore >= UserAccountPolicyConstants.MEDIUM_RISK_SCORE_THRESHOLD) {
            return "medium";
        }
        if (riskScore > 0) {
            return "low";
        }
        return "normal";
    }

    private String resolveCreditLevel(Integer creditScore) {
        int score = value(creditScore);
        if (score >= UserAccountPolicyConstants.CREDIT_SCORE_EXCELLENT_MIN) {
            return "excellent";
        }
        if (score >= UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN) {
            return "good";
        }
        if (score >= UserAccountPolicyConstants.CREDIT_SCORE_NORMAL_MIN) {
            return "normal";
        }
        if (score >= UserAccountPolicyConstants.CREDIT_SCORE_OBSERVE_MIN) {
            return "observe";
        }
        return "restricted";
    }
}
