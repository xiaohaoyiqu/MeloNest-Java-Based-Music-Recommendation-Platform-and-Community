   
                      
   
package com.haoran.music.dto.friend;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class FriendGroupCreateRequest {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 30, message = "分组名称不能超过30字")
    private String groupName;
}
