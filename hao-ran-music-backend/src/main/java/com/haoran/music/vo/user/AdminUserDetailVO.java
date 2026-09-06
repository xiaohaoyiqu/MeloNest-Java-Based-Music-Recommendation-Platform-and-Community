   
                      
   
package com.haoran.music.vo.user;

import com.haoran.music.entity.User;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

                                                                     
@Data
public class AdminUserDetailVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer gender;
    private LocalDate birthday;
    private String province;
    private String city;
    private String introduction;
    private String role;
    private Integer status;
    private Integer isBanned;
    private String banReason;
    private LocalDateTime banStartTime;
    private LocalDateTime banEndTime;
    private Integer userType;
    private Integer riskScore;
    private LocalDateTime lastLoginTime;
    private LocalDateTime lastActiveTime;
    private Integer isOnline;
    private Integer isModerator;
    private String moderatorStatus;
    private Integer isCreator;
    private String creatorStatus;
    private Integer creditScore;
    private Integer isOfficial;
    private String creatorType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AdminUserDetailVO from(User user) {
        if (user == null) {
            return null;
        }
        AdminUserDetailVO result = new AdminUserDetailVO();
        result.id = user.getId();
        result.username = user.getUsername();
        result.nickname = user.getNickname();
        result.avatar = user.getAvatar();
        result.email = user.getEmail();
        result.phone = user.getPhone();
        result.gender = user.getGender();
        result.birthday = user.getBirthday();
        result.province = user.getProvince();
        result.city = user.getCity();
        result.introduction = user.getIntroduction();
        result.role = user.getRole();
        result.status = user.getStatus();
        result.isBanned = user.getIsBanned();
        result.banReason = user.getBanReason();
        result.banStartTime = user.getBanStartTime();
        result.banEndTime = user.getBanEndTime();
        result.userType = user.getUserType();
        result.riskScore = user.getRiskScore();
        result.lastLoginTime = user.getLastLoginTime();
        result.lastActiveTime = user.getLastActiveTime();
        result.isOnline = user.getIsOnline();
        result.isModerator = user.getIsModerator();
        result.moderatorStatus = user.getModeratorStatus();
        result.isCreator = user.getIsCreator();
        result.creatorStatus = user.getCreatorStatus();
        result.creditScore = user.getCreditScore();
        result.isOfficial = user.getIsOfficial();
        result.creatorType = user.getCreatorType();
        result.createTime = user.getCreateTime();
        result.updateTime = user.getUpdateTime();
        return result;
    }
}
