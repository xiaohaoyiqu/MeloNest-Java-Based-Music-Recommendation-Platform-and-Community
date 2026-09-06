package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("share_record")
public class ShareRecord implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private String shareType;




    private Long targetId;




    private String shareChannel;




    private String shareTitle;




    private String shareDescription;




    private String shareUrl;




    private Integer viewCount;




    private Integer clickCount;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;





    private Integer deleted;
}
