package com.haoran.music.dto.playlist;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;






@Data
public class PlaylistOrderDTO {

    @NotNull(message = "歌单ID不能为空")
    @Positive(message = "歌单ID不合法")
    private Long playlistId;

    @Valid
    @NotEmpty(message = "歌曲ID列表不能为空")
    @Size(max = 200, message = "单次最多排序200首歌曲")
    private List<@NotNull(message = "歌曲ID不能为空") @Positive(message = "歌曲ID不合法") Long> songIds;

    @NotBlank(message = "排序版本不能为空")
    @Size(max = 120, message = "排序版本不合法")
    private String expectedOrderVersion;
}
