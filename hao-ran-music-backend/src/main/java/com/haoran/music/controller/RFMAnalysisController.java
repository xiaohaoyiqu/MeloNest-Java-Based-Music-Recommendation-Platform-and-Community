package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.RFMAnalysisService;
import com.haoran.music.vo.user.RFMVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

   
                      
                            
   
@Slf4j
@RestController
@RequestMapping("/api/rfm")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class RFMAnalysisController {

    @Autowired
    private RFMAnalysisService rfmAnalysisService;

       
                  
      
                         
                      
  
    @ApiLog("获取用户RFM分析结果")
    @GetMapping("/user/{userId}")
    public Result<RFMVO> getUserRFM(@PathVariable Long userId) {
        RFMVO rfm = rfmAnalysisService.calculateUserRFM(userId);
        return Result.success(rfm);
    }

       
                    
      
                            
                        
  
    @ApiLog("批量获取用户RFM分析结果")
    @PostMapping("/batch")
    public Result<List<RFMVO>> batchGetUserRFM(@RequestBody List<Long> userIds) {
        List<RFMVO> rfms = rfmAnalysisService.batchCalculateUserRFM(userIds);
        return Result.success(rfms);
    }

       
               
      
                        
  
    @ApiLog("获取用户分层统计")
    @GetMapping("/statistics/segments")
    public Result<Map<String, Integer>> getSegmentStatistics() {
        Map<String, Integer> stats = rfmAnalysisService.getSegmentStatistics();
        return Result.success(stats);
    }

       
                   
      
                              
                     
                       
                        
  
    @ApiLog("根据分层类型获取用户列表")
    @GetMapping("/segment/{segmentType}")
    public Result<List<RFMVO>> getUsersBySegment(
            @PathVariable String segmentType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        List<RFMVO> users = rfmAnalysisService.getUsersBySegment(segmentType, page, size);
        return Result.success(users);
    }

       
                 
      
                     
  
    @ApiLog("获取用户分层分布数据")
    @GetMapping("/distribution")
    public Result<Map<String, Object>> getSegmentDistribution() {
        Map<String, Object> distribution = rfmAnalysisService.getSegmentDistribution();
        return Result.success(distribution);
    }

       
               
      
                         
                           
  
    @ApiLog("预测用户流失概率")
    @GetMapping("/churn-probability/{userId}")
    public Result<Integer> predictChurnProbability(@PathVariable Long userId) {
        Integer probability = rfmAnalysisService.predictChurnProbability(userId);
        return Result.success(probability);
    }

       
                
      
                         
                     
  
    @ApiLog("刷新用户RFM缓存")
    @PostMapping("/refresh/{userId}")
    public Result<Void> refreshUserRFMCache(@PathVariable Long userId) {
        rfmAnalysisService.refreshUserRFMCache(userId);
        return Result.success();
    }

       
                
      
                   
  
    @ApiLog("导出RFM分析报告")
    @GetMapping("/export/report")
    public Result<Map<String, Object>> exportRFMReport() {
        Map<String, Object> report = rfmAnalysisService.exportRFMReport();
        return Result.success(report);
    }


       
              
      
                     
  
    @GetMapping("/segments/description")
    public Result<List<Map<String, String>>> getSegmentDescriptions() {
        List<Map<String, String>> descriptions = new ArrayList<>();
        
        Map<String, String> map1 = new LinkedHashMap<>();
        map1.put("code", "important_value");
        map1.put("name", "重要价值用户");
        map1.put("description", "高活跃+高消费+高价值，核心VIP用户");
        descriptions.add(map1);
        
        Map<String, String> map2 = new LinkedHashMap<>();
        map2.put("code", "important_retention");
        map2.put("name", "重要保持用户");
        map2.put("description", "流失风险高价值用户，需重点召回");
        descriptions.add(map2);
        
        Map<String, String> map3 = new LinkedHashMap<>();
        map3.put("code", "important_development");
        map3.put("name", "重要发展用户");
        map3.put("description", "高价值低频用户，有发展潜力");
        descriptions.add(map3);
        
        Map<String, String> map4 = new LinkedHashMap<>();
        map4.put("code", "important_win_back");
        map4.put("name", "重要挽留用户");
        map4.put("description", "已流失高价值用户，强力召回");
        descriptions.add(map4);
        
        Map<String, String> map5 = new LinkedHashMap<>();
        map5.put("code", "general_value");
        map5.put("name", "一般价值用户");
        map5.put("description", "活跃但消费少，引导付费");
        descriptions.add(map5);
        
        Map<String, String> map6 = new LinkedHashMap<>();
        map6.put("code", "general_retention");
        map6.put("name", "一般保持用户");
        map6.put("description", "低价值稳定用户，维持活跃");
        descriptions.add(map6);
        
        Map<String, String> map7 = new LinkedHashMap<>();
        map7.put("code", "general_development");
        map7.put("name", "一般发展用户");
        map7.put("description", "新用户，引导转化");
        descriptions.add(map7);
        
        Map<String, String> map8 = new LinkedHashMap<>();
        map8.put("code", "churned");
        map8.put("name", "流失用户");
        map8.put("description", "低价值流失用户，放弃或低成本维护");
        descriptions.add(map8);
        
        return Result.success(descriptions);
    }
}
