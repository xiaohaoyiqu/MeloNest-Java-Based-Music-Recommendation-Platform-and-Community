package com.haoran.music.dto.audio;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

   
                 
  
                      
   
@Data
public class SmartPlaylistSaveDTO {

    @NotBlank(message = "歌单名称不能为空")
    @Size(max = 50, message = "歌单名称不能超过50个字符")
    private String name;

    @Size(max = 200, message = "歌单描述不能超过200个字符")
    private String description;

    @NotEmpty(message = "歌曲列表不能为空")
    @Size(max = 60, message = "智能歌单最多保存60首歌曲")
    private List<@Positive(message = "歌曲ID必须为正数") Long> songIds;
}
