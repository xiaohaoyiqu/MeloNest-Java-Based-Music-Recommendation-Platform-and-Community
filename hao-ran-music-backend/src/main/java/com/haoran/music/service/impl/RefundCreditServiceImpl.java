package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.RefundCreditLog;
import com.haoran.music.mapper.RefundCreditLogMapper;
import com.haoran.music.service.RefundCreditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;






@Service
public class RefundCreditServiceImpl extends ServiceImpl<RefundCreditLogMapper, RefundCreditLog> implements RefundCreditService {

    private static final Logger logger = LoggerFactory.getLogger(RefundCreditServiceImpl.class);

    private static final Integer DEFAULT_CREDIT = 100;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer recordRefundCredit(Long userId, Long refundId, Integer score, String reason) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(score)) {
            return null;
        }

        Integer currentCredit = getCurrentRefundCredit(userId);
        Integer afterScore = currentCredit + score;

        RefundCreditLog refundCredit = new RefundCreditLog();
        refundCredit.setUserId(userId);
        refundCredit.setRefundId(refundId);
        refundCredit.setChangeType(score < 0 ? "subtract" : "add");
        refundCredit.setScore(Math.abs(score));
        refundCredit.setReason(reason);
        refundCredit.setAfterScore(afterScore);

        YearMonth currentMonth = YearMonth.now();
        refundCredit.setCreditPeriod(currentMonth.toString());
        refundCredit.setPeriodStartTime(currentMonth.atDay(1).atStartOfDay());
        refundCredit.setPeriodEndTime(currentMonth.atEndOfMonth().atTime(23, 59, 59));

        save(refundCredit);

        logger.info("记录退款信用分: userId={}, refundId={}, score={}, reason={}, afterScore={}",
                userId, refundId, score, reason, afterScore);

        return afterScore;
    }

    @Override
    public Integer getCurrentRefundCredit(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return DEFAULT_CREDIT;
        }

        YearMonth currentMonth = YearMonth.now();
        LambdaQueryWrapper<RefundCreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCreditLog::getUserId, userId)
                .eq(RefundCreditLog::getCreditPeriod, currentMonth.toString())
                .eq(RefundCreditLog::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(RefundCreditLog::getCreateTime)
                .last("LIMIT 1");

        RefundCreditLog lastRecord = getOne(wrapper);
        if (lastRecord != null && ObjectUtils.isNotEmpty(lastRecord.getAfterScore())) {
            return lastRecord.getAfterScore();
        }

        return DEFAULT_CREDIT;
    }

    @Override
    public List<RefundCreditLog> getRefundCreditByPeriod(Long userId, String period) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(period)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RefundCreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCreditLog::getUserId, userId)
                .eq(RefundCreditLog::getCreditPeriod, period)
                .eq(RefundCreditLog::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(RefundCreditLog::getCreateTime);

        return list(wrapper);
    }

    @Override
    public List<RefundCreditLog> getRefundCreditHistory(Long userId, Integer limit) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RefundCreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCreditLog::getUserId, userId)
                .eq(RefundCreditLog::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(RefundCreditLog::getCreateTime);

        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }

        return list(wrapper);
    }

    @Override
    public String getCurrentPeriod() {
        return YearMonth.now().toString();
    }

    @Override
    public LocalDateTime getCurrentPeriodStartTime() {
        return YearMonth.now().atDay(1).atStartOfDay();
    }

    @Override
    public LocalDateTime getCurrentPeriodEndTime() {
        return YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
    }

    @Override
    public Integer getCurrentMonthCreditChange(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return 0;
        }

        String currentPeriod = getCurrentPeriod();
        LambdaQueryWrapper<RefundCreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCreditLog::getUserId, userId)
                .eq(RefundCreditLog::getCreditPeriod, currentPeriod)
                .eq(RefundCreditLog::getDeleted, CommonConstants.NOT_DELETED);

        List<RefundCreditLog> records = list(wrapper);
        int change = 0;
        for (RefundCreditLog record : records) {
            if ("add".equals(record.getChangeType())) {
                change += record.getScore();
            } else if ("subtract".equals(record.getChangeType())) {
                change -= record.getScore();
            }
        }

        return change;
    }

    @Override
    public boolean hasDeductThisMonth(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return false;
        }

        String currentPeriod = getCurrentPeriod();
        LambdaQueryWrapper<RefundCreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCreditLog::getUserId, userId)
                .eq(RefundCreditLog::getCreditPeriod, currentPeriod)
                .eq(RefundCreditLog::getChangeType, "subtract")
                .eq(RefundCreditLog::getDeleted, CommonConstants.NOT_DELETED);

        return count(wrapper) > 0;
    }

    @Override
    public Integer resetRefundCredit(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }

        RefundCreditLog refundCredit = new RefundCreditLog();
        refundCredit.setUserId(userId);
        refundCredit.setChangeType("reset");
        refundCredit.setScore(DEFAULT_CREDIT);
        refundCredit.setReason("系统重置");
        refundCredit.setAfterScore(DEFAULT_CREDIT);

        YearMonth currentMonth = YearMonth.now();
        refundCredit.setCreditPeriod(currentMonth.toString());
        refundCredit.setPeriodStartTime(currentMonth.atDay(1).atStartOfDay());
        refundCredit.setPeriodEndTime(currentMonth.atEndOfMonth().atTime(23, 59, 59));

        save(refundCredit);

        logger.info("重置退款信用分: userId={}, newScore={}", userId, DEFAULT_CREDIT);
        return DEFAULT_CREDIT;
    }

    private String getCreditLevel(Integer credit) {
        if (credit >= 90) return "excellent";
        if (credit >= 75) return "good";
        if (credit >= 60) return "normal";
        if (credit >= 40) return "warning";
        return "danger";
    }
}
