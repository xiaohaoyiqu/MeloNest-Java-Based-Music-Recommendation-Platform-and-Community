package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AuditLogService;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.VerifiedService;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.user.PublicUserVO;
import com.haoran.music.vo.user.VerifiedInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

   
                      
                           
   
@Slf4j
@Service
public class VerifiedServiceImpl implements VerifiedService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private CreatorEligibilityService creatorEligibilityService;

    @Resource
    private AuditLogService auditLogService;

    @Resource
    private UserVipService userVipService;

       
             
       
    private static final Map<String, VerifiedTypeInfo> VERIFIED_TYPE_MAP = new HashMap<>();

       
             
       
    private static final Map<String, VerifiedLevelInfo> VERIFIED_LEVEL_MAP = new HashMap<>();

    static {
                                     
        VERIFIED_TYPE_MAP.put("individual", new VerifiedTypeInfo("个人音乐人", "icon-user", "#409eff"));
        VERIFIED_TYPE_MAP.put("band", new VerifiedTypeInfo("乐队", "icon-microphone", "#67c23a"));
        VERIFIED_TYPE_MAP.put("label", new VerifiedTypeInfo("唱片公司", "icon-office-building", "#e6a23c"));

                                   
        VERIFIED_LEVEL_MAP.put("normal", new VerifiedLevelInfo("普通认证", 1));
        VERIFIED_LEVEL_MAP.put("premium", new VerifiedLevelInfo("优质认证", 2));
        VERIFIED_LEVEL_MAP.put("gold", new VerifiedLevelInfo("金牌认证", 3));
    }

    @Override
    public VerifiedInfoVO getUserVerifiedInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user) || !UserAccountStatusUtil.canExposePublicContent(user)) {
            return createEmptyVerifiedInfo();
        }

        VerifiedInfoVO vo = new VerifiedInfoVO();
        vo.setIsVerified(user.getIsOfficial() != null && user.getIsOfficial() == 1);

        if (vo.getIsVerified()) {
                                    
            if (user.getCreatorType() != null) {
                VerifiedTypeInfo typeInfo = VERIFIED_TYPE_MAP.get(user.getCreatorType());
                if (typeInfo != null) {
                    vo.setVerifiedType(user.getCreatorType());
                    vo.setVerifiedTypeName(typeInfo.name);
                    vo.setVerifiedIcon(typeInfo.icon);
                    vo.setVerifiedColor(typeInfo.color);
                }
            }

                      
            vo.setVerifiedLevel("normal");
            VerifiedLevelInfo levelInfo = VERIFIED_LEVEL_MAP.get("normal");
            if (levelInfo != null) {
                vo.setVerifiedLevelName(levelInfo.name);
            }

                          
            vo.setVerifiedReason(user.getCreatorNote());
            vo.setVerifiedTime(user.getCreatorApplyTime());
        }

        return vo;
    }

    @Override
    public PageResult<PublicUserVO> getVerifiedList(String type, String level, Integer page, Integer size) {
        if (!ObjectUtils.isEmpty(type) && !VERIFIED_TYPE_MAP.containsKey(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "认证类型不合法");
        }
        if (!ObjectUtils.isEmpty(level) && !"normal".equals(level)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前仅支持普通认证等级");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 20 : Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getIsOfficial, 1)
                .eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.isNull(User::getIsBanned).or().eq(User::getIsBanned, CommonConstants.NO))
                .and(w -> w.isNull(User::getUserType).or().in(User::getUserType, UserType.nonRestrictedCodes()));

        if (!ObjectUtils.isEmpty(type)) {
            wrapper.eq(User::getCreatorType, type);
        }

                                

        wrapper.orderByDesc(User::getCreatorApplyTime);

        Page<User> userPage = userMapper.selectPage(new Page<>(safePage, safeSize), wrapper);

        Map<Long, LocalDateTime> vipExpirations = userVipService.getActiveVipExpirations(
                userPage.getRecords().stream().map(User::getId).collect(java.util.stream.Collectors.toList()));
        PageResult<PublicUserVO> result = new PageResult<>();
        result.setRecords(convertToPublicVOList(userPage.getRecords(), vipExpirations));
        result.setTotal(userPage.getTotal());
        result.setCurrent(userPage.getCurrent());
        result.setSize(userPage.getSize());
        result.setPages(userPage.getPages());

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewVerified(Long creatorId, Boolean approved, String verifiedType, String verifiedLevel,
                               String reason, Long operatorId) {
        requireReviewParameters(creatorId, approved, verifiedType, verifiedLevel, reason, operatorId);
        reason = requireReason(reason);
        User user = userMapper.selectById(creatorId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        if (approved) {
            creatorEligibilityService.requireEligible(creatorId, "授予官方创作者认证");
            user.setIsOfficial(1);
            user.setCreatorType(verifiedType);
            user.setCreatorNote(reason);
            user.setCreatorApplyTime(LocalDateTime.now());
        } else {
            user.setIsOfficial(0);
        }

        userMapper.updateById(user);
        auditLogService.logAudit(creatorId, "official_verification", "review",
                approved ? "unverified" : "verified", approved ? "verified" : "unverified",
                operatorId, null, reason, null, null, null, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVerifiedInfo(Long userId, Map<String, Object> params, Long operatorId) {
        if (userId == null || operatorId == null || params == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "认证更新参数不能为空");
        }
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!Integer.valueOf(1).equals(user.getIsOfficial())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户尚未通过官方认证");
        }

        if (params.containsKey("verifiedType")) {
            Object typeValue = params.get("verifiedType");
            if (!(typeValue instanceof String) || !VERIFIED_TYPE_MAP.containsKey(typeValue)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "认证类型不合法");
            }
            user.setCreatorType((String) typeValue);
        }
        if (params.containsKey("verifiedReason")) {
            Object reasonValue = params.get("verifiedReason");
            if (!(reasonValue instanceof String)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "认证说明格式不合法");
            }
            user.setCreatorNote(requireReason((String) reasonValue));
        }

        userMapper.updateById(user);
        auditLogService.logAudit(userId, "official_verification", "update",
                "verified", "verified", operatorId, null, "更新官方认证资料",
                null, null, null, true);
    }

       
               
       
    private VerifiedInfoVO createEmptyVerifiedInfo() {
        VerifiedInfoVO vo = new VerifiedInfoVO();
        vo.setIsVerified(false);
        return vo;
    }

       
              
       
    private List<PublicUserVO> convertToPublicVOList(List<User> users,
                                                     Map<Long, LocalDateTime> vipExpirations) {
        List<PublicUserVO> result = new ArrayList<>();
        for (User user : users) {
            PublicUserVO vo = new PublicUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setSignature(user.getIntroduction());
            vo.setStatus(user.getStatus());
            vo.setFansCount(user.getFansCount());
            vo.setFollowingCount(user.getFollowingCount());
            vo.setIsCreator(user.getIsCreator());
            vo.setCreatorStatus(user.getCreatorStatus());
            vo.setIsVip(vipExpirations.containsKey(user.getId()));
            vo.setVerifiedInfo(toVerifiedInfo(user));

            result.add(vo);
        }
        return result;
    }

    private VerifiedInfoVO toVerifiedInfo(User user) {
        VerifiedInfoVO vo = new VerifiedInfoVO();
        vo.setIsVerified(Integer.valueOf(1).equals(user.getIsOfficial()));
        if (!vo.getIsVerified()) {
            return vo;
        }
        VerifiedTypeInfo typeInfo = VERIFIED_TYPE_MAP.get(user.getCreatorType());
        if (typeInfo != null) {
            vo.setVerifiedType(user.getCreatorType());
            vo.setVerifiedTypeName(typeInfo.name);
            vo.setVerifiedIcon(typeInfo.icon);
            vo.setVerifiedColor(typeInfo.color);
        }
        vo.setVerifiedLevel("normal");
        vo.setVerifiedLevelName(VERIFIED_LEVEL_MAP.get("normal").name);
        vo.setVerifiedReason(user.getCreatorNote());
        vo.setVerifiedTime(user.getCreatorApplyTime());
        return vo;
    }

    private void requireReviewParameters(Long creatorId, Boolean approved, String verifiedType,
                                         String verifiedLevel, String reason, Long operatorId) {
        if (creatorId == null || approved == null || operatorId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "认证审核参数不能为空");
        }
        if (!VERIFIED_TYPE_MAP.containsKey(verifiedType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "认证类型不合法");
        }
        if (!"normal".equals(verifiedLevel)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前仅支持普通认证等级");
        }
        requireReason(reason);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2 || reason.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "认证说明长度必须为2到500字");
        }
        for (int i = 0; i < reason.length(); i++) {
            char ch = reason.charAt(i);
            if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "认证说明包含非法控制字符");
            }
        }
        return reason.trim();
    }

       
             
       
    private static class VerifiedTypeInfo {
        String name;
        String icon;
        String color;

        VerifiedTypeInfo(String name, String icon, String color) {
            this.name = name;
            this.icon = icon;
            this.color = color;
        }
    }

       
             
       
    private static class VerifiedLevelInfo {
        String name;
        Integer level;

        VerifiedLevelInfo(String name, Integer level) {
            this.name = name;
            this.level = level;
        }
    }
}
