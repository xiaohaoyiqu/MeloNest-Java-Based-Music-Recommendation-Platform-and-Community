   
                      
   
package com.haoran.music.common.aspect;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private PermissionService permissionService;

    @Before("@annotation(com.haoran.music.common.annotation.RequireRole) || @within(com.haoran.music.common.annotation.RequireRole)")
    public void checkPermission(JoinPoint joinPoint) {
        RequireRole requireRole = resolveRequireRole(joinPoint);
        if (requireRole == null) {
            return;
        }

        Long userId = getCurrentUserId();
        if (userId == null) {
            log.warn("Permission check failed: current user id is missing");
            throw new BusinessException(401, "Please login first");
        }

        String userRole = permissionService.getUserRole(userId);
        if (userRole == null) {
            log.warn("Permission check failed: userId={} has no role", userId);
            throw new BusinessException(403, "User role is not configured");
        }

        UserRole[] requiredRoles = requireRole.value();
        RequireRole.LogicalType logicalType = requireRole.logical();
        boolean hasPermission = checkRole(userRole, requiredRoles, logicalType);

        if (!hasPermission) {
            log.warn("Permission check failed: userId={}, userRole={}, requiredRoles={}",
                    userId, userRole, Arrays.toString(requiredRoles));
            throw new BusinessException(403, "Permission denied");
        }

        log.debug("Permission check passed: userId={}, role={}", userId, userRole);
    }

    private RequireRole resolveRequireRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        RequireRole methodRole = method.getAnnotation(RequireRole.class);
        if (methodRole != null) {
            return methodRole;
        }

        Object target = joinPoint.getTarget();
        Class<?> targetClass = target == null ? method.getDeclaringClass() : AopUtils.getTargetClass(target);
        RequireRole classRole = targetClass.getAnnotation(RequireRole.class);
        if (classRole != null) {
            return classRole;
        }
        return method.getDeclaringClass().getAnnotation(RequireRole.class);
    }

    boolean checkRole(String userRole, UserRole[] requiredRoles, RequireRole.LogicalType logicalType) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }

        if (!UserRole.isValidCode(userRole)) {
            return false;
        }
        UserRole currentUserRole = UserRole.fromCode(userRole);
        if (logicalType == RequireRole.LogicalType.AND) {
            for (UserRole requiredRole : requiredRoles) {
                if (!hasRoleLevel(currentUserRole, requiredRole)) {
                    return false;
                }
            }
            return true;
        }

        for (UserRole requiredRole : requiredRoles) {
            if (hasRoleLevel(currentUserRole, requiredRole)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRoleLevel(UserRole currentRole, UserRole requiredRole) {
        return roleLevel(currentRole) >= roleLevel(requiredRole);
    }

    private int roleLevel(UserRole role) {
        switch (role) {
            case SUPER_ADMIN:
                return 5;
            case ADMIN:
                return 4;
            case MODERATOR:
                return 3;
            case CREATOR:
                return 2;
            case USER:
            default:
                return 1;
        }
    }

    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();
            Object attrUserId = request.getAttribute("userId");
            Long userId = parseUserId(attrUserId);
            if (userId != null) {
                return userId;
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to resolve current user id");
            return null;
        }
    }

    private Long parseUserId(Object userId) {
        if (userId == null) {
            return null;
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        try {
            String value = String.valueOf(userId).trim();
            return value.isEmpty() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId value: {}", userId);
            return null;
        }
    }
}
