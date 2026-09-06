package com.haoran.music.dto.creator;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;





@Data
public class CreatorWorkDTO {

    @NotNull(message = "作品类型不能为空")
    private Integer workType;

    @NotBlank(message = "作品名称不能为空")
    @Size(max = 200, message = "作品名称不能超过200个字符")
    private String workName;

    private String coverUrl;

    private String fileUrl;

    @NotBlank(message = "作品描述不能为空")
    @Size(max = 1000, message = "作品描述不能超过1000个字符")
    private String description;

    private String tags;

    @NotNull(message = "语言类型不能为空")
    private Integer language;

    @Size(max = 200000, message = "歌词不能超过200000个字符")
    private String lyric;

    @NotNull(message = "请选择是否展示实名信息")
    private Boolean showRealName;




    private Integer allowDownload;




    private Integer allowComment;




    private Integer allowShare;




    private Integer isPaid;




    private Integer price;




    private Integer subscribePeriod;




    private Integer uploadType;




    private String fileUrls;




    private String zipFileUrl;
}
