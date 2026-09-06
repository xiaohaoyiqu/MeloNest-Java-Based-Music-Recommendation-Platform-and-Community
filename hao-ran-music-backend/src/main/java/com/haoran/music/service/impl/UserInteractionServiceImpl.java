




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserInteraction;
import com.haoran.music.mapper.UserInteractionMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserInteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;




@Slf4j
@Service
public class UserInteractionServiceImpl extends ServiceImpl<UserInteractionMapper, UserInteraction>
        implements UserInteractionService {

    @Autowired
    private UserInteractionMapper userInteractionMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordInteraction(Long userId, Long targetUserId, String interactionType,
                                  String targetType, Long targetId) {

        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(targetUserId) || userId.equals(targetUserId)) {
            return;
        }
        if (!canContributeUserInteraction(userId, targetUserId)) {
            log.debug("跳过非公共统计账号互动记录: userId={}, targetUserId={}", userId, targetUserId);
            return;
        }

        UserInteraction interaction = new UserInteraction();
        interaction.setUserId(userId);
        interaction.setTargetUserId(targetUserId);
        interaction.setInteractionType(ObjectUtils.isEmpty(interactionType) ? "like" : interactionType);
        interaction.setTargetType(targetType);
        interaction.setTargetId(targetId);
        interaction.setInteractionTime(LocalDateTime.now());

        userInteractionMapper.insert(interaction);

        log.debug("记录互动: userId={}, targetUserId={}, type={}", userId, targetUserId, interactionType);
    }

    @Override
    public Result<IPage<UserInteraction>> getUserInteractions(Long userId, String interactionType, PageQuery query) {
        Page<UserInteraction> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<UserInteraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserInteraction::getUserId, userId);

        if (ObjectUtils.isNotEmpty(interactionType)) {
            queryWrapper.eq(UserInteraction::getInteractionType, interactionType);
        }

        queryWrapper.orderByDesc(UserInteraction::getInteractionTime);

        IPage<UserInteraction> result = userInteractionMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<IPage<UserInteraction>> getInteractionsWithUser(Long userId, Long targetUserId, PageQuery query) {
        Page<UserInteraction> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<UserInteraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq(UserInteraction::getUserId, userId)
                .eq(UserInteraction::getTargetUserId, targetUserId)
        ).or(wrapper -> wrapper
                .eq(UserInteraction::getUserId, targetUserId)
                .eq(UserInteraction::getTargetUserId, userId)
        );

        queryWrapper.orderByDesc(UserInteraction::getInteractionTime);

        IPage<UserInteraction> result = userInteractionMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> getInteractionStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            stats.put("totalCount", 0L);
            stats.put("interactionTypeStats", new HashMap<>());
            stats.put("targetTypeStats", new HashMap<>());
            stats.put("uniqueUsersCount", 0L);
            return Result.success(stats);
        }

        Map<String, Long> interactionTypeStats = new HashMap<>();
        Map<String, Long> targetTypeStats = new HashMap<>();
        List<Map<String, Object>> groupedRows = userInteractionMapper.selectPublicInteractionTypeStats(userId);
        for (Map<String, Object> row : groupedRows) {
            long count = toLong(row.get("interactionCount"));
            interactionTypeStats.merge(toStatKey(row.get("interactionType")), count, Long::sum);
            targetTypeStats.merge(toStatKey(row.get("targetType")), count, Long::sum);
        }

        Map<String, Object> totals = userInteractionMapper.selectPublicInteractionTotals(userId);
        stats.put("totalCount", toLong(totals == null ? null : totals.get("totalCount")));
        stats.put("interactionTypeStats", interactionTypeStats);
        stats.put("targetTypeStats", targetTypeStats);
        stats.put("uniqueUsersCount", toLong(totals == null ? null : totals.get("uniqueUsersCount")));

        return Result.success(stats);
    }

    @Override
    public Result<List<Map<String, Object>>> getMostInteractedUsers(Long userId, Integer limit) {
        if (ObjectUtils.isEmpty(limit) || limit <= 0) {
            limit = 10;
        }
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            return Result.success(new ArrayList<>());
        }

        return Result.success(userInteractionMapper.selectMostInteractedPublicUsers(userId, limit));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanOldInteractions(Integer days) {
        if (ObjectUtils.isEmpty(days) || days <= 0) {
            days = 90;
        }

        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<UserInteraction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(UserInteraction::getInteractionTime, expireTime);

        int count = userInteractionMapper.delete(queryWrapper);

        log.info("清理过期互动记录完成: days={}, count={}", days, count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRecord(List<UserInteraction> interactions) {
        if (ObjectUtils.isEmpty(interactions) || interactions.isEmpty()) {
            return;
        }

        List<Long> userIds = interactions.stream()
                .filter(interaction -> interaction != null)
                .flatMap(interaction -> Stream.of(
                        interaction.getUserId(), interaction.getTargetUserId()))
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> usersById = loadUsersByIds(userIds);

        List<UserInteraction> validInteractions = interactions.stream()
                .filter(interaction -> canContributeUserInteraction(interaction, usersById))
                .collect(Collectors.toList());
        for (UserInteraction interaction : validInteractions) {
            if (ObjectUtils.isEmpty(interaction.getInteractionTime())) {
                interaction.setInteractionTime(LocalDateTime.now());
            }
        }
        if (!validInteractions.isEmpty()) {
            saveBatch(validInteractions);
        }

        log.info("批量记录互动成功: count={}", validInteractions.size());
    }

    private boolean canContributeUserInteraction(Long userId, Long targetUserId) {
        if (ObjectUtils.isEmpty(userId) || ObjectUtils.isEmpty(targetUserId)) {
            return false;
        }
        Map<Long, User> usersById = loadUsersByIds(Stream.of(userId, targetUserId)
                .distinct()
                .collect(Collectors.toList()));
        return UserAccountStatusUtil.canContributePublicStats(usersById.get(userId))
                && UserAccountStatusUtil.canContributePublicStats(usersById.get(targetUserId));
    }

    private boolean canContributeUserInteraction(UserInteraction interaction, Map<Long, User> usersById) {
        if (interaction == null || usersById == null) {
            return false;
        }
        return UserAccountStatusUtil.canContributePublicStats(usersById.get(interaction.getUserId()))
                && UserAccountStatusUtil.canContributePublicStats(usersById.get(interaction.getTargetUserId()));
    }

    private Map<Long, User> loadUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String toStatKey(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }
}
