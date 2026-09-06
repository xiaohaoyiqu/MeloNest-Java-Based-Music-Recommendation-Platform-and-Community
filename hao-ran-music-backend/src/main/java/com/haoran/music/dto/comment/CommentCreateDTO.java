package com.haoran.music.dto.comment;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;





@Data

public class CommentCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Integer targetType;


    private Long targetId;





    private Long parentId;

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 1000, message = "评论内容长度在1-1000字符之间")
    private String content;





    private Long replyToUserId;
}
