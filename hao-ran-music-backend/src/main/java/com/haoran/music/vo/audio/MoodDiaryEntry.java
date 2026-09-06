package com.haoran.music.vo.audio;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

   
           
  
                      
   
@Data
public class MoodDiaryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    private LocalDate date;

       
            
       
    private Double avgValence;

       
           
       
    private Double avgEnergy;

       
           
       
    private Integer listenCount;

       
             
       
    private String moodTag;

       
           
       
    private String moodDescription;

       
                  
       
    private Long topSongId;

       
                 
       
    private String topSongName;

       
             
       
    private Map<String, Integer> moodDistribution;
    
       
            
       
    private Double avgDanceability;

       
           
       
    private Double avgTempo;

       
           
       
    private Integer playCount;

       
             
       
    private Integer uniqueSongs;
}
