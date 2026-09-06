package com.haoran.music.dto.localMusic;

import lombok.Data;

import javax.validation.constraints.NotBlank;





@Data
public class LocalMusicUpdateDTO {




    @NotBlank(message = "名称不能为空")
    private String name;




    @NotBlank(message = "歌手名称不能为空")
    private String artistName;




    private String albumName;




    private String versionType;




    private String versionName;
}
