package com.haoran.music.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.RedisConstants;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ConvertHelper;
import com.haoran.music.common.util.FileUploadUtil;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.user.*;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserFollow;
import com.haoran.music.enums.UserRole;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.mapper.UserFollowMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.UserPasswordService;
import com.haoran.music.service.UserPrivateService;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.PhoneVerificationService;
import com.haoran.music.service.EmailVerificationService;
import com.haoran.music.service.UserService;
import com.haoran.music.service.UserSessionRevocationService;
import com.haoran.music.service.search.SearchIndexService;
import com.haoran.music.vo.user.UserLoginVO;
import com.haoran.music.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

   
                      
                                           
   
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String DUMMY_PASSWORD_HASH = BCrypt.hashpw("invalid-login-password");

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    @Resource
    private UserFollowMapper userFollowMapper;

    @Resource
    private UserPasswordService userPasswordService;

    @Resource
    private UserPrivateService userPrivateService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private PhoneVerificationService phoneVerificationService;

    @Resource
    private EmailVerificationService emailVerificationService;

    @Resource
    private com.haoran.music.service.UserVipService userVipService;

    @Resource
    private FileUploadUtil fileUploadUtil;

    @Resource
    private com.haoran.music.mapper.UserPasswordMapper userPasswordMapper;
    @Resource
    private com.haoran.music.mapper.ListenHistoryMapper listenHistoryMapper;
    @Resource
    private com.haoran.music.mapper.SearchHistoryMapper searchHistoryMapper;
    @Resource
    private com.haoran.music.mapper.SongLikeMapper songLikeMapper;
    @Resource
    private com.haoran.music.mapper.PlaylistMapper playlistMapper;
    @Resource
    private com.haoran.music.mapper.PlaylistSongMapper playlistSongMapper;
    @Resource
    private com.haoran.music.mapper.LocalMusicMapper localMusicMapper;
    @Resource
    private com.haoran.music.mapper.CommentLikeMapper commentLikeMapper;
    @Resource
    private com.haoran.music.mapper.FavoriteHistoryMapper favoriteHistoryMapper;
    @Resource
    private com.haoran.music.mapper.SongRatingMapper songRatingMapper;
    @Resource
    private com.haoran.music.mapper.UserProfileMapper userProfileMapper;
    @Resource
    private com.haoran.music.mapper.UserEquipmentConfigMapper userEquipmentConfigMapper;
    @Resource
    private com.haoran.music.mapper.UserExtensionMapper userExtensionMapper;

    @Resource
    private SearchIndexService searchIndexService;

    @Resource
    private UserSessionRevocationService userSessionRevocationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(User user) {
        boolean result = super.save(user);
        if (result && user != null) {
            searchIndexService.sync("user", user.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(User user) {
        boolean result = super.updateById(user);
        if (result && user != null && user.getId() != null) {
            searchIndexService.sync("user", user.getId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result && id != null) {
            searchIndexService.sync("user", Long.valueOf(String.valueOf(id)));
        }
        return result;
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        String account = dto.getUsername().trim();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.eq(User::getUsername, account)
                        .or().eq(User::getPhone, account)
                        .or().eq(User::getEmail, account));

        User user = getOne(wrapper);
        if (ObjectUtils.isEmpty(user)) {
            BCrypt.checkpw(dto.getPassword(), DUMMY_PASSWORD_HASH);
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }

        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_ERROR);
        }

        if (!UserAccountStatusUtil.canAuthenticate(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account access is restricted");
        }

        String token = jwtUtils.generateToken(user.getId());

        String cacheKey = RedisConstants.USER_INFO_PREFIX + user.getId();
        redisUtils.set(cacheKey, user, RedisConstants.USER_INFO_EXPIRE, RedisConstants.USER_INFO_TIME_UNIT);

        String tokenKey = RedisConstants.TOKEN_PREFIX + user.getId();
        redisUtils.set(tokenKey, token, RedisConstants.TOKEN_EXPIRE, RedisConstants.TOKEN_TIME_UNIT);

        return new UserLoginVO(token, convertToVO(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(UserRegisterDTO dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        String username = dto.getUsername().trim();
        String phone = normalizeNullable(dto.getPhone());
        String email = normalizeEmail(dto.getEmail());

        ensureUsernameAvailable(username);
                                                                                                                           
        ensurePhoneAvailable(phone, null);
        ensureEmailAvailable(email, null);

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setPhone(phone);
        user.setEmail(email);
        user.setNickname(ObjectUtils.isNotEmpty(dto.getNickname()) ? dto.getNickname().trim() : username);
        user.setStatus(CommonConstants.STATUS_NORMAL);
        user.setFansCount(0);
        user.setFollowingCount(0);
        user.setGender(CommonConstants.GENDER_UNKNOWN);

        save(user);

        log.info("event=user_registration_succeeded userId={}", user.getId());
        return convertToVO(user);
    }

    private void ensureUsernameAvailable(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST, "Username already exists");
        }
    }

    private void ensurePhoneAvailable(String phone, Long currentUserId) {
        if (ObjectUtils.isEmpty(phone)) {
            return;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .ne(currentUserId != null, User::getId, currentUserId);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST, "Phone number already registered");
        }
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        if (ObjectUtils.isEmpty(email)) {
            return;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .ne(currentUserId != null, User::getId, currentUserId);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXIST, "Email already registered");
        }
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeNullable(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireCurrentPassword(User user, String password) {
        if (ObjectUtils.isEmpty(password)) {
            throw new BusinessException("请输入当前密码完成敏感操作验证");
        }
        if (user == null || ObjectUtils.isEmpty(user.getPassword())
                || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }
    }

    @Override
    public UserVO getCurrentUserInfo(Long userId) {
        String cacheKey = RedisConstants.USER_INFO_PREFIX + userId;
        User user = CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> {
                    User u = getById(userId);
                    if (ObjectUtils.isEmpty(u)) {
                        throw new BusinessException(ResultCode.USER_NOT_EXIST);
                    }
                    return u;
                },
                RedisConstants.USER_INFO_EXPIRE,
                RedisConstants.USER_INFO_TIME_UNIT,
                User.class
        );
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUserInfo(Long userId, UserUpdateDTO dto) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        if (ObjectUtils.isNotEmpty(dto.getNewPassword())) {
            requireCurrentPassword(user, dto.getOldPassword());
            user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        }

        if (ObjectUtils.isNotEmpty(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (ObjectUtils.isNotEmpty(dto.getPhone())) {
            String phone = normalizeNullable(dto.getPhone());
            if (!java.util.Objects.equals(phone, user.getPhone())) {
                requireCurrentPassword(user, dto.getOldPassword());
                if (ObjectUtils.isEmpty(dto.getPhoneVerifyCode())) {
                    throw new BusinessException("修改手机号需要新手机号验证码");
                }
                ensurePhoneAvailable(phone, userId);
                phoneVerificationService.verifyCode(phone, "change_phone", dto.getPhoneVerifyCode());
                user.setPhone(phone);
            }
        }
        if (ObjectUtils.isNotEmpty(dto.getEmail())) {
            String email = normalizeEmail(dto.getEmail());
            if (!java.util.Objects.equals(email, normalizeEmail(user.getEmail()))) {
                requireCurrentPassword(user, dto.getOldPassword());
                if (ObjectUtils.isEmpty(dto.getEmailVerifyCode())) {
                    throw new BusinessException("修改邮箱需要新邮箱验证码");
                }
                ensureEmailAvailable(email, userId);
                emailVerificationService.verifyCode(email, "change_email", dto.getEmailVerifyCode());
                user.setEmail(email);
            }
        }
        if (ObjectUtils.isNotEmpty(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar());
        }
        if (ObjectUtils.isNotEmpty(dto.getSignature())) {
            user.setIntroduction(dto.getSignature());
        }
        if (ObjectUtils.isNotEmpty(dto.getGender())) {
            user.setGender(dto.getGender());
        }
        if (ObjectUtils.isNotEmpty(dto.getBirthday())) {
            user.setBirthday(dto.getBirthday());
        }
        if (ObjectUtils.isNotEmpty(dto.getWallpaper())) {
            user.setWallpaper(dto.getWallpaper());
        }

        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);

        clearUserCache(userId);
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        String avatarUrl = null;
        try {
            avatarUrl = fileUploadUtil.uploadAvatar(file);
            user.setAvatar(avatarUrl);
            user.setUpdateTime(java.time.LocalDateTime.now());
            if (!updateById(user)) {
                throw new IllegalStateException("avatar database update returned false");
            }

            clearUserCache(userId);
            log.info("event=user_avatar_upload_succeeded userId={}", userId);
            return avatarUrl;
        } catch (IllegalArgumentException e) {
            compensateAvatarUpload(avatarUrl, userId);
            throw new BusinessException(ResultCode.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            compensateAvatarUpload(avatarUrl, userId);
            log.error("Avatar upload failed: userId={}", userId);
            throw new BusinessException(ResultCode.ERROR, "头像上传失败，请稍后重试");
        }
    }

    private void compensateAvatarUpload(String avatarUrl, Long userId) {
        if (ObjectUtils.isEmpty(avatarUrl)) {
            return;
        }
        if (!fileUploadUtil.deleteFile(avatarUrl)) {
            log.error("Avatar upload compensation failed: userId={}", userId);
        }
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user) || CommonConstants.DELETED.equals(user.getDeleted())) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return convertToVO(user);
    }

    @Override
    public IPage<UserVO> pageUsers(UserQueryDTO dto) {
        Page<User> page = new Page<>(dto.getPage(), dto.getSize());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getDeleted, CommonConstants.NOT_DELETED);

                         
        if (ObjectUtils.isNotEmpty(dto.getKeyword())) {
            wrapper.and(w -> w.like(User::getUsername, dto.getKeyword())
                    .or()
                    .like(User::getNickname, dto.getKeyword())
                    .or()
                    .like(User::getEmail, dto.getKeyword()));
        } else {
                     
            if (ObjectUtils.isNotEmpty(dto.getUsername())) {
                wrapper.like(User::getUsername, dto.getUsername());
            }
            if (ObjectUtils.isNotEmpty(dto.getNickname())) {
                wrapper.like(User::getNickname, dto.getNickname());
            }
        }

        String accountState = dto.getAccountState() == null
                ? "" : dto.getAccountState().trim().toLowerCase(java.util.Locale.ROOT);
        if ("normal".equals(accountState)) {
            wrapper.eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                    .and(w -> w.isNull(User::getIsBanned).or().eq(User::getIsBanned, 0));
        } else if ("frozen".equals(accountState)) {
            wrapper.eq(User::getStatus, CommonConstants.STATUS_DISABLED)
                    .and(w -> w.isNull(User::getIsBanned).or().eq(User::getIsBanned, 0));
        } else if ("banned".equals(accountState)) {
            wrapper.eq(User::getIsBanned, 1);
        } else if (ObjectUtils.isNotEmpty(dto.getStatus())) {
                                              
            wrapper.eq(User::getStatus, dto.getStatus());
        }

        if (ObjectUtils.isNotEmpty(dto.getRole()) && UserRole.isValidCode(dto.getRole())) {
            wrapper.apply("UPPER(role) = {0}", UserRole.fromCode(dto.getRole()).getCode());
        }
                                                                      

                 
        if (ObjectUtils.isNotEmpty(dto.getUserType())) {
            wrapper.eq(User::getUserType, dto.getUserType());
        }

                                    
        if (dto.getIsVip() != null) {
            if (dto.getIsVip()) {
                wrapper.inSql(User::getId,
                        "SELECT user_id FROM user_vip WHERE vip_status = 1 " +
                                "AND vip_expire_time > NOW() AND deleted = 0");
            } else {
                wrapper.notInSql(User::getId,
                        "SELECT user_id FROM user_vip WHERE vip_status = 1 " +
                                "AND vip_expire_time > NOW() AND deleted = 0");
            }
        }

                   
        if (ObjectUtils.isNotEmpty(dto.getRegisterTimeStart())) {
            wrapper.ge(User::getCreateTime, dto.getRegisterTimeStart());
        }
        if (ObjectUtils.isNotEmpty(dto.getRegisterTimeEnd())) {
            wrapper.le(User::getCreateTime, dto.getRegisterTimeEnd());
        }

                  
        if (ObjectUtils.isNotEmpty(dto.getFansCountMin())) {
            wrapper.ge(User::getFansCount, dto.getFansCountMin());
        }
        if (ObjectUtils.isNotEmpty(dto.getFansCountMax())) {
            wrapper.le(User::getFansCount, dto.getFansCountMax());
        }

                  
        if (ObjectUtils.isNotEmpty(dto.getCreditScoreMin())) {
            wrapper.ge(User::getCreditScore, dto.getCreditScoreMin());
        }
        if (ObjectUtils.isNotEmpty(dto.getCreditScoreMax())) {
            wrapper.le(User::getCreditScore, dto.getCreditScoreMax());
        }


                  
        if (dto.getIsCreator() != null) {
            if (dto.getIsCreator()) {
                wrapper.eq(User::getIsCreator, 1);
            } else {
                wrapper.and(w -> w.isNull(User::getIsCreator).or().ne(User::getIsCreator, 1));
            }
        }

                  
        if (dto.getIsModerator() != null) {
            if (dto.getIsModerator()) {
                wrapper.eq(User::getIsModerator, 1);
            } else {
                wrapper.and(w -> w.isNull(User::getIsModerator).or().ne(User::getIsModerator, 1));
            }
        }

                 
        if (dto.getIsOfficial() != null) {
            if (dto.getIsOfficial()) {
                wrapper.eq(User::getIsOfficial, 1);
            } else {
                wrapper.and(w -> w.isNull(User::getIsOfficial).or().ne(User::getIsOfficial, 1));
            }
        }

        wrapper.orderByDesc(User::getFansCount, User::getCreateTime);

        IPage<User> userPage = page(page, wrapper);
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                userPage.getRecords().stream().map(User::getId).collect(Collectors.toSet())).keySet();
        return userPage.convert(user -> convertToVO(user, vipUserIds.contains(user.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException("Old password is incorrect");
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        updateById(user);

        clearUserCache(userId);
        return true;
    }

    @Override
    public Boolean logout(Long userId) {
        userSessionRevocationService.revokeWebSocketSessions(userId, "user_logout");
        String tokenKey = RedisConstants.TOKEN_PREFIX + userId;
        redisUtils.delete(tokenKey);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean followUser(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException("Cannot follow yourself");
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Long count = userFollowMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("Already followed this user");
        }

        UserFollow userFollow = new UserFollow();
        userFollow.setFollowerId(followerId);
        userFollow.setFolloweeId(followeeId);
        userFollowMapper.insert(userFollow);

        User follower = getById(followerId);
        User followee = getById(followeeId);

        follower.setFollowingCount(follower.getFollowingCount() + 1);
        followee.setFansCount(followee.getFansCount() + 1);

        updateById(follower);
        updateById(followee);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unfollowUser(Long followerId, Long followeeId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        UserFollow userFollow = userFollowMapper.selectOne(wrapper);
        if (ObjectUtils.isEmpty(userFollow)) {
            throw new BusinessException("Not following this user");
        }

        userFollowMapper.deleteById(userFollow.getId());

        User follower = getById(followerId);
        User followee = getById(followeeId);

        follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
        followee.setFansCount(Math.max(0, followee.getFansCount() - 1));

        updateById(follower);
        updateById(followee);

        return true;
    }

    @Override
    public IPage<UserVO> getFollowingList(Long userId, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }

        Page<UserFollow> followPage = new Page<>(page != null ? page : 1, size != null ? size : 20);

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(UserFollow::getCreateTime);

        IPage<UserFollow> followResult = userFollowMapper.selectPage(followPage, wrapper);

                      
        List<Long> followeeIds = followResult.getRecords().stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(followeeIds)) {
            return ConvertHelper.emptyPage(page, size, UserVO.class);
        }

                   
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, followeeIds)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED);
        List<User> users = list(userWrapper);

                
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                users.stream().map(User::getId).collect(Collectors.toSet())).keySet();
        List<UserVO> voList = ConvertHelper.toVOList(users,
                user -> convertToVO(user, vipUserIds.contains(user.getId())));
        IPage<UserVO> resultPage = new Page<>(followPage.getCurrent(), followPage.getSize(), followResult.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public IPage<UserVO> getFollowersList(Long userId, Integer page, Integer size) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }

        Page<UserFollow> followPage = new Page<>(page != null ? page : 1, size != null ? size : 20);

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED)
                .orderByDesc(UserFollow::getCreateTime);

        IPage<UserFollow> followResult = userFollowMapper.selectPage(followPage, wrapper);

                    
        List<Long> followerIds = followResult.getRecords().stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(followerIds)) {
            return ConvertHelper.emptyPage(page, size, UserVO.class);
        }

                   
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, followerIds)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED);
        List<User> users = list(userWrapper);

                
        Set<Long> vipUserIds = userVipService.getActiveVipExpirations(
                users.stream().map(User::getId).collect(Collectors.toSet())).keySet();
        List<UserVO> voList = ConvertHelper.toVOList(users,
                user -> convertToVO(user, vipUserIds.contains(user.getId())));
        IPage<UserVO> resultPage = new Page<>(followPage.getCurrent(), followPage.getSize(), followResult.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public Boolean isFollowing(Long followerId, Long followeeId) {
        if (ObjectUtils.isEmpty(followerId) || ObjectUtils.isEmpty(followeeId)) {
            return false;
        }

        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);
        Long count = userFollowMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public void sendVerifyCode(String phone, String type) {
                                                                                                             
        phoneVerificationService.sendCode(phone, type, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        phoneVerificationService.verifyCode(dto.getPhone(), "reset", dto.getVerifyCode());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, dto.getPhone())
                .eq(User::getDeleted, CommonConstants.NOT_DELETED);

        User user = getOne(wrapper);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("This phone number is not registered");
        }

        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        updateById(user);
        log.info("Password reset successful: userId={}", user.getId());
        clearUserCache(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserWallpapers(Long userId, String wallpapers) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        String settings = "{\"wallpapers\":" + wallpapers + "}";
        user.setPrivacySettings(settings);
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);

        clearUserCache(userId);

        int count = 0;
        if (wallpapers != null && !wallpapers.equals("[]")) {
            count = wallpapers.split(",").length;
        }
        log.info("User wallpapers saved: userId={}, count={}", userId, count);
    }

    @Override
    public String getUserWallpapers(Long userId) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return "[]";
        }

        String settings = user.getPrivacySettings();
        if (ObjectUtils.isEmpty(settings)) {
            return "[]";
        }

        try {
            if (settings.contains("\"wallpapers\"")) {
                int start = settings.indexOf("\"wallpapers\":") + 13;
                int end = settings.indexOf("}", start);
                if (end > start) {
                    return settings.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse user wallpapers: userId={}", userId);
        }

        return "[]";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addWallpaper(Long userId, String wallpaper) {
        String current = getUserWallpapers(userId);

        List<String> wallpapers = new ArrayList<>();
        if (current != null && !current.equals("[]")) {
            String trimmed = current.trim();
            if (trimmed.startsWith("[")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.endsWith("]")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                String[] parts = trimmed.split(",");
                for (String part : parts) {
                    String url = part.trim().replaceAll("\"", "");
                    if (!url.isEmpty()) {
                        wallpapers.add(url);
                    }
                }
            }
        }

        if (!wallpapers.contains(wallpaper)) {
            if (wallpapers.size() >= 20) {
                throw new BusinessException("Maximum 20 wallpapers allowed");
            }
            wallpapers.add(wallpaper);
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < wallpapers.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(wallpapers.get(i)).append("\"");
        }
        sb.append("]");

        saveUserWallpapers(userId, sb.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWallpaper(Long userId, String wallpaper) {
        String current = getUserWallpapers(userId);

        List<String> wallpapers = new ArrayList<>();
        if (current != null && !current.equals("[]")) {
            String trimmed = current.trim();
            if (trimmed.startsWith("[")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.endsWith("]")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            if (!trimmed.isEmpty()) {
                String[] parts = trimmed.split(",");
                for (String part : parts) {
                    String url = part.trim().replaceAll("\"", "");
                    if (!url.isEmpty() && !url.equals(wallpaper)) {
                        wallpapers.add(url);
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < wallpapers.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(wallpapers.get(i)).append("\"");
        }
        sb.append("]");

        saveUserWallpapers(userId, sb.toString());
        log.info("User wallpaper removed: userId={}, wallpaper={}", userId, wallpaper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setLocalMusicPath(Long userId, String path) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "User ID cannot be empty");
        }
        if (ObjectUtils.isEmpty(path)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Path cannot be empty");
        }

        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        user.setLocalMusicPath(path);
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);

        clearUserCache(userId);
        log.info("event=legacy_local_music_path_set userId={}", userId);
    }

    @Override
    public String getLocalMusicPath(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }
        User user = getById(userId);
        return ObjectUtils.isNotEmpty(user) ? user.getLocalMusicPath() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearLocalMusicPath(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return;
        }
        User user = getById(userId);
        if (ObjectUtils.isNotEmpty(user)) {
            user.setLocalMusicPath(null);
            updateById(user);

            clearUserCache(userId);
            log.info("Local music path cleared: userId={}", userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId, userId).eq(User::getDeleted, CommonConstants.NOT_DELETED);
        User user = getOne(wrapper);

        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException("Password error");
        }

        cleanupUserRelatedData(userId);
        anonymizeUserData(user);

        if (baseMapper.softDeleteActiveById(userId) != 1) {
            throw new IllegalStateException("账户注销状态更新失败");
        }

        clearUserCache(userId);
        log.info("User account deleted: userId={}", userId);
    }

       
                                 
      
                       
       
    private void cleanupUserRelatedData(Long userId) {
        userPasswordService.deleteByUserId(userId);
        userPrivateService.deleteUserPrivate(userId);

        userProfileMapper.delete(new LambdaQueryWrapper<com.haoran.music.entity.UserProfile>()
                .eq(com.haoran.music.entity.UserProfile::getUserId, userId));
        userEquipmentConfigMapper.delete(new LambdaQueryWrapper<com.haoran.music.entity.UserEquipmentConfig>()
                .eq(com.haoran.music.entity.UserEquipmentConfig::getUserId, userId));
        userExtensionMapper.delete(new LambdaQueryWrapper<com.haoran.music.entity.UserExtension>()
                .eq(com.haoran.music.entity.UserExtension::getUserId, userId));
        CacheHelper.delete(redisUtils, "user:profile:" + userId, "user:tags:" + userId);

        userFollowMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.UserFollow>()
                .eq(com.haoran.music.entity.UserFollow::getFollowerId, userId)
        );
        userFollowMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.UserFollow>()
                .eq(com.haoran.music.entity.UserFollow::getFolloweeId, userId)
        );

        listenHistoryMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.ListenHistory>()
                .eq(com.haoran.music.entity.ListenHistory::getUserId, userId)
        );

        searchHistoryMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.SearchHistory>()
                .eq(com.haoran.music.entity.SearchHistory::getUserId, userId)
        );

        songLikeMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.SongLike>()
                .eq(com.haoran.music.entity.SongLike::getUserId, userId)
        );

        java.util.List<Long> playlistIds = playlistMapper.selectList(
            new LambdaQueryWrapper<com.haoran.music.entity.Playlist>()
                .eq(com.haoran.music.entity.Playlist::getUserId, userId)
        ).stream().map(com.haoran.music.entity.Playlist::getId).collect(Collectors.toList());

        if (CollUtil.isNotEmpty(playlistIds)) {
            playlistSongMapper.delete(
                new LambdaQueryWrapper<com.haoran.music.entity.PlaylistSong>()
                    .in(com.haoran.music.entity.PlaylistSong::getPlaylistId, playlistIds)
            );
            playlistMapper.deleteBatchIds(playlistIds);
        }

        localMusicMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.LocalMusic>()
                .eq(com.haoran.music.entity.LocalMusic::getUserId, userId)
        );

        commentLikeMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.CommentLike>()
                .eq(com.haoran.music.entity.CommentLike::getUserId, userId)
        );

        favoriteHistoryMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.FavoriteHistory>()
                .eq(com.haoran.music.entity.FavoriteHistory::getUserId, userId)
        );

        songRatingMapper.delete(
            new LambdaQueryWrapper<com.haoran.music.entity.SongRating>()
                .eq(com.haoran.music.entity.SongRating::getUserId, userId)
        );

        log.info("User related data cleanup completed: userId={}", userId);
    }

       
                                                                                            
      
                         
       
    private void anonymizeUserData(User user) {
        user.setUsername("deleted_user_" + user.getId());
        user.setPassword(null);
        user.setNickname("Deleted User");
        user.setPhone(null);
        user.setEmail(null);
        user.setAvatar(null);
        user.setIntroduction(null);
        user.setGender(0);
        user.setBirthday(null);
        user.setLocalMusicPath(null);
        user.setWallpaper(null);
        updateById(user);
        log.info("User data anonymized: userId={}", user.getId());
    }

       
                                         
      
                       
       
    private void clearUserCache(Long userId) {
        CacheHelper.delete(
                redisUtils,
                RedisConstants.TOKEN_PREFIX + userId,
                RedisConstants.USER_INFO_PREFIX + userId
        );
        permissionService.clearRoleCache(userId);
    }

       
                    
      
                              
                     
       
    private UserVO convertToVO(User user) {
        return convertToVO(user, Boolean.TRUE.equals(userVipService.isVip(user.getId())));
    }

    private UserVO convertToVO(User user, boolean isVip) {
        UserVO vo = BeanUtil.copyProperties(user, UserVO.class);
        if (ObjectUtils.isNotEmpty(user.getIntroduction())) {
            vo.setSignature(user.getIntroduction());
        }
        vo.setIsVip(isVip);
        return vo;
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (ObjectUtils.isEmpty(refreshToken)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Refresh token cannot be empty");
        }

        try {
            if (!jwtUtils.validateToken(refreshToken)) {
                throw new BusinessException(ResultCode.TOKEN_INVALID, "Invalid or expired refresh token");
            }
            Long userId = jwtUtils.getUserIdFromToken(refreshToken);
            String tokenKey = RedisConstants.TOKEN_PREFIX + userId;
            Object currentToken = redisUtils.get(tokenKey);
            if (ObjectUtils.isEmpty(currentToken) || !refreshToken.equals(currentToken.toString())) {
                throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh token is no longer current");
            }
            User user = getById(userId);
            if (ObjectUtils.isEmpty(user)) {
                throw new BusinessException(ResultCode.USER_NOT_EXIST, "User not found");
            }
            if (!UserAccountStatusUtil.canAuthenticate(user)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "Account access is restricted");
            }
            String newToken = jwtUtils.generateToken(userId);
            boolean rotated = redisUtils.compareAndSet(tokenKey, refreshToken, newToken,
                    RedisConstants.TOKEN_EXPIRE, RedisConstants.TOKEN_TIME_UNIT);
            if (!rotated) {
                throw new BusinessException(ResultCode.TOKEN_INVALID, "Refresh token is no longer current");
            }
            log.info("Token refreshed successfully: userId={}", userId);
            return newToken;
        } catch (BusinessException e) {
            log.warn("Token refresh rejected: code={}", e.getCode());
            throw e;
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Invalid or expired refresh token");
        }
    }

    @Override
    public boolean validateToken(String token) {
        if (ObjectUtils.isEmpty(token)) {
            return false;
        }
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            User user = getById(userId);
            if (ObjectUtils.isEmpty(user)) {
                return false;
            }
            if (!UserAccountStatusUtil.canAuthenticate(user)) {
                return false;
            }
            String tokenKey = RedisConstants.TOKEN_PREFIX + userId;
            Object cachedToken = redisUtils.get(tokenKey);
            if (ObjectUtils.isEmpty(cachedToken)) {
                return false;
            }
            return cachedToken.equals(token);
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

       
                 
                         
                        
                       
                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean addVipDays(Long userId, Integer days, String reason) {
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException("用户不存在");
        }
        UserAccountStatusUtil.requireCanInteract(user, "获得VIP奖励");
        if (days == null || days <= 0 || days > 365) {
            throw new BusinessException("VIP奖励天数不合法");
        }
        userVipService.grantVip(userId, 1, days, "feedback_reward");
        log.info("给用户增加VIP天数: userId={}, days={}, reason={}", userId, days, reason);

        return true;
    }

                                                                   

       
                    
      
                         
                                  
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, Integer status) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        requireCanOperateAdminTarget(user, "更新用户状态");
        Integer previousStatus = user.getStatus();
        user.setStatus(status);
        user.setUpdateTime(java.time.LocalDateTime.now());
        if (CommonConstants.STATUS_NORMAL.equals(status)) {
            UpdateWrapper<User> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", userId)
                    .set("status", status)
                    .set("is_banned", 0)
                    .set("ban_reason", null)
                    .set("ban_start_time", null)
                    .set("ban_end_time", null)
                    .set("update_time", user.getUpdateTime());
            update(wrapper);
        } else {
            updateById(user);
        }
        clearUserCache(userId);
        if (!java.util.Objects.equals(previousStatus, status)) {
            bumpCandidateCacheVersion("user status changed:" + userId);
        }
        log.info("管理员更新用户状态: userId={}, status={}", userId, status);
    }

       
               
      
                            
                                   
                      
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateUserStatus(List<Long> userIds, Integer status) {
        if (CollUtil.isEmpty(userIds)) {
            return 0;
        }
        int count = 0;
        for (Long userId : userIds) {
            try {
                updateUserStatus(userId, status);
                count++;
            } catch (Exception e) {
                log.error("批量更新用户状态失败: userId={}, error={}", userId, e.getClass().getSimpleName());
            }
        }
        return count;
    }

       
                    
      
                         
                                   
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserRole(Long userId, String role) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (ObjectUtils.isEmpty(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Role cannot be empty");
        }
        if (!UserRole.isValidCode(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid role");
        }
        String normalizedRole = UserRole.fromCode(role).getCode();
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        AdminAccountOperationGuard.requireCanAssignRole(
                getCurrentAdminOperator(), user, UserRole.fromCode(normalizedRole));
        user.setRole(normalizedRole);
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
        clearUserCache(userId);
        log.info("管理员更新用户角色: userId={}, role={}", userId, normalizedRole);
    }

       
               
      
                            
                                    
                      
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateUserRole(List<Long> userIds, String role) {
        if (CollUtil.isEmpty(userIds)) {
            return 0;
        }
        int count = 0;
        for (Long userId : userIds) {
            try {
                updateUserRole(userId, role);
                count++;
            } catch (Exception e) {
                log.error("批量更新用户角色失败: userId={}, error={}", userId, e.getClass().getSimpleName());
            }
        }
        return count;
    }

       
                  
      
                         
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteUser(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        requireCanOperateAdminTarget(user, "删除用户");
        cleanupUserRelatedData(userId);
        anonymizeUserData(user);
        if (baseMapper.softDeleteActiveById(userId) != 1) {
            throw new IllegalStateException("用户删除状态更新失败");
        }
        clearUserCache(userId);
        userSessionRevocationService.revokeWebSocketSessions(userId, "account_deleted");
        bumpCandidateCacheVersion("user deleted:" + userId);
        log.warn("管理员删除用户: userId={}", userId);
    }

       
             
      
                            
                      
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteUsers(List<Long> userIds) {
        if (CollUtil.isEmpty(userIds)) {
            return 0;
        }
        int count = 0;
        for (Long userId : userIds) {
            try {
                adminDeleteUser(userId);
                count++;
            } catch (Exception e) {
                log.error("批量删除用户失败: userId={}, error={}", userId, e.getClass().getSimpleName());
            }
        }
        return count;
    }

       
                    
      
                              
                             
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminResetPassword(Long userId, String newPassword) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (ObjectUtils.isEmpty(newPassword) || newPassword.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        requireCanOperateAdminTarget(user, "重置用户密码");
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
        clearUserCache(userId);
        log.warn("管理员重置用户密码: userId={}", userId);
    }

       
                    
      
                         
                            
       
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminEditUserInfo(Long userId, java.util.Map<String, Object> params) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        requireCanOperateAdminTarget(user, "编辑用户信息");
        if (ObjectUtils.isNotEmpty(params.get("nickname"))) {
            user.setNickname(String.valueOf(params.get("nickname")));
        }
        if (ObjectUtils.isNotEmpty(params.get("email"))) {
            String email = normalizeEmail(String.valueOf(params.get("email")));
            ensureEmailAvailable(email, userId);
            user.setEmail(email);
        }
        if (ObjectUtils.isNotEmpty(params.get("phone"))) {
            String phone = normalizeNullable(String.valueOf(params.get("phone")));
            ensurePhoneAvailable(phone, userId);
            user.setPhone(phone);
        }
        if (ObjectUtils.isNotEmpty(params.get("signature"))) {
            user.setIntroduction(String.valueOf(params.get("signature")));
        }
        if (ObjectUtils.isNotEmpty(params.get("gender"))) {
            user.setGender(Integer.valueOf(String.valueOf(params.get("gender"))));
        }
        if (ObjectUtils.isNotEmpty(params.get("avatar"))) {
            user.setAvatar(String.valueOf(params.get("avatar")));
        }
        user.setUpdateTime(java.time.LocalDateTime.now());
        updateById(user);
        clearUserCache(userId);
        log.info("管理员编辑用户信息: userId={}", userId);
    }

       
                    
      
                         
                   
       
    @Override
    public User getUserEntityById(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        User user = getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return user;
    }

    private void requireCanOperateAdminTarget(User target, String action) {
        AdminAccountOperationGuard.requireCanOperateAccount(getCurrentAdminOperator(), target, action);
    }

    private void bumpCandidateCacheVersion(String reason) {
        try {
            musicIntelligenceCacheService.bumpCandidateCacheVersion(reason, UserContext.getCurrentUserId());
            musicIntelligenceCacheService.bumpRecommendCacheVersion(reason, UserContext.getCurrentUserId());
            musicIntelligenceCacheService.bumpRankingCacheVersion(reason, UserContext.getCurrentUserId());
        } catch (Exception e) {
            log.warn("用户状态变更后更新音乐智能缓存版本失败，不阻断用户操作: reason={}, error={}", reason, e.getClass().getSimpleName());
        }
    }

    private User getCurrentAdminOperator() {
        Long operatorId = UserContext.getCurrentUserId();
        if (ObjectUtils.isEmpty(operatorId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User operator = getById(operatorId);
        if (ObjectUtils.isEmpty(operator)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作账号不存在");
        }
        return operator;
    }
}
