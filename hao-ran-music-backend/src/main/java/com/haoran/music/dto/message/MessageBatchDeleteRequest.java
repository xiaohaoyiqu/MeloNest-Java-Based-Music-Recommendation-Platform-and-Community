



package com.haoran.music.dto.message;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class MessageBatchDeleteRequest {

    @NotEmpty(message = "请选择要删除的消息")
    @Size(max = 50, message = "一次最多删除50条消息")
    private List<Long> messageIds;
}
