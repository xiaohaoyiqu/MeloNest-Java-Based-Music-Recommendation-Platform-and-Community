package com.haoran.music.enums;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;

import java.util.Locale;






public enum PrivateAttachmentPurpose {

    MESSAGE_IMAGE("PRIVATE_MESSAGE", 1),
    REPORT_EVIDENCE("REPORT", 5),
    FEEDBACK_ATTACHMENT("FEEDBACK", 5),
    APPEAL_EVIDENCE("APPEAL", 5),
    MODERATION_APPEAL_EVIDENCE("MODERATION_APPEAL", 5);

    private final String targetType;
    private final int maxFiles;

    PrivateAttachmentPurpose(String targetType, int maxFiles) {
        this.targetType = targetType;
        this.maxFiles = maxFiles;
    }







    public static PrivateAttachmentPurpose require(String value) {
        if (ObjectUtils.isNotEmpty(value)) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {

            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的私有附件用途");
    }

    public String getTargetType() {
        return targetType;
    }

    public int getMaxFiles() {
        return maxFiles;
    }
}
