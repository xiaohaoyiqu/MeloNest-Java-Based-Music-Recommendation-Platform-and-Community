package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserViolation;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserViolationMapper;
import com.haoran.music.service.UserViolationService;
import com.haoran.music.service.CreatorService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.SimpleUserClassificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

   
                      
                          
   
@Slf4j
@Service
public class UserViolationServiceImpl extends ServiceImpl<UserViolationMapper, UserViolation>
        implements UserViolationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private CreatorService creatorService;

    @Autowired
    private NotificationService notificationService;

    @Autowired(required = false)
    private SimpleUserClassificationService userClassificationService;

       
           
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordViolation(Long userId, String contentType, Long contentId,
                                String violationType, Integer level, String description) {
        log.info("event=user_violation_record_started userId={}", userId);

        UserViolation violation = new UserViolation();
        violation.setUserId(userId);
        violation.setViolationType(violationType);
        violation.setViolationLevel(level);
        violation.setContentType(contentType);
        violation.setContentId(contentId);
                                      
        if (ObjectUtils.isNotEmpty(description)) {
            SecurityCheckUtil.CheckResult descriptionCheck = SecurityCheckUtil.checkDescription(description);
            if (!descriptionCheck.isSafe()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, descriptionCheck.getMessage());
            }
            violation.setDescription(descriptionCheck.getCleanedValue());
        } else {
            violation.setDescription(description);
        }
        violation.setIsResolved(0);
        save(violation);

                        
        checkAndPenalty(userId, level);

        log.info("event=user_violation_recorded violationId={}", violation.getId());
    }

       
              
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndPenalty(Long userId, Integer currentLevel) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            return;
        }
        if (AdminAccountOperationGuard.isPrivilegedAccount(user)) {
            log.warn("event=user_violation_auto_penalty_skipped reason=privileged_account userId={}", userId);
            return;
        }

                       
        long minorCount = count(new LambdaQueryWrapper<UserViolation>()
                .eq(UserViolation::getUserId, userId)
                .eq(UserViolation::getIsResolved, 0)
                .eq(UserViolation::getViolationLevel, 1));

        long normalCount = count(new LambdaQueryWrapper<UserViolation>()
                .eq(UserViolation::getUserId, userId)
                .eq(UserViolation::getIsResolved, 0)
                .eq(UserViolation::getViolationLevel, 2));

        long seriousCount = count(new LambdaQueryWrapper<UserViolation>()
                .eq(UserViolation::getUserId, userId)
                .eq(UserViolation::getIsResolved, 0)
                .eq(UserViolation::getViolationLevel, 3));

        log.info("event=user_violation_summary_evaluated userId={}", userId);

                 
                      
        long actualNormalCount = normalCount + (minorCount / 3);

                                 
        if (actualNormalCount >= 3 || seriousCount >= 1) {
            downgradeOrBanUser(user, seriousCount >= 3);
        }
    }

       
              
       
    private void downgradeOrBanUser(User user, boolean permanent) {
        Long userId = user.getId();
        log.warn("event=user_violation_penalty_applied userId={}", userId);

        if (permanent) {
                                                                                              
                                                                                              
            if (!UserType.fromCode(user.getUserType()).shouldRestrict()) {
                user.setUserType(UserType.BANNED.getCode());
            }
            user.setStatus(2);
            user.setIsBanned(1);
            user.setBanReason("累计严重违规达到自动封禁阈值");
            user.setBanStartTime(LocalDateTime.now());
            user.setBanEndTime(null);
            userMapper.updateById(user);
            if (userClassificationService != null) {
                userClassificationService.forceLogout(userId);
            }
            notificationService.sendSystemNotificationOnce(
                    userId,
                    "账户限制通知",
                    "账户因累计严重违规达到自动封禁阈值，已被封禁。可通过已绑定的手机或邮箱提交申诉。",
                    null,
                    "account-restriction:auto-ban:" + userId);
            log.warn("event=user_violation_account_restricted userId={}", userId);
        } else {
                      
            if (creatorService != null) {
                try {
                    creatorService.removeCreator(userId, "违规被取消创作者认证");
                    log.info("event=user_violation_creator_eligibility_removed userId={}", userId);
                } catch (Exception e) {
                    log.error("event=user_violation_creator_eligibility_removal_failed userId={}", userId);
                }
            } else {
                log.warn("event=user_violation_creator_eligibility_removal_skipped reason=service_unavailable userId={}",
                        userId);
            }
        }
    }
}
