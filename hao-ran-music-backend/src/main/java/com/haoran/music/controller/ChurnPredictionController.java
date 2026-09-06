package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.ChurnPredictionService;
import com.haoran.music.vo.user.ChurnPredictionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

   
                      
                         
   
@Slf4j
@RestController
@RequestMapping("/api/churn")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class ChurnPredictionController {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_RESULT_LIMIT = 100;
    private static final int MAX_ANALYSIS_DAYS = 365;

    @Autowired
    private ChurnPredictionService churnPredictionService;

       
               
      
                         
                     
  
    @ApiLog("预测用户流失风险")
    @GetMapping("/predict/{userId}")
    public Result<ChurnPredictionVO> predictUserChurn(@PathVariable Long userId) {
        ChurnPredictionVO prediction = churnPredictionService.predictUserChurn(userId);
        return Result.success(prediction);
    }

       
                 
      
                            
                       
  
    @ApiLog("批量预测用户流失风险")
    @PostMapping("/batch-predict")
    public Result<List<ChurnPredictionVO>> batchPredictUserChurn(@RequestBody List<Long> userIds) {
        List<ChurnPredictionVO> predictions = churnPredictionService.batchPredictUserChurn(normalizeUserIds(userIds));
        return Result.success(predictions);
    }

       
                  
      
                            
                        
                      
  
    @ApiLog("获取高风险流失用户列表")
    @GetMapping("/high-risk")
    public Result<List<ChurnPredictionVO>> getHighRiskUsers(
            @RequestParam(required = false, defaultValue = "medium") String riskLevel,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        List<ChurnPredictionVO> users = churnPredictionService.getHighRiskUsers(riskLevel, normalizeLimit(limit));
        return Result.success(users);
    }

       
               
      
                       
  
    @ApiLog("获取流失用户统计")
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getChurnStatistics() {
        Map<String, Object> stats = churnPredictionService.getChurnStatistics();
        return Result.success(stats);
    }

       
               
      
                         
                     
  
    @ApiLog("分析用户流失原因")
    @GetMapping("/reasons/{userId}")
    public Result<List<String>> analyzeChurnReasons(@PathVariable Long userId) {
        List<String> reasons = churnPredictionService.analyzeChurnReasons(userId);
        return Result.success(reasons);
    }

       
               
      
                         
                     
  
    @ApiLog("生成用户召回策略")
    @GetMapping("/recall-strategy/{userId}")
    public Result<List<String>> generateRecallStrategy(@PathVariable Long userId) {
        List<String> strategies = churnPredictionService.generateRecallStrategy(userId);
        return Result.success(strategies);
    }

       
               
      
                     
                     
  
    @ApiLog("获取流失趋势数据")
    @GetMapping("/trend")
    public Result<Map<String, Object>> getChurnTrend(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        Map<String, Object> trend = churnPredictionService.getChurnTrend(normalizeDays(days));
        return Result.success(trend);
    }

       
               
      
                         
                     
  
    @ApiLog("发送流失预警通知")
    @PostMapping("/alert/{userId}")
    public Result<Boolean> sendChurnAlert(@PathVariable Long userId) {
        Boolean result = churnPredictionService.sendChurnAlert(userId);
        return Result.success(result);
    }

       
                 
      
                            
                   
  
    @ApiLog("批量发送流失预警通知")
    @PostMapping("/alert/batch")
    public Result<Integer> batchSendChurnAlerts(@RequestParam String riskLevel) {
        Integer count = churnPredictionService.batchSendChurnAlerts(riskLevel);
        return Result.success(count);
    }

       
             
      
                         
                         
                     
                     
  
    @ApiLog("记录召回操作")
    @PostMapping("/recall/record")
    public Result<Boolean> recordRecallAction(
            @RequestParam Long userId,
            @RequestParam String action,
            @RequestParam(required = false) Double cost) {
        Boolean result = churnPredictionService.recordRecallAction(userId, action, cost);
        return Result.success(result);
    }

       
               
      
                     
                     
  
    @ApiLog("获取召回效果统计")
    @GetMapping("/recall/effectiveness")
    public Result<Map<String, Object>> getRecallEffectiveness(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        Map<String, Object> effectiveness = churnPredictionService.getRecallEffectiveness(normalizeDays(days));
        return Result.success(effectiveness);
    }

    private List<Long> normalizeUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("用户ID列表不能为空");
        }
        if (userIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多预测100个用户");
        }
        if (userIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("用户ID必须为正整数");
        }
        return userIds.stream().distinct().collect(Collectors.toList());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(limit, MAX_RESULT_LIMIT);
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return 30;
        }
        return Math.min(days, MAX_ANALYSIS_DAYS);
    }
}
