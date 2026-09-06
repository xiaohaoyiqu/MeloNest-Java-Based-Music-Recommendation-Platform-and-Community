package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ChurnPredictionService;
import com.haoran.music.service.RFMAnalysisService;
import com.haoran.music.vo.user.ChurnPredictionVO;
import com.haoran.music.vo.user.RFMVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                      
                           
   
@Slf4j
@Service
public class ChurnPredictionServiceImpl implements ChurnPredictionService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RFMAnalysisService rfmAnalysisService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

       
                   
       
    private static final Integer CHURN_CACHE_HOURS = 2;

       
               
       
    private static final Integer LOW_RISK_DAYS = 7;
    private static final Integer MEDIUM_RISK_DAYS = 14;
    private static final Integer HIGH_RISK_DAYS = 30;
    private static final Integer CHURNED_DAYS = 60;
    private static final int CHURN_STATS_BATCH_SIZE = 500;

    @Override
    public ChurnPredictionVO predictUserChurn(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }

                  
        String cacheKey = "churn:prediction:" + userId;
        ChurnPredictionVO cached = (ChurnPredictionVO) redisTemplate.opsForValue().get(cacheKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

                 
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return null;
        }

        ChurnPredictionVO prediction = new ChurnPredictionVO();
        prediction.setUserId(userId);
        prediction.setUsername(user.getUsername());
        prediction.setPredictTime(LocalDateTime.now());

                                                  
        RFMVO rfm = rfmAnalysisService.calculateUserRFM(userId);
        LocalDateTime lastActiveTime = rfm == null ? null : rfm.getLastActiveTime();
        prediction.setLastActiveTime(lastActiveTime);

                  
        int inactiveDays = calculateInactiveDays(lastActiveTime);
        prediction.setInactiveDays(inactiveDays);

                 
        ChurnPredictionVO.RiskLevel riskLevel = determineRiskLevel(inactiveDays);
        prediction.setRiskLevel(riskLevel.getCode());
        prediction.setRiskDescription(riskLevel.getDescription());

                 
        int churnProbability = calculateChurnProbability(rfm, riskLevel);
        prediction.setChurnProbability(churnProbability);

                   
        ChurnPredictionVO.UserValueLevel valueLevel = determineUserValueLevel(rfm);
        prediction.setValueLevel(valueLevel.getName());

                 
        List<ChurnPredictionVO.RiskFactor> riskFactors = analyzeRiskFactors(userId, inactiveDays);
        prediction.setRiskFactors(riskFactors);

                 
        List<String> recallActions = generateRecallActionsByRisk(riskLevel, valueLevel);
        prediction.setRecallActions(recallActions);

                 
        prediction.setSuggestedBudget(determineSuggestedBudget(valueLevel, riskLevel));

                 
        if (inactiveDays >= HIGH_RISK_DAYS) {
            prediction.setPredictedChurnTime(LocalDateTime.now().plusDays(30));
        } else {
            prediction.setPredictedChurnTime(LocalDateTime.now().plusDays(60));
        }

               
        redisTemplate.opsForValue().set(cacheKey, prediction, CHURN_CACHE_HOURS, TimeUnit.HOURS);

        log.info("用户流失预测完成: userId={}, riskLevel={}, probability={}", userId, riskLevel.getCode(), churnProbability);

        return prediction;
    }

    @Override
    public List<ChurnPredictionVO> batchPredictUserChurn(List<Long> userIds) {
        if (ObjectUtils.isEmpty(userIds)) {
            return new ArrayList<>();
        }

        return userIds.stream()
                .map(this::predictUserChurn)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChurnPredictionVO> getHighRiskUsers(String riskLevel, Integer limit) {
        List<ChurnPredictionVO> highRiskUsers = new ArrayList<>();

        int maxUsers = ObjectUtils.isNotEmpty(limit) ? limit : 50;
        int candidateLimit = Math.min(Math.max(maxUsers * 10, 100), 1000);
        List<User> candidateUsers = userMapper.selectList(buildRecallCandidateQuery(candidateLimit));

        for (User user : candidateUsers) {
            ChurnPredictionVO prediction = predictUserChurn(user.getId());
            if (ObjectUtils.isNotEmpty(prediction)) {
                                        
                if (ObjectUtils.isEmpty(riskLevel) || prediction.getRiskLevel().equals(riskLevel)) {
                                   
                    if (!ChurnPredictionVO.RiskLevel.NO_RISK.getCode().equals(prediction.getRiskLevel())) {
                        highRiskUsers.add(prediction);
                        if (highRiskUsers.size() >= maxUsers) {
                            break;
                        }
                    }
                }
            }
        }

                  
        highRiskUsers.sort((a, b) -> b.getChurnProbability().compareTo(a.getChurnProbability()));

        return highRiskUsers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getChurnStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

                  
        String cacheKey = "churn:stats:overall";
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        if (ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

                      
        Map<String, Integer> riskLevelStats = new LinkedHashMap<>();
        for (ChurnPredictionVO.RiskLevel level : ChurnPredictionVO.RiskLevel.values()) {
            riskLevelStats.put(level.getCode(), 0);
        }

                   
        int totalProbability = 0;
        int userCount = 0;
        int publicUsers = 0;
        int restrictedUsers = 0;

        Long lastUserId = 0L;
        while (true) {
            List<Long> userIds = userMapper.selectActiveUserIdsAfter(lastUserId, CHURN_STATS_BATCH_SIZE);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            for (RFMVO rfm : rfmAnalysisService.batchCalculateUserRFM(userIds)) {
                if (rfm == null) {
                    continue;
                }
                int inactiveDays = calculateInactiveDays(rfm.getLastActiveTime());
                ChurnPredictionVO.RiskLevel riskLevel = determineRiskLevel(inactiveDays);
                riskLevelStats.merge(riskLevel.getCode(), 1, Integer::sum);
                totalProbability += calculateChurnProbability(rfm, riskLevel);
                userCount++;
                if (Boolean.TRUE.equals(rfm.getPublicFlowRestricted())) {
                    restrictedUsers++;
                } else {
                    publicUsers++;
                }
            }

            lastUserId = userIds.get(userIds.size() - 1);
            if (userIds.size() < CHURN_STATS_BATCH_SIZE) {
                break;
            }
        }

        stats.put("riskLevelStats", riskLevelStats);
        stats.put("totalUsers", userCount);
        stats.put("publicUsers", publicUsers);
        stats.put("restrictedUsers", restrictedUsers);
        stats.put("averageChurnProbability", userCount > 0 ? totalProbability / userCount : 0);
        stats.put("highRiskUsers", riskLevelStats.get(ChurnPredictionVO.RiskLevel.HIGH_RISK.getCode()));
        stats.put("churnedUsers", riskLevelStats.get(ChurnPredictionVO.RiskLevel.CHURNED.getCode()));
        stats.put("generateTime", LocalDateTime.now());

                    
        redisTemplate.opsForValue().set(cacheKey, stats, 1, TimeUnit.HOURS);

        return stats;
    }

    @Override
    public List<String> analyzeChurnReasons(Long userId) {
        List<String> reasons = new ArrayList<>();

        ChurnPredictionVO prediction = predictUserChurn(userId);
        if (ObjectUtils.isEmpty(prediction)) {
            return reasons;
        }

                     
        if (ObjectUtils.isNotEmpty(prediction.getRiskFactors())) {
            for (ChurnPredictionVO.RiskFactor factor : prediction.getRiskFactors()) {
                reasons.add(factor.getFactorDescription());
            }
        }

        return reasons;
    }

    @Override
    public List<String> generateRecallStrategy(Long userId) {
        ChurnPredictionVO prediction = predictUserChurn(userId);
        if (ObjectUtils.isEmpty(prediction)) {
            return new ArrayList<>();
        }

        return prediction.getRecallActions();
    }

    @Override
    public Map<String, Object> getChurnTrend(Integer days) {
        Map<String, Object> trend = new LinkedHashMap<>();

        int analysisDays = ObjectUtils.isNotEmpty(days) ? days : 30;
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(analysisDays);

        List<Map<String, Object>> dailyData = new ArrayList<>();

        for (int i = 0; i < analysisDays; i++) {
            LocalDateTime dayTime = startTime.plusDays(i);
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", dayTime.toLocalDate());

                            
            Map<String, Integer> dayStats = new LinkedHashMap<>();
            for (ChurnPredictionVO.RiskLevel level : ChurnPredictionVO.RiskLevel.values()) {
                dayStats.put(level.getCode(), 0);
            }

            dayData.put("riskLevels", dayStats);
            dailyData.add(dayData);
        }

        trend.put("period", analysisDays + "天");
        trend.put("startDate", startTime.toLocalDate());
        trend.put("endDate", endTime.toLocalDate());
        trend.put("dailyData", dailyData);
        trend.put("generateTime", LocalDateTime.now());

        return trend;
    }

    @Override
    public Boolean sendChurnAlert(Long userId) {
        if (!UserAccountStatusUtil.canAppearInRecommendations(userId, userMapper::selectById)) {
            log.info("跳过非推荐池账号的流失预警: userId={}", userId);
            return false;
        }

        ChurnPredictionVO prediction = predictUserChurn(userId);
        if (ObjectUtils.isEmpty(prediction)) {
            return false;
        }

                         
        if (ChurnPredictionVO.RiskLevel.NO_RISK.getCode().equals(prediction.getRiskLevel())) {
            return false;
        }

                       
        String alertKey = "churn:alert:" + userId;
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("userId", userId);
        alert.put("riskLevel", prediction.getRiskLevel());
        alert.put("churnProbability", prediction.getChurnProbability());
        alert.put("sendTime", LocalDateTime.now());
        alert.put("recallActions", prediction.getRecallActions());

        redisTemplate.opsForValue().set(alertKey, alert, 24, TimeUnit.HOURS);

        log.info("发送流失预警: userId={}, riskLevel={}", userId, prediction.getRiskLevel());

        return true;
    }

    @Override
    public Integer batchSendChurnAlerts(String riskLevel) {
        List<ChurnPredictionVO> highRiskUsers = getHighRiskUsers(riskLevel, 100);

        int sentCount = 0;
        for (ChurnPredictionVO prediction : highRiskUsers) {
            if (sendChurnAlert(prediction.getUserId())) {
                sentCount++;
            }
        }

        log.info("批量发送流失预警: riskLevel={}, sentCount={}", riskLevel, sentCount);

        return sentCount;
    }

    @Override
    public Boolean recordRecallAction(Long userId, String action, Double cost) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(action)) {
            return false;
        }

                 
        String recordKey = "churn:recall:" + userId;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("userId", userId);
        record.put("action", action);
        record.put("cost", ObjectUtils.isNotEmpty(cost) ? cost : 0.0);
        record.put("actionTime", LocalDateTime.now());

                     
        redisTemplate.opsForList().rightPush(recordKey + ":list", record);
        redisTemplate.expire(recordKey + ":list", 30, TimeUnit.DAYS);

        log.info("记录召回操作: userId={}, action={}, cost={}", userId, action, cost);

        return true;
    }

    @Override
    public Map<String, Object> getRecallEffectiveness(Integer days) {
        Map<String, Object> effectiveness = new LinkedHashMap<>();

        int analysisDays = ObjectUtils.isNotEmpty(days) ? days : 30;

        effectiveness.put("period", analysisDays + "天");
        effectiveness.put("totalRecallActions", 0);
        effectiveness.put("recalledUsers", 0);
        effectiveness.put("recallSuccessRate", 0);
        effectiveness.put("averageCost", 0.0);
        effectiveness.put("generateTime", LocalDateTime.now());

        return effectiveness;
    }

       
                                  
      
                          
                       
       
    private LambdaQueryWrapper<User> buildRecallCandidateQuery(int limit) {
        return UserAccountStatusUtil.publicStatsUserQuery()
                .orderByAsc(User::getLastLoginTime)
                .last("LIMIT " + limit);
    }

       
             
       
    private ChurnPredictionVO.RiskLevel determineRiskLevel(int inactiveDays) {
        if (inactiveDays >= CHURNED_DAYS) {
            return ChurnPredictionVO.RiskLevel.CHURNED;
        } else if (inactiveDays >= HIGH_RISK_DAYS) {
            return ChurnPredictionVO.RiskLevel.HIGH_RISK;
        } else if (inactiveDays >= MEDIUM_RISK_DAYS) {
            return ChurnPredictionVO.RiskLevel.MEDIUM_RISK;
        } else if (inactiveDays >= LOW_RISK_DAYS) {
            return ChurnPredictionVO.RiskLevel.LOW_RISK;
        } else {
            return ChurnPredictionVO.RiskLevel.NO_RISK;
        }
    }

       
             
       
    private int calculateChurnProbability(RFMVO rfm, ChurnPredictionVO.RiskLevel riskLevel) {
                     
        int baseProbability = riskLevel.getBaseProbability();

                      
        if (rfm != null) {
            int rfmProbability = calculateRfmChurnProbability(rfm);
                   
            baseProbability = (baseProbability + rfmProbability) / 2;
        }

        return Math.min(100, Math.max(0, baseProbability));
    }

       
               
       
    private ChurnPredictionVO.UserValueLevel determineUserValueLevel(RFMVO rfm) {
        if (rfm == null) {
            return ChurnPredictionVO.UserValueLevel.LOW;
        }

                        
        int totalScore = ObjectUtils.isNotEmpty(rfm.getTotalScore()) ? rfm.getTotalScore() : 0;

        if (totalScore >= 12) {
            return ChurnPredictionVO.UserValueLevel.HIGH;
        } else if (totalScore >= 8) {
            return ChurnPredictionVO.UserValueLevel.MEDIUM;
        } else {
            return ChurnPredictionVO.UserValueLevel.LOW;
        }
    }

    private int calculateInactiveDays(LocalDateTime lastActiveTime) {
        if (lastActiveTime == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(lastActiveTime, LocalDateTime.now());
    }

    private int calculateRfmChurnProbability(RFMVO rfm) {
        Integer rScore = rfm.getRecencyScore();
        int probability;
        switch (rScore == null ? 0 : rScore) {
            case 5:
                probability = 5;
                break;
            case 4:
                probability = 15;
                break;
            case 3:
                probability = 40;
                break;
            case 2:
                probability = 70;
                break;
            case 1:
                probability = 90;
                break;
            default:
                probability = 50;
                break;
        }
        if (rfm.getFrequencyScore() != null && rfm.getFrequencyScore() >= 4) {
            probability += 10;
        }
        if (rfm.getMonetaryScore() != null && rfm.getMonetaryScore() >= 4) {
            probability -= 10;
        }
        return Math.min(100, Math.max(0, probability));
    }

       
             
       
    private List<ChurnPredictionVO.RiskFactor> analyzeRiskFactors(Long userId, int inactiveDays) {
        List<ChurnPredictionVO.RiskFactor> factors = new ArrayList<>();

                     
        ChurnPredictionVO.RiskFactor inactiveFactor = new ChurnPredictionVO.RiskFactor();
        inactiveFactor.setFactorName("未活跃天数");
        inactiveFactor.setFactorDescription("最近" + inactiveDays + "天未活跃");
        inactiveFactor.setImpact(inactiveDays >= CHURNED_DAYS ? 5 : inactiveDays >= HIGH_RISK_DAYS ? 4 : 3);
        inactiveFactor.setCurrentValue(inactiveDays + "天");
        inactiveFactor.setNormalRange("7天内");
        factors.add(inactiveFactor);

                     
        ChurnPredictionVO.RiskFactor activityFactor = new ChurnPredictionVO.RiskFactor();
        activityFactor.setFactorName("活跃度变化");
        activityFactor.setFactorDescription("近期活跃度明显下降");
        activityFactor.setImpact(2);
        activityFactor.setCurrentValue("下降");
        activityFactor.setNormalRange("稳定或上升");
        factors.add(activityFactor);

        return factors;
    }

       
                        
       
    private List<String> generateRecallActionsByRisk(ChurnPredictionVO.RiskLevel riskLevel,
                                                      ChurnPredictionVO.UserValueLevel valueLevel) {
        List<String> actions = new ArrayList<>();

        switch (riskLevel) {
            case LOW_RISK:
                actions.add("发送个性化推荐邮件");
                actions.add("推送新歌通知");
                actions.add("赠送1天VIP体验");
                break;
            case MEDIUM_RISK:
                actions.add("发送召回短信");
                actions.add("提供回归奖励（积分或VIP）");
                actions.add("推送好友动态");
                actions.add("个性化歌单推荐");
                break;
            case HIGH_RISK:
                actions.add("电话回访（高价值用户）");
                actions.add("发送专属回归礼包");
                actions.add("赠送7天VIP");
                actions.add("推送平台新功能");
                actions.add("好友提醒召回");
                break;
            case CHURNED:
                actions.add("强力召回活动（优惠券等）");
                actions.add("30天VIP免费试用");
                actions.add("一对一客服跟进");
                actions.add("定期推送回归活动");
                break;
            default:
                break;
        }

                       
        if (valueLevel == ChurnPredictionVO.UserValueLevel.HIGH) {
            actions.add("专属客服跟进");
            actions.add("定制化回归方案");
        }

        return actions;
    }

       
               
       
    private String determineSuggestedBudget(ChurnPredictionVO.UserValueLevel valueLevel,
                                           ChurnPredictionVO.RiskLevel riskLevel) {
        if (valueLevel == ChurnPredictionVO.UserValueLevel.HIGH) {
            if (riskLevel == ChurnPredictionVO.RiskLevel.HIGH_RISK || riskLevel == ChurnPredictionVO.RiskLevel.CHURNED) {
                return "高（100-200积分或7天VIP）";
            }
            return "中（50-100积分或3天VIP）";
        } else if (valueLevel == ChurnPredictionVO.UserValueLevel.MEDIUM) {
            return "低（20-50积分或1天VIP）";
        } else {
            return "最低（推送通知）";
        }
    }
}
