




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.config.CreditConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.UserCreditMapper;
import com.haoran.music.mapper.CreditRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;




@Slf4j
@Service
public class CreditServiceImpl implements CreditService {

    private final UserCreditMapper userCreditMapper;
    private final CreditRecordMapper creditRecordMapper;
    private final UserMapper userMapper;
    private final RedisUtils redisUtils;
    private final CreditConfig creditConfig;
    private final CreatorEligibilityService creatorEligibilityService;

    public CreditServiceImpl(
            UserCreditMapper userCreditMapper,
            CreditRecordMapper creditRecordMapper,
            UserMapper userMapper,
            RedisUtils redisUtils,
            CreditConfig creditConfig,
            CreatorEligibilityService creatorEligibilityService) {
        this.userCreditMapper = userCreditMapper;
        this.creditRecordMapper = creditRecordMapper;
        this.userMapper = userMapper;
        this.redisUtils = redisUtils;
        this.creditConfig = creditConfig;
        this.creatorEligibilityService = creatorEligibilityService;
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer getUserCredit(Long userId) {
        UserCredit credit = userCreditMapper.selectOne(
                new LambdaQueryWrapper<UserCredit>()
                        .eq(UserCredit::getUserId, userId)
        );


        if (ObjectUtils.isEmpty(credit)) {
            credit = getUserCreditEntity(userId);
        }

        return credit != null ? credit.getCreditScore() : 100;
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean initUserCredit(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }


        UserCredit existing = userCreditMapper.selectOne(
                new LambdaQueryWrapper<UserCredit>()
                        .eq(UserCredit::getUserId, userId)
        );

        if (existing != null) {
            return true;
        }

        UserCredit credit = new UserCredit();
        credit.setUserId(userId);
        credit.setCreditScore(100);           
        credit.setCreditLevel("normal");
        credit.setTotalReportCount(0);
        credit.setApprovedReportCount(0);
        credit.setRejectedReportCount(0);

        userCreditMapper.insert(credit);
        syncUserCreditScore(userId, 100);

        log.info("event=user_credit_initialized userId={}", userId);
        return true;
    }




    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addCreditRecord(Long userId, String creditType, Integer score,
                                   String reason, Long operatorId) {

        UserCredit credit = getUserCreditEntity(userId);

        Integer currentScore = credit.getCreditScore();
        Integer newScore = currentScore + score;


        newScore = Math.max(0, Math.min(100, newScore));

        insertCreditRecord(userId, creditType, score, reason, operatorId);


        credit.setCreditScore(newScore);
        credit.setCreditLevel(getCreditLevel(newScore));
        userCreditMapper.updateById(credit);
        syncUserCreditScore(userId, newScore);

        log.info("event=user_credit_updated userId={} creditType={}", userId, creditType);


        if (score < 0 && newScore < creditConfig.getThresholdScore()) {
            handleCreditDeduction(userId, creditType);
        }

        return newScore;
    }




    @Override
    public boolean hasReceivedTodayReward(Long userId, String creditType) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();

        Integer count = creditRecordMapper.countTodayByType(userId, creditType, startOfDay);
        return count != null && count > 0;
    }




    @Override
    public Integer getTodayRewardCount(Long userId, String creditType) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();

        Integer count = creditRecordMapper.countTodayByType(userId, creditType, startOfDay);
        return count != null ? count : 0;
    }




    @Override
    public Map<String, Object> getCreditRecords(Long userId, String creditType,
                                               Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        Page<CreditRecord> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<CreditRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreditRecord::getUserId, userId);


        if ("positive".equalsIgnoreCase(creditType)) {
            wrapper.gt(CreditRecord::getScore, 0);
        } else if ("negative".equalsIgnoreCase(creditType)) {
            wrapper.lt(CreditRecord::getScore, 0);
        } else if (ObjectUtils.isNotEmpty(creditType)) {
            wrapper.eq(CreditRecord::getCreditType, creditType);
        }

        wrapper.orderByDesc(CreditRecord::getCreateTime);

        IPage<CreditRecord> recordPage = creditRecordMapper.selectPage(pageParam, wrapper);


        result.put("records", recordPage.getRecords());
        result.put("total", recordPage.getTotal());                                  
        result.put("current", page);
        result.put("pages", recordPage.getPages());
        result.put("size", size);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateReportCredit(Long userId, Long reportId, Integer score, String reason) {

        UserCredit credit = getUserCreditEntity(userId);

        Integer currentScore = credit.getCreditScore();
        Integer newScore = currentScore + score;


        newScore = Math.max(0, Math.min(100, newScore));


        if (score > 0) {

            credit.setApprovedReportCount(
                (credit.getApprovedReportCount() != null ? credit.getApprovedReportCount() : 0) + 1
            );
        }
        credit.setTotalReportCount(
            (credit.getTotalReportCount() != null ? credit.getTotalReportCount() : 0) + 1
        );

        insertCreditRecord(userId, "report", score, reason, null);


        credit.setCreditScore(newScore);
        credit.setCreditLevel(getCreditLevel(newScore));
        userCreditMapper.updateById(credit);
        syncUserCreditScore(userId, newScore);

        log.info("event=user_report_credit_updated userId={}", userId);

        return newScore;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateRefundCredit(Long userId, Long refundId, Integer score, String reason) {

        UserCredit credit = getUserCreditEntity(userId);

        Integer currentScore = credit.getCreditScore();
        Integer newScore = currentScore + score;


        newScore = Math.max(0, Math.min(100, newScore));

        insertCreditRecord(userId, "refund", score, reason, null);


        credit.setCreditScore(newScore);
        credit.setCreditLevel(getCreditLevel(newScore));
        userCreditMapper.updateById(credit);
        syncUserCreditScore(userId, newScore);

        log.info("event=user_refund_credit_updated userId={}", userId);


        if (score < 0) {
            handleCreditDeduction(userId, "refund");
        }

        return newScore;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer resetAllCredits() {
        final int batchSize = 500;
        long lastCreditId = 0L;
        int count = 0;

        while (true) {
            LambdaQueryWrapper<UserCredit> query = new LambdaQueryWrapper<UserCredit>()
                    .select(UserCredit::getId, UserCredit::getUserId)
                    .gt(UserCredit::getId, lastCreditId)
                    .orderByAsc(UserCredit::getId)
                    .last("LIMIT " + batchSize);
            List<UserCredit> credits = userCreditMapper.selectList(query);
            if (credits.isEmpty()) {
                break;
            }

            List<Long> creditIds = new java.util.ArrayList<>(credits.size());
            Set<Long> userIds = new java.util.LinkedHashSet<>();
            Set<String> cacheKeys = new java.util.LinkedHashSet<>();
            for (UserCredit credit : credits) {
                if (credit.getId() != null) {
                    creditIds.add(credit.getId());
                    lastCreditId = credit.getId();
                }
                if (credit.getUserId() != null) {
                    userIds.add(credit.getUserId());
                    cacheKeys.add(RedisConstants.USER_INFO_PREFIX + credit.getUserId());
                }
            }

            if (!creditIds.isEmpty()) {
                userCreditMapper.update(null, new LambdaUpdateWrapper<UserCredit>()
                        .in(UserCredit::getId, creditIds)
                        .set(UserCredit::getCreditScore, 100)
                        .set(UserCredit::getCreditLevel, "normal"));
            }
            if (!userIds.isEmpty()) {
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .in(User::getId, userIds)
                        .set(User::getCreditScore, 100)
                        .set(User::getUpdateTime, LocalDateTime.now()));
                redisUtils.delete(cacheKeys);
            }
            count += creditIds.size();
        }

        log.info("event=user_credit_reset_completed resetCount={}", count);
        return count;
    }

    @Override
    public String getCreditLevel(Integer credit) {
        if (credit >= 90) return "excellent";
        if (credit >= 80) return "good";
        if (credit >= 70) return "normal";
        if (credit >= 60) return "poor";
        return "bad";
    }

    @Override
    public Boolean isBelowThreshold(Long userId) {
        UserCredit credit = getUserCreditEntity(userId);
        return credit.getCreditScore() < creditConfig.getThresholdScore();
    }

    @Override
    public String getCurrentPeriod() {

        LocalDateTime now = LocalDateTime.now();
        return String.format("%04d%02d", now.getYear(), now.getMonthValue());
    }

    @Override
    public LocalDateTime getNextResetTime() {

        LocalDateTime now = LocalDateTime.now();
        return now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean handleCreditDeduction(Long userId, String creditType) {

        if (!isBelowThreshold(userId)) {
            return false;
        }


        if (creatorEligibilityService.isEligible(userId)) {
            creatorEligibilityService.remove(userId, null,
                    "信用分低于" + creditConfig.getThresholdScore() + "分，自动移除创作者身份");

            log.info("event=creator_eligibility_removed reason=low_credit userId={} creditType={}",
                    userId, creditType);
            return true;
        }

        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean adjustCredit(Long userId, String creditType, Integer score,
                               String reason, Long operatorId) {
        addCreditRecord(userId, creditType, score, "管理员调整: " + reason, operatorId);

        log.info("event=user_credit_admin_adjusted userId={} creditType={} operatorId={}",
                userId, creditType, operatorId);
        return true;
    }

    @Override
    public Map<String, Object> getCreditStatistics() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> statistics = userCreditMapper.selectCreditStatistics();
        result.put("excellent", numberValue(statistics, "excellent"));
        result.put("good", numberValue(statistics, "good"));
        result.put("normal", numberValue(statistics, "normal"));
        result.put("poor", numberValue(statistics, "poor"));
        result.put("bad", numberValue(statistics, "bad"));
        result.put("total", numberValue(statistics, "total"));

        return result;
    }






    private UserCredit getUserCreditEntity(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserCredit credit = userCreditMapper.selectOne(
                new LambdaQueryWrapper<UserCredit>()
                        .eq(UserCredit::getUserId, userId)
        );

        if (credit == null) {
            UserCredit initialCredit = new UserCredit();
            initialCredit.setUserId(userId);
            initialCredit.setCreditScore(100);
            initialCredit.setCreditLevel("normal");
            initialCredit.setTotalReportCount(0);
            initialCredit.setApprovedReportCount(0);
            initialCredit.setRejectedReportCount(0);
            userCreditMapper.insert(initialCredit);
            syncUserCreditScore(userId, 100);
            credit = userCreditMapper.selectOne(
                    new LambdaQueryWrapper<UserCredit>()
                            .eq(UserCredit::getUserId, userId)
            );
        }

        return credit;
    }

    private void insertCreditRecord(Long userId, String creditType, Integer score, String reason, Long operatorId) {
        CreditRecord record = new CreditRecord();
        record.setUserId(userId);
        record.setCreditType(creditType);
        record.setScore(score);
        record.setReason(reason);
        record.setOperatorId(operatorId);
        record.setCreateTime(LocalDateTime.now());
        creditRecordMapper.insert(record);
    }

    private void syncUserCreditScore(Long userId, Integer creditScore) {
        if (ObjectUtils.isEmpty(userId) || creditScore == null) {
            return;
        }
        User user = new User();
        user.setId(userId);
        user.setCreditScore(creditScore);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        redisUtils.delete(RedisConstants.USER_INFO_PREFIX + userId);
    }

    private int numberValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
