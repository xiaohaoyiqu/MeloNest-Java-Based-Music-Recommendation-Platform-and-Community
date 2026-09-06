import { request } from '@/utils/request';
export interface ShareInfo {
    shareCode: string;
    shareUrl: string;
    type: string;
    resourceId: string;
    title: string;
    description: string;
    coverUrl: string;
    qrCodeUrl: string;
    createTime: string;
    expireTime: string;
}
export interface ShareStats {
    totalShares: number;
    totalViews: number;
    totalClicks: number;
    platformStats: Record<string, number>;
}
export function generateShareLink(type: string, resourceId: string) {
    return request<ShareInfo>({
        url: '/share/generate',
        method: 'POST',
        params: { type, resourceId }
    });
}
export function getResourceByShareCode(shareCode: string) {
    return request<{
        type: string;
        resourceId: string;
        title: string;
        description: string;
        coverUrl: string;
        creatorInfo: any;
    }>({
        url: `/share/resource/${shareCode}`,
        method: 'GET'
    });
}
export function recordShare(type: string, resourceId: string, platform: string) {
    return request<void>({
        url: '/share/record',
        method: 'POST',
        params: { type, resourceId, platform }
    });
}
export function getShareStats(type: string, resourceId: string) {
    return request<ShareStats>({
        url: '/share/stats',
        method: 'GET',
        params: { type, resourceId }
    });
}
export function batchGenerateShareLinks(items: Array<{
    type: string;
    resourceId: string;
}>) {
    return request<{
        success: string[];
        failed: Array<{
            type: string;
            resourceId: string;
        }>;
    }>({
        url: '/share/batch-generate',
        method: 'POST',
        data: { items }
    });
}
export function cancelShare(shareCode: string) {
    return request<void>({
        url: `/share/${shareCode}`,
        method: 'DELETE'
    });
}
export const shareApi = {
    generateShareLink,
    getResourceByShareCode,
    recordShare,
    getShareStats,
    batchGenerateShareLinks,
    cancelShare
};
