




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.entity.UserPrivate;
import com.haoran.music.mapper.UserPrivateMapper;
import com.haoran.music.common.util.DataEncryptionUtil;
import com.haoran.music.service.UserPrivateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;





@Service
public class UserPrivateServiceImpl implements UserPrivateService {

    private static final Logger log = LoggerFactory.getLogger(UserPrivateServiceImpl.class);

    private final UserPrivateMapper userPrivateMapper;

    public UserPrivateServiceImpl(UserPrivateMapper userPrivateMapper) {
        this.userPrivateMapper = userPrivateMapper;
    }

    @Override
    public UserPrivateService.UserPrivateDTO getUserPrivateInfo(Long userId) {
        if (userId == null) {
            return null;
        }

        UserPrivate userPrivate = userPrivateMapper.selectOne(
                new LambdaQueryWrapper<UserPrivate>()
                        .eq(UserPrivate::getUserId, userId)
        );

        if (userPrivate == null) {
            return new UserPrivateService.UserPrivateDTO();
        }


        UserPrivateService.UserPrivateDTO dto = new UserPrivateService.UserPrivateDTO();
        dto.setRealName(decryptBytes(userPrivate.getRealName()));
        dto.setIdCard(decryptBytes(userPrivate.getIdCard()));
        dto.setPhone(decryptBytes(userPrivate.getPhoneEncrypted()));
        dto.setEmail(decryptBytes(userPrivate.getEmailEncrypted()));
        dto.setProvinceCode(userPrivate.getProvinceCode());
        dto.setCityCode(userPrivate.getCityCode());
        dto.setDistrictCode(userPrivate.getDistrictCode());
        dto.setAddressDetail(decryptBytes(userPrivate.getAddressDetail()));
        dto.setRealNameVerified(Integer.valueOf(1).equals(userPrivate.getRealNameVerified()));
        dto.setBankName(decryptAES(userPrivate.getBankName()));
        dto.setBankAccount(decryptAES(userPrivate.getBankAccount()));

        return dto;
    }

    @Override
    public void saveUserPrivateInfo(Long userId, UserPrivateService.UserPrivateDTO dto) {
        if (userId == null || dto == null) {
            throw new IllegalArgumentException("用户ID和DTO不能为空");
        }


        UserPrivate existing = userPrivateMapper.selectOne(
                new LambdaQueryWrapper<UserPrivate>()
                        .eq(UserPrivate::getUserId, userId)
        );

        UserPrivate userPrivate;
        if (existing != null) {
            userPrivate = existing;
        } else {
            userPrivate = new UserPrivate();
            userPrivate.setUserId(userId);
        }


        if (dto.getRealName() != null) {
            userPrivate.setRealName(encryptAES(dto.getRealName()));
        }
        if (dto.getIdCard() != null) {
            userPrivate.setIdCard(encryptSM4(dto.getIdCard()));
        }
        if (dto.getPhone() != null) {
            userPrivate.setPhoneEncrypted(encryptAES(dto.getPhone()));
        }
        if (dto.getEmail() != null) {
            userPrivate.setEmailEncrypted(encryptAES(dto.getEmail()));
        }
        if (dto.getAddressDetail() != null) {
            userPrivate.setAddressDetail(encryptAES(dto.getAddressDetail()));
        }
        if (dto.getBankName() != null) {
            userPrivate.setBankName(encryptAES(dto.getBankName()));
        }
        if (dto.getBankAccount() != null) {
            userPrivate.setBankAccount(encryptAES(dto.getBankAccount()));
        }


        userPrivate.setProvinceCode(dto.getProvinceCode());
        userPrivate.setCityCode(dto.getCityCode());
        userPrivate.setDistrictCode(dto.getDistrictCode());

        if (existing != null) {
            userPrivateMapper.updateById(userPrivate);
            log.info("[UserPrivate] 更新用户 {} 的隐私信息", userId);
        } else {
            userPrivateMapper.insert(userPrivate);
            log.info("[UserPrivate] 新增用户 {} 的隐私信息", userId);
        }
    }

    @Override
    public void saveRealName(Long userId, String realName) {
        if (userId == null || realName == null || realName.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID和真实姓名不能为空");
        }

        saveOrUpdateField(userId, "realName", encryptAES(realName));
    }

    @Override
    public void saveIdCard(Long userId, String idCard) {
        if (userId == null || idCard == null || idCard.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID和身份证号不能为空");
        }


        if (!isValidIdCard(idCard)) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }

        saveOrUpdateField(userId, "idCard", encryptSM4(idCard));
    }

    @Override
    public void savePhone(Long userId, String phone) {
        if (userId == null || phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID和手机号不能为空");
        }


        if (!isValidPhone(phone)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        saveOrUpdateField(userId, "phoneEncrypted", encryptAES(phone));
    }

    @Override
    public String getPhone(Long userId) {
        UserPrivate userPrivate = getUserPrivate(userId);
        if (userPrivate == null || userPrivate.getPhoneEncrypted() == null) {
            return null;
        }
        return decryptBytes(userPrivate.getPhoneEncrypted());
    }

    @Override
    public String getMaskedPhone(Long userId) {
        String phone = getPhone(userId);
        if (phone == null) {
            return null;
        }
        return DataEncryptionUtil.maskPhone(phone);
    }

    @Override
    public String getIdCard(Long userId) {
        UserPrivate userPrivate = getUserPrivate(userId);
        if (userPrivate == null || userPrivate.getIdCard() == null) {
            return null;
        }
        return decryptBytes(userPrivate.getIdCard());
    }

    @Override
    public boolean verifyRealName(Long userId, String realName, String idCard) {
        UserPrivateService.UserPrivateDTO stored = getUserPrivateInfo(userId);
        if (stored == null) {
            return false;
        }

        boolean nameMatch = realName.equals(stored.getRealName());
        boolean idCardMatch = idCard.equals(stored.getIdCard());

        if (nameMatch && idCardMatch) {

            setRealNameVerified(userId, true, "idcard");
            return true;
        }

        return false;
    }

    @Override
    public void setRealNameVerified(Long userId, boolean verified, String verifyMethod) {
        UserPrivate userPrivate = getUserPrivate(userId);
        if (userPrivate == null) {
            userPrivate = new UserPrivate();
            userPrivate.setUserId(userId);
        }

        userPrivate.setRealNameVerified(verified ? 1 : 0);
        userPrivate.setVerifyMethod(verifyMethod);
        userPrivate.setVerifyTime(LocalDateTime.now());

        if (userPrivate.getId() != null) {
            userPrivateMapper.updateById(userPrivate);
        } else {
            userPrivateMapper.insert(userPrivate);
        }

        log.info("[UserPrivate] 用户 {} 实名认证状态更新为: {}, 方式: {}", userId, verified, verifyMethod);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserPrivate(Long userId) {
        if (userId == null) {
            return;
        }

        UserPrivate userPrivate = userPrivateMapper.selectOne(
                new LambdaQueryWrapper<UserPrivate>()
                        .eq(UserPrivate::getUserId, userId)
        );

        if (userPrivate != null) {
            if (userPrivateMapper.scrubAndDeleteSensitiveProfile(userId) != 1) {
                throw new IllegalStateException("用户敏感资料清除失败");
            }
            log.info("[UserPrivate] 删除用户 {} 的隐私信息", userId);
        }
        userPrivateMapper.deletePrivacySettingsByUserId(userId);
    }




    private UserPrivate getUserPrivate(Long userId) {
        if (userId == null) {
            return null;
        }

        return userPrivateMapper.selectOne(
                new LambdaQueryWrapper<UserPrivate>()
                        .eq(UserPrivate::getUserId, userId)
        );
    }




    private void saveOrUpdateField(Long userId, String fieldName, byte[] encryptedValue) {
        UserPrivate userPrivate = getUserPrivate(userId);
        boolean isNew = false;

        if (userPrivate == null) {
            userPrivate = new UserPrivate();
            userPrivate.setUserId(userId);
            isNew = true;
        }

        switch (fieldName) {
            case "realName":
                userPrivate.setRealName(encryptedValue);
                break;
            case "idCard":
                userPrivate.setIdCard(encryptedValue);
                break;
            case "phoneEncrypted":
                userPrivate.setPhoneEncrypted(encryptedValue);
                break;
            default:
                throw new IllegalArgumentException("不支持的字段: " + fieldName);
        }

        if (isNew) {
            userPrivateMapper.insert(userPrivate);
        } else {
            userPrivateMapper.updateById(userPrivate);
        }
    }




    private byte[] encryptAES(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        String encrypted = DataEncryptionUtil.encryptAES(plainText);
        return encrypted.getBytes(StandardCharsets.UTF_8);
    }




    private String decryptAES(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) {
            return null;
        }
        String cipherText = new String(encrypted, StandardCharsets.UTF_8);
        return DataEncryptionUtil.decryptAES(cipherText);
    }




    private byte[] encryptSM4(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        String encrypted = DataEncryptionUtil.encryptAES(plainText);
        return encrypted.getBytes(StandardCharsets.UTF_8);
    }




    private String decryptSM4(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) {
            return null;
        }
        String cipherText = new String(encrypted, StandardCharsets.UTF_8);
        return DataEncryptionUtil.decryptAES(cipherText);
    }




    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }




    private boolean isValidIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return false;
        }

        return idCard.matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$") ||
                idCard.matches("^[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}$");
    }




    private String decryptBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        return decryptAES(data);
    }
}
