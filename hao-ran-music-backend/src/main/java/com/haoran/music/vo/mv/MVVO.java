package com.haoran.music.vo.mv;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.haoran.music.common.util.MediaPlaybackUrlUtil;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                      
   
@Data

public class MVVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;


    private String name;


    private Long artistId;


       
                    
       
    private String artistNames;


       
            
       
    private String artistAvatar;


       
           
       
    private String albumName;


    private Long songId;


    private String songName;

       
              
       
    private String songLanguage;


    private String cover;


    private String description;


    private Long playCount;


    private Integer favoriteCount;


    private Integer commentCount;


    private Integer shareCount;


    private Integer likeCount;


    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate;


    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;


    private Integer duration;


       
                
       
    private String url360p;


       
                
       
    private String url720p;


       
                  
       
    private String url1080p;


       
                            
       
    private String url2160p;


       
           
                                                   
       
    @JsonAlias({"isFavorated", "isFavorite"})
    private Boolean isFavorite;

       
           
                                           
       
    @JsonAlias({"isLiked", "isLike"})
    private Boolean isLike;

    public String getUrl360p() {
        return MediaPlaybackUrlUtil.mvUrl(id, "360p", url360p);
    }

    public String getUrl720p() {
        return MediaPlaybackUrlUtil.mvUrl(id, "720p", url720p);
    }

    public String getUrl1080p() {
        return MediaPlaybackUrlUtil.mvUrl(id, "1080p", url1080p);
    }

    public String getUrl2160p() {
        return MediaPlaybackUrlUtil.mvUrl(id, "2160p", url2160p);
    }
}
