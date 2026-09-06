package com.haoran.music.vo.audio;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

   
           
  
                      
   
@Data
public class MoodDiaryStats implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Integer days;

       
            
       
    private Integer totalListens;

       
            
       
    private Double avgValence;

       
           
       
    private Double avgEnergy;

       
            
       
    private Double avgDanceability;

       
             
       
    private Integer uniqueSongs;

       
              
       
    private String dominantMood;

       
           
       
    private Map<String, Integer> moodDistribution;

       
                    
       
    private String moodTrend;

       
                     
       
    private Integer activeDays;

       
             
       
    private String happiestDay;

       
                    
       
    private String mostEmotionalDay;

       
            
       
    private Double maxValence;

       
            
       
    private Double minValence;

       
                 
       
    private List<Map<String, Object>> topSongs;
}
