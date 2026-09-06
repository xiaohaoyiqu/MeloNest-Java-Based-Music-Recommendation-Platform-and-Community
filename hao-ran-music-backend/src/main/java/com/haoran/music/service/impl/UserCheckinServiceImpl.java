   
                      
                        
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.config.UserGrowthConfig;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserCheckin;
import com.haoran.music.mapper.UserCheckinMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserActivityPointsService;
import com.haoran.music.service.UserCheckinService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.service.SigninAchievementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

   
           
  
          
              
                        
                  
  
                                                 
   
@Slf4j
@Service
public class UserCheckinServiceImpl extends ServiceImpl<UserCheckinMapper, UserCheckin> implements UserCheckinService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserActivityPointsService activityPointsService;

    @Resource
    private UserVipService userVipService;

    @Resource
    private SigninAchievementService signinAchievementService;

    @Resource
    private UserGrowthConfig userGrowthConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkin(Long userId) {
                   
        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "签到");

        LocalDateTime checkinTime = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate today = checkinTime.toLocalDate();

                    
        if (hasCheckedInToday(userId)) {
            throw new BusinessException("今日已签到，请明天再来~");
        }

                    
        UserCheckin yesterdayRecord = getCheckinRecord(userId, today.minusDays(1));

                 
        int continuousDays;
        if (yesterdayRecord != null) {
            continuousDays = yesterdayRecord.getContinuousDays() + 1;
        } else {
            continuousDays = 1;
        }

               
        UserGrowthConfig.Checkin checkinConfig = userGrowthConfig.getCheckin();
        int rewardPoints = checkinConfig.getDailyPoints();
        int rewardVipDays = 0;
        int bonusPoints = 0;

                         
        if (continuousDays % 7 == 0) {
            bonusPoints += checkinConfig.getWeeklyBonus();
            rewardVipDays += checkinConfig.getWeeklyVipDays();             
        }
        if (continuousDays % 30 == 0) {
            bonusPoints += checkinConfig.getMonthlyBonus();
        }

                 
        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setUsername(user.getUsername());
        checkin.setCheckinDate(today);
        checkin.setContinuousDays(continuousDays);
        checkin.setRewardPoints(rewardPoints + bonusPoints);
        checkin.setRewardVipDays(rewardVipDays);
        checkin.setCheckinType(1);
        checkin.setCheckinTime(checkinTime);
        if (!save(checkin)) {
            throw new IllegalStateException("签到记录创建失败");
        }

        signinAchievementService.checkAndUnlockAchievement(userId, continuousDays);

                  
        activityPointsService.addPoints(userId, rewardPoints + bonusPoints, "sign",
                String.format("签到奖励，连续签到%d天", continuousDays));

                         
        if (rewardVipDays > 0) {
            userVipService.grantVip(userId, 1, rewardVipDays, "sign");
        }

                 
        Map<String, Object> result = new HashMap<>();
        result.put("checkinDate", today);
        result.put("continuousDays", continuousDays);
        result.put("rewardPoints", rewardPoints + bonusPoints);
        result.put("rewardVipDays", rewardVipDays);
        result.put("isNewRecord", continuousDays == 1);
        result.put("totalPoints", activityPointsService.getUserTotalPoints(userId));

        log.info("用户签到成功: userId={}, continuousDays={}, points={}, vipDays={}",
                userId, continuousDays, rewardPoints + bonusPoints, rewardVipDays);

        return result;
    }

    @Override
    public boolean hasCheckedInToday(Long userId) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, today)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED);
        return count(wrapper) > 0;
    }

    @Override
    public Integer getContinuousDays(Long userId) {
                             
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(UserCheckin::getCheckinDate)
                .last("LIMIT 1");

        UserCheckin lastCheckin = getOne(wrapper);
        if (lastCheckin == null) {
            return 0;
        }

                                 
        LocalDate lastDate = lastCheckin.getCheckinDate();
        long daysBetween = ChronoUnit.DAYS.between(lastDate, today);
        if (daysBetween > 1) {
            return 0;
        }

        return lastCheckin.getContinuousDays();
    }

    @Override
    public Integer getMonthCheckinCount(Long userId) {
        LocalDate monthStart = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        return baseMapper.getMonthCheckinCount(userId, monthStart);
    }

    @Override
    public java.util.List<LocalDate> getMonthCheckinDates(Long userId) {
        LocalDate monthStart = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED)
                .between(UserCheckin::getCheckinDate, monthStart, monthEnd)
                .orderByAsc(UserCheckin::getCheckinDate);

        List<UserCheckin> records = list(wrapper);
        java.util.List<LocalDate> dates = new ArrayList<>();
        for (UserCheckin record : records) {
            dates.add(record.getCheckinDate());
        }
        return dates;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> makeupCheckin(Long userId, LocalDate date) {
                   
        User user = userMapper.selectByIdForUpdate(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "补签");

                             
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        UserGrowthConfig.Checkin checkinConfig = userGrowthConfig.getCheckin();
        if (date == null || !date.isBefore(today)
                || date.isBefore(today.minusDays(checkinConfig.getMakeupWindowDays()))) {
            throw new BusinessException("只能补签最近7天的签到");
        }

                  
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, date)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED);
        if (count(wrapper) > 0) {
            throw new BusinessException("该日期已签到");
        }

                    
        Integer currentPoints = activityPointsService.getUserTotalPoints(userId);
        if (currentPoints < checkinConfig.getMakeupCostPoints()) {
            throw new BusinessException("活跃值不足，无法补签");
        }

        activityPointsService.consumePoints(userId, checkinConfig.getMakeupCostPoints(), "makeup", "补签");

                              
        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setUsername(user.getUsername());
        checkin.setCheckinDate(date);
        checkin.setContinuousDays(0);             
        checkin.setRewardPoints(checkinConfig.getDailyPoints());
        checkin.setRewardVipDays(0);
        checkin.setCheckinType(2);      
        checkin.setCheckinTime(LocalDateTime.now(BUSINESS_ZONE));
        if (!save(checkin)) {
            throw new IllegalStateException("补签记录创建失败");
        }

                
        activityPointsService.addPoints(userId, checkinConfig.getDailyPoints(), "sign", "补签奖励");

        Map<String, Object> result = new HashMap<>();
        result.put("checkinDate", date);
        result.put("continuousDays", getContinuousDays(userId));
        result.put("rewardPoints", checkinConfig.getDailyPoints());
        result.put("costPoints", checkinConfig.getMakeupCostPoints());
        result.put("rewardVipDays", 0);
        result.put("isNewRecord", true);
        result.put("totalPoints", activityPointsService.getUserTotalPoints(userId));

        log.info("用户补签成功: userId={}, date={}", userId, date);
        return result;
    }

    @Override
    public Map<String, Object> getCheckinStats(Long userId) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate monthStart = today.withDayOfMonth(1);

                 
        int monthCount = getMonthCheckinCount(userId);

                 
        int continuousDays = getContinuousDays(userId);

                  
        boolean checkedToday = hasCheckedInToday(userId);

                
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED);
        long totalCount = count(wrapper);

                
        int totalPoints = activityPointsService.getUserTotalPoints(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("monthCount", monthCount);
        stats.put("continuousDays", continuousDays);
        stats.put("checkedToday", checkedToday);
        stats.put("totalCount", totalCount);
        stats.put("totalPoints", totalPoints);
        stats.put("businessDate", today);
        stats.put("businessZone", BUSINESS_ZONE.getId());
        stats.put("makeupWindowDays", userGrowthConfig.getCheckin().getMakeupWindowDays());
        stats.put("makeupCostPoints", userGrowthConfig.getCheckin().getMakeupCostPoints());
        stats.put("makeupRewardPoints", userGrowthConfig.getCheckin().getDailyPoints());
        stats.put("makeupAffectsContinuousDays", false);
                                                                                       
                                                                            
        stats.put("dailyRewardPoints", userGrowthConfig.getCheckin().getDailyPoints());
        stats.put("weeklyBonusPoints", userGrowthConfig.getCheckin().getWeeklyBonus());
        stats.put("weeklyVipDays", userGrowthConfig.getCheckin().getWeeklyVipDays());
        stats.put("monthlyBonusPoints", userGrowthConfig.getCheckin().getMonthlyBonus());

        return stats;
    }

       
                  
  
    private UserCheckin getCheckinRecord(Long userId, LocalDate date) {
        LambdaQueryWrapper<UserCheckin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCheckin::getUserId, userId)
                .eq(UserCheckin::getCheckinDate, date)
                .eq(UserCheckin::getDeleted, CommonConstants.NOT_DELETED);
        return getOne(wrapper);
    }
}
