package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.ActivityAntiSpamConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserPointsRecord;
import com.haoran.music.entity.UserRewardClaim;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserPointsRecordMapper;
import com.haoran.music.mapper.UserRewardClaimMapper;
import com.haoran.music.service.ActivityRewardService;
import com.haoran.music.service.UserActivityEnhancedService;
import com.haoran.music.service.UserPointsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

   
                      
                         
  
                      
   
@Slf4j
@Service
public class ActivityRewardServiceImpl implements ActivityRewardService {

    private final UserActivityEnhancedService userActivityEnhancedService;
    private final UserPointsService userPointsService;
    private final UserMapper userMapper;
    private final UserPointsRecordMapper userPointsRecordMapper;
    private final UserRewardClaimMapper userRewardClaimMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Clock clock;

                 
    private static final int INACTIVE_REWARD = 0;
    private static final int NORMAL_REWARD = 10;                 
    private static final int ACTIVE_REWARD = 25;                  
    private static final int SUPER_ACTIVE_REWARD = 50;             

             
    private static final String POINTS_CHANGE_TYPE = "activity_reward";

               
    private static final String CLAIM_KEY_PREFIX = "activity:reward:claimed:";
    private static final int DEFAULT_HISTORY_PAGE_SIZE = 20;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    public ActivityRewardServiceImpl(
            UserActivityEnhancedService userActivityEnhancedService,
            UserPointsService userPointsService,
            UserMapper userMapper,
            UserPointsRecordMapper userPointsRecordMapper,
            UserRewardClaimMapper userRewardClaimMapper,
            RedisTemplate<String, Object> redisTemplate) {
        this(userActivityEnhancedService, userPointsService, userMapper, userPointsRecordMapper,
                userRewardClaimMapper, redisTemplate, Clock.system(BUSINESS_ZONE));
    }

    ActivityRewardServiceImpl(
            UserActivityEnhancedService userActivityEnhancedService,
            UserPointsService userPointsService,
            UserMapper userMapper,
            UserPointsRecordMapper userPointsRecordMapper,
            UserRewardClaimMapper userRewardClaimMapper,
            RedisTemplate<String, Object> redisTemplate,
            Clock clock) {
        this.userActivityEnhancedService = userActivityEnhancedService;
        this.userPointsService = userPointsService;
        this.userMapper = userMapper;
        this.userPointsRecordMapper = userPointsRecordMapper;
        this.userRewardClaimMapper = userRewardClaimMapper;
        this.redisTemplate = redisTemplate;
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    @Override
    public Map<String, Object> getCurrentReward(Long userId) {
        Map<String, Object> reward = new HashMap<>();
        User user = ObjectUtils.isEmpty(userId) ? null : userMapper.selectById(userId);
        boolean canInteract = UserAccountStatusUtil.canInteract(user);

                  
        Integer activityScore = userActivityEnhancedService.getEnhancedActivityScore(userId);
        String activityLevel = getActivityLevel(activityScore);
        int pointsReward = calculateRewardPoints(activityScore);

                  
        boolean claimedThisMonth = isClaimedThisMonth(userId);

                 
        reward.put("userId", userId);
        reward.put("activityScore", activityScore);
        reward.put("activityLevel", activityLevel);
        reward.put("activityLevelName", getActivityLevelName(activityLevel));
        reward.put("pointsReward", pointsReward);
        reward.put("claimedThisMonth", claimedThisMonth);
        reward.put("canClaim", canInteract && pointsReward > 0 && !claimedThisMonth);
        reward.put("accountUnavailable", !canInteract);
        if (!canInteract) {
            reward.put("accountUnavailableMessage", UserAccountStatusUtil.currentUnavailableMessage(user));
        }

               
        if (pointsReward > 0) {
            reward.put("rewardDescription", String.format(
                    "您的活跃度等级为【%s】，本月可获得%d积分奖励！",
                    getActivityLevelName(activityLevel), pointsReward
            ));
        } else {
            reward.put("rewardDescription", "活跃度达到30分以上可领取奖励，继续加油！");
        }

                 
        reward.put("nextLevelInfo", getNextLevelInfo(activityScore));

        return reward;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean claimReward(Long userId) {
        User lockedUser = ObjectUtils.isEmpty(userId) ? null : userMapper.selectByIdForUpdate(userId);
        UserAccountStatusUtil.requireCanInteract(lockedUser, "领取活跃度奖励");
        try {
                     
            Map<String, Object> reward = getCurrentReward(userId);
            boolean claimedThisMonth = (boolean) reward.get("claimedThisMonth");
            int pointsReward = (int) reward.get("pointsReward");
            String activityLevel = (String) reward.get("activityLevel");

                       
            if (pointsReward <= 0) {
                log.warn("event=activity_reward_claim_rejected reason=no_reward userId={} score={}",
                        userId, reward.get("activityScore"));
                return false;
            }

            if (claimedThisMonth) {
                log.warn("event=activity_reward_claim_rejected reason=already_claimed userId={}", userId);
                return false;
            }

            UserRewardClaim claim = new UserRewardClaim();
            claim.setUserId(userId);
            claim.setClaimType(POINTS_CHANGE_TYPE);
            claim.setClaimPeriod(currentClaimPeriod());
            claim.setStatus("pending");
            claim.setGrantedPoints(0);
            try {
                if (userRewardClaimMapper.insert(claim) != 1) {
                    throw new IllegalStateException("奖励领取记录创建失败");
                }
            } catch (DuplicateKeyException e) {
                log.warn("event=activity_reward_claim_replayed userId={}", userId);
                return false;
            }

                             
            String reason = "活跃度奖励-" + activityLevel + "级";
            Map<String, Object> pointsBeforeGrant = userPointsService.getUserPoints(userId);
            Object currentPointsValue = pointsBeforeGrant == null ? null : pointsBeforeGrant.get("currentPoints");
            if (!(currentPointsValue instanceof Number)) {
                throw new IllegalStateException("积分余额读取失败");
            }
            int beforePoints = ((Number) currentPointsValue).intValue();

            Integer afterPoints = userPointsService.addPoints(
                    userId,
                    POINTS_CHANGE_TYPE,
                    pointsReward,
                    reason,
                    null,
                    "activity_reward"
            );

            if (afterPoints == null || afterPoints < 0) {
                throw new IllegalStateException("积分发放失败");
            }
            if (afterPoints - beforePoints != pointsReward) {
                throw new IllegalStateException("月度活跃度奖励未全额发放");
            }

            if (userRewardClaimMapper.markGranted(claim.getId(), pointsReward, LocalDateTime.now(clock)) != 1) {
                throw new IllegalStateException("奖励领取状态更新失败");
            }

            cacheClaimedAfterCommit(userId);

            log.info("event=activity_reward_claim_succeeded userId={} level={} points={} afterPoints={}",
                    userId, activityLevel, pointsReward, afterPoints);

            return true;
        } catch (RuntimeException e) {
            log.error("event=activity_reward_claim_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public Boolean isClaimedThisMonth(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }
        boolean claimedInDb = hasGrantedClaimThisMonth(userId) || hasClaimedRewardRecordThisMonth(userId);
        if (claimedInDb) {
            setClaimedFlag(userId);
        } else {
            clearClaimedFlag(userId);
        }
        return claimedInDb;
    }
    @Override
    public Map<String, Object> getClaimHistory(Long userId, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();
        int safePage = page == null || page <= 0 ? 1 : page;
        int safeSize = size == null || size <= 0 ? DEFAULT_HISTORY_PAGE_SIZE : Math.min(size, MAX_HISTORY_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        Long total = userPointsRecordMapper.selectCount(buildRewardRecordQuery(userId));
        LambdaQueryWrapper<UserPointsRecord> listWrapper = buildRewardRecordQuery(userId);
        listWrapper.orderByDesc(UserPointsRecord::getCreateTime)
                .last("LIMIT " + offset + ", " + safeSize);
        List<UserPointsRecord> records = userPointsRecordMapper.selectList(listWrapper);

        List<Map<String, Object>> historyList = records.stream()
                .map(r -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", r.getId());
                    item.put("points", r.getPoints());
                    item.put("reason", r.getReason());
                    item.put("afterPoints", r.getAfterPoints());
                    item.put("createTime", r.getCreateTime());
                    return item;
                })
                .collect(Collectors.toList());

        result.put("records", historyList);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }
    @Override
    public Integer calculateRewardPoints(Integer activityScore) {
        String level = getActivityLevel(activityScore);
        switch (level) {
            case "super_active":
                return SUPER_ACTIVE_REWARD;
            case "active":
                return ACTIVE_REWARD;
            case "normal":
                return NORMAL_REWARD;
            case "inactive":
            default:
                return INACTIVE_REWARD;
        }
    }

    @Override
    public Map<String, Integer> getRewardConfig() {
        Map<String, Integer> config = new HashMap<>();
        config.put("inactive", INACTIVE_REWARD);
        config.put("normal", NORMAL_REWARD);
        config.put("active", ACTIVE_REWARD);
        config.put("super_active", SUPER_ACTIVE_REWARD);
        return config;
    }

                                                       

       
              
       
    private String getActivityLevel(int score) {
        if (score >= ActivityAntiSpamConstants.SUPER_ACTIVE_MIN_SCORE) {
            return "super_active";
        } else if (score >= ActivityAntiSpamConstants.ACTIVE_MIN_SCORE) {
            return "active";
        } else if (score >= ActivityAntiSpamConstants.NORMAL_MIN_SCORE) {
            return "normal";
        } else {
            return "inactive";
        }
    }

       
                
       
    private String getActivityLevelName(String level) {
        switch (level) {
            case "super_active":
                return "超级活跃";
            case "active":
                return "活跃";
            case "normal":
                return "普通";
            case "inactive":
            default:
                return "不活跃";
        }
    }

       
               
       
    private Map<String, Object> getNextLevelInfo(int currentScore) {
        Map<String, Object> info = new HashMap<>();

        String currentLevel = getActivityLevel(currentScore);
        String nextLevel = null;
        int nextLevelScore = 0;
        int progress = 0;

        switch (currentLevel) {
            case "inactive":
                nextLevel = "normal";
                nextLevelScore = ActivityAntiSpamConstants.NORMAL_MIN_SCORE;
                progress = (int) ((currentScore / (double) nextLevelScore) * 100);
                break;
            case "normal":
                nextLevel = "active";
                nextLevelScore = ActivityAntiSpamConstants.ACTIVE_MIN_SCORE;
                progress = (int) (((currentScore - ActivityAntiSpamConstants.NORMAL_MIN_SCORE) /
                        (double) (nextLevelScore - ActivityAntiSpamConstants.NORMAL_MIN_SCORE)) * 100);
                break;
            case "active":
                nextLevel = "super_active";
                nextLevelScore = ActivityAntiSpamConstants.SUPER_ACTIVE_MIN_SCORE;
                progress = (int) (((currentScore - ActivityAntiSpamConstants.ACTIVE_MIN_SCORE) /
                        (double) (nextLevelScore - ActivityAntiSpamConstants.ACTIVE_MIN_SCORE)) * 100);
                break;
            case "super_active":
                nextLevel = null;
                nextLevelScore = currentScore;
                progress = 100;
                break;
        }

        info.put("currentLevel", getActivityLevelName(currentLevel));
        info.put("nextLevel", nextLevel != null ? getActivityLevelName(nextLevel) : null);
        info.put("nextLevelScore", nextLevelScore);
        info.put("progress", progress);
        info.put("scoreToNext", nextLevel != null ? (nextLevelScore - currentScore) : 0);

        return info;
    }

       
                
       
    private String getClaimKey(Long userId) {
        return CLAIM_KEY_PREFIX + userId + ":" + currentClaimPeriod();
    }

       
              
       
    private void setClaimedFlag(Long userId) {
        try {
                                            
            redisTemplate.opsForValue().set(getClaimKey(userId), true, 35, TimeUnit.DAYS);
        } catch (RuntimeException e) {
            log.warn("event=activity_reward_cache_write_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }
    }

    private void cacheClaimedAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            setClaimedFlag(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                setClaimedFlag(userId);
            }
        });
    }

    private void clearClaimedFlag(Long userId) {
        try {
            redisTemplate.delete(getClaimKey(userId));
        } catch (RuntimeException e) {
            log.warn("event=activity_reward_cache_delete_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }
    }

    private LambdaQueryWrapper<UserPointsRecord> buildRewardRecordQuery(Long userId) {
        return new LambdaQueryWrapper<UserPointsRecord>()
                .eq(UserPointsRecord::getUserId, userId)
                .eq(UserPointsRecord::getChangeType, POINTS_CHANGE_TYPE);
    }

    private boolean hasClaimedRewardRecordThisMonth(Long userId) {
        LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);
        LambdaQueryWrapper<UserPointsRecord> wrapper = buildRewardRecordQuery(userId)
                .gt(UserPointsRecord::getPoints, 0)
                .ge(UserPointsRecord::getCreateTime, monthStart)
                .lt(UserPointsRecord::getCreateTime, nextMonthStart);
        Long count = userPointsRecordMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String currentClaimPeriod() {
        return YearMonth.now(clock).toString();
    }

    private boolean hasGrantedClaimThisMonth(Long userId) {
        Long count = userRewardClaimMapper.selectCount(new LambdaQueryWrapper<UserRewardClaim>()
                .eq(UserRewardClaim::getUserId, userId)
                .eq(UserRewardClaim::getClaimType, POINTS_CHANGE_TYPE)
                .eq(UserRewardClaim::getClaimPeriod, currentClaimPeriod())
                .eq(UserRewardClaim::getStatus, "granted"));
        return count != null && count > 0;
    }
}
