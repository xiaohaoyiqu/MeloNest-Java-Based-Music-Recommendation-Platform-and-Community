import { request } from '@/utils/request';
export type ReportType = 'inappropriate' | 'copyright' | 'spam' | 'wrong_info' | 'illegal' | 'porn' | 'abuse' | 'fake' | 'other';
export type TargetType = 'song' | 'album' | 'artist' | 'playlist' | 'mv' | 'user' | 'comment' | 'post' | 'MARKETPLACE_ITEM';
export type ReportStatus = 'pending' | 'approved' | 'rejected' | 'rewarded' | 'withdrawn';
export interface ReportRecord {
    id: string;
    reporterId: string;
    targetType: string;
    targetId: string;
    reportType: string;
    reason: string;
    description: string;
    attachmentUrls: string;
    attachmentAssetIds?: string[];
    status: string;
    reviewerId: string;
    reviewReason: string;
    rewardAmount: number;
    createTime: string;
    reviewTime: string;
}
export interface ReportStatistics {
    totalReports: number;
    pendingReports: number;
    approvedReports: number;
    rejectedReports: number;
    rewardedReports: number;
    totalRewards: number;
    currentCredit: number;
}
export interface RewardConfig {
    approveReward: number;
    rejectReward: number;
    maxDailyRewards: number;
    rewardThreshold: number;
}
export function submitReport(targetType: TargetType, targetId: string | number, reportType: ReportType, reason: string, description?: string, attachmentAssetIds?: string[]) {
    return request<number>({
        url: '/report/submit',
        method: 'POST',
        data: { targetType, targetId, reportType, reason, description, attachmentAssetIds }
    });
}
export function getMyReports(status?: ReportStatus, page = 1, size = 20) {
    return request<{
        records: ReportRecord[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/report/my',
        method: 'GET',
        params: { status, page, size }
    });
}
export function getReportStatistics() {
    return request<ReportStatistics>({
        url: '/report/statistics',
        method: 'GET'
    });
}
export function getReportDetail(reportId: string | number) {
    return request<ReportRecord>({
        url: `/report/${reportId}`,
        method: 'GET'
    });
}
export function withdrawReport(reportId: string | number) {
    return request<boolean>({
        url: `/report/withdraw/${reportId}`,
        method: 'POST'
    });
}
export function getTargetReports(targetType: TargetType, targetId: string | number, page = 1, size = 20) {
    return request<{
        records: ReportRecord[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/report/target',
        method: 'GET',
        params: { targetType, targetId, page, size }
    });
}
export function getPendingReports(page = 1, size = 20) {
    return request<{
        records: ReportRecord[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/report/pending',
        method: 'GET',
        params: { page, size }
    });
}
export function reviewReport(reportId: string | number, approved: boolean, reviewReason?: string, action?: string) {
    return request<boolean>({
        url: `/report/review/${reportId}`,
        method: 'POST',
        params: { approved, reviewReason, action }
    });
}
export function grantReportReward(reportId: string | number) {
    return request<number>({
        url: `/report/${reportId}/reward`,
        method: 'POST'
    });
}
export function getReportCredit() {
    return request<number>({
        url: '/report/credit',
        method: 'GET'
    });
}
export function getReportRewardConfig() {
    return request<RewardConfig>({
        url: '/report/reward-config',
        method: 'GET'
    });
}
export const reportApi = {
    submitReport,
    getMyReports,
    getReportStatistics,
    getReportDetail,
    withdrawReport,
    getTargetReports,
    getPendingReports,
    reviewReport,
    grantReportReward,
    getReportCredit,
    getReportRewardConfig
};
