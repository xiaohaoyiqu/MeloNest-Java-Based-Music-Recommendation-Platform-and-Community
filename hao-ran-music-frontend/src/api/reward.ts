import { request } from '@/utils/request';
export interface RewardRecord {
    id: string;
    userId: string;
    creatorId: string;
    amount: number;
    message: string;
    resourceId: string;
    resourceType: string;
    isAnonymous: boolean;
    createTime: string;
}
export interface PublicRewardSummary {
    amount: number;
    message?: string;
    isAnonymous: boolean;
    createTime: string;
}
export interface RewardStatistics {
    totalAmount: number;
    totalCount: number;
    todayAmount: number;
    todayCount: number;
    monthAmount: number;
    monthCount: number;
}
export interface RewardCreateResult {
    rewardId: string | number;
    orderNo: string;
    amount: number;
    paymentOrderId: string | number;
    message: string;
}
export interface PageResult<T> {
    records: T[];
    total: number;
    size: number;
    current: number;
    pages: number;
}
export function rewardCreator(creatorId: string | number, amount: number, message?: string, resourceId?: string | number, resourceType?: string, isAnonymous?: boolean) {
    return request<RewardCreateResult>({
        url: `/reward/${creatorId}`,
        method: 'POST',
        params: { amount, message, resourceId, resourceType, isAnonymous }
    });
}
export function getMyRewardRecords(page = 1, size = 20) {
    return request<PageResult<RewardRecord>>({
        url: '/reward/my',
        method: 'GET',
        params: { page, size }
    });
}
export function getReceivedRewards(page = 1, size = 20) {
    return request<PageResult<RewardRecord>>({
        url: '/reward/received',
        method: 'GET',
        params: { page, size }
    });
}
export function getRewardStatistics() {
    return request<RewardStatistics>({
        url: '/reward/statistics',
        method: 'GET'
    });
}
export function getResourceRewards(resourceId: string | number, resourceType: string, page = 1, size = 20) {
    return request<PageResult<PublicRewardSummary>>({
        url: '/reward/resource',
        method: 'GET',
        params: { resourceId, resourceType, page, size }
    });
}
export function cancelReward(rewardId: string) {
    return request<boolean>({
        url: `/reward/${rewardId}/cancel`,
        method: 'POST'
    });
}
export function getRewardDetail(rewardId: string) {
    return request<RewardRecord>({
        url: `/reward/${rewardId}`,
        method: 'GET'
    });
}
export const rewardApi = {
    rewardCreator,
    getMyRewardRecords,
    getReceivedRewards,
    getRewardStatistics,
    getResourceRewards,
    cancelReward,
    getRewardDetail
};
