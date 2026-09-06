   
                      
   
package com.haoran.music.common.constant;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

   
                                                     
   
public final class ModerationConstants {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_SKIPPED = "skipped";

    public static final int DEFAULT_PRIORITY = 5;
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;

    private static final Map<String, String> TARGET_TYPE_NAMES;
    private static final Map<String, String> SUBMITTER_SOURCE_NAMES;
    private static final Map<String, String> STATUS_NAMES;
    private static final Set<String> ADMIN_ONLY_TARGET_TYPES;

    static {
        LinkedHashMap<String, String> targetTypes = new LinkedHashMap<>();
        targetTypes.put("song_resource_request", "歌曲资源申请");
        targetTypes.put("resource_request", "资源申请");
        targetTypes.put("creator_work", "创作者作品");
        targetTypes.put("user_upload", "用户上传");
        targetTypes.put("user_work", "普通用户投稿");
        targetTypes.put("music_square_work", "音乐广场投稿");
        targetTypes.put("emoji_package", "表情包投稿");
        targetTypes.put("decoration", "装饰投稿");
        targetTypes.put("lyric_request", "歌词修正");
        targetTypes.put("report", "举报处理");
        targetTypes.put("comment_report", "评论举报");
        targetTypes.put("post_report", "动态举报");

        targetTypes.put("artist_application", "艺人认证");
        targetTypes.put("creator_apply", "创作者申请");
        targetTypes.put("external_creator_apply", "外部创作者申请");
        targetTypes.put("creator_vip_apply", "创作者VIP申请");
        targetTypes.put("user_verification", "用户认证");
        targetTypes.put("withdraw_apply", "提现申请");
        targetTypes.put("refund_apply", "退款申请");
        targetTypes.put("payment_order", "支付凭证");
        targetTypes.put("external_content", "外部内容入库");
        targetTypes.put("marketplace_item", "装扮市场物品");

        targetTypes.put("song", "歌曲");
        targetTypes.put("album", "专辑");
        targetTypes.put("playlist", "歌单");
        targetTypes.put("mv", "MV");
        targetTypes.put("comment", "评论");
        targetTypes.put("post", "动态");
        targetTypes.put("user", "用户");
        TARGET_TYPE_NAMES = Collections.unmodifiableMap(targetTypes);

        LinkedHashSet<String> adminTypes = new LinkedHashSet<>();
        adminTypes.add("artist_application");
        adminTypes.add("creator_apply");
        adminTypes.add("external_creator_apply");
        adminTypes.add("creator_vip_apply");
        adminTypes.add("user_verification");
        adminTypes.add("withdraw_apply");
        adminTypes.add("refund_apply");
        adminTypes.add("payment_order");
        adminTypes.add("external_content");
        adminTypes.add("marketplace_item");
        ADMIN_ONLY_TARGET_TYPES = Collections.unmodifiableSet(adminTypes);

        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        sources.put("platform", "平台");
        sources.put("external", "外部");
        sources.put("creator", "创作者");
        sources.put("user", "用户");
        sources.put("system", "系统");
        sources.put("admin", "管理员");
        SUBMITTER_SOURCE_NAMES = Collections.unmodifiableMap(sources);

        LinkedHashMap<String, String> statuses = new LinkedHashMap<>();
        statuses.put(STATUS_PENDING, "待处理");
        statuses.put(STATUS_IN_PROGRESS, "审核中");
        statuses.put(STATUS_APPROVED, "已通过");
        statuses.put(STATUS_REJECTED, "已拒绝");
        statuses.put(STATUS_SKIPPED, "已跳过");
        STATUS_NAMES = Collections.unmodifiableMap(statuses);
    }

    private ModerationConstants() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeTargetType(String targetType) {
        return normalize(targetType);
    }

    public static String normalizeSubmitterSource(String submitterSource) {
        return normalize(submitterSource);
    }

    public static boolean isSupportedTargetType(String targetType) {
        return targetType != null && TARGET_TYPE_NAMES.containsKey(targetType);
    }

    public static boolean isSupportedSubmitterSource(String submitterSource) {
        return submitterSource != null && SUBMITTER_SOURCE_NAMES.containsKey(submitterSource);
    }

    public static boolean isSupportedStatus(String status) {
        return status != null && STATUS_NAMES.containsKey(status);
    }

    public static boolean isAdminOnlyTargetType(String targetType) {
        String normalized = normalizeTargetType(targetType);
        return normalized != null && ADMIN_ONLY_TARGET_TYPES.contains(normalized);
    }

    public static String getTargetTypeName(String targetType) {
        String normalized = normalizeTargetType(targetType);
        String name = normalized == null ? null : TARGET_TYPE_NAMES.get(normalized);
        return name != null ? name : (targetType == null ? "-" : targetType);
    }

    public static String getSubmitterSourceName(String submitterSource) {
        String normalized = normalizeSubmitterSource(submitterSource);
        String name = normalized == null ? null : SUBMITTER_SOURCE_NAMES.get(normalized);
        return name != null ? name : (submitterSource == null ? "-" : submitterSource);
    }

    public static String getStatusName(String status) {
        String normalized = normalize(status);
        String name = normalized == null ? null : STATUS_NAMES.get(normalized);
        return name != null ? name : (status == null ? "-" : status);
    }

    public static Map<String, String> getTargetTypeNames() {
        return TARGET_TYPE_NAMES;
    }

    public static Map<String, String> getSubmitterSourceNames() {
        return SUBMITTER_SOURCE_NAMES;
    }

    public static Map<String, String> getStatusNames() {
        return STATUS_NAMES;
    }

    public static Collection<String> getAdminOnlyTargetTypes() {
        return ADMIN_ONLY_TARGET_TYPES;
    }

    public static int normalizePriority(Integer priority) {
        if (priority == null) {
            return DEFAULT_PRIORITY;
        }
        if (priority < MIN_PRIORITY) {
            return MIN_PRIORITY;
        }
        if (priority > MAX_PRIORITY) {
            return MAX_PRIORITY;
        }
        return priority;
    }
}
