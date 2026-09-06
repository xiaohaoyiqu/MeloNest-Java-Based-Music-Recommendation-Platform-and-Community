package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.common.vo.ModeratorVO;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ModeratorManagementService;
import com.haoran.music.service.ModerationAssignmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

   
                      
                         
   
@Slf4j
@Service
public class ModeratorManagementServiceImpl implements ModeratorManagementService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ModerationAssignmentService assignmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setAsModerator(Long userId, String moderatorNote, Integer dailyQuota) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("设置审核员失败：用户不存在, userId={}", userId);
            return false;
        }
        AdminAccountOperationGuard.requireCanAssignRole(
                getCurrentOperator(), user, UserRole.MODERATOR);

                 
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getIsModerator, 1)
                        .set(User::getModeratorStatus, "active")
                        .set(User::getModeratorNote, moderatorNote)
                        .set(User::getDailyQuota, dailyQuota != null ? dailyQuota : 100)
        );

        log.info("event=moderator_granted userId={}", userId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeModerator(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("取消审核员失败：用户不存在, userId={}", userId);
            return false;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(
                getCurrentOperator(), user, "取消审核员资格");

                 
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getIsModerator, 0)
                        .set(User::getModeratorStatus, "inactive")
        );

        log.info("event=moderator_revoked userId={}", userId);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateModeratorStatus(Long userId, String moderatorStatus) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新审核员状态失败：用户不存在, userId={}", userId);
            return false;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(
                getCurrentOperator(), user, "更新审核员状态");

        if (!Arrays.asList("active", "inactive", "suspended").contains(moderatorStatus)) {
            log.warn("更新审核员状态失败：无效状态, userId={}, status={}", userId, moderatorStatus);
            return false;
        }

        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getModeratorStatus, moderatorStatus)
        );

        log.info("更新审核员状态: userId={}, status={}", userId, moderatorStatus);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateModeratorInfo(Long userId, String moderatorNote, Integer dailyQuota) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("更新审核员信息失败：用户不存在, userId={}", userId);
            return false;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(
                getCurrentOperator(), user, "更新审核员信息");

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId);

        if (moderatorNote != null) {
            wrapper.set(User::getModeratorNote, moderatorNote);
        }
        if (dailyQuota != null) {
            wrapper.set(User::getDailyQuota, dailyQuota);
        }

        userMapper.update(null, wrapper);

        log.info("更新审核员信息: userId={}, note={}, quota={}",
                userId, moderatorNote, dailyQuota);

        return true;
    }

    @Override
    public List<ModeratorVO> getAllModerators(String status) {
        return selectModerationUsers(status).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public IPage<ModeratorVO> getModeratorPage(String status, PageQuery pageQuery) {
        List<User> users = selectModerationUsers(status);
        long pageNum = pageQuery.getPageNum();
        long pageSize = pageQuery.getPageSize();
        long fromIndex = (pageNum - 1) * pageSize;
        long toIndex = Math.min(fromIndex + pageSize, users.size());

        List<ModeratorVO> records = fromIndex >= users.size()
                ? Collections.emptyList()
                : users.subList((int) fromIndex, (int) toIndex).stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

        Page<ModeratorVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(users.size());
        result.setRecords(records);
        return result;
    }

    @Override
    public ModeratorVO getModeratorDetail(Long userId) {
        User user = userMapper.selectById(userId);
        if (!canModerate(user)) {
            return null;
        }
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetTodayQuota(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(
                getCurrentOperator(), user, "重置审核员配额");
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getTodayReviewCount, 0)
        );

        log.info("重置审核员今日配额: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSetAsModerator(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Long userId : userIds) {
            if (setAsModerator(userId, null, 100)) {
                count++;
            }
        }

        log.info("批量设置审核员: 总数={}, 成功={}", userIds.size(), count);

        return count;
    }

    private User getCurrentOperator() {
        Long operatorId = UserContext.getCurrentUserId();
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作账号不存在");
        }
        return operator;
    }

       
            
       
    private List<User> selectModerationUsers(String status) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getDeleted, 0)
                        .orderByDesc(User::getTotalReviewCount)
        ).stream()
                .filter(this::canModerate)
                .filter(user -> matchesStatus(user, status))
                .collect(Collectors.toList());
    }

    private boolean canModerate(User user) {
        if (user == null) {
            return false;
        }
        if (UserRole.canModerate(user.getRole())) {
            return true;
        }
        return Integer.valueOf(1).equals(user.getIsModerator())
                && "active".equals(user.getModeratorStatus());
    }

    private boolean matchesStatus(User user, String status) {
        if (status == null || status.isEmpty()) {
            return true;
        }
        if (status.equals(user.getModeratorStatus())) {
            return true;
        }
        return "active".equals(status) && UserRole.canModerate(user.getRole());
    }
    private ModeratorVO convertToVO(User user) {
        ModeratorVO vo = new ModeratorVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setModeratorStatus(user.getModeratorStatus());
        vo.setModeratorNote(user.getModeratorNote());
        vo.setIsOnline(user.getIsOnline());
        vo.setLastOnlineTime(user.getLastOnlineTime());
        vo.setTodayReviewCount(user.getTodayReviewCount() != null ? user.getTodayReviewCount() : 0);
        vo.setTotalReviewCount(user.getTotalReviewCount() != null ? user.getTotalReviewCount() : 0);
        vo.setDailyQuota(user.getDailyQuota() != null ? user.getDailyQuota() : 100);
        vo.setLastReviewTime(user.getLastReviewTime());

                  
        Map<Long, Integer> loads = assignmentService.getAllModeratorLoads();
        vo.setCurrentTaskCount(loads.getOrDefault(user.getId(), 0));

                
        vo.setHasQuota(assignmentService.hasQuota(user.getId()));

                  
        Integer dailyQuota = user.getDailyQuota() != null ? user.getDailyQuota() : 100;
        Integer todayCount = user.getTodayReviewCount() != null ? user.getTodayReviewCount() : 0;
        if (dailyQuota > 0) {
            vo.setTodayCompletionRate(todayCount.doubleValue() / dailyQuota);
        } else {
            vo.setTodayCompletionRate(0.0);
        }

                 
        if (Integer.valueOf(1).equals(user.getIsOnline())) {
            vo.setOnlineStatus("在线");
        } else {
            vo.setOnlineStatus("离线");
        }

        return vo;
    }
}
