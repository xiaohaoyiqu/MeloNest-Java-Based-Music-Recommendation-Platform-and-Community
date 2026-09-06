package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;





@Data
@TableName("creator_external_apply")
public class ExternalCreatorApply extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private String applyNo;




    private String realName;



    @TableField("id_card")
    private String idCardNo;



    private String idCardMasked;




    private String idCardUrl;




    private String phone;




    private String email;



    private String creatorName;




    private String externalPlatform;




    private String externalHomepage;




    private String worksDescription;




    private String cooperationType;




    private BigDecimal expectedFeeRate;



    private String attachmentUrls;



    private String status;




    private Long reviewerId;




    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime reviewTime;




    private String reviewReason;




    private Long linkedUserId;




    private String contractFile;
}
