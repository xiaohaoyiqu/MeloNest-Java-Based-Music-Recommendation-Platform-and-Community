package com.haoran.music.dto.song;

import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

   
                      
                      
   
@Data
public class SongVO implements Serializable {

    private static final long serialVersionUID = 1L;

               
    private Long id;

               
    private String name;

                 
    private Long artistId;

                        
    private String artistIds;

               
    private String artistNames;

               
    private String albumName;

               
    private Long albumId;

                             
    private Long mvId;

                  
    private Integer albumTrackNo;

               
    private LocalDate releaseDate;

                  
    private Integer duration;

             
    private String language;

              
    private String mainGenre;

              
    private String mainType;

                
    private List<String> subTypes;

                
    private String cover;

                  
    private String urlStandard;

                   
    private String urlHigh;

                  
    private String urlLossless;

                                           
    private String urlHires;

                                            
    private String urlMaster;

                     
    private String urlInstrumental;

                 
    private Long sizeStandard;

                  
    private Long sizeHigh;

                 
    private Long sizeLossless;

                         
    private Long sizeHires;

                     
    private Long sizeMaster;

                        
    private Long sizeInstrumental;

              
    private Integer favoriteCount;

               
    private Long playCount;

              
    private Integer commentCount;

               
    private Integer downloadCount;

               
    private Integer hotScore;

               
    private BigDecimal avgRating;

               
    private Integer ratingCount;

               
    private Integer isNew;

               
    private Integer isHot;

                        
    private Integer isSingle;

                     
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private Boolean isFavorite;

               
    private LocalDateTime createTime;

               
    private String versionType;

                 
    private String versionName;

                      
    private String lyric;

                 
    private Integer allowDownload;

                 
    private Integer allowComment;

                 
    private Integer allowShare;

                
    private Long uploaderId;

                
    private Integer uploaderType;

                
    private String uploaderName;

                          
    private Integer matchTagCount;

               
    private String recommendReason;

                          
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private Boolean playable;

    public String getUrlStandard() {
        return MediaPlaybackUrlUtil.songUrl(id, "standard", urlStandard);
    }

    public String getUrlHigh() {
        return MediaPlaybackUrlUtil.songUrl(id, "high", urlHigh);
    }

    public String getUrlLossless() {
        return MediaPlaybackUrlUtil.songUrl(id, "lossless", urlLossless);
    }

    public String getUrlHires() {
        return null;
    }

    public String getUrlMaster() {
        return null;
    }

    public String getUrlInstrumental() {
        return null;
    }
}
