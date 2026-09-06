package com.haoran.music.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

   
                      
                             
   
@Data
public class SongResourceRequestUploadDTO {

       
           
       
    private String songName;

       
           
       
    private String artistName;

       
           
       
    private String albumName;

       
           
       
    private String versionInfo;

       
           
       
    private String sourceDescription;

       
           
       
    private String remark;
}
