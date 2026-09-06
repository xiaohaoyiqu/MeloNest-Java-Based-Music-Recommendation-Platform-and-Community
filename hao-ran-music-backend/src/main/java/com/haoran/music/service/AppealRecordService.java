package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.AppealRecord;

import java.util.List;

   
                      
                        
   
public interface AppealRecordService extends IService<AppealRecord> {

       
                    
      
                           
                     
       
    List<AppealRecord> getRecordsByAppealId(Long appealId);

       
                     
      
                              
                     
       
    List<AppealRecord> getRecordsByOperatorId(Long operatorId);

       
               
      
                                   
                                    
                                    
                                   
                             
                               
                   
       
    IPage<AppealRecord> pageRecords(Long appealId, Long operatorId, String operatorType, String action, Long page, Long size);

       
               
      
                               
                                
                                                   
                                                                         
                               
                   
       
    Long recordAction(Long appealId, Long operatorId, String operatorType, String action, String actionRemark);

       
                  
      
                           
                     
       
    AppealRecord getLatestRecord(Long appealId);
}
