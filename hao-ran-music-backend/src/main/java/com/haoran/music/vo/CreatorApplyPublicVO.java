package com.haoran.music.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public class CreatorApplyPublicVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private Long userId;




    private String username;




    private String nickname;




    private String avatar;




    private String applyType;




    private String creatorType;




    private String reason;




    private String workUrls;




    private Integer status;




    private String reviewComment;




    private String statusDesc;




    private LocalDateTime applyTime;




    private Integer fansCount;




    private Integer workCount;




    private String idCardMasked;




    private String phone;




    private String email;
}
