   
                      
                        
  
                                                   
                                     
                         
  
                                        
                                 
                    
                          
                                                               
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.DataMaskingUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.CreatorApplyMapper;
import com.haoran.music.mapper.CreatorEarningsMapper;
import com.haoran.music.mapper.ExternalCreatorApplyMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserFollowMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.CreatorEligibilityService;
import com.haoran.music.service.CreatorService;
import com.haoran.music.service.UserPrivateService;
import com.haoran.music.vo.CreatorApplyPrivateVO;
import com.haoran.music.vo.CreatorApplyPublicVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

   
           
                   
   
@Slf4j
@Service
public class CreatorServiceImpl implements CreatorService {

    private static final int CREATOR_MIN_REGISTER_DAYS = 30;
    private static final int CREATOR_MIN_PUBLISHED_WORKS = 5;

    @Resource
    private CreatorApplyMapper creatorApplyMapper;

    @Resource
    private ExternalCreatorApplyMapper externalCreatorApplyMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CreatorEligibilityService creatorEligibilityService;

    @Resource
    private UserPrivateService userPrivateService;

    @Resource
    private CreatorEarningsMapper creatorEarningsMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private UserFollowMapper userFollowMapper;

       
                         
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyCreator(Long userId, String realName, String idCardNo,
                                            String idCardUrl, String phone, String email,
                                            String applyReason, String worksSample) {
               
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException("用户ID不能为空");
        }
        if (ObjectUtils.isEmpty(realName) || realName.trim().isEmpty()) {
            throw new BusinessException("请输入真实姓名");
        }
        if (ObjectUtils.isEmpty(phone) || !DataMaskingUtil.isValidPhone(phone)) {
            throw new BusinessException("手机号格式不正确");
        }
        if (ObjectUtils.isNotEmpty(email) && !DataMaskingUtil.isValidEmail(email)) {
            throw new BusinessException("邮箱格式不正确");
        }

                   
        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "申请创作者");
        validateCreatorApplication(realName, idCardNo, idCardUrl, email, applyReason, worksSample);

                     
        if (user.getIsCreator() != null && user.getIsCreator() == 1) {
            throw new BusinessException("您已经是创作者，无需重复申请");
        }

                      
        LambdaQueryWrapper<CreatorApply> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(CreatorApply::getUserId, userId)
                .eq(CreatorApply::getStatus, "pending");
        Long pendingCount = creatorApplyMapper.selectCount(pendingWrapper);
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException("您有待审核的申请，请勿重复提交");
        }

                                                                                
                                                                          
        long registerDays = user.getCreateTime() == null ? 0L
                : java.time.temporal.ChronoUnit.DAYS.between(user.getCreateTime(), LocalDateTime.now());
        Long publishedWorkCount = songMapper.selectCount(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getUploaderId, userId)
                        .eq(Song::getStatus, 1)
        );
        long publishedWorkCountValue = publishedWorkCount == null ? 0L : publishedWorkCount;
        if (registerDays < CREATOR_MIN_REGISTER_DAYS
                || publishedWorkCountValue < CREATOR_MIN_PUBLISHED_WORKS) {
            throw new BusinessException(String.format(
                    "暂未满足申请条件：需注册满%d天且发布至少%d个作品",
                    CREATOR_MIN_REGISTER_DAYS, CREATOR_MIN_PUBLISHED_WORKS));
        }

                  
        String idCardMasked = null;
        if (ObjectUtils.isNotEmpty(idCardNo)) {
            if (!DataMaskingUtil.isValidIdCard(idCardNo)) {
                throw new BusinessException("身份证号格式不正确");
            }
            idCardMasked = DataMaskingUtil.maskIdCard(idCardNo);
        }

                 
        CreatorApply apply = new CreatorApply();
        apply.setUserId(userId);
        apply.setUsername(user.getUsername());
        apply.setArtistName(ObjectUtils.isNotEmpty(user.getNickname()) ? user.getNickname() : user.getUsername());
        apply.setRealName(realName);
                                                                              
                                                                               
                                       
        apply.setIdCardNo("");
        apply.setIdCardMasked(idCardMasked);
        apply.setIdCardUrl(idCardUrl);
        apply.setPhone(DataMaskingUtil.maskPhone(phone));
        apply.setEmail(email);
        apply.setApplyReason(applyReason);
        apply.setWorksSample(worksSample);
        apply.setStatus("pending");
        apply.setCreateTime(LocalDateTime.now());

        creatorApplyMapper.insert(apply);

        userPrivateService.saveRealName(userId, realName.trim());
        userPrivateService.savePhone(userId, phone.trim());
        if (ObjectUtils.isNotEmpty(idCardNo)) {
            userPrivateService.saveIdCard(userId, idCardNo.trim().toUpperCase(Locale.ROOT));
        }

        log.info("event=creator_application_submitted userId={}", userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("applicationId", String.valueOf(apply.getId()));
        result.put("status", "pending");
        result.put("message", "申请已提交，请等待审核");

        return result;
    }

       
              
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewCreatorApply(Long applicationId, Long reviewerId,
                                                  Boolean approved, String reviewReason,
                                                  String creatorType, BigDecimal feeRate) {
               
        if (ObjectUtils.isEmpty(applicationId)) {
            throw new BusinessException("申请ID不能为空");
        }
        if (ObjectUtils.isEmpty(reviewerId)) {
            throw new BusinessException("审核人ID不能为空");
        }
        if (ObjectUtils.isEmpty(approved)) {
            throw new BusinessException("审核结果不能为空");
        }
        String normalizedCreatorType = creatorType == null
                ? "independent" : creatorType.trim().toLowerCase(Locale.ROOT);
        if (!new HashSet<>(Arrays.asList("independent", "signed")).contains(normalizedCreatorType)) {
            throw new BusinessException("不支持的创作者类型");
        }
        if (feeRate != null && (feeRate.compareTo(BigDecimal.ZERO) < 0
                || feeRate.compareTo(BigDecimal.ONE) > 0)) {
            throw new BusinessException("创作者费率必须在0到1之间");
        }
        if (reviewReason != null && reviewReason.length() > 1000) {
            throw new BusinessException("审核原因不能超过1000字");
        }

                 
        CreatorApply apply = creatorApplyMapper.selectById(applicationId);
        if (ObjectUtils.isEmpty(apply)) {
            throw new BusinessException("申请记录不存在");
        }

                 
        if (!"pending".equals(apply.getStatus())) {
            throw new BusinessException("该申请已被审核");
        }

                
        User reviewer = userMapper.selectById(reviewerId);
        if (ObjectUtils.isEmpty(reviewer)) {
            throw new BusinessException("审核人不存在");
        }

        String reviewStatus = approved ? "approved" : "rejected";
        int updated = creatorApplyMapper.update(null, new LambdaUpdateWrapper<CreatorApply>()
                .eq(CreatorApply::getId, applicationId)
                .eq(CreatorApply::getStatus, "pending")
                .set(CreatorApply::getStatus, reviewStatus)
                .set(CreatorApply::getReviewTime, LocalDateTime.now())
                .set(CreatorApply::getReviewReason, reviewReason)
                .set(CreatorApply::getReviewerId, reviewerId));
        if (updated != 1) {
            throw new BusinessException("该申请已被其他审核人处理");
        }
        apply.setStatus(reviewStatus);

        Map<String, Object> result = new HashMap<>();

        if (approved) {
            creatorEligibilityService.activate(apply.getUserId(), normalizedCreatorType, reviewerId,
                    ObjectUtils.isEmpty(reviewReason) ? "creator application approved" : reviewReason,
                    feeRate);
            log.info("event=creator_application_reviewed userId={} reviewerId={} approved=true",
                    apply.getUserId(), reviewerId);

            result.put("success", true);
            result.put("action", "approved");
            result.put("creatorId", apply.getUserId());
            result.put("message", "审核通过");
        } else {
                   
            log.info("event=creator_application_reviewed userId={} reviewerId={} approved=false",
                    apply.getUserId(), reviewerId);
            result.put("success", true);
            result.put("action", "rejected");
            result.put("message", "审核拒绝");
        }

        return result;
    }

       
                
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> externalCreatorApply(String realName, String idCardNo, String idCardUrl,
                                                     String phone, String email,
                                                     String creatorName, String externalPlatform,
                                                     String externalHomepage, String worksDescription,
                                                     String cooperationType, BigDecimal expectedFeeRate,
                                                     String attachmentUrls) {
               
        if (ObjectUtils.isEmpty(realName) || realName.trim().isEmpty()) {
            throw new BusinessException("请输入真实姓名");
        }
        if (ObjectUtils.isEmpty(creatorName) || creatorName.trim().isEmpty()) {
            throw new BusinessException("请输入创作者名称");
        }

                  
        String idCardMasked = null;
        if (ObjectUtils.isNotEmpty(idCardNo)) {
            if (!DataMaskingUtil.isValidIdCard(idCardNo)) {
                throw new BusinessException("身份证号格式不正确");
            }
            idCardMasked = DataMaskingUtil.maskIdCard(idCardNo);
        }

                   
        ExternalCreatorApply apply = new ExternalCreatorApply();
        apply.setRealName(realName);
        apply.setIdCardNo(idCardNo);
        apply.setIdCardMasked(idCardMasked);
        apply.setIdCardUrl(idCardUrl);
        apply.setPhone(DataMaskingUtil.maskPhone(phone));
        apply.setEmail(email);
        apply.setCreatorName(creatorName);
        apply.setExternalPlatform(externalPlatform);
        apply.setExternalHomepage(externalHomepage);
        apply.setWorksDescription(worksDescription);
        apply.setCooperationType(cooperationType);
        apply.setExpectedFeeRate(expectedFeeRate);
        apply.setAttachmentUrls(attachmentUrls);
        apply.setStatus("pending");
        apply.setCreateTime(LocalDateTime.now());

        externalCreatorApplyMapper.insert(apply);

        log.info("外部创作者申请: creatorName={}, platform={}", creatorName, externalPlatform);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("applicationId", String.valueOf(apply.getId()));
        result.put("status", "pending");
        result.put("message", "申请已提交，请等待审核");

        return result;
    }

       
              
       
    @Override
    public Map<String, Object> getCreatorInfo(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            throw new BusinessException("创作者ID不能为空");
        }

        User user = userMapper.selectById(creatorId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!Integer.valueOf(1).equals(user.getIsCreator())) {
            throw new BusinessException(404, "用户当前不是有效创作者");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("userName", ObjectUtils.isNotEmpty(user.getNickname()) ? user.getNickname() : user.getUsername());
        result.put("userAvatar", user.getAvatar());
                                    
        result.put("signature", user.getIntroduction());
        result.put("isCreator", user.getIsCreator() != null && user.getIsCreator() == 1);
        result.put("creatorStatus", user.getCreatorStatus());
        result.put("status", "active".equals(user.getCreatorStatus()) ? "approved" : user.getCreatorStatus());
        result.put("creatorType", user.getCreatorType());
        result.put("feeRate", user.getFeeRate() != null ? user.getFeeRate() : new BigDecimal("0.98"));
        result.put("creatorApplyTime", user.getCreatorApplyTime());
        result.put("creatorNote", user.getCreatorNote());

                                       
        LambdaQueryWrapper<CreatorEarnings> earningsWrapper = new LambdaQueryWrapper<>();
        earningsWrapper.eq(CreatorEarnings::getUserId, creatorId);

        List<CreatorEarnings> earningsList = creatorEarningsMapper.selectList(earningsWrapper);

               
        BigDecimal totalEarnings = BigDecimal.ZERO;
        if (ObjectUtils.isNotEmpty(earningsList)) {
            for (CreatorEarnings earnings : earningsList) {
                if (earnings.getEarningsAmount() != null) {
                    totalEarnings = totalEarnings.add(
                        new BigDecimal(earnings.getEarningsAmount())
                            .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP)
                    );
                }
            }
        }

                            
        if (user.getTotalEarnings() != null) {
            result.put("totalEarnings", user.getTotalEarnings());
        } else {
            result.put("totalEarnings", totalEarnings);
        }

        result.put("withdrawnAmount", user.getWithdrawnEarnings() != null ? user.getWithdrawnEarnings() : BigDecimal.ZERO);
        result.put("pendingAmount", user.getPendingEarnings() != null ? user.getPendingEarnings() : BigDecimal.ZERO);

        return result;
    }

       
                
       
    @Override
    public Map<String, Object> getMyCreatorInfo(Long userId) {
        return getCreatorInfo(userId);
    }

       
                
       
    @Override
    public Map<String, Object> getCreatorEarnings(Long creatorId) {
        if (ObjectUtils.isEmpty(creatorId)) {
            throw new BusinessException("创作者ID不能为空");
        }

                 
        User user = userMapper.selectById(creatorId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }

                                
        LambdaQueryWrapper<CreatorEarnings> earningsWrapper = new LambdaQueryWrapper<>();
        earningsWrapper.eq(CreatorEarnings::getUserId, creatorId);

        List<CreatorEarnings> earningsList = creatorEarningsMapper.selectList(earningsWrapper);

        BigDecimal totalEarnings = BigDecimal.ZERO;
        if (ObjectUtils.isNotEmpty(earningsList)) {
            for (CreatorEarnings earnings : earningsList) {
                if (earnings.getEarningsAmount() != null) {
                    totalEarnings = totalEarnings.add(
                        new BigDecimal(earnings.getEarningsAmount())
                            .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP)
                    );
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalEarnings", user.getTotalEarnings() != null ? user.getTotalEarnings() : totalEarnings);
        result.put("withdrawnAmount", user.getWithdrawnEarnings() != null ? user.getWithdrawnEarnings() : BigDecimal.ZERO);
        result.put("pendingAmount", user.getPendingEarnings() != null ? user.getPendingEarnings() : BigDecimal.ZERO);
                                             
        result.put("lastWithdrawTime", null);

        return result;
    }

       
                   
       
    @Override
    public Map<String, Object> getCreatorList(String status, String creatorType, Integer page, Integer size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getIsCreator, 1);

        if (ObjectUtils.isNotEmpty(status)) {
            wrapper.eq(User::getCreatorStatus, status);
        }
        if (ObjectUtils.isNotEmpty(creatorType)) {
            wrapper.eq(User::getCreatorType, creatorType);
        }

        wrapper.orderByDesc(User::getCreatorApplyTime);

        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", convertToCreatorInfoList(userPage.getRecords()));
        result.put("total", userPage.getTotal());
        result.put("current", userPage.getCurrent());
        result.put("size", userPage.getSize());
        result.put("pages", userPage.getPages());

        return result;
    }

       
                             
       
    @Override
    public Map<String, Object> getPendingApplications(Integer page, Integer size) {
        LambdaQueryWrapper<CreatorApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorApply::getStatus, "pending")
                .orderByDesc(CreatorApply::getCreateTime);

        Page<CreatorApply> applyPage = creatorApplyMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", convertToPrivateVOList(applyPage.getRecords()));
        result.put("total", applyPage.getTotal());
        result.put("current", applyPage.getCurrent());
        result.put("size", applyPage.getSize());
        result.put("pages", applyPage.getPages());

        return result;
    }

       
                            
       
    @Override
    public Map<String, Object> getPendingApplicationsPublic(Integer page, Integer size) {
        LambdaQueryWrapper<CreatorApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorApply::getStatus, "pending")
                .orderByDesc(CreatorApply::getCreateTime);

        Page<CreatorApply> applyPage = creatorApplyMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", convertToPublicVOList(applyPage.getRecords()));
        result.put("total", applyPage.getTotal());
        result.put("current", applyPage.getCurrent());
        result.put("size", applyPage.getSize());
        result.put("pages", applyPage.getPages());

        return result;
    }

       
                       
       
    @Override
    public CreatorApplyPrivateVO getApplicationDetail(Long applicationId) {
        if (ObjectUtils.isEmpty(applicationId)) {
            throw new BusinessException("申请ID不能为空");
        }

        CreatorApply apply = creatorApplyMapper.selectById(applicationId);
        if (ObjectUtils.isEmpty(apply)) {
            throw new BusinessException("申请记录不存在");
        }

        return convertToPrivateVO(apply);
    }

       
                      
       
    @Override
    public CreatorApplyPublicVO getApplicationDetailPublic(Long applicationId) {
        if (ObjectUtils.isEmpty(applicationId)) {
            throw new BusinessException("申请ID不能为空");
        }

        CreatorApply apply = creatorApplyMapper.selectById(applicationId);
        if (ObjectUtils.isEmpty(apply)) {
            throw new BusinessException("申请记录不存在");
        }

        return convertToPublicVO(apply);
    }

       
                     
       
    @Override
    public CreatorApplyPublicVO getMyApplication(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException("用户ID不能为空");
        }

        LambdaQueryWrapper<CreatorApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreatorApply::getUserId, userId)
                .orderByDesc(CreatorApply::getCreateTime)
                .last("LIMIT 1");

        CreatorApply apply = creatorApplyMapper.selectOne(wrapper);
        if (ObjectUtils.isEmpty(apply)) {
            throw new BusinessException("未找到申请记录");
        }

        CreatorApplyPublicVO result = convertToPublicVO(apply);
                                                                            
                                                                          
        result.setReviewComment(apply.getReviewReason());
        return result;
    }

       
              
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateCreatorStatus(Long creatorId, String status, String reason) {
        if (ObjectUtils.isEmpty(creatorId)) {
            throw new BusinessException("创作者ID不能为空");
        }

        String normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if ("approved".equals(normalizedStatus)) {
            normalizedStatus = "active";
        }
        if (!"active".equals(normalizedStatus) && !"suspended".equals(normalizedStatus)) {
            throw new BusinessException("创作者状态仅支持active或suspended；移除身份请使用移除接口");
        }
        validateStatusReason(reason);

        creatorEligibilityService.changeStatus(
                creatorId, normalizedStatus, UserContext.getCurrentUserId(), reason);

        log.info("event=creator_status_updated creatorId={} status={}", creatorId, normalizedStatus);

        return true;
    }

       
              
                                                             
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean removeCreator(Long creatorId, String reason) {
        if (ObjectUtils.isEmpty(creatorId)) {
            throw new BusinessException("创作者ID不能为空");
        }
        validateStatusReason(reason);

        creatorEligibilityService.remove(creatorId, UserContext.getCurrentUserId(), reason);

        log.info("event=creator_eligibility_removed creatorId={}", creatorId);

        return true;
    }

    private void validateStatusReason(String reason) {
        if (reason == null || reason.trim().length() < 2 || reason.length() > 500
                || reason.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException("创作者状态变更原因长度必须为2到500字且不能包含控制字符");
        }
    }

       
                            
       
    @Override
    public Map<String, Object> getCreatorStats(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException("用户ID不能为空");
        }

        User user = userMapper.selectById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }

                 
        long registerDays = 0;
        if (ObjectUtils.isNotEmpty(user.getCreateTime())) {
            registerDays = java.time.temporal.ChronoUnit.DAYS.between(user.getCreateTime(), LocalDateTime.now());
        }

                             
        Long publishedWorkCount = songMapper.selectCount(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getUploaderId, userId)
                        .eq(Song::getStatus, 1)
        );
                                           
        long publishedWorkCountValue = publishedWorkCount != null ? publishedWorkCount : 0L;

                  
        long totalPlays = 0L;
        List<Song> userSongs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getUploaderId, userId)
        );
        if (ObjectUtils.isNotEmpty(userSongs)) {
            for (Song song : userSongs) {
                if (ObjectUtils.isNotEmpty(song.getPlayCount())) {
                    totalPlays += song.getPlayCount();
                }
            }
        }

                
        Long fansCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
        );
                                  
        long fansCountValue = fansCount != null ? fansCount : 0L;

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("registerDays", registerDays);
        result.put("publishedWorkCount", publishedWorkCountValue);
        result.put("totalPlays", totalPlays);
        result.put("fansCount", fansCountValue);
        result.put("creditScore", user.getCreditScore() != null ? user.getCreditScore() : 0);

                                    
        boolean canApply = registerDays >= CREATOR_MIN_REGISTER_DAYS
                && publishedWorkCountValue >= CREATOR_MIN_PUBLISHED_WORKS;
        result.put("requiredRegisterDays", CREATOR_MIN_REGISTER_DAYS);
        result.put("requiredPublishedWorks", CREATOR_MIN_PUBLISHED_WORKS);
        result.put("canApply", canApply);
        result.put("applyReason", canApply ? "满足申请条件" : String.format(
                "暂未满足申请条件：需注册满%d天且发布至少%d个作品",
                CREATOR_MIN_REGISTER_DAYS, CREATOR_MIN_PUBLISHED_WORKS));

        return result;
    }

                                                     

       
                 
       
    private List<Map<String, Object>> convertToCreatorInfoList(List<User> users) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> info = new HashMap<>();
            info.put("userId", user.getId());
            info.put("username", user.getUsername());
            info.put("nickname", user.getNickname());
            info.put("avatar", user.getAvatar());
                                        
            info.put("signature", user.getIntroduction());
            info.put("creatorType", user.getCreatorType());
            info.put("creatorStatus", user.getCreatorStatus());
            info.put("creatorApplyTime", user.getCreatorApplyTime());
            info.put("creatorNote", user.getCreatorNote());
            result.add(info);
        }
        return result;
    }

       
                
       
    private List<CreatorApplyPrivateVO> convertToPrivateVOList(List<CreatorApply> applies) {
        List<CreatorApplyPrivateVO> result = new ArrayList<>();
        for (CreatorApply apply : applies) {
            result.add(convertToPrivateVO(apply));
        }
        return result;
    }

       
              
       
    private CreatorApplyPrivateVO convertToPrivateVO(CreatorApply apply) {
        CreatorApplyPrivateVO vo = new CreatorApplyPrivateVO();
        vo.setId(apply.getId() != null ? String.valueOf(apply.getId()) : null);
        vo.setUserId(apply.getUserId() != null ? String.valueOf(apply.getUserId()) : null);
        vo.setUsername(apply.getUsername());
        vo.setRealName(apply.getRealName());
        vo.setIdCard(apply.getUserId() == null ? null : userPrivateService.getIdCard(apply.getUserId()));
        vo.setIdCardMasked(apply.getIdCardMasked());         
        vo.setIdCardUrl(apply.getIdCardUrl());
        vo.setPhone(apply.getPhone());
        vo.setEmail(apply.getEmail());
        vo.setReason(apply.getApplyReason());
        vo.setWorkUrls(apply.getWorksSample());
        vo.setStatus("pending".equals(apply.getStatus()) ? 0 : "approved".equals(apply.getStatus()) ? 1 : 2);
        vo.setApplyTime(apply.getCreateTime());
        vo.setReviewTime(apply.getReviewTime());
        vo.setReviewerId(apply.getReviewerId() != null ? String.valueOf(apply.getReviewerId()) : null);
        vo.setReviewComment(apply.getReviewReason());

                  
        if (ObjectUtils.isNotEmpty(apply.getReviewerId())) {
            User reviewer = userMapper.selectById(apply.getReviewerId());
            if (ObjectUtils.isNotEmpty(reviewer)) {
                vo.setReviewerName(reviewer.getNickname());
            }
        }

                          
        if (ObjectUtils.isNotEmpty(apply.getUserId())) {
            Long fans = userFollowMapper.selectCount(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFolloweeId, apply.getUserId())
            );
            vo.setFansCount(fans != null ? fans : 0L);

            Long works = songMapper.selectCount(
                    new LambdaQueryWrapper<Song>()
                            .eq(Song::getUploaderId, apply.getUserId())
            );
            vo.setWorkCount(works != null ? works : 0L);
        }

        return vo;
    }

       
                
       
    private List<CreatorApplyPublicVO> convertToPublicVOList(List<CreatorApply> applies) {
        if (applies == null || applies.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = applies.stream()
                .map(CreatorApply::getUserId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        List<CreatorApplyPublicVO> result = new ArrayList<>();
        for (CreatorApply apply : applies) {
            result.add(convertToPublicVO(apply, users.get(apply.getUserId())));
        }
        return result;
    }

       
              
       
    private CreatorApplyPublicVO convertToPublicVO(CreatorApply apply) {
        User user = apply.getUserId() == null ? null : userMapper.selectById(apply.getUserId());
        return convertToPublicVO(apply, user);
    }

    private CreatorApplyPublicVO convertToPublicVO(CreatorApply apply, User user) {
        CreatorApplyPublicVO vo = new CreatorApplyPublicVO();
        vo.setId(apply.getId());
        vo.setUserId(apply.getUserId());
        vo.setUsername(apply.getUsername());

                                                          
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setCreatorType(user.getCreatorType());
        }
                                                   
        if (vo.getCreatorType() == null && apply.getCreatorType() != null) {
            vo.setCreatorType(apply.getCreatorType());
        }

        vo.setReason(apply.getApplyReason());
        vo.setWorkUrls(apply.getWorksSample());
        vo.setStatus("pending".equals(apply.getStatus()) ? 0 : "approved".equals(apply.getStatus()) ? 1 : 2);
        vo.setApplyTime(apply.getCreateTime());
        vo.setIdCardMasked(apply.getIdCardMasked());
        vo.setPhone(apply.getPhone());
        vo.setEmail(DataMaskingUtil.maskEmail(apply.getEmail()));
        return vo;
    }

    private void validateCreatorApplication(String realName, String idCardNo, String idCardUrl,
                                              String email, String applyReason, String worksSample) {
        String normalizedName = realName == null ? "" : realName.trim();
        if (normalizedName.length() < 2 || normalizedName.length() > 50
                || normalizedName.matches(".*[\\p{Cntrl}].*")) {
            throw new BusinessException("真实姓名长度或格式不正确");
        }
        if (ObjectUtils.isNotEmpty(idCardNo)
                && !DataMaskingUtil.isValidIdCard(idCardNo.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("身份证号格式不正确");
        }
        if (idCardUrl != null && idCardUrl.length() > 2048) {
            throw new BusinessException("证件材料引用过长");
        }
        if (email != null && email.length() > 254) {
            throw new BusinessException("邮箱地址过长");
        }
        String normalizedReason = applyReason == null ? "" : applyReason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 1000) {
            throw new BusinessException("申请理由应为10至1000字");
        }
        if (worksSample != null && worksSample.length() > 2000) {
            throw new BusinessException("作品样例不能超过2000字");
        }
    }
}
