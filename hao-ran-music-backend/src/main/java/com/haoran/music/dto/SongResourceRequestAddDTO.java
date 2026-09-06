package com.haoran.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;





@Data
public class SongResourceRequestAddDTO {




    @NotBlank(message = "歌曲名称不能为空")
    @Size(max = 200, message = "歌曲名称最多200个字符")
    private String songName;




    @NotBlank(message = "歌手名称不能为空")
    @Size(max = 200, message = "歌手名称最多200个字符")
    private String artistName;




    @Size(max = 200, message = "专辑名称最多200个字符")
    private String albumName;




    @Size(max = 100, message = "版本信息最多100个字符")
    private String versionInfo;




    @Size(max = 200, message = "来源描述最多200个字符")
    private String sourceDescription;




    @Size(max = 1000, message = "备注最多1000个字符")
    private String remark;
}
