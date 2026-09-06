package com.haoran.music.dto.song;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

   
                      
                       
   
@Data
public class SongRatingDTO {

    private static final long serialVersionUID = 1L;

       
           
       
    @NotNull(message = "歌曲ID不能为空")
    private Long songId;

       
               
       
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低为1星")
    @Max(value = 5, message = "评分最高为5星")
    private Integer rating;
}
