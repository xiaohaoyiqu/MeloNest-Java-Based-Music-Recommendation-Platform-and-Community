package com.haoran.music.common.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;





public enum UserType {





    NORMAL(0, "正常用户", false),




    ACTIVE(1, "活跃用户", false),




    VIP(2, "VIP用户", false),




    SUPER_USER(3, "超级用户", false),





    INACTIVE(10, "不活跃用户", false),




    SUSPICIOUS(11, "可疑用户", true),




    BOT(12, "机器人用户", true),




    BANNED(13, "封禁用户", true),




    ACCOUNT_FARM(14, "账号农场", true),




    IMPERSONATION(15, "虚假身份/冒充账号", true),




    COMPROMISED(16, "被盗或安全风险账号", true),




    SPAM(17, "垃圾营销账号", true),




    MONETIZATION_RESTRICTED(18, "变现受限账号", true),




    LOW_QUALITY_CREATOR(19, "低质或违规创作者", true),




    SYNTHETIC_IDENTITY(20, "异常AI/自动生成账号", true),




    COORDINATED_ABUSE(21, "协同刷量/互刷账号", true),




    FAKE_ENGAGEMENT(22, "虚假互动账号", true),




    RATING_MANIPULATION(23, "评分操纵账号", true),




    FOLLOW_MANIPULATION(24, "关注/粉丝操纵账号", true),




    COMMENT_MESSAGE_ABUSE(25, "评论/私信滥用账号", true),




    REPORT_ABUSE(26, "举报/申诉滥用账号", true),




    PLAYBACK_VIEW_ABUSE(27, "播放/浏览刷量账号", true),




    CRAWLER_SCRAPER(28, "爬虫/采集账号", true),




    DOWNLOAD_ABUSE(29, "异常下载账号", true),




    RESOURCE_UPLOAD_ABUSE(30, "资源上传滥用账号", true),




    CONTENT_THEFT(31, "搬运/盗传账号", true),




    COPYRIGHT_INFRINGER(32, "版权侵权账号", true),




    MALICIOUS_EDIT(33, "恶意编辑账号", true),




    PAYMENT_RISK(34, "支付风险账号", true),




    REFUND_ABUSE(35, "退款滥用账号", true),




    CHARGEBACK_RISK(36, "拒付/争议风险账号", true),




    BENEFIT_ABUSE(37, "权益滥用账号", true),




    BAN_EVASION(38, "规避封禁账号", true),




    DEVICE_IP_RISK(39, "设备/IP风险账号", true),




    MANUAL_REVIEW_HOLD(40, "人工复核限制账号", true);




    public static final String RESTRICTED_CODE_SQL = "11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, "
            + "22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40";
    private static final List<Integer> RESTRICTED_CODES = Collections.unmodifiableList(
            Arrays.stream(values())
                    .filter(UserType::shouldRestrict)
                    .map(UserType::getCode)
                    .collect(Collectors.toList()));
    private static final List<Integer> NON_RESTRICTED_CODES = Collections.unmodifiableList(
            Arrays.stream(values())
                    .filter(type -> !type.shouldRestrict())
                    .map(UserType::getCode)
                    .collect(Collectors.toList()));




    private final Integer code;




    private final String description;




    private final Boolean restricted;

    UserType(Integer code, String description, Boolean restricted) {
        this.code = code;
        this.description = description;
        this.restricted = restricted;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Boolean isRestricted() {
        return restricted;
    }




    public static UserType fromCode(Integer code) {
        if (code == null) {
            return NORMAL;
        }
        for (UserType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return NORMAL;
    }




    public static boolean isKnownCode(Integer code) {
        return code != null && Arrays.stream(values()).anyMatch(type -> type.code.equals(code));
    }




    public boolean isNormalUser() {
        return this == NORMAL || this == ACTIVE || this == VIP || this == SUPER_USER;
    }




    public boolean isAbnormalUser() {
        return this == INACTIVE || shouldRestrict();
    }




    public boolean shouldRestrict() {
        return restricted;
    }




    public static List<Integer> restrictedCodes() {
        return RESTRICTED_CODES;
    }




    public static List<Integer> nonRestrictedCodes() {
        return NON_RESTRICTED_CODES;
    }
}
