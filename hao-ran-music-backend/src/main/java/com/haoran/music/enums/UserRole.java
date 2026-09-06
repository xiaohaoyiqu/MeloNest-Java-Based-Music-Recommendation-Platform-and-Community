package com.haoran.music.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;





public enum UserRole {




    USER("USER", "普通用户"),




    CREATOR("CREATOR", "创作者"),




    MODERATOR("MODERATOR", "审核员"),




    ADMIN("ADMIN", "管理员"),




    SUPER_ADMIN("SUPER_ADMIN", "超级管理员");




    private final String code;




    private final String description;

    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }







    public static UserRole fromCode(String code) {
        if (code == null) {
            return USER;
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code.equals(normalizedCode)) {
                return role;
            }
        }
        return USER;
    }

    public static boolean isValidCode(String code) {
        if (code == null) {
            return false;
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        for (UserRole role : values()) {
            if (role.code.equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }







    public static boolean isAdmin(String role) {
        UserRole userRole = fromCode(role);
        return userRole == ADMIN || userRole == SUPER_ADMIN;
    }







    public static boolean canModerate(String role) {
        UserRole userRole = fromCode(role);
        return userRole == MODERATOR || userRole == ADMIN || userRole == SUPER_ADMIN;
    }
}
