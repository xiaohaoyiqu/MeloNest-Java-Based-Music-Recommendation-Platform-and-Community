   
                      
   
package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.DataMaskingUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.appeal.AccountRestrictionAppealCodeDTO;
import com.haoran.music.dto.appeal.AccountRestrictionAppealSubmitDTO;
import com.haoran.music.dto.appeal.AppealCreateDTO;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AccountRestrictionAppealService;
import com.haoran.music.service.AppealService;
import com.haoran.music.service.EmailVerificationService;
import com.haoran.music.service.PhoneVerificationService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Locale;

   
                                                                                                       
   
@Service
public class AccountRestrictionAppealServiceImpl implements AccountRestrictionAppealService {

    static final String APPEAL_SCENE = "account_restriction_appeal";
    private static final String CHANNEL_PHONE = "PHONE";
    private static final String CHANNEL_EMAIL = "EMAIL";

    @Resource
    private UserMapper userMapper;

    @Resource
    private PhoneVerificationService phoneVerificationService;

    @Resource
    private EmailVerificationService emailVerificationService;

    @Resource
    private AppealService appealService;

    @Override
    public void requestBoundContactCode(AccountRestrictionAppealCodeDTO dto, String clientIp, String userAgent) {
        ContactClaim claim = parseClaim(dto == null ? null : dto.getChannel(), dto == null ? null : dto.getContact());
        User user = findBoundUser(claim);
        if (!isAppealable(user)) {
            return;
        }
        if (CHANNEL_PHONE.equals(claim.channel)) {
            phoneVerificationService.sendCode(claim.contact, APPEAL_SCENE, clientIp, userAgent);
        } else {
            emailVerificationService.sendCode(claim.contact, APPEAL_SCENE, clientIp, userAgent);
        }
    }

    @Override
    public boolean submitBoundContactAppeal(AccountRestrictionAppealSubmitDTO dto) {
        ContactClaim claim = parseClaim(dto == null ? null : dto.getChannel(), dto == null ? null : dto.getContact());
        User user = findBoundUser(claim);
        if (!isAppealable(user)) {
            return false;
        }

        if (CHANNEL_PHONE.equals(claim.channel)) {
            phoneVerificationService.verifyCode(claim.contact, APPEAL_SCENE, dto.getVerifyCode());
        } else {
            emailVerificationService.verifyCode(claim.contact, APPEAL_SCENE, dto.getVerifyCode());
        }

        AppealCreateDTO appeal = new AppealCreateDTO();
        appeal.setAppealType("BAN");
        appeal.setAppealReason(dto.getAppealReason().trim());
        appealService.createAppeal(user.getId(), appeal);
        return true;
    }

    private User findBoundUser(ContactClaim claim) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0);
        if (CHANNEL_PHONE.equals(claim.channel)) {
            wrapper.eq(User::getPhone, claim.contact);
        } else {
            wrapper.eq(User::getEmail, claim.contact);
        }
        return userMapper.selectOne(wrapper);
    }

    private boolean isAppealable(User user) {
        return user != null && (UserAccountStatusUtil.isBanned(user)
                || UserAccountStatusUtil.isFrozen(user)
                || UserType.BOT.getCode().equals(user.getUserType()));
    }

    private ContactClaim parseClaim(String channelValue, String contactValue) {
        String channel = channelValue == null ? "" : channelValue.trim().toUpperCase(Locale.ROOT);
        String contact = contactValue == null ? "" : contactValue.trim();
        if (CHANNEL_PHONE.equals(channel)) {
            if (!DataMaskingUtil.isValidPhone(contact)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "手机号格式不正确");
            }
            return new ContactClaim(channel, contact);
        }
        if (CHANNEL_EMAIL.equals(channel)) {
            String normalizedEmail = contact.toLowerCase(Locale.ROOT);
            if (!DataMaskingUtil.isValidEmail(normalizedEmail)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "邮箱格式不正确");
            }
            return new ContactClaim(channel, normalizedEmail);
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "验证渠道不正确");
    }

    private static final class ContactClaim {
        private final String channel;
        private final String contact;

        private ContactClaim(String channel, String contact) {
            this.channel = channel;
            this.contact = contact;
        }
    }
}
