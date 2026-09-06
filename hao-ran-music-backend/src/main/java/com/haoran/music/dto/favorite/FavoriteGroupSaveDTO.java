package com.haoran.music.dto.favorite;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;


@Data
public class FavoriteGroupSaveDTO {
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 30, message = "分组名称不能超过30个字")
    private String name;
}
