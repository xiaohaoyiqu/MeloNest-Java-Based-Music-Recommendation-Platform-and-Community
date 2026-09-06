package com.haoran.music.dto.favorite;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

                                        
@Data
public class FavoriteGroupOrderDTO {
    @NotNull(message = "分组顺序不能为空")
    @Size(min = 1, max = 20, message = "一次最多整理20个分组")
    private List<@NotNull(message = "分组编号不能为空") Long> groupIds;
}
