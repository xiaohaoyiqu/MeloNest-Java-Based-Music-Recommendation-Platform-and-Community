




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;




@Data
@TableName("music_emoji")
public class Emoji implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    @JsonProperty("packageId")
    private Long packageId;




    @JsonProperty("emojiId")
    private String code;




    @JsonProperty("emojiName")
    private String name;




    @JsonProperty("gifUrl")
    private String imageUrl;




    @TableField(exist = false)
    private String imagePath;




    @JsonProperty("category")
    private String category;




    private Integer sortOrder;




    @JsonProperty("isEnabled")
    private Integer enabled;




    private Long creatorId;




    @JsonProperty("usageCount")
    private Long usageCount;




    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createTime")
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    @TableField("is_deleted")
    @TableLogic
    private Integer deleted;




    public Boolean getIsSystem() {
        return packageId != null && packageId == 0;
    }
}
