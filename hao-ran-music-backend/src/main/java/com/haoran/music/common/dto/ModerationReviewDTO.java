package com.haoran.music.common.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

   
                      
                       
   
@Data
public class ModerationReviewDTO {

    @NotNull(message = "审核记录ID不能为空")
    private Long recordId;

    @NotBlank(message = "审核结果不能为空")
    private String reviewResult;

    private String reviewReason;
}
