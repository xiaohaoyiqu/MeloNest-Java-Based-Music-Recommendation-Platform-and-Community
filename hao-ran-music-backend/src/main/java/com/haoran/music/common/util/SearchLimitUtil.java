


package com.haoran.music.common.util;

import com.haoran.music.common.constant.CommonConstants;




public final class SearchLimitUtil {

    private static final int DEFAULT_LIMIT = 10;

    private SearchLimitUtil() {
    }

    public static int normalize(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, CommonConstants.MAX_SIZE);
    }
}
