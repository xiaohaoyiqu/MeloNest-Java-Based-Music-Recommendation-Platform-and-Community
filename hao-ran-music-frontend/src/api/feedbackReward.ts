import { request } from '@/utils/request';
export function createFeedbackReward(params: {
    feedbackId: string;
    rewardLevel: string;
    rewardDescription?: string;
}) {
    return request<boolean>({
        url: '/feedback-reward/create',
        method: 'POST',
        params
    });
}
export function grantFeedbackReward(id: string) {
    return request<boolean>({
        url: `/feedback-reward/${id}/grant`,
        method: 'POST'
    });
}
export function cancelFeedbackReward(id: string, cancelReason: string) {
    return request<boolean>({
        url: `/feedback-reward/${id}/cancel`,
        method: 'POST',
        params: { cancelReason }
    });
}
export function getMyRewards(page = 1, size = 20) {
    return request<{
        records: any[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/feedback-reward/my-rewards',
        method: 'GET',
        params: { page, size }
    });
}
export function getPendingRewards(page = 1, size = 20) {
    return request<{
        records: any[];
        total: number;
    }>({
        url: '/feedback-reward/pending',
        method: 'GET',
        params: { page, size }
    });
}
export function getRewardLevels() {
    return request<string[]>({
        url: '/feedback-reward/reward-levels',
        method: 'GET'
    });
}
export function getFeedbackRewardDetail(id: string) {
    return request<any>({
        url: `/feedback-reward/${id}`,
        method: 'GET'
    });
}
export function batchGrantRewards(ids: number[]) {
    return request<{
        total: number;
        success: number;
    }>({
        url: '/feedback-reward/batch-grant',
        method: 'POST',
        params: { ids: ids.join(',') }
    });
}
export function getRewardStatistics() {
    return request<{
        totalRewards: number;
        pendingRewards: number;
        todayGranted: number;
        totalPoints: number;
        totalVipDays: number;
    }>({
        url: '/feedback-reward/statistics',
        method: 'GET'
    });
}
export interface FeedbackReward {
    id: string;
    feedbackId: string;
    rewardLevel: string;
    rewardDescription: string;
    rewardPoints?: number;
    rewardVipDays?: number;
    rewardBadgeId?: string | number;
    grantorId?: string | number;
    grantorName?: string;
    grantedTime?: string;
    status: string;
    createTime: string;
    updateTime: string;
}
