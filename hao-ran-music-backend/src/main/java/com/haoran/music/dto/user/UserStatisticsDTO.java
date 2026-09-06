package com.haoran.music.dto.user;

import lombok.Data;

   
                      
                         
   
@Data
public class UserStatisticsDTO {

       
           
       
    private Long userId;

       
          
       
    private String username;

       
           
       
    private String statDate;

       
           
       
    private Integer playCount;

       
              
       
    private Integer playDuration;

       
            
       
    private Integer uniqueSongCount;

    private Integer completePlayCount;

       
           
       
    private Integer likeCount;

       
           
       
    private Integer favoriteCount;

    private Integer unfavoriteCount;

       
           
       
    private Integer commentCount;

    private Integer shareCount;

    private Integer downloadCount;

    private Integer activeDuration;

    private Integer createPlaylistCount;

    private Integer followArtistCount;

       
           
       
    private Integer searchCount;

       
           
       
    private Integer loginCount;

       
           
       
    private Boolean isAbnormal;

    private String abnormalReason;

       
           
       
    private Integer riskScore;
}
