package com.haoran.music.dto.localMusic;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

   
                      
                            
   
@Data
public class LocalMusicMVAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @NotBlank(message = "MV名称不能为空")
    private String name;

       
               
       
    @NotBlank(message = "歌手名称不能为空")
    private String artist;

       
                        
       
    @NotBlank(message = "视频URL不能为空")
    private String url;

       
                
       
    private String cover;

       
               
       
    private Long fileSize;

       
            
       
    private Integer duration;
     
                                       
     
  private Integer quality;

}
