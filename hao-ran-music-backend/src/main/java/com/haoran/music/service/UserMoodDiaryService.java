package com.haoran.music.service;

import com.haoran.music.vo.audio.MoodDiaryEntry;
import com.haoran.music.vo.audio.MoodDiaryStats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

   
           
                
  
                      
   
public interface UserMoodDiaryService {

       
                    
      
                         
                     
                   
       
    MoodDiaryEntry getMoodDiary(Long userId, LocalDate date);

       
                  
      
                         
                            
                          
                     
       
    List<MoodDiaryEntry> getMoodDiaryTimeline(Long userId, LocalDate startDate, LocalDate endDate);

       
               
      
                         
                       
                   
       
    MoodDiaryStats getMoodStats(Long userId, Integer days);

       
                 
               
      
                         
                     
                   
       
    List<Map<String, Object>> getMoodCurve(Long userId, Integer days);

       
                 
      
                         
                   
       
    Map<String, Object> analyzeMoodTrend(Long userId);

       
             
                          
      
                         
                        
                   
       
    List<Long> getMoodBasedRecommendation(Long userId, Integer limit);
}
