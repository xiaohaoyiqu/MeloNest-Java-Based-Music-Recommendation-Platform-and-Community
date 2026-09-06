package com.haoran.music.vo.appeal;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;





@Data
public class AppealVO {




    private Long id;




    private Long userId;




    private String username;




    private String nickname;




    private String avatar;




    private String appealType;




    private String appealTypeName;




    private String appealReason;




    private String appealStatus;




    private String appealStatusName;




    private Long relatedId;




    private String evidenceUrls;




    private List<Long> evidenceAssetIds;




    private Long reviewerId;




    private String reviewerName;




    private String reviewResult;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;
}
