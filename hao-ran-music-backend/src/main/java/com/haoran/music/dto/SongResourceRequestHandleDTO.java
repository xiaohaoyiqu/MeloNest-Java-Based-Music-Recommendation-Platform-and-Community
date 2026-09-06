package com.haoran.music.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

   
                      
                           
   
@Data
public class SongResourceRequestHandleDTO {

       
           
       
    @NotNull(message = "申请ID不能为空")
    private Long id;

       
                 
       
    private Long matchedSongId;

       
                                     
       
    @NotNull(message = "状态不能为空")
    @Pattern(regexp = "completed|rejected", message = "状态只能是completed或rejected")
    private String status;

       
             
       
    private String handleResult;
}
