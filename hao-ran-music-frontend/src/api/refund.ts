import { request } from '@/utils/request';
export type RefundStatus = 'pending' | 'approved' | 'rejected' | 'processing' | 'completed' | 'failed';
export type EntityId = string | number;
export type DecimalAmount = string | number;
export type RefundReason = 'quality' | 'description' | 'price' | 'service' | 'accident' | 'other';
export interface RefundRecord {
    id: string;
    refundNo: string;
    userId: string;
    orderId: string;
    orderType: string;
    amount: DecimalAmount;
    reason: string;
    description: string | null;
    status: RefundStatus;
    reviewerId: string | null;
    reviewTime: string | null;
    reviewReason: string | null;
    completedTime: string | null;
    feedbackId: string | null;
    isUnreasonable?: number | null;
    unreasonableReason?: string | null;
    createTime: string | null;
    updateTime: string | null;
}
export interface RefundDetail {
    id: string;
    userId: string;
    orderId: string;
    orderType: string;
    amount: DecimalAmount;
    reason: string;
    description: string | null;
    status: RefundStatus;
    createTime: string | null;
    reviewTime: string | null;
    reviewReason: string | null;
}
export interface RefundStatistics {
    totalCount: number;
    successCount: number;
    rejectCount: number;
    unreasonableCount: number;
    creditScore: number;
}
export interface RefundEligibility {
    eligible: boolean;
    reason: string;
}
export interface RefundApplicationResult {
    success: boolean;
    refundId: EntityId;
    orderId: EntityId;
    status: 'refunding';
    message: string;
}
export interface RefundPage {
    records: RefundRecord[];
    total: number;
    current: number;
    size: number;
    pages: number;
}
interface RawRefundPage {
    records?: Record<string, unknown>[];
    list?: Record<string, unknown>[];
    total?: number;
    current?: number;
    page?: number;
    size?: number;
    pages?: number;
}
function normalizeRefundRecord(record: Record<string, unknown>): RefundRecord {
    return {
        id: String(record.id ?? record.refundId ?? ''),
        refundNo: String(record.refundNo ?? ''),
        userId: String(record.userId ?? ''),
        orderId: String(record.orderId ?? ''),
        orderType: String(record.orderType ?? ''),
        amount: typeof record.amount === 'string' || typeof record.amount === 'number'
            ? record.amount
            : (typeof record.refundAmount === 'string' || typeof record.refundAmount === 'number' ? record.refundAmount : '0'),
        reason: String(record.reason ?? ''),
        description: record.description == null ? null : String(record.description),
        status: String(record.status ?? '') as RefundStatus,
        reviewerId: record.reviewerId == null ? null : String(record.reviewerId),
        reviewTime: record.reviewTime == null ? null : String(record.reviewTime),
        reviewReason: record.reviewReason == null ? null : String(record.reviewReason),
        completedTime: record.completedTime == null ? null : String(record.completedTime),
        feedbackId: record.feedbackId == null ? null : String(record.feedbackId),
        isUnreasonable: typeof record.isUnreasonable === 'number' ? record.isUnreasonable : null,
        unreasonableReason: record.unreasonableReason == null ? null : String(record.unreasonableReason),
        createTime: record.createTime == null ? null : String(record.createTime),
        updateTime: record.updateTime == null ? null : String(record.updateTime)
    };
}
function normalizeRefundPage(payload: RawRefundPage | undefined, fallbackPage: number, fallbackSize: number): RefundPage {
    const records = payload?.records ?? payload?.list ?? [];
    const total = payload?.total ?? 0;
    const current = payload?.current ?? payload?.page ?? fallbackPage;
    const size = payload?.size ?? fallbackSize;
    return {
        records: records.map(normalizeRefundRecord),
        total,
        current,
        size,
        pages: payload?.pages ?? (size > 0 ? Math.ceil(total / size) : 0)
    };
}
export function applyRefund(orderId: string, orderType: string, reason: string, description?: string) {
    return request<RefundApplicationResult>({
        url: '/refund/apply',
        method: 'POST',
        params: { orderId, orderType, reason, description }
    });
}
export async function getMyRefundRecords(status?: RefundStatus, page = 1, size = 20) {
    const response = await request<RawRefundPage>({
        url: '/refund/my',
        method: 'GET',
        params: { status, page, size }
    });
    return { ...response, data: normalizeRefundPage(response.data, page, size) };
}
export function getRefundStatistics() {
    return request<RefundStatistics>({
        url: '/refund/statistics',
        method: 'GET'
    });
}
export function checkRefundEligible(orderId: string) {
    return request<RefundEligibility>({
        url: '/refund/check-eligible',
        method: 'GET',
        params: { orderId }
    });
}
export async function getRefundDetail(refundId: string) {
    const response = await request<Record<string, unknown>>({
        url: `/refund/${refundId}`,
        method: 'GET'
    });
    const record = normalizeRefundRecord(response.data ?? {});
    const detail: RefundDetail = {
        id: record.id,
        userId: record.userId,
        orderId: record.orderId,
        orderType: record.orderType,
        amount: record.amount,
        reason: record.reason,
        description: record.description,
        status: record.status,
        createTime: record.createTime,
        reviewTime: record.reviewTime,
        reviewReason: record.reviewReason
    };
    return { ...response, data: detail };
}
export async function getPendingRefunds(page = 1, size = 20) {
    const response = await request<RawRefundPage>({
        url: '/refund/pending',
        method: 'GET',
        params: { page, size }
    });
    return { ...response, data: normalizeRefundPage(response.data, page, size) };
}
export function reviewRefund(refundId: string, approved: boolean, reviewReason?: string, options: {
    isUnreasonable?: boolean;
    unreasonableReason?: string;
} = {}) {
    const params: Record<string, unknown> = { approved, reviewReason };
    if (options.isUnreasonable !== undefined)
        params.isUnreasonable = options.isUnreasonable;
    if (options.unreasonableReason !== undefined)
        params.unreasonableReason = options.unreasonableReason;
    return request<{
        status: 'approved' | 'rejected';
        message: string;
        reason?: string;
    }>({
        url: `/refund/review/${refundId}`,
        method: 'POST',
        params
    });
}
export function completeRefund(refundId: string) {
    return request<boolean>({
        url: `/refund/${refundId}/complete`,
        method: 'POST'
    });
}
export function getRefundCredit() {
    return request<number>({
        url: '/refund/credit',
        method: 'GET'
    });
}
export function cancelRefund(refundId: string) {
    return request<boolean>({
        url: `/refund/${refundId}/cancel`,
        method: 'POST'
    });
}
export function createRefundFromFeedback(feedbackId: string) {
    return request<EntityId>({
        url: '/refund/create-from-feedback',
        method: 'POST',
        params: { feedbackId }
    });
}
export const refundApi = {
    applyRefund,
    getMyRefundRecords,
    getRefundStatistics,
    checkRefundEligible,
    getRefundDetail,
    getPendingRefunds,
    reviewRefund,
    completeRefund,
    getRefundCredit,
    cancelRefund,
    createRefundFromFeedback
};
