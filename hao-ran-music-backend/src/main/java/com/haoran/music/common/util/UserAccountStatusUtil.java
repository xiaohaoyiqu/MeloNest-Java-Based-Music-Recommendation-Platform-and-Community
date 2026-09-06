


package com.haoran.music.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class UserAccountStatusUtil {

    private static final Integer BANNED_STATUS = 2;
    private static final Integer BANNED_FLAG = 1;
    private static final int HIGH_RISK_SCORE_THRESHOLD = UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD;
    private static final int NORMAL_CREDIT_MIN_SCORE = UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE;

    private UserAccountStatusUtil() {
    }

    public static boolean canInteract(User user) {
        return user != null
                && !CommonConstants.DELETED.equals(user.getDeleted())
                && CommonConstants.STATUS_NORMAL.equals(user.getStatus())
                && !BANNED_FLAG.equals(user.getIsBanned())
                && !isRestrictedType(user);
    }





    public static boolean canAuthenticate(User user) {
        return user != null
                && !CommonConstants.DELETED.equals(user.getDeleted())
                && !isBanned(user)
                && !isRestrictedType(user)
                && (CommonConstants.STATUS_NORMAL.equals(user.getStatus()) || isFrozen(user));
    }

    public static boolean canInteract(Long userId, Function<Long, User> userLookup) {
        return userId != null && userLookup != null && canInteract(userLookup.apply(userId));
    }




    public static LambdaQueryWrapper<User> interactableUserQuery() {
        return new LambdaQueryWrapper<User>()
                .eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.isNull(User::getIsBanned).or().eq(User::getIsBanned, CommonConstants.NO))
                .and(w -> w.isNull(User::getUserType).or().in(User::getUserType, UserType.nonRestrictedCodes()));
    }




    public static boolean canAppearInRecommendations(User user) {
        return canInteract(user) && !isRecommendationRisk(user);
    }





    public static boolean canRetainPublicContent(User user) {
        return user != null
                && !CommonConstants.DELETED.equals(user.getDeleted())
                && (isBanned(user)
                || isFrozen(user)
                || canExposePublicContent(user));
    }

    public static boolean canRetainPublicContent(Long userId, Function<Long, User> userLookup) {
        return userId != null && userLookup != null && canRetainPublicContent(userLookup.apply(userId));
    }

    public static Set<Long> filterRetainedPublicContentUserIds(Collection<Long> userIds,
                                                                Function<Collection<Long>, Collection<User>> userLookup) {
        return filterUserIds(userIds, userLookup, UserAccountStatusUtil::canRetainPublicContent);
    }

    public static boolean canAppearInRecommendations(Long userId, Function<Long, User> userLookup) {
        return userId != null && userLookup != null && canAppearInRecommendations(userLookup.apply(userId));
    }

    public static Set<Long> filterRecommendationUserIds(Collection<Long> userIds,
                                                        Function<Collection<Long>, Collection<User>> userLookup) {
        return filterUserIds(userIds, userLookup, UserAccountStatusUtil::canAppearInRecommendations);
    }




    public static boolean canContributePublicStats(User user) {
        return canAppearInRecommendations(user);
    }

    public static boolean canContributePublicStats(Long userId, Function<Long, User> userLookup) {
        return userId != null && userLookup != null && canContributePublicStats(userLookup.apply(userId));
    }

    public static Set<Long> filterPublicStatsUserIds(Collection<Long> userIds,
                                                     Function<Collection<Long>, Collection<User>> userLookup) {
        return filterUserIds(userIds, userLookup, UserAccountStatusUtil::canContributePublicStats);
    }




    public static boolean canExposePublicContent(User user) {
        return canContributePublicStats(user)
                && (user.getCreatorStatus() == null || !"suspended".equalsIgnoreCase(user.getCreatorStatus()));
    }

    public static boolean canExposePublicContent(Long userId, Function<Long, User> userLookup) {
        return userId != null && userLookup != null && canExposePublicContent(userLookup.apply(userId));
    }

    public static Set<Long> filterPublicContentUserIds(Collection<Long> userIds,
                                                       Function<Collection<Long>, Collection<User>> userLookup) {
        return filterUserIds(userIds, userLookup, UserAccountStatusUtil::canExposePublicContent);
    }




    public static LambdaQueryWrapper<User> publicStatsUserQuery() {
        return applyPublicStatsUserFilter(new LambdaQueryWrapper<>());
    }

    public static LambdaQueryWrapper<User> applyPublicStatsUserFilter(LambdaQueryWrapper<User> wrapper) {
        LambdaQueryWrapper<User> safeWrapper = wrapper == null ? new LambdaQueryWrapper<>() : wrapper;
        return safeWrapper.eq(User::getStatus, CommonConstants.STATUS_NORMAL)
                .eq(User::getDeleted, CommonConstants.NOT_DELETED)
                .and(w -> w.isNull(User::getIsBanned).or().eq(User::getIsBanned, CommonConstants.NO))
                .and(w -> w.isNull(User::getUserType).or().in(User::getUserType, UserType.nonRestrictedCodes()))
                .and(w -> w.isNull(User::getRiskScore).or().lt(User::getRiskScore, HIGH_RISK_SCORE_THRESHOLD))
                .and(w -> w.isNull(User::getCreditScore).or().ge(User::getCreditScore, NORMAL_CREDIT_MIN_SCORE));
    }




    public static LambdaQueryWrapper<User> publicContentUserQuery() {
        return applyPublicContentUserFilter(new LambdaQueryWrapper<>());
    }

    public static LambdaQueryWrapper<User> applyPublicContentUserFilter(LambdaQueryWrapper<User> wrapper) {
        return applyPublicStatsUserFilter(wrapper)
                .and(w -> w.isNull(User::getCreatorStatus).or()
                        .ne(User::getCreatorStatus, UserAccountPolicyConstants.CREATOR_STATUS_SUSPENDED));
    }

    public static void requireCanInteract(User user, String action) {
        if (!canInteract(user)) {
            String safeAction = action == null || action.trim().isEmpty() ? "执行此操作" : action;
            throw new BusinessException(ResultCode.FORBIDDEN,
                    currentUnavailableMessage(user) + "，无法" + safeAction);
        }
    }

    public static void requireCanInteract(Long userId, Function<Long, User> userLookup, String action) {
        requireCanInteract(userLookup == null || userId == null ? null : userLookup.apply(userId), action);
    }

    public static String targetUnavailableMessage(User user) {
        if (user == null || CommonConstants.DELETED.equals(user.getDeleted())) {
            return "用户不存在";
        }
        if (isBanned(user)) {
            return "该用户已被封禁";
        }
        if (isFrozen(user)) {
            return "该用户已被冻结";
        }
        return "该用户账号异常";
    }

    public static String currentUnavailableMessage(User user) {
        if (user == null || CommonConstants.DELETED.equals(user.getDeleted())) {
            return "当前账号不存在";
        }
        if (isBanned(user)) {
            return "当前账号已被封禁";
        }
        if (isFrozen(user)) {
            return "当前账号已被冻结";
        }
        return "当前账号异常";
    }

    public static boolean isBanned(User user) {
        return user != null
                && (BANNED_FLAG.equals(user.getIsBanned())
                || BANNED_STATUS.equals(user.getStatus())
                || UserType.BANNED.getCode().equals(user.getUserType()));
    }

    public static boolean isFrozen(User user) {
        return user != null && CommonConstants.STATUS_DISABLED.equals(user.getStatus());
    }

    public static boolean isRestrictedType(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return !UserType.isKnownCode(user.getUserType())
                || UserType.fromCode(user.getUserType()).shouldRestrict();
    }

    private static boolean isRecommendationRisk(User user) {
        if (user == null) {
            return true;
        }

        UserType userType = UserType.fromCode(user.getUserType());
        if (userType.shouldRestrict()) {
            return true;
        }

        Integer creditScore = user.getCreditScore();
        if (creditScore != null && creditScore < NORMAL_CREDIT_MIN_SCORE) {
            return true;
        }

        Integer riskScore = user.getRiskScore();
        return riskScore != null && riskScore >= HIGH_RISK_SCORE_THRESHOLD;
    }

    private static Set<Long> filterUserIds(Collection<Long> userIds,
                                           Function<Collection<Long>, Collection<User>> userLookup,
                                           Predicate<User> predicate) {
        if (userIds == null || userIds.isEmpty() || userLookup == null || predicate == null) {
            return Collections.emptySet();
        }
        Set<Long> safeUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (safeUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        Collection<User> users = userLookup.apply(safeUserIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptySet();
        }
        return users.stream()
                .filter(predicate)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
