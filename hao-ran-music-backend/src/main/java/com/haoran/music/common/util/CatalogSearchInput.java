package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;

   
                                                                            
  
                      
   
public final class CatalogSearchInput {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private CatalogSearchInput() {
    }

    public static String normalize(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "搜索关键词不能超过100个字符");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "搜索关键词包含非法控制字符");
        }
        return normalized;
    }

                                                                                                
    public static String normalizeForLike(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
