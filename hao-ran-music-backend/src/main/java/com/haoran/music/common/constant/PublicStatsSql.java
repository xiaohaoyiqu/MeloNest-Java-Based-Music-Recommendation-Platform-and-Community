


package com.haoran.music.common.constant;

import com.haoran.music.common.enums.UserType;




public final class PublicStatsSql {

    public static final int HIGH_RISK_SCORE_THRESHOLD = UserAccountPolicyConstants.HIGH_RISK_SCORE_THRESHOLD;
    public static final int NORMAL_CREDIT_MIN_SCORE = UserAccountPolicyConstants.PUBLIC_FLOW_CREDIT_MIN_SCORE;

    public static final String USER_JOIN = " JOIN `user` u ON u.id = ";
    public static final String INNER_USER_JOIN = " INNER JOIN `user` u ON u.id = ";
    public static final String USER_EXISTS_PREFIX = "SELECT 1 FROM `user` u WHERE u.id = ";

    public static final String USER_FILTER = " AND u.deleted = 0"
            + " AND u.status = 1"
            + " AND (u.is_banned IS NULL OR u.is_banned <> 1)"
            + " AND (u.user_type IS NULL OR u.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + "))"
            + " AND (u.risk_score IS NULL OR u.risk_score < " + HIGH_RISK_SCORE_THRESHOLD + ")"
            + " AND (u.credit_score IS NULL OR u.credit_score >= " + NORMAL_CREDIT_MIN_SCORE + ")";
    public static final String USER_FILTER_XML = " AND u.deleted = 0"
            + " AND u.status = 1"
            + " AND (u.is_banned IS NULL OR u.is_banned &lt;&gt; 1)"
            + " AND (u.user_type IS NULL OR u.user_type NOT IN (" + UserType.RESTRICTED_CODE_SQL + "))"
            + " AND (u.risk_score IS NULL OR u.risk_score &lt; " + HIGH_RISK_SCORE_THRESHOLD + ")"
            + " AND (u.credit_score IS NULL OR u.credit_score &gt;= " + NORMAL_CREDIT_MIN_SCORE + ")";

    public static final String CREATOR_FILTER = USER_FILTER
            + " AND (u.creator_status IS NULL OR u.creator_status <> '" + UserAccountPolicyConstants.CREATOR_STATUS_SUSPENDED + "')";
    public static final String CREATOR_FILTER_XML = USER_FILTER_XML
            + " AND (u.creator_status IS NULL OR u.creator_status &lt;&gt; '" + UserAccountPolicyConstants.CREATOR_STATUS_SUSPENDED + "')";






    public static final String RETAINED_PUBLIC_CONTENT_FILTER = " AND u.deleted = 0"
            + " AND (u.status IN (0, 2)"
            + " OR u.is_banned = 1"
            + " OR u.user_type = " + UserType.BANNED.getCode()
            + " OR (u.status = 1 AND (u.user_type IS NULL OR u.user_type NOT IN ("
            + UserType.RESTRICTED_CODE_SQL + "))))";


    public static final String ACTIVE_CREATOR_FILTER = USER_FILTER
            + " AND u.is_creator = 1 AND u.creator_status = 'active'";

    private PublicStatsSql() {
    }
}
