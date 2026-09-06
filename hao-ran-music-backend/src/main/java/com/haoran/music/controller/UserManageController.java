


package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.constant.UserAccountPolicyConstants;
import com.haoran.music.common.enums.UserType;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.user.UserStatisticsDTO;
import com.haoran.music.dto.user.UserTypeUpdateDTO;
import com.haoran.music.entity.User;
import com.haoran.music.entity.UserStatistics;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.SimpleUserClassificationService;
import com.haoran.music.service.UserMonitoringService;
import com.haoran.music.service.UserStatisticsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;




@RestController
@RequestMapping("/user")
public class UserManageController {

    @Resource
    private SimpleUserClassificationService userClassificationService;

    @Resource
    private UserStatisticsService userStatisticsService;

    @Resource
    private UserMonitoringService userMonitoringService;

    @Resource
    private UserMapper userMapper;




    @ApiLog("获取用户类型")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/type/{userId}")
    public Result<Object> getUserType(@PathVariable("userId") Long userId) {
        String userType = userClassificationService.getUserType(userId).getDescription();
        return Result.successData(userType);
    }




    @ApiLog("更新用户类型")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/manage/type/update")
    public Result<Void> updateUserType(@RequestBody Map<String, Object> params) {
        UserTypeUpdateDTO dto = toUserTypeUpdateDTO(params);
        if (dto == null || dto.getUserId() == null || dto.getUserType() == null) {
            return Result.error(400, "用户ID和用户类型不能为空");
        }
        if (dto.getReason() != null && dto.getReason().length() > 500) {
            return Result.error(400, "变更原因不能超过500个字符");
        }
        if (!UserType.isKnownCode(dto.getUserType())) {
            return Result.error(400, "不支持的用户类型");
        }
        UserType userType = UserType.fromCode(dto.getUserType());
        userClassificationService.updateUserType(dto.getUserId(), userType, dto.getReason());
        return Result.success();
    }

    private UserTypeUpdateDTO toUserTypeUpdateDTO(Map<String, Object> params) {
        if (params == null || !(params.get("reason") == null || params.get("reason") instanceof String)) {
            return null;
        }
        Long userId = parseLong(params.get("userId"));
        Integer userType = parseInteger(params.get("userType"));
        if (userId == null || userId <= 0 || userType == null) {
            return null;
        }
        UserTypeUpdateDTO dto = new UserTypeUpdateDTO();
        dto.setUserId(userId);
        dto.setUserType(userType);
        dto.setReason((String) params.get("reason"));
        return dto;
    }

    private Long parseLong(Object value) {
        try {
            return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        try {
            return value instanceof Number ? ((Number) value).intValue() : Integer.valueOf(String.valueOf(value));
        } catch (RuntimeException e) {
            return null;
        }
    }




    @ApiLog("获取用户统计数据")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/statistics/{userId}")
    public Result<List<UserStatisticsDTO>> getUserStatistics(
            @PathVariable("userId") Long userId,
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        List<UserStatistics> stats = userStatisticsService.getStatsByDateRange(userId, startDate, endDate);
        List<UserStatisticsDTO> dtoList = stats.stream().map(stat -> {
            UserStatisticsDTO dto = new UserStatisticsDTO();
            BeanUtils.copyProperties(stat, dto);
            dto.setStatDate(stat.getStatDate() == null ? null : stat.getStatDate().toString());
            dto.setIsAbnormal(Integer.valueOf(1).equals(stat.getIsAbnormal()));
            return dto;
        }).collect(Collectors.toList());
        return Result.success(dtoList);
    }




    @ApiLog("手动执行用户分类")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/manage/classify/run")
    public Result<String> runClassification() {
        userClassificationService.dailyUserClassification();
        return Result.success("用户分类任务已执行");
    }




    @ApiLog("检查用户是否为机器人")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/check-bot/{userId}")
    public Result<Boolean> checkBotUser(@PathVariable("userId") Long userId) {
        Boolean isBot = userClassificationService.isBotUser(userId);
        return Result.success(isBot);
    }




    @ApiLog("获取后台用户监控系统总览")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/monitoring/overview")
    public Result<Map<String, Object>> getMonitoringOverview(@RequestParam(defaultValue = "30") Integer days,
                                                             @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(userMonitoringService.getSystemMonitoringOverview(days, limit));
    }




    @ApiLog("获取单个用户监控总览")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/monitoring/overview/{userId}")
    public Result<Map<String, Object>> getUserMonitoringOverview(@PathVariable("userId") Long userId,
                                                                 @RequestParam(defaultValue = "30") Integer days) {
        return Result.success(userMonitoringService.getUserMonitoringOverview(userId, days));
    }




    @ApiLog("获取系统用户统计概览")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/manage/overview")
    public Result<Map<String, Object>> getOverview() {
        Long totalUsers = userMapper.selectCount(null);
        Long publicEligibleUsers = userMapper.selectCount(UserAccountStatusUtil.publicStatsUserQuery());
        LambdaQueryWrapper<User> highRiskWrapper = new LambdaQueryWrapper<>();
        highRiskWrapper.ge(User::getRiskScore, UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD);
        Long highRiskUsers = userMapper.selectCount(highRiskWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("date", LocalDate.now().toString());
        result.put("normalCount", publicEligibleUsers);
        result.put("abnormalCount", Math.max(0L, totalUsers - publicEligibleUsers));
        result.put("highRiskCount", highRiskUsers);
        result.put("highRiskThreshold", UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD);
        result.put("publicEligibleCount", publicEligibleUsers);
        return Result.success(result);
    }
}
