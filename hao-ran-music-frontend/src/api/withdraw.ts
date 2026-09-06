import { request } from '@/utils/request';
export type WithdrawStatus = 'pending' | 'processing' | 'rejected' | 'completed' | 'failed';
export type DecimalAmount = string | number;
export type WithdrawType = 'alipay' | 'wechat' | 'bank';
export interface WithdrawRecord {
    id: string;
    creatorId: string;
    amount: DecimalAmount;
    withdrawType: string;
    withdrawAccount: string;
    withdrawName: string;
    status: string;
    reviewReason: string | null;
    transactionId: string | null;
    reviewerId?: string | null;
    reviewerName?: string | null;
    applyTime: string | null;
    reviewTime: string | null;
    completeTime: string | null;
}
export interface WithdrawStatistics {
    totalEarnings: DecimalAmount;
    withdrawnEarnings: DecimalAmount;
    pendingEarnings: DecimalAmount;
    availableEarnings: DecimalAmount;
}
interface WithdrawPagePayload {
    records?: Record<string, unknown>[];
    list?: Record<string, unknown>[];
    total?: number;
    current?: number;
    page?: number;
    size?: number;
    pages?: number;
}
function normalizeWithdrawRecord(record: Record<string, unknown>): WithdrawRecord {
    return {
        id: String(record.withdrawId ?? record.id ?? ''),
        creatorId: String(record.creatorId ?? ''),
        amount: typeof record.amount === 'string' || typeof record.amount === 'number' ? record.amount : '0',
        withdrawType: String(record.withdrawType ?? ''),
        withdrawAccount: String(record.withdrawAccount ?? ''),
        withdrawName: String(record.withdrawName ?? ''),
        status: String(record.status ?? ''),
        reviewReason: record.reviewReason == null ? null : String(record.reviewReason),
        transactionId: record.transactionId == null ? null : String(record.transactionId),
        reviewerId: record.reviewerId == null ? null : String(record.reviewerId),
        reviewerName: record.reviewerName == null ? null : String(record.reviewerName),
        applyTime: record.applyTime == null && record.createTime == null
            ? null
            : String(record.applyTime ?? record.createTime),
        reviewTime: record.reviewTime == null ? null : String(record.reviewTime),
        completeTime: record.completeTime == null && record.completedTime == null
            ? null
            : String(record.completeTime ?? record.completedTime)
    };
}
function normalizeWithdrawPage(payload: WithdrawPagePayload | undefined, fallbackPage: number, fallbackSize: number) {
    const records = payload?.records ?? payload?.list ?? [];
    const current = payload?.current ?? payload?.page ?? fallbackPage;
    const size = payload?.size ?? fallbackSize;
    const total = payload?.total ?? 0;
    return {
        records: records.map(normalizeWithdrawRecord),
        total,
        current,
        size,
        pages: payload?.pages ?? (size > 0 ? Math.ceil(total / size) : 0)
    };
}
export function applyWithdraw(amount: DecimalAmount, withdrawType: WithdrawType, withdrawAccount: string, withdrawName: string) {
    return request<number>({
        url: '/withdraw/apply',
        method: 'POST',
        params: { amount, withdrawType, withdrawAccount, withdrawName }
    });
}
export async function getMyWithdrawRecords(status?: WithdrawStatus, page = 1, size = 20) {
    const response = await request<WithdrawPagePayload>({
        url: '/withdraw/my',
        method: 'GET',
        params: { status, page, size }
    });
    return { ...response, data: normalizeWithdrawPage(response.data, page, size) };
}
export function getWithdrawStatistics() {
    return request<WithdrawStatistics>({
        url: '/withdraw/statistics',
        method: 'GET'
    });
}
export function getAvailableAmount() {
    return request<DecimalAmount>({
        url: '/withdraw/available',
        method: 'GET'
    });
}
export async function getPendingWithdraws(page = 1, size = 20) {
    const response = await request<WithdrawPagePayload>({
        url: '/withdraw/pending',
        method: 'GET',
        params: { page, size }
    });
    return { ...response, data: normalizeWithdrawPage(response.data, page, size) };
}
export function reviewWithdraw(id: string, approved: boolean, reviewReason?: string) {
    return request<boolean>({
        url: `/withdraw/review/${id}`,
        method: 'POST',
        params: { approved, reviewReason }
    });
}
export function completeWithdraw(id: string, transactionId: string) {
    return request<boolean>({
        url: `/withdraw/${id}/complete`,
        method: 'POST',
        params: { transactionId }
    });
}
export async function getWithdrawDetail(id: string) {
    const response = await request<Record<string, unknown>>({
        url: `/withdraw/${id}`,
        method: 'GET'
    });
    return { ...response, data: normalizeWithdrawRecord(response.data ?? {}) };
}
export const withdrawApi = {
    applyWithdraw,
    getMyWithdrawRecords,
    getWithdrawStatistics,
    getAvailableAmount,
    getPendingWithdraws,
    reviewWithdraw,
    completeWithdraw,
    getWithdrawDetail
};
