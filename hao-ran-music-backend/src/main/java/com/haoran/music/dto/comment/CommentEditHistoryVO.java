package com.haoran.music.dto.comment;

import lombok.Data;

import java.time.LocalDateTime;





@Data
public class CommentEditHistoryVO {




    private Long id;




    private String content;




    private LocalDateTime editTime;




    private String editorName;
}
