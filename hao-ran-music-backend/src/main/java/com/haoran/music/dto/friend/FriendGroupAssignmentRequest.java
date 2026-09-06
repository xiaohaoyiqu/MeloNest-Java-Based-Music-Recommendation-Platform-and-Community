


package com.haoran.music.dto.friend;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class FriendGroupAssignmentRequest {

    @NotNull(message = "好友ID不能为空")
    @Positive(message = "好友ID必须为正整数")
    private Long friendId;

    @Positive(message = "好友分组ID必须为正整数")
    private Long groupId;
}
