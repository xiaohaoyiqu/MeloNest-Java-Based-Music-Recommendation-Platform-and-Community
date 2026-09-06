




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserBehaviorRecord;
import com.haoran.music.mapper.UserBehaviorRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserBehaviorRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;




@Slf4j
@Service
public class UserBehaviorRecordServiceImpl extends ServiceImpl<UserBehaviorRecordMapper, UserBehaviorRecord>
        implements UserBehaviorRecordService {

    @Autowired
    private UserBehaviorRecordMapper userBehaviorRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordBehavior(Long userId, String behaviorType, String targetType, Long targetId,
                               Integer duration, String deviceType, String clientType, String ipAddress) {
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            log.debug("跳过非公共统计账号行为记录: userId={}, behaviorType={}", userId, behaviorType);
            return;
        }

        UserBehaviorRecord record = new UserBehaviorRecord();
        record.setUserId(userId);
        record.setBehaviorType(behaviorType);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setDuration(duration);
        record.setDeviceType(ObjectUtils.isEmpty(deviceType) ? "web" : deviceType);
        record.setClientType(clientType);
        record.setIpAddress(ipAddress);
        record.setBehaviorTime(LocalDateTime.now());

        userBehaviorRecordMapper.insert(record);

        log.debug("记录用户行为: userId={}, behaviorType={}, targetType={}, targetId={}",
                userId, behaviorType, targetType, targetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRecord(List<UserBehaviorRecord> records) {
        if (ObjectUtils.isEmpty(records) || records.isEmpty()) {
            return;
        }

        List<Long> userIds = records.stream()
                .filter(record -> record != null && record.getUserId() != null)
                .map(UserBehaviorRecord::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> usersById = userIds.isEmpty()
                ? new HashMap<>()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<UserBehaviorRecord> validRecords = records.stream()
                .filter(record -> record != null
                        && UserAccountStatusUtil.canContributePublicStats(usersById.get(record.getUserId())))
                .collect(Collectors.toList());
        if (validRecords.isEmpty()) {
            return;
        }

        for (UserBehaviorRecord record : validRecords) {
            if (ObjectUtils.isEmpty(record.getBehaviorTime())) {
                record.setBehaviorTime(LocalDateTime.now());
            }
        }


        this.saveBatch(validRecords);

        log.info("批量记录用户行为成功: count={}", validRecords.size());
    }

    @Override
    public Result<List<UserBehaviorRecord>> getUserBehaviors(Long userId, String behaviorType,
                                                             String startTime, String endTime, Integer limit) {
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            return Result.success(new ArrayList<>());
        }

        LambdaQueryWrapper<UserBehaviorRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBehaviorRecord::getUserId, userId);

        if (ObjectUtils.isNotEmpty(behaviorType)) {
            queryWrapper.eq(UserBehaviorRecord::getBehaviorType, behaviorType);
        }

        if (ObjectUtils.isNotEmpty(startTime)) {
            LocalDateTime start = LocalDateTime.parse(startTime + " 00:00:00",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            queryWrapper.ge(UserBehaviorRecord::getBehaviorTime, start);
        }

        if (ObjectUtils.isNotEmpty(endTime)) {
            LocalDateTime end = LocalDateTime.parse(endTime + " 23:59:59",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            queryWrapper.le(UserBehaviorRecord::getBehaviorTime, end);
        }

        queryWrapper.orderByDesc(UserBehaviorRecord::getBehaviorTime);

        if (ObjectUtils.isNotEmpty(limit) && limit > 0) {
            queryWrapper.last("LIMIT " + limit);
        }

        List<UserBehaviorRecord> records = userBehaviorRecordMapper.selectList(queryWrapper);

        return Result.success(records);
    }

    @Override
    public Result<Map<String, Object>> getUserBehaviorStats(Long userId, String startDate, String endDate) {
        Map<String, Object> stats = new HashMap<>();
        if (!UserAccountStatusUtil.canContributePublicStats(userId, userMapper::selectById)) {
            stats.put("totalCount", 0L);
            stats.put("behaviorTypeStats", new HashMap<>());
            stats.put("targetTypeStats", new HashMap<>());
            stats.put("totalDuration", 0L);
            stats.put("deviceTypeStats", new HashMap<>());
            return Result.success(stats);
        }

        LocalDateTime start = ObjectUtils.isNotEmpty(startDate)
                ? LocalDate.parse(startDate).atStartOfDay() : null;
        LocalDateTime end = ObjectUtils.isNotEmpty(endDate)
                ? LocalDate.parse(endDate).plusDays(1).atStartOfDay() : null;

        Map<String, Long> behaviorTypeStats = new HashMap<>();
        Map<String, Long> targetTypeStats = new HashMap<>();
        Map<String, Long> deviceTypeStats = new HashMap<>();
        long totalCount = 0L;
        long totalDuration = 0L;
        for (Map<String, Object> row : userBehaviorRecordMapper.selectPublicUserBehaviorStats(userId, start, end)) {
            long count = toLong(row.get("behaviorCount"));
            totalCount += count;
            totalDuration += toLong(row.get("totalDuration"));
            behaviorTypeStats.merge(toStatKey(row.get("behaviorType")), count, Long::sum);
            targetTypeStats.merge(toStatKey(row.get("targetType")), count, Long::sum);
            deviceTypeStats.merge(toStatKey(row.get("deviceType")), count, Long::sum);
        }

        stats.put("totalCount", totalCount);
        stats.put("behaviorTypeStats", behaviorTypeStats);
        stats.put("targetTypeStats", targetTypeStats);
        stats.put("totalDuration", totalDuration);
        stats.put("deviceTypeStats", deviceTypeStats);

        return Result.success(stats);
    }

    @Override
    public Result<Map<String, Object>> getTargetBehaviorStats(String targetType, Long targetId) {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> totals = userBehaviorRecordMapper.selectPublicTargetBehaviorTotals(targetType, targetId);
        Map<String, Long> behaviorTypeStats = new HashMap<>();
        for (Map<String, Object> row : userBehaviorRecordMapper
                .selectPublicTargetBehaviorTypeStats(targetType, targetId)) {
            behaviorTypeStats.merge(toStatKey(row.get("behaviorType")),
                    toLong(row.get("behaviorCount")), Long::sum);
        }

        stats.put("totalCount", toLong(totals == null ? null : totals.get("totalCount")));
        stats.put("behaviorTypeStats", behaviorTypeStats);
        stats.put("playCount", toLong(totals == null ? null : totals.get("playCount")));
        stats.put("likeCount", toLong(totals == null ? null : totals.get("likeCount")));
        stats.put("totalDuration", toLong(totals == null ? null : totals.get("totalDuration")));
        stats.put("uniqueUsers", toLong(totals == null ? null : totals.get("uniqueUsers")));

        return Result.success(stats);
    }

    @Override
    public Result<List<Map<String, Object>>> getHotResources(String targetType, String behaviorType, Integer limit) {
        if (ObjectUtils.isEmpty(limit) || limit <= 0) {
            limit = 50;
        }

        List<Map<String, Object>> result = userBehaviorRecordMapper
                .selectPublicHotResources(targetType, behaviorType, limit);
        for (Map<String, Object> item : result) {
            item.put("targetType", targetType);
        }
        return Result.success(result);
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String toStatKey(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanOldRecords(Integer days) {
        if (ObjectUtils.isEmpty(days) || days <= 0) {
            days = 90;           
        }

        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);


        LambdaQueryWrapper<UserBehaviorRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(UserBehaviorRecord::getBehaviorTime, expireTime);

        int count = userBehaviorRecordMapper.delete(queryWrapper);

        log.info("清理过期行为记录完成: days={}, count={}", days, count);
    }

    @Override
    @Async
    public void recordBehaviorAsync(Long userId, String behaviorType, String targetType, Long targetId, Integer duration) {
        try {
            recordBehavior(userId, behaviorType, targetType, targetId, duration, "web", null, null);
        } catch (Exception e) {
            log.error("异步记录用户行为失败: userId={}, behaviorType={}, error={}",
                    userId, behaviorType, e.getClass().getSimpleName());
        }
    }
}
