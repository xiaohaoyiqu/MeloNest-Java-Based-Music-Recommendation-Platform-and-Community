package com.haoran.music.dto.appeal;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;





@Data
public class AppealCreateDTO {




    @NotBlank(message = "申诉类型不能为空")
    private String appealType;




    @NotBlank(message = "申诉理由不能为空")
    @Size(min = 10, max = 2000, message = "申诉理由长度必须为10到2000字")
    private String appealReason;




    private Long relatedId;




    @Size(max = 4096, message = "证据材料引用过长")
    private String evidenceUrls;




    @Size(max = 5, message = "证据附件最多5个")
    private List<Long> evidenceAssetIds;
}
