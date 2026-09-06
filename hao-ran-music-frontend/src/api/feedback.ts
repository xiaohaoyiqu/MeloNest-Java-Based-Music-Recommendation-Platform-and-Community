import { request } from '@/utils/request';
export type FeedbackType = 'issue' | 'suggestion' | 'content' | 'accessibility' | 'complaint' | 'refund';
export const GENERAL_FEEDBACK_TYPES: ReadonlyArray<{
    value: Exclude<FeedbackType, 'refund'>;
    label: string;
    description: string;
}> = [
    { value: 'issue', label: '功能异常', description: '页面、播放或操作没有按预期工作' },
    { value: 'suggestion', label: '功能建议', description: '想补充的新功能或体验改进' },
    { value: 'content', label: '内容纠错', description: '歌曲、歌词、专辑等资料需要修正' },
    { value: 'accessibility', label: '显示与无障碍', description: '颜色、字号、键盘或读屏使用不便' },
    { value: 'complaint', label: '服务投诉', description: '举报不当内容或说明服务问题' }
];
export function feedbackTypeLabel(type: string): string {
    if (type === 'refund')
        return '退款申请';
    return GENERAL_FEEDBACK_TYPES.find(item => item.value === type)?.label || type;
}
export type FeedbackStatus = 'pending' | 'processing' | 'resolved' | 'closed' | 'rejected';
export interface FeedbackRecord {
    id: string;
    userId: string;
    feedbackType: string;
    orderId: string;
    orderType: string;
    title: string;
    content: string;
    attachmentUrls: string;
    attachmentAssetIds?: string[];
    status: string;
    handlerId: string;
    handleResult: string;
    closeReason: string;
    createTime: string;
    handleTime: string;
    closeTime: string;
}
export interface FeedbackPage {
    list: FeedbackRecord[];
    total: number;
    page: number;
    size: number;
}
export interface FeedbackStatistics {
    totalFeedbacks: number;
    pendingFeedbacks: number;
    resolvedFeedbacks: number;
    closedFeedbacks: number;
    todayFeedbacks: number;
    weekFeedbacks: number;
    avgResponseTime: number;
}
export function submitFeedback(feedbackType: FeedbackType, title: string, content: string, orderId?: string | number, orderType?: string, attachmentAssetIds?: string[]) {
    return request<number>({
        url: '/feedback/submit',
        method: 'POST',
        params: { feedbackType, orderId, orderType, title, content, attachmentAssetIds }
    });
}
export function getMyFeedbacks(feedbackType?: FeedbackType, status?: FeedbackStatus, page = 1, size = 20) {
    return request<FeedbackPage>({
        url: '/feedback/my',
        method: 'GET',
        params: { feedbackType, status, page, size }
    });
}
export function getFeedbackStatistics() {
    return request<FeedbackStatistics>({
        url: '/feedback/statistics',
        method: 'GET'
    });
}
export function getFeedbackDetail(feedbackId: string | number) {
    return request<FeedbackRecord>({
        url: `/feedback/${feedbackId}`,
        method: 'GET'
    });
}
export function getPendingFeedbacks(feedbackType?: FeedbackType, page = 1, size = 20) {
    return request<{
        records: FeedbackRecord[];
        total: number;
        current: number;
        pages: number;
    }>({
        url: '/feedback/pending',
        method: 'GET',
        params: { feedbackType, page, size }
    });
}
export function handleFeedback(feedbackId: string | number, status: FeedbackStatus, handleResult?: string) {
    return request<boolean>({
        url: `/feedback/${feedbackId}/handle`,
        method: 'POST',
        params: { status, handleResult }
    });
}
export function closeFeedback(feedbackId: string | number, closeReason?: string) {
    return request<boolean>({
        url: `/feedback/${feedbackId}/close`,
        method: 'POST',
        params: { closeReason }
    });
}
export function createRefundFromFeedback(feedbackId: string | number) {
    return request<number>({
        url: `/feedback/${feedbackId}/create-refund`,
        method: 'POST'
    });
}
export function batchHandleFeedback(feedbackIds: number[], status: FeedbackStatus, handleResult?: string) {
    return request<{
        success: number;
        failed: number[];
    }>({
        url: '/feedback/batch-handle',
        method: 'POST',
        params: { feedbackIds: feedbackIds.join(','), status, handleResult }
    });
}
export const feedbackApi = {
    submitFeedback,
    getMyFeedbacks,
    getFeedbackStatistics,
    getFeedbackDetail,
    getPendingFeedbacks,
    handleFeedback,
    closeFeedback,
    createRefundFromFeedback,
    batchHandleFeedback
};
