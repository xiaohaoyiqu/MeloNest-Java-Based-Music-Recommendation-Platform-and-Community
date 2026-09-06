package com.haoran.music.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data

public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Long id;




    private String uuid;




    private String username;




    private String nickname;




    private String phone;




    private String email;




    private String avatar;




    private String signature;




    private Integer gender;




    private LocalDate birthday;




    private Integer status;




    private Integer userType;




    private Integer isCreator;




    private String creatorStatus;




    private Boolean isVip;




    private Integer isBanned;




    private String banReason;




    private LocalDateTime banStartTime;




    private LocalDateTime banEndTime;




    private String role;




    private Integer fansCount;




    private Integer followingCount;




    private String wallpaper;




    private String localMusicPath;




    private LocalDateTime createTime;




    private LocalDateTime updateTime;




    private VerifiedInfoVO verifiedInfo;




    private Boolean isFollowing;




    private Integer worksCount;
}
