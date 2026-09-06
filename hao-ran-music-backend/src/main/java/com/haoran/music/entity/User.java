package com.haoran.music.entity;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("user")
public class User extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    @TableField(exist = false)
    private String uuid;




    private String username;




    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;



    private String nickname;




    private String avatar;




    private String email;




    private String phone;




    private Integer gender;




    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;




    private String province;




    private String city;




    private String introduction;




    private String role;




    private Integer status;




    private Integer isBanned;




    private String banReason;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banStartTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime banEndTime;




    private Integer fansCount;




    private Integer followingCount;




    private String privacySettings;




    private String localMusicPath;




    private String wallpaper;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;




    private String lastLoginIp;




    @TableLogic
    private Integer deleted;






    private Integer isModerator;




    private String moderatorStatus;




    private String moderatorNote;




    private Integer userType;



    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime userTypeUpdateTime;




    private Integer riskScore;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;




    private Integer isOnline;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOnlineTime;




    private Integer todayReviewCount;




    private Integer totalReviewCount;




    private Integer dailyQuota;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastReviewTime;






    private Integer isCreator;




    private String creatorStatus;




    private Long creatorEligibilityVersion;




    private Integer creditScore;




    private Integer isOfficial;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime creatorApplyTime;




    private String creatorNote;




    private String creatorType;




    private BigDecimal feeRate;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipExpireTime;




    private BigDecimal totalEarnings;




    private BigDecimal withdrawnEarnings;




    private BigDecimal pendingEarnings;

}
