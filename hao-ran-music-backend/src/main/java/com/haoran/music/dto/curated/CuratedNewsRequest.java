   
                      
   
package com.haoran.music.dto.curated;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

   
              
   
@Data
public class CuratedNewsRequest {

    @NotBlank(message = "新闻标题不能为空")
    @Size(max = 128, message = "新闻标题不能超过128个字符")
    private String title;

    @Size(max = 2000, message = "新闻摘要不能超过2000个字符")
    private String description;

    @Size(max = 32, message = "新闻标签不能超过32个字符")
    private String badge;

    @Size(max = 512, message = "封面地址不能超过512个字符")
    private String coverUrl;

    @Size(max = 512, message = "原文链接不能超过512个字符")
    private String link;

    @Size(max = 512, message = "备用链接不能超过512个字符")
    private String fallbackLink;
    private Integer priority;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
