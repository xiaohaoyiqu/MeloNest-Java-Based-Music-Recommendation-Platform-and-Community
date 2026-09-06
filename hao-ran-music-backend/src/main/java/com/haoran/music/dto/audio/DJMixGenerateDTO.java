package com.haoran.music.dto.audio;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

   
             
  
                      
   
@Data
public class DJMixGenerateDTO {

    @Positive(message = "起始歌曲ID必须为正数")
    private Long baseSongId;

                    
    @Positive(message = "起始歌曲ID必须为正数")
    private Long startSongId;

                    
    @Positive(message = "歌曲ID必须为正数")
    private Long songId;

    @Min(value = 15, message = "混音时长不能少于15分钟")
    @Max(value = 180, message = "混音时长不能超过180分钟")
    private Integer durationMinutes;

                                     
    @Size(max = 29, message = "一次最多手选29首歌曲")
    private List<@Positive(message = "手选歌曲ID必须为正数") Long> selectedSongIds;

    public Long resolveBaseSongId() {
        if (baseSongId != null) {
            return baseSongId;
        }
        return startSongId != null ? startSongId : songId;
    }
}
