



package com.haoran.music.dto.message;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class MessageForwardRequest {

    @NotEmpty(message = "请选择要转发的消息")
    @Size(max = 20, message = "一次最多转发20条消息")
    private List<Long> messageIds;

    @NotNull(message = "请选择接收人")
    @Positive(message = "接收人不合法")
    private Long receiverId;
}
