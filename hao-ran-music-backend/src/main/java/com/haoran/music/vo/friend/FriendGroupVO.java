




package com.haoran.music.vo.friend;

import lombok.Data;

import java.io.Serializable;
import java.util.List;




@Data
public class FriendGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private Long userId;




    private String groupName;




    private Integer count;




    private String createTime;
}
