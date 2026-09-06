   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.UserPointsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
            
  
                                       
                                                  
   
@Slf4j
@Service
public class UserPointsServiceImpl implements UserPointsService {

    private static final int DEFAULT_DAILY_LIMIT = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ACTIVITY_VALUE_CHANGE_TYPES = Arrays.asList(
            "sign", "makeup", "achievement", "redeem", "decoration", "expire", "invite", "playlist", "follow"
    );

    private final UserPointsMapper userPointsMapper;
    private final UserPointsRecordMapper userPointsRecordMapper;
    private final UserLevelMapper userLevelMapper;
    private final UserMapper userMapper;
    private final UserGrowthConfig userGrowthConfig;


    public UserPointsServiceImpl(UserPointsMapper userPointsMapper,
                               UserPointsRecordMapper userPointsRecordMapper,
                               UserLevelMapper userLevelMapper,
                               UserMapper userMapper,
                               UserGrowthConfig userGrowthConfig) {
        this.userPointsMapper = userPointsMapper;
        this.userPointsRecordMapper = userPointsRecordMapper;
        this.userLevelMapper = userLevelMapper;
        this.userMapper = userMapper;
        this.userGrowthConfig = userGrowthConfig;
    }

    @Override
    public Map<String, Object> getUserPoints(Long userId) {
        UserPoints points = getUserPointsEntity(userId);
        Integer todayPoints = getTodayPoints(userId);
        Integer monthPoints = getMonthPoints(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("currentPoints", safeInt(points.getCurrentPoints()));
        result.put("availablePoints", safeInt(points.getCurrentPoints()));
        result.put("totalPoints", safeInt(points.getTotalPoints()));
        result.put("frozenPoints", safeInt(points.getFrozenPoints()));
        result.put("todayPoints", todayPoints);
        result.put("monthPoints", monthPoints);
        result.put("lastResetDate", LocalDate.now());
        result.put("lastUpdateTime", points.getUpdateTime() != null ? points.getUpdateTime() : points.getLastUpdateTime());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean initUserPoints(Long userId) {
        UserPoints existing = userPointsMapper.selectOne(
                new LambdaQueryWrapper<UserPoints>()
                        .eq(UserPoints::getUserId, userId)
        );

        if (existing != null) {
            return true;
        }

        UserPoints points = new UserPoints();
        points.setUserId(userId);
        points.setCurrentPoints(0);
        points.setTotalPoints(0);
        points.setFrozenPoints(0);
        points.setTodayPoints(0);
        points.setMonthPoints(0);
        points.setLastResetDate(LocalDate.now());
        points.setLastUpdateTime(LocalDateTime.now());

        userPointsMapper.insert(points);

                
        initUserLevel(userId);

        log.info("初始化用户积分: userId={}", userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addPoints(Long userId, String changeType, Integer points,
                           String reason, Long businessId, String businessType) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(points) || points == 0) {
            return ObjectUtils.isEmpty(userId) ? 0 : safeInt(getUserPointsEntity(userId).getCurrentPoints());
        }
        if (!isAdminAdjustment(changeType)) {
            UserAccountStatusUtil.requireCanInteract(userMapper.selectByIdForUpdate(userId), "变更积分");
        }

        UserPoints userPoints = getUserPointsEntityForUpdate(userId);
        int beforePoints = safeInt(userPoints.getCurrentPoints());
        int delta = points;

                                              
        if (delta > 0 && !isAdminAdjustment(changeType)) {
            int todayEarned = getTodayPoints(userId);
            int remaining = Math.max(getDailyLimit() - todayEarned, 0);
            if (remaining <= 0) {
                log.warn("达到每日积分上限: userId={}, todayEarned={}", userId, todayEarned);
                return beforePoints;
            }
            if (delta > remaining) {
                log.info("积分奖励超过剩余额度，按剩余额度发放: userId={}, requested={}, remaining={}", userId, delta, remaining);
                delta = remaining;
            }
        }

        int newPoints = beforePoints + delta;
        if (newPoints < 0) {
            throw new BusinessException("积分不足");
        }

                                           
        UserPointsRecord record = new UserPointsRecord();
        record.setUserId(userId);
        record.setChangeType(changeType);
        record.setPoints(delta);
        record.setBeforePoints(beforePoints);
        record.setAfterPoints(newPoints);
        record.setReason(reason);
        record.setBusinessId(businessId);
        record.setBusinessType(businessType);
        record.setDescription(businessType);
        userPointsRecordMapper.insert(record);

        userPoints.setCurrentPoints(newPoints);
        userPoints.setTotalPoints(safeInt(userPoints.getTotalPoints()) + Math.max(delta, 0));
        userPoints.setFrozenPoints(safeInt(userPoints.getFrozenPoints()));
        userPoints.setTodayPoints(getTodayPoints(userId));
        userPoints.setMonthPoints(getMonthPoints(userId));
        userPoints.setLastUpdateTime(LocalDateTime.now());
        userPointsMapper.updateById(userPoints);

                                  
        updateUserLevel(userId, userPoints.getTotalPoints());

        log.info("变更积分: userId={}, changeType={}, delta={}, newPoints={}",
                userId, changeType, delta, newPoints);

        return newPoints;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deductPoints(Long userId, String changeType, Integer points,
                              String reason, Long businessId, String businessType) {
        int delta = Math.abs(safeInt(points));
        return addPoints(userId, changeType, -delta, reason, businessId, businessType);
    }

    @Override
    public Boolean checkPointsEnough(Long userId, Integer points) {
        UserPoints userPoints = getUserPointsEntity(userId);
        return safeInt(userPoints.getCurrentPoints()) >= safeInt(points);
    }

    @Override
    public Map<String, Object> getPointsRecords(Long userId, String changeType,
                                               Integer page, Integer size) {
        int safePage = page == null || page <= 0 ? 1 : page;
        int safeSize = size == null || size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;

        LambdaQueryWrapper<UserPointsRecord> countWrapper = buildRecordQuery(userId, changeType);
        Long total = userPointsRecordMapper.selectCount(countWrapper);

        LambdaQueryWrapper<UserPointsRecord> listWrapper = buildRecordQuery(userId, changeType);
        listWrapper.orderByDesc(UserPointsRecord::getCreateTime)
                .last("LIMIT " + offset + ", " + safeSize);
        List<UserPointsRecord> records = userPointsRecordMapper.selectList(listWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("list", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer resetDailyPoints() {
                                          
        log.info("每日积分无需重置: 今日积分由 user_points_record 实时汇总");
        return 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean adjustPoints(Long userId, Integer points, String reason, Long operatorId) {
        if (ObjectUtils.isEmpty(points) || points == 0) {
            return false;
        }
        String changeType = points > 0 ? "admin_add" : "admin_deduct";
        addPoints(userId, changeType, points,
                "管理员调整: " + reason, operatorId, "admin");

        log.info("管理员调整积分: userId={}, points={}, operatorId={}",
                userId, points, operatorId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer checkinPoints(Long userId) {
        LocalDate today = LocalDate.now();

        LambdaQueryWrapper<UserPointsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPointsRecord::getUserId, userId)
                .eq(UserPointsRecord::getChangeType, "checkin")
                .ge(UserPointsRecord::getCreateTime, today.atStartOfDay());

        Long count = userPointsRecordMapper.selectCount(wrapper);

        if (count > 0) {
            throw new BusinessException("今天已签到");
        }

        return addPoints(userId, "checkin", userGrowthConfig.getPoints().getCheckinPoints(), "签到奖励", null, "checkin");
    }

    @Override
    public Integer getTodayPoints(Long userId) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return sumPositivePoints(userId, start, end);
    }

    @Override
    public Integer getMonthPoints(Long userId) {
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        return sumPositivePoints(userId, start, end);
    }

    @Override
    public Boolean isReachDailyLimit(Long userId) {
        return getTodayPoints(userId) >= getDailyLimit();
    }

    @Override
    public Map<String, Object> getPointsConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("dailyLimit", getDailyLimit());
        result.put("checkinPoints", userGrowthConfig.getPoints().getCheckinPoints());
        result.put("listenPoints", userGrowthConfig.getPoints().getListenPoints());
        result.put("sharePoints", userGrowthConfig.getPoints().getSharePoints());
        result.put("commentPoints", userGrowthConfig.getPoints().getCommentPoints());

        return result;
    }

    @Override
    public Map<String, Object> getPointsRanking(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, MAX_PAGE_SIZE);
        List<UserPoints> rankedUsers = userPointsMapper.selectPublicRanking(safeLimit);

        Map<String, Object> result = new HashMap<>();
        result.put("list", rankedUsers);
        result.put("limit", safeLimit);

        return result;
    }

    @Override
    public Integer convertPointsToExp(Long userId, Integer points) {
        int exp = safeInt(points) / getExpDivisor();

        UserLevel level = userLevelMapper.selectOne(
                new LambdaQueryWrapper<UserLevel>()
                        .eq(UserLevel::getUserId, userId)
        );

        if (level != null) {
            level.setCurrentExp(safeInt(level.getCurrentExp()) + exp);
            level.setTotalExp(safeInt(level.getTotalExp()) + exp);

                     
            checkLevelUp(level);

            userLevelMapper.updateById(level);
        }

        return exp;
    }

                                                     

    private UserPoints getUserPointsEntity(Long userId) {
        UserPoints points = userPointsMapper.selectOne(
                new LambdaQueryWrapper<UserPoints>()
                        .eq(UserPoints::getUserId, userId)
        );

        if (points == null) {
            initUserPoints(userId);
            points = userPointsMapper.selectOne(
                    new LambdaQueryWrapper<UserPoints>()
                            .eq(UserPoints::getUserId, userId)
            );
        }

        if (points.getCurrentPoints() == null) {
            points.setCurrentPoints(0);
        }
        if (points.getTotalPoints() == null) {
            points.setTotalPoints(0);
        }
        if (points.getFrozenPoints() == null) {
            points.setFrozenPoints(0);
        }
        return points;
    }

    private UserPoints getUserPointsEntityForUpdate(Long userId) {
        UserPoints points = userPointsMapper.selectOne(
                new LambdaQueryWrapper<UserPoints>()
                        .eq(UserPoints::getUserId, userId)
                        .last("FOR UPDATE")
        );
        if (points == null) {
            initUserPoints(userId);
            points = userPointsMapper.selectOne(
                    new LambdaQueryWrapper<UserPoints>()
                            .eq(UserPoints::getUserId, userId)
                            .last("FOR UPDATE")
            );
        }
        if (points.getCurrentPoints() == null) {
            points.setCurrentPoints(0);
        }
        if (points.getTotalPoints() == null) {
            points.setTotalPoints(0);
        }
        if (points.getFrozenPoints() == null) {
            points.setFrozenPoints(0);
        }
        return points;
    }

    private LambdaQueryWrapper<UserPointsRecord> buildRecordQuery(Long userId, String changeType) {
        LambdaQueryWrapper<UserPointsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPointsRecord::getUserId, userId);
        if (ObjectUtils.isNotEmpty(changeType)) {
            wrapper.eq(UserPointsRecord::getChangeType, changeType);
        } else {
            wrapper.notIn(UserPointsRecord::getChangeType, ACTIVITY_VALUE_CHANGE_TYPES);
        }
        return wrapper;
    }

       
                                  
      
  
    private Integer sumPositivePoints(Long userId, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<UserPointsRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPointsRecord::getUserId, userId)
                .gt(UserPointsRecord::getPoints, 0)
                .notIn(UserPointsRecord::getChangeType, ACTIVITY_VALUE_CHANGE_TYPES)
                .ge(UserPointsRecord::getCreateTime, start)
                .lt(UserPointsRecord::getCreateTime, end)
                .select(UserPointsRecord::getPoints);

        return userPointsRecordMapper.selectList(wrapper).stream()
                .map(UserPointsRecord::getPoints)
                .filter(ObjectUtils::isNotEmpty)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private boolean isAdminAdjustment(String changeType) {
        return "admin".equals(changeType)
                || "admin_add".equals(changeType)
                || "admin_deduct".equals(changeType);
    }

    private int getDailyLimit() {
        Integer dailyLimit = userGrowthConfig.getPoints().getDailyLimit();
        return dailyLimit == null || dailyLimit <= 0 ? DEFAULT_DAILY_LIMIT : dailyLimit;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void initUserLevel(Long userId) {
        UserLevel existing = userLevelMapper.selectOne(
                new LambdaQueryWrapper<UserLevel>()
                        .eq(UserLevel::getUserId, userId)
        );

        if (existing != null) {
            return;
        }

        UserLevel level = new UserLevel();
        level.setUserId(userId);
        level.setLevel(1);
        level.setCurrentExp(0);
        level.setNextLevelExp(userGrowthConfig.getPoints().resolveNextLevelExp(1));
        level.setTotalExp(0);
        level.setLevelTitle(getLevelTitle(1));

        userLevelMapper.insert(level);
    }

    private void updateUserLevel(Long userId, Integer totalPoints) {
        UserLevel level = userLevelMapper.selectOne(
                new LambdaQueryWrapper<UserLevel>()
                        .eq(UserLevel::getUserId, userId)
        );

        if (level != null) {
            int exp = safeInt(totalPoints) / getExpDivisor();
            level.setTotalExp(exp);
            level.setCurrentExp(exp);

            checkLevelUp(level);

            userLevelMapper.updateById(level);
        }
    }

    private void checkLevelUp(UserLevel level) {
        List<Integer> levelExpThresholds = userGrowthConfig.getPoints().getLevelExpThresholds();
        if (ObjectUtils.isEmpty(levelExpThresholds)) {
            return;
        }

        for (int i = 0; i < levelExpThresholds.size(); i++) {
            int lvl = i + 1;
            Integer exp = levelExpThresholds.get(i);
            if (ObjectUtils.isEmpty(exp)) {
                continue;
            }

            if (safeInt(level.getCurrentExp()) >= exp && safeInt(level.getLevel()) < lvl) {
                level.setLevel(lvl);
                level.setNextLevelExp(getNextLevelExp(lvl));
                level.setLevelTitle(getLevelTitle(lvl));

                log.info("User level upgraded: userId={}, newLevel={}", level.getUserId(), lvl);
            }
        }
    }

    private int getNextLevelExp(int level) {
        return userGrowthConfig.getPoints().resolveNextLevelExp(level);
    }

    private String getLevelTitle(int level) {
        return userGrowthConfig.getPoints().resolveLevelTitle(level);
    }

    private int getExpDivisor() {
        Integer expDivisor = userGrowthConfig.getPoints().getExpDivisor();
        return ObjectUtils.isEmpty(expDivisor) || expDivisor <= 0 ? 1 : expDivisor;
    }
}
