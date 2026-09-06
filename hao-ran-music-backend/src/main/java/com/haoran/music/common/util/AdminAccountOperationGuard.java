   
                      
   
package com.haoran.music.common.util;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;

   
                                                                         
   
public final class AdminAccountOperationGuard {

    private AdminAccountOperationGuard() {
    }

    public static boolean isPrivilegedAccount(User user) {
        return user != null
                && (UserRole.canModerate(user.getRole()) || Integer.valueOf(1).equals(user.getIsModerator()));
    }

    public static boolean isSuperAdmin(User user) {
        return user != null && roleOf(user) == UserRole.SUPER_ADMIN;
    }

    public static void requireCanOperateAccount(User operator, User target, String action) {
        requireOperator(operator);
        requireTarget(target);

        if (!isPrivilegedAccount(target)) {
            return;
        }

        String safeAction = safeAction(action);
        if (sameUser(operator, target)) {
            throw forbidden("不能" + safeAction + "当前登录的后台账号");
        }

        if (roleOf(target) == UserRole.SUPER_ADMIN) {
            throw forbidden("超级管理员账号不能通过此后台操作被直接影响");
        }

        if (isSuperAdmin(operator)) {
            return;
        }

        throw forbidden("只有超级管理员可以" + safeAction + "审核员或管理员账号");
    }

    public static void requireCanAssignRole(User operator, User target, UserRole assignedRole) {
        requireCanOperateAccount(operator, target, "更新用户角色");

        UserRole safeAssignedRole = assignedRole == null ? UserRole.USER : assignedRole;
        if (safeAssignedRole == UserRole.SUPER_ADMIN) {
            throw forbidden("不能通过普通后台用户管理接口授予超级管理员角色");
        }

        if (isPrivilegedRole(safeAssignedRole) && !isSuperAdmin(operator)) {
            throw forbidden("只有超级管理员可以授予审核员或管理员角色");
        }
    }

    public static boolean isPrivilegedRole(UserRole role) {
        return role == UserRole.MODERATOR || role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }

    private static void requireOperator(User operator) {
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作账号不存在");
        }
    }

    private static void requireTarget(User target) {
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
    }

    private static UserRole roleOf(User user) {
        return user == null ? UserRole.USER : UserRole.fromCode(user.getRole());
    }

    private static boolean sameUser(User operator, User target) {
        return operator != null
                && target != null
                && operator.getId() != null
                && operator.getId().equals(target.getId());
    }

    private static String safeAction(String action) {
        return action == null || action.trim().isEmpty() ? "操作" : action.trim();
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(ResultCode.FORBIDDEN, message);
    }
}
