


package com.haoran.music.common.enums;




public enum VerificationType {


    EMAIL(0, "邮箱验证"),


    PHONE(1, "手机号验证"),


    RESET_PASSWORD(2, "找回密码"),


    CHANGE_PHONE(3, "修改手机号"),


    CHANGE_EMAIL(4, "修改邮箱"),


    ACCOUNT_RESTRICTION_APPEAL(5, "受限账号申诉");

    private final Integer code;
    private final String description;

    VerificationType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static VerificationType fromCode(Integer code) {
        if (code == null) {
            return EMAIL;
        }
        for (VerificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return EMAIL;
    }
}
