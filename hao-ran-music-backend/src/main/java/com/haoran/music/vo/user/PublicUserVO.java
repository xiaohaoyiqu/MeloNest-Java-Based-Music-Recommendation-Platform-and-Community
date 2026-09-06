


package com.haoran.music.vo.user;

import lombok.Data;

import java.io.Serializable;




@Data
public class PublicUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String signature;
    private Integer status;
    private Integer fansCount;
    private Integer followingCount;
    private Integer isCreator;
    private String creatorStatus;
    private Boolean isVip;
    private VerifiedInfoVO verifiedInfo;
    private Integer worksCount;
}
