import { request } from '@/utils/request';
import { createUploadProgressHandler, reportUploadProgress, type UploadProgressHandler } from '@/utils/uploadProgress';
export type BusinessType = 'vip' | 'purchase' | 'reward' | 'subscribe' | 'gift_vip' | 'gift_marketplace' | 'emoji_package' | 'decoration' | 'VIP';
export type OrderStatus = 'pending' | 'submitted' | 'paid' | 'rejected' | 'cancelled' | 'refunding' | 'refunded' | 'expired' | 'success' | 'completed';
export type PaymentMethod = 'alipay' | 'wechat' | 'credit' | 'qrcode';
export type EntityId = string | number;
export interface PaymentOrder {
    id: EntityId;
    orderId: EntityId;
    orderNo: string;
    userId: EntityId;
    businessType: string;
    businessId: EntityId;
    amount: string | number;
    currency: string;
    status: OrderStatus;
    completionStatus: 'pending' | 'processing' | 'completed' | 'blocked' | 'failed' | null;
    completionTime: string | null;
    completionPending: boolean;
    paymentMethod: PaymentMethod | string | null;
    paymentType: PaymentMethod | string | null;
    userRemark: string | null;
    createTime: string | null;
    reviewTime: string | null;
    expireTime: string | null;
}
export interface AdminPaymentOrder extends PaymentOrder {
    payeeId: EntityId | null;
    orderTitle: string | null;
    reviewerId: EntityId | null;
    reviewReason: string | null;
    completionError: string | null;
    completionAttemptCount?: number | null;
    completionLastAttemptTime?: string | null;
    completionNextRetryTime?: string | null;
    completionLeaseUntil?: string | null;
    completionDeadLetterTime?: string | null;
    proofImageUrl?: string;
}
export interface PaymentOrderDetail extends PaymentOrder {
    canCancel: boolean;
    canSubmitProof: boolean;
    remainingTime: number;
}
export interface CreateOrderResult {
    id: EntityId;
    orderId: EntityId;
    orderNo: string;
    amount: string | number;
    currency: string;
    businessType: string;
    expireTime: string;
    status: OrderStatus;
    message: string;
    idempotentReplay: boolean;
}
export interface CreateOrderRequest {
    idempotencyKey: string;
    businessType: BusinessType;
    businessId: string | number;
    amount?: number;
    payeeId?: string | number;
    userRemark?: string;
}
export function createOrder(data: CreateOrderRequest) {
    const { idempotencyKey, ...params } = data;
    return request<CreateOrderResult>({
        url: '/payment/create',
        method: 'POST',
        params,
        headers: { 'Idempotency-Key': idempotencyKey }
    });
}
export function getOrderQrCode(orderId: string) {
    return request<{
        qrCodeUrl?: string;
        qrcodeUrl?: string;
        qrCodeWithVerify?: string;
        verifyCode: string;
        paymentType?: string;
        platformAccount?: string;
        uploadUrl?: string;
    }>({
        url: `/payment/qrcode/${orderId}`,
        method: 'GET'
    });
}
export function submitPaymentProof(orderId: string, proofUrl: string, verifyCode: string) {
    return request<{
        orderId: string | number;
        status: OrderStatus;
        message: string;
    }>({
        url: '/payment/submit',
        method: 'POST',
        params: { orderId, proofUrl, verifyCode }
    });
}
export function uploadPaymentProof(file: File, orderId: string | number, onProgress?: UploadProgressHandler) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('orderId', String(orderId));
    const onUploadProgress = createUploadProgressHandler(onProgress);
    return request<string>({
        url: '/payment/proof/upload',
        method: 'POST',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        },
        ...(onUploadProgress ? { onUploadProgress } : {})
    }).then(response => {
        reportUploadProgress(onProgress, 100);
        return response;
    });
}
export function getMyOrders(params: {
    status?: OrderStatus;
    businessType?: BusinessType;
    page?: number;
    size?: number;
} = {}) {
    return request<{
        list: PaymentOrderDetail[];
        total: number;
        page: number;
        size: number;
    }>({
        url: '/payment/my',
        method: 'GET',
        params: { page: 1, size: 20, ...params }
    });
}
export function getPendingOrders(payeeId?: string | number, page = 1, size = 20) {
    return request<{
        records: AdminPaymentOrder[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/payment/pending',
        method: 'GET',
        params: { payeeId, page, size }
    });
}
export function getPendingCompletionOrders(page = 1, size = 20) {
    return request<{
        records: AdminPaymentOrder[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/payment/completion-pending',
        method: 'GET',
        params: { page, size }
    });
}
export function cancelOrder(orderId: string) {
    return request<boolean>({
        url: `/payment/cancel/${orderId}`,
        method: 'POST'
    });
}
export function getOrderDetail(orderId: string) {
    return request<PaymentOrderDetail>({
        url: `/payment/${orderId}`,
        method: 'GET'
    });
}
export function getOrderByNo(orderNo: string) {
    return request<PaymentOrderDetail>({
        url: `/payment/order/${orderNo}`,
        method: 'GET'
    });
}
export function reviewOrder(orderId: string, approved: boolean, reviewReason?: string) {
    return request<{
        status: OrderStatus;
        completionPending?: boolean;
        message: string;
        reason?: string;
    }>({
        url: `/payment/review/${orderId}`,
        method: 'POST',
        params: { approved, reviewReason }
    });
}
export function retryOrderCompletion(orderId: string) {
    return request<boolean>({
        url: `/payment/retry-completion/${orderId}`,
        method: 'POST'
    });
}
export function resubmitPaymentProof(orderId: string, proofUrl: string, verifyCode: string) {
    return request<{
        orderId: string | number;
        status: OrderStatus;
        message: string;
    }>({
        url: '/payment/resubmit',
        method: 'POST',
        params: { orderId, proofUrl, verifyCode }
    });
}
export function checkOrderStatus(orderId: string) {
    return request<{
        status: OrderStatus;
        canCancel: boolean;
        canSubmitProof: boolean;
        remainingTime: number;
    }>({
        url: `/payment/${orderId}/status`,
        method: 'GET'
    });
}
export function getOrderStatistics() {
    return request<{
        pendingCount: number;
        submittedCount: number;
        paidCount: number;
        rejectedCount: number;
        cancelledCount: number;
        expiredCount: number;
        refundingCount: number;
        refundedCount: number;
        totalAmount: string | number;
        todayAmount: string | number;
        currency?: string;
    }>({
        url: '/payment/statistics',
        method: 'GET'
    });
}
export const paymentApi = {
    createOrder,
    getOrderQrCode,
    submitPaymentProof,
    uploadPaymentProof,
    getMyOrders,
    getPendingOrders,
    getPendingCompletionOrders,
    cancelOrder,
    getOrderDetail,
    getOrderByNo,
    reviewOrder,
    retryOrderCompletion,
    resubmitPaymentProof,
    checkOrderStatus,
    getOrderStatistics
};
