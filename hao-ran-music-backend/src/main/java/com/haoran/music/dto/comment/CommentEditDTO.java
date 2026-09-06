package com.haoran.music.dto.comment;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

   
                      
                       
   
@Data
public class CommentEditDTO {

       
           
       
    @NotNull(message = "评论ID不能为空")
    private Long id;

       
               
       
    @NotBlank(message = "评论内容不能为空")
    private String content;
}
