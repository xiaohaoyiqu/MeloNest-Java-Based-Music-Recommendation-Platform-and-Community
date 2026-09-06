package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserCredit;
import com.haoran.music.mapper.UserCreditMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserCreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;





@Slf4j
@Service
public class UserCreditServiceImpl extends ServiceImpl<UserCreditMapper, UserCredit> implements UserCreditService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addUserCredit(Long userId, Integer creditScore, String reason) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (creditScore == null || creditScore <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "信用分必须大于0");
        }

        UserCredit userCredit = getUserCreditRecord(userId);

        Integer currentScore = userCredit.getCreditScore() != null
                ? userCredit.getCreditScore()
                : UserAccountPolicyConstants.CREDIT_SCORE_MAX;
        int newScore = currentScore + creditScore;

        if (newScore > UserAccountPolicyConstants.CREDIT_SCORE_MAX) {
            newScore = UserAccountPolicyConstants.CREDIT_SCORE_MAX;
        }

        userCredit.setCreditScore(newScore);
        userCredit.setCreditLevel(calculateCreditLevel(newScore));
        userCredit.setUpdateTime(LocalDateTime.now());

        updateById(userCredit);
        syncUserCreditScore(userId, newScore);

        log.info("增加用户信用分: userId={}, add={}, newScore={}, reason={}",
                 userId, creditScore, newScore, reason);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deductUserCredit(Long userId, Integer creditScore, String reason) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (creditScore == null || creditScore <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "信用分必须大于0");
        }

        UserCredit userCredit = getUserCreditRecord(userId);

        Integer currentScore = userCredit.getCreditScore() != null
                ? userCredit.getCreditScore()
                : UserAccountPolicyConstants.CREDIT_SCORE_MAX;
        int newScore = currentScore - creditScore;

        if (newScore < UserAccountPolicyConstants.CREDIT_SCORE_MIN) {
            newScore = UserAccountPolicyConstants.CREDIT_SCORE_MIN;
        }

        userCredit.setCreditScore(newScore);
        userCredit.setCreditLevel(calculateCreditLevel(newScore));
        userCredit.setUpdateTime(LocalDateTime.now());

        updateById(userCredit);
        syncUserCreditScore(userId, newScore);

        log.info("扣除用户信用分: userId={}, deduct={}, newScore={}, reason={}",
                 userId, creditScore, newScore, reason);

        return true;
    }

    @Override
    public Integer getUserCredit(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }

        UserCredit userCredit = getUserCreditRecord(userId);
        return userCredit.getCreditScore() != null
                ? userCredit.getCreditScore()
                : UserAccountPolicyConstants.CREDIT_SCORE_MAX;
    }




    private UserCredit getUserCreditRecord(Long userId) {
        UserCredit userCredit = getOne(new LambdaQueryWrapper<UserCredit>()
                .eq(UserCredit::getUserId, userId));

        if (userCredit == null) {
            userCredit = new UserCredit();
            userCredit.setUserId(userId);
            userCredit.setCreditScore(UserAccountPolicyConstants.CREDIT_SCORE_MAX);
            userCredit.setCreditLevel("normal");
            userCredit.setTotalReportCount(0);
            userCredit.setApprovedReportCount(0);
            userCredit.setRejectedReportCount(0);
            userCredit.setCreateTime(LocalDateTime.now());
            userCredit.setUpdateTime(LocalDateTime.now());
            save(userCredit);
            syncUserCreditScore(userId, UserAccountPolicyConstants.CREDIT_SCORE_MAX);
        }

        return userCredit;
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

    private String calculateCreditLevel(Integer creditScore) {
        if (creditScore == null) {
            return "normal";
        }

        if (creditScore >= UserAccountPolicyConstants.CREDIT_SCORE_EXCELLENT_MIN) {
            return "excellent";
        } else if (creditScore >= UserAccountPolicyConstants.CREDIT_SCORE_GOOD_MIN) {
            return "good";
        } else if (creditScore >= UserAccountPolicyConstants.CREDIT_SCORE_NORMAL_MIN) {
            return "normal";
        } else if (creditScore >= UserAccountPolicyConstants.CREDIT_SCORE_OBSERVE_MIN) {
            return "poor";
        } else {
            return "bad";
        }
    }
}
