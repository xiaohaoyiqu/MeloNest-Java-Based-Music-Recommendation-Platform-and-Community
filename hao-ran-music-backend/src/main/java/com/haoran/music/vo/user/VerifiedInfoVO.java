package com.haoran.music.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
public class VerifiedInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;




    private Boolean isVerified;




    private String verifiedType;




    private String verifiedTypeName;




    private String verifiedLevel;




    private String verifiedLevelName;




    private String verifiedReason;




    private LocalDateTime verifiedTime;




    private String verifiedIcon;




    private String verifiedColor;
}
