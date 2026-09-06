package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.dto.appeal.AppealCreateDTO;
import com.haoran.music.dto.appeal.AppealQueryDTO;
import com.haoran.music.dto.appeal.AppealReviewDTO;
import com.haoran.music.entity.Appeal;
import com.haoran.music.vo.appeal.AppealVO;

   
                      
                      
   
public interface AppealService extends IService<Appeal> {

       
           
      
                         
                          
                   
       
    Long createAppeal(Long userId, AppealCreateDTO dto);

       
               
      
                      
                     
       
    IPage<AppealVO> pageAppeals(AppealQueryDTO dto);

       
             
      
                           
                   
       
    AppealVO getAppealDetail(Long appealId);

       
           
      
                              
                             
       
    void reviewAppeal(Long reviewerId, AppealReviewDTO dto);

       
           
      
                           
                           
       
    void cancelAppeal(Long userId, Long appealId);

       
               
      
                         
                         
                     
       
    IPage<AppealVO> getMyAppeals(Long userId, AppealQueryDTO dto);

       
               
      
                                   
       
    java.util.Map<String, Long> getAppealStats();
}
