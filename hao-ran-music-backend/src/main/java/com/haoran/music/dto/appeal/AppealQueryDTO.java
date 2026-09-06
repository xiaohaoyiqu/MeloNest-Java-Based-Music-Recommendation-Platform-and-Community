package com.haoran.music.dto.appeal;

import com.haoran.music.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;





@Data
@EqualsAndHashCode(callSuper = true)
public class AppealQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;




    private Long userId;




    private String appealType;




    private String appealStatus;




    private Long reviewerId;




    private String keyword;
}
