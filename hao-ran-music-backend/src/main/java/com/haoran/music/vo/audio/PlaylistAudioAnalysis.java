package com.haoran.music.vo.audio;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

   
             
  
                      
   
@Data
public class PlaylistAudioAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Long playlistId;

       
              
       
    private Boolean hasAudioData;

       
              
       
    private Integer songCount;

       
               
       
    private Double avgEnergy;

       
               
       
    private Double avgValence;

       
                
       
    private Double avgDanceability;

       
               
       
    private Double avgTempo;

       
                 
       
    private Double avgAcousticness;

       
                  
       
    private Double avgInstrumentalness;

       
           
       
    private Map<String, Integer> energyDistribution;

       
           
       
    private Map<String, Integer> valenceDistribution;

       
           
       
    private Map<String, Integer> tempoDistribution;

       
           
       
    private List<String> styleTags;

       
           
       
    private List<String> scenarioTags;

       
           
       
    private String moodTag;

       
                
       
    private Double consistencyScore;
}
