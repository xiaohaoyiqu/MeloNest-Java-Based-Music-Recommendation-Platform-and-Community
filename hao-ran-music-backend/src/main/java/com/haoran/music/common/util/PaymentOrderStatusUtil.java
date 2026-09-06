   
                      
   
package com.haoran.music.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.entity.PaymentOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PaymentOrderStatusUtil {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REFUNDING = "refunding";
    public static final String STATUS_REFUNDED = "refunded";

    public static final String PAID_STATUS_SQL_VALUES = "'" + STATUS_PAID + "', '" + STATUS_SUCCESS + "', '" + STATUS_COMPLETED + "'";
    public static final String PAID_STATUS_SQL_FRAGMENT = "status IN (" + PAID_STATUS_SQL_VALUES + ")";
    public static final String HANDLED_STATUS_SQL_VALUES = PAID_STATUS_SQL_VALUES + ", '" + STATUS_REFUNDING + "', '" + STATUS_REFUNDED + "'";
    public static final String HANDLED_STATUS_SQL_FRAGMENT = "status IN (" + HANDLED_STATUS_SQL_VALUES + ")";

    private static final List<String> PAID_STATUSES = Collections.unmodifiableList(Arrays.asList(
            STATUS_PAID,
            STATUS_SUCCESS,
            STATUS_COMPLETED
    ));
    private static final List<String> HANDLED_STATUSES = Collections.unmodifiableList(Arrays.asList(
            STATUS_PAID,
            STATUS_SUCCESS,
            STATUS_COMPLETED,
            STATUS_REFUNDING,
            STATUS_REFUNDED
    ));

    private PaymentOrderStatusUtil() {
    }

    public static List<String> paidStatuses() {
        return PAID_STATUSES;
    }

    public static List<String> handledStatuses() {
        return HANDLED_STATUSES;
    }

    public static boolean isPaidStatus(String status) {
        return PAID_STATUSES.contains(status);
    }

    public static boolean isPaymentHandled(String status) {
        return HANDLED_STATUSES.contains(status);
    }

    public static <T> QueryWrapper<T> applyPaidOrderFilter(QueryWrapper<T> wrapper) {
        QueryWrapper<T> safeWrapper = wrapper == null ? new QueryWrapper<>() : wrapper;
        return safeWrapper.eq("deleted", CommonConstants.NOT_DELETED)
                .in("status", PAID_STATUSES);
    }

    public static LambdaQueryWrapper<PaymentOrder> applyPaidOrderFilter(LambdaQueryWrapper<PaymentOrder> wrapper) {
        LambdaQueryWrapper<PaymentOrder> safeWrapper = wrapper == null ? new LambdaQueryWrapper<>() : wrapper;
        return safeWrapper.eq(PaymentOrder::getDeleted, CommonConstants.NOT_DELETED)
                .in(PaymentOrder::getStatus, PAID_STATUSES);
    }
}
