package com.haoran.music.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;





@Data
public class ModerationCreateDTO {

    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @NotNull(message = "目标ID不能为空")
    private Long targetId;

    @NotNull(message = "提交者ID不能为空")
    private Long submitterId;

    @NotBlank(message = "提交者来源不能为空")
    private String submitterSource;

    private Integer priority;

    private String title;

    private String description;
}
