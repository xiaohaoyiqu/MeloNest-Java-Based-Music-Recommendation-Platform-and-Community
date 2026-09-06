import { request } from '@/utils/request';
export type CreatorStatus = 'pending' | 'approved' | 'rejected' | 'suspended' | 'removed';
export type CreatorType = 'independent' | 'label' | 'partner';
export type DecimalAmount = string | number;
export interface CreatorInfo {
    id: string;
    userId: string;
    userName: string;
    userAvatar: string;
    realName: string;
    phone: string;
    email: string;
    creatorType: string;
    status: string;
    feeRate: number;
    totalEarnings: number;
    todayEarnings: number;
    monthEarnings: number;
    applyReason: string;
    worksSample: string;
    reviewReason: string;
    reviewerId: string;
    reviewerName: string;
    applyTime: string;
    reviewTime: string;
    createTime: string;
}
export interface CreatorEarnings {
    totalEarnings: DecimalAmount;
    withdrawnAmount: DecimalAmount;
    pendingAmount: DecimalAmount;
    lastWithdrawTime?: string | null;
}
export interface CreatorStats {
    publishedWorkCount: number;
    registerDays: number;
    creditScore: number;
    totalPlays: number;
    fansCount: number;
    requiredRegisterDays: number;
    requiredPublishedWorks: number;
    canApply: boolean;
    applyReason: string;
}
export interface CreatorApplication {
    id: string;
    userId: string;
    username?: string;
    nickname?: string;
    avatar?: string;
    creatorType?: string;
    reason?: string;
    workUrls?: string;
    status: 0 | 1 | 2;
    statusDesc?: string;
    reviewComment?: string;
    applyTime?: string;
    phone?: string;
    email?: string;
}
export interface CreatorApplyParams {
    realName: string;
    idCardNo?: string;
    idCardUrl?: string;
    phone: string;
    email: string;
    applyReason: string;
    worksSample?: string;
}
export function applyCreator(params: CreatorApplyParams): ReturnType<typeof request<number>>;
export function applyCreator(realName: string, phone: string, email: string, applyReason: string, worksSample?: string): ReturnType<typeof request<number>>;
export function applyCreator(paramsOrRealName: CreatorApplyParams | string, phone?: string, email?: string, applyReason?: string, worksSample?: string) {
    const params: CreatorApplyParams = typeof paramsOrRealName === 'string'
        ? {
            realName: paramsOrRealName,
            phone: phone || '',
            email: email || '',
            applyReason: applyReason || '',
            worksSample
        }
        : paramsOrRealName;
    return request<number>({
        url: '/creator/apply',
        method: 'POST',
        params
    });
}
export function getMyCreatorInfo() {
    return request<CreatorInfo>({
        url: '/creator/my',
        method: 'GET'
    });
}
export function getMyApplication() {
    return request<CreatorApplication>({
        url: '/creator/my-application',
        method: 'GET'
    });
}
export function getCreatorEarnings() {
    return request<CreatorEarnings>({
        url: '/creator/earnings',
        method: 'GET'
    });
}
export function getCreatorStats() {
    return request<CreatorStats>({
        url: '/creator/stats',
        method: 'GET'
    });
}
export function reviewCreatorApply(id: string, approved: boolean, reviewReason?: string, creatorType?: CreatorType, feeRate?: number) {
    return request<boolean>({
        url: `/creator/review/${id}`,
        method: 'POST',
        params: { approved, reviewReason, creatorType, feeRate }
    });
}
export function getCreatorList(status?: CreatorStatus, creatorType?: CreatorType, page = 1, size = 20) {
    return request<{
        records: CreatorInfo[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/creator/list',
        method: 'GET',
        params: { status, creatorType, page, size }
    });
}
export function getPendingApplications(page = 1, size = 20) {
    return request<{
        records: CreatorInfo[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/creator/applications/pending',
        method: 'GET',
        params: { page, size }
    });
}
export function getPendingApplicationsPublic(page = 1, size = 20) {
    return request<{
        records: any[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/creator/applications/pending/public',
        method: 'GET',
        params: { page, size }
    });
}
export function getApplicationDetail(id: string) {
    return request<any>({
        url: `/creator/application/${id}`,
        method: 'GET'
    });
}
export function updateCreatorStatus(creatorId: string, status: CreatorStatus, reason?: string) {
    return request<boolean>({
        url: `/creator/${creatorId}/status`,
        method: 'POST',
        params: { status, reason }
    });
}
export function removeCreator(creatorId: string, reason?: string) {
    return request<boolean>({
        url: `/creator/${creatorId}/remove`,
        method: 'POST',
        params: { reason }
    });
}
export const creatorApi = {
    applyCreator,
    getMyCreatorInfo,
    getMyApplication,
    getCreatorEarnings,
    getCreatorStats,
    reviewCreatorApply,
    getCreatorList,
    getPendingApplications,
    getPendingApplicationsPublic,
    getApplicationDetail,
    updateCreatorStatus,
    removeCreator
};
