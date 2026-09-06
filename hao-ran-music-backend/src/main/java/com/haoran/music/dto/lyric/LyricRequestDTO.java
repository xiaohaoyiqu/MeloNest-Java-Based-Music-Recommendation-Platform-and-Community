package com.haoran.music.dto.lyric;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

   
                      
                         
   
@Data
public class LyricRequestDTO {

    @NotNull(message = "歌曲ID不能为空")
    private Long songId;

    @Size(max = 200, message = "歌曲名称不能超过200个字符")
    private String songName;

    @Size(max = 20000, message = "原始歌词不能超过20000个字符")
    private String originalLyric;

    @NotBlank(message = "修正后歌词不能为空")
    @Size(max = 20000, message = "修正后歌词不能超过20000个字符")
    private String correctedLyric;

    @Size(max = 500, message = "修改说明不能超过500个字符")
    private String changeDescription;

    private Integer changeType;

       
                                                                                    
       
    @Size(max = 500, message = "修改说明不能超过500个字符")
    private String description;

    @Size(max = 50, message = "修改类型不能超过50个字符")
    private String correctionType;
}
