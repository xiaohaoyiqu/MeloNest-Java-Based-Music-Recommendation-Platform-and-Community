   
                      
   
package com.haoran.music.dto.curated;

import lombok.Data;

import javax.validation.constraints.NotNull;

   
          
   
@Data
public class CuratedReviewRequest {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    private String remark;
}
