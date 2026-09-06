




package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserVisit;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.mapper.UserVisitMapper;
import com.haoran.music.service.UserVisitService;
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




@Slf4j
@Service
public class UserVisitServiceImpl extends ServiceImpl<UserVisitMapper, UserVisit>
        implements UserVisitService {

    @Autowired
    private UserVisitMapper userVisitMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordVisit(Long visitorId, Long visitedUserId, String visitSource, String ipAddress) {

        if (ObjectUtils.isEmpty(visitorId) || ObjectUtils.isEmpty(visitedUserId) || visitorId.equals(visitedUserId)) {
            return;
        }

        UserVisit visit = new UserVisit();
        visit.setVisitorId(visitorId);
        visit.setVisitedUserId(visitedUserId);
        visit.setVisitTime(LocalDateTime.now());
        visit.setIpAddress(ipAddress);
        visit.setVisitSource(ObjectUtils.isEmpty(visitSource) ? "profile" : visitSource);

        userVisitMapper.insert(visit);

        log.debug("记录访问: visitorId={}, visitedUserId={}, source={}", visitorId, visitedUserId, visitSource);
    }

    @Override
    public Result<IPage<UserVisit>> getVisitRecords(Long visitorId, PageQuery query) {
        Page<UserVisit> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<UserVisit> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserVisit::getVisitorId, visitorId)
                .orderByDesc(UserVisit::getVisitTime);

        IPage<UserVisit> result = userVisitMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<IPage<UserVisit>> getVisitorRecords(Long visitedUserId, PageQuery query) {
        Page<UserVisit> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<UserVisit> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserVisit::getVisitedUserId, visitedUserId)
                .orderByDesc(UserVisit::getVisitTime);

        IPage<UserVisit> result = userVisitMapper.selectPage(page, queryWrapper);

        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> getVisitStats(Long userId) {
        Map<String, Object> aggregate = userVisitMapper.selectVisitStats(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVisitCount", longValue(aggregate, "totalVisitCount"));
        stats.put("totalVisitorCount", longValue(aggregate, "totalVisitorCount"));
        stats.put("uniqueVisitedCount", longValue(aggregate, "uniqueVisitedCount"));
        stats.put("uniqueVisitorCount", longValue(aggregate, "uniqueVisitorCount"));

        return Result.success(stats);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanOldVisits(Integer days) {
        if (ObjectUtils.isEmpty(days) || days <= 0) {
            days = 90;
        }

        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);

        LambdaQueryWrapper<UserVisit> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(UserVisit::getVisitTime, expireTime);

        int count = userVisitMapper.delete(queryWrapper);

        log.info("清理过期访问记录完成: days={}, count={}", days, count);
    }

    @Override
    public Result<List<Map<String, Object>>> getRecentVisitors(Long userId, Integer limit) {
        if (ObjectUtils.isEmpty(limit) || limit <= 0) {
            limit = 10;
        }
        limit = Math.min(limit, 100);

        LambdaQueryWrapper<UserVisit> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserVisit::getVisitedUserId, userId)
                .orderByDesc(UserVisit::getVisitTime)
                .last("LIMIT " + limit);

        List<UserVisit> visits = userVisitMapper.selectList(queryWrapper);
        List<Long> visitorIds = visits.stream()
                .map(UserVisit::getVisitorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> visitorsById = userMapper.selectBatchIds(visitorIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserVisit visit : visits) {
            Map<String, Object> item = new HashMap<>();
            item.put("visitTime", visit.getVisitTime());
            item.put("visitSource", visit.getVisitSource());


            User visitor = visitorsById.get(visit.getVisitorId());
            if (ObjectUtils.isNotEmpty(visitor)) {
                item.put("visitorId", visitor.getId());
                item.put("visitorName", visitor.getNickname());
                item.put("visitorAvatar", visitor.getAvatar());
            }

            result.add(item);
        }

        return Result.success(result);
    }

    private long longValue(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return 0L;
        }
        Object value = row.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse visit statistic {}={}", key, value);
            return 0L;
        }
    }
}
