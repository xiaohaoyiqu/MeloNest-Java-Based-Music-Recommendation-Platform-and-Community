package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.JwtUtils;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PermissionService;
import com.haoran.music.service.CreatorEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

   
                      
                         
   
@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreatorEligibilityService creatorEligibilityService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private JwtUtils jwtUtils;

       
               
       
    private static final String USER_ROLE_CACHE_PREFIX = "user:role:";

       
                       
       
    private static final int ROLE_CACHE_TTL = 3600;

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        String role = getUserRole(userId);
        return UserRole.isAdmin(role);
    }

    @Override
    public boolean isModerator(Long userId) {
        if (userId == null) {
            return false;
        }
        String role = getUserRole(userId);
        if (UserRole.canModerate(role)) {
            return true;
        }
        User user = userMapper.selectById(userId);
        return user != null && Integer.valueOf(1).equals(user.getIsModerator());
    }

    @Override
    public boolean isCreator(Long userId) {
        if (userId == null) {
            return false;
        }

        return creatorEligibilityService.isEligible(userId);
    }

    @Override
    public boolean hasRole(Long userId, String role) {
        if (userId == null || role == null) {
            return false;
        }
        String userRole = getUserRole(userId);
        return UserRole.fromCode(role) == UserRole.fromCode(userRole);
    }

    @Override
    public boolean hasRole(Long userId, UserRole role) {
        if (userId == null || role == null) {
            return false;
        }
        return hasRole(userId, role.getCode());
    }

    @Override
    public String getUserRole(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return null;
        }

        String cacheKey = USER_ROLE_CACHE_PREFIX + userId;

        return CacheHelper.getOrLoad(
                redisUtils,
                cacheKey,
                () -> {
                    User user = userMapper.selectById(userId);
                    if (ObjectUtils.isEmpty(user)) {
                        return null;
                    }

                    String role = user.getRole();
                    if (Integer.valueOf(1).equals(user.getIsModerator()) && !UserRole.isAdmin(role)) {
                        return UserRole.MODERATOR.getCode();
                    }
                    return UserRole.fromCode(role).getCode();
                },
                ROLE_CACHE_TTL,
                TimeUnit.SECONDS,
                String.class
        );
    }

    @Override
    public Long getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            return jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("解析Token失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public boolean hasPermission(Long userId, String resource, String action) {
        if (userId == null) {
            return false;
        }

                      
        if (hasRole(userId, UserRole.SUPER_ADMIN)) {
            return true;
        }

                     
        if (hasRole(userId, UserRole.ADMIN)) {
                                
            if ("system".equals(resource) && "config".equals(action)) {
                return false;
            }
            return true;
        }

                  
        if ("moderation".equals(resource) && isModerator(userId)) {
            return true;
        }

                    
        if ("content".equals(resource) && "create".equals(action) && isCreator(userId)) {
            return true;
        }

                   
        return "user".equals(resource) && ("profile".equals(action) || "settings".equals(action));
    }

    @Override
    public void clearRoleCache(Long userId) {
        if (userId != null) {
            String cacheKey = USER_ROLE_CACHE_PREFIX + userId;
            CacheHelper.delete(redisUtils, cacheKey);
        }
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        if (userId == null || role == null) {
            return;
        }

        User user = userMapper.selectById(userId);
        if (user != null) {
            UserRole normalizedRole = UserRole.fromCode(role);
            User operator = getCurrentOperatorOrNull();
            if (operator != null
                    || AdminAccountOperationGuard.isPrivilegedAccount(user)
                    || AdminAccountOperationGuard.isPrivilegedRole(normalizedRole)) {
                AdminAccountOperationGuard.requireCanAssignRole(operator, user, normalizedRole);
            }

            user.setRole(normalizedRole.getCode());
            userMapper.updateById(user);

                   
            clearRoleCache(userId);

            log.info("更新用户角色: userId={}, role={}", userId, role);
        }
    }

    private User getCurrentOperatorOrNull() {
        Long operatorId = UserContext.getCurrentUserId();
        if (operatorId == null) {
            return null;
        }
        return userMapper.selectById(operatorId);
    }
}
