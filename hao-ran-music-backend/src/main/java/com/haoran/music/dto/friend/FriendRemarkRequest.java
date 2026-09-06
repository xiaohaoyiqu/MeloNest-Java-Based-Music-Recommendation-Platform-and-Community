   
                      
   
package com.haoran.music.dto.friend;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class FriendRemarkRequest {

    @NotNull(message = "好友ID不能为空")
    @Positive(message = "好友ID必须为正整数")
    private Long friendId;

    @Size(max = 50, message = "好友备注不能超过50字")
    private String remark;
}
