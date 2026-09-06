package com.haoran.music.dto.favorite;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

                            
@Data
public class FavoriteGroupItemDTO {
    @NotBlank(message = "收藏类型不能为空")
    @Pattern(regexp = "song|album|mv|playlist", message = "收藏类型不正确")
    private String resourceType;

    @NotNull(message = "收藏资源不能为空")
    private Long resourceId;
}
