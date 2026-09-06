package com.haoran.music.dto.playlist;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import java.io.Serializable;





@Data
public class PlaylistPaidSettingsDTO implements Serializable {

    private static final long serialVersionUID = 1L;




    @NotNull(message = "歌单ID不能为空")
    @Min(value = 1, message = "歌单ID必须为正数")
    private Long playlistId;





    @NotNull(message = "月度价格不能为空")
    @Min(value = 5, message = "月度价格不能低于5元")
    @Max(value = 30, message = "月度价格不能高于30元")
    private Integer monthlyPrice;

}
