package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.appeal.AppealCreateDTO;
import com.haoran.music.dto.appeal.AppealQueryDTO;
import com.haoran.music.dto.appeal.AppealReviewDTO;
import com.haoran.music.service.AppealService;
import com.haoran.music.vo.appeal.AppealVO;
import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

   
                      
                                 
   
@Slf4j
@RestController
@RequestMapping("/appeal")
public class AppealController {

    @Resource
    private AppealService appealService;

       
           
      
                      
                   
       
    @PostMapping("/create")
    @ApiLog("用户创建申诉")
    public Result<Long> createAppeal(@Valid @RequestBody AppealCreateDTO dto) {
                               
        Long userId = getCurrentUserId();
        Long appealId = appealService.createAppeal(userId, dto);
        return Result.success(appealId);
    }

       
               
      
                      
                   
       
    @PostMapping("/my")
    @ApiLog("用户获取我的申诉列表")
    public Result<PageResult<AppealVO>> getMyAppeals(@RequestBody AppealQueryDTO dto) {
        Long userId = getCurrentUserId();
        IPage<AppealVO> page = appealService.getMyAppeals(userId, dto);
        return Result.success(buildPageResult(page));
    }

       
             
      
                           
                   
       
    @GetMapping("/detail/{appealId}")
    @ApiLog("用户获取申诉详情")
    public Result<AppealVO> getAppealDetail(@PathVariable Long appealId) {
        Long userId = getCurrentUserId();
        AppealVO vo = appealService.getAppealDetail(appealId);
                    
        if (!vo.getUserId().equals(userId)) {
            return Result.error("无权查看此申诉");
        }
        return Result.success(vo);
    }

       
           
      
                           
                   
       
    @PostMapping("/cancel/{appealId}")
    @ApiLog("用户取消申诉")
    public Result<Void> cancelAppeal(@PathVariable Long appealId) {
        Long userId = getCurrentUserId();
        appealService.cancelAppeal(userId, appealId);
        return Result.success();
    }

       
               
      
                   
       
    private Long getCurrentUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new RuntimeException("用户未登录");
        }
        return userId;
    }

       
             
       
    private PageResult<AppealVO> buildPageResult(IPage<AppealVO> page) {
        PageResult<AppealVO> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
