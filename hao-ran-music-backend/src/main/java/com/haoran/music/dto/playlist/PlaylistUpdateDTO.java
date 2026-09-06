package com.haoran.music.dto.playlist;

import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.util.List;





@Data

public class PlaylistUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    @NotNull(message = "歌单ID不能为空")
    @Positive(message = "歌单ID不合法")
    private Long id;


    @Size(max = 50, message = "歌单名称不能超过50个字符")
    private String name;


    @Size(max = 200, message = "歌单描述不能超过200个字符")
    private String description;


    @Size(max = 500, message = "歌单封面地址不能超过500个字符")
    private String cover;


    @Min(value = 0, message = "公开性参数不合法")
    @Max(value = 1, message = "公开性参数不合法")
    private Integer isPublic;


    @Size(max = 20, message = "歌单标签不能超过20个")
    private List<@NotBlank(message = "歌单标签不能为空") @Size(max = 20, message = "单个标签不能超过20个字符") String> tags;
}
