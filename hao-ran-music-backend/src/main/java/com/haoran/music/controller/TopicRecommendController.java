package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.TopicRecommendService;

import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

   
          
  
                         
  
                      
   
@RestController
@RequestMapping("/recommend/topics")
public class TopicRecommendController {

    @Resource
    private TopicRecommendService topicRecommendService;

       
                   
      
                         
                             
                            
       
    @ApiLog("获取推荐话题")
    @GetMapping
    public Result<List<Map<String, Object>>> getRecommendedTopics(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> result = topicRecommendService.getRecommendedTopics(userId, limit);
        return Result.success(result);
    }

       
                 
      
                         
                                                                                
                             
                            
       
    @ApiLog("按流派获取推荐话题")
    @GetMapping("/genre")
    public Result<List<Map<String, Object>>> getTopicsByGenre(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam String genre,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> result = topicRecommendService.getTopicsByGenre(userId, genre, limit);
        return Result.success(result);
    }
}
