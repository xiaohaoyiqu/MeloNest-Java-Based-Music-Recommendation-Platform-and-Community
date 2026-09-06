import { request } from '@/utils/request';
export function addToBlacklist(blacklistedUserId: string | number, reason?: string) {
    return request<boolean>({
        url: '/blacklist/add',
        method: 'POST',
        params: {
            blacklistedUserId,
            reason
        }
    });
}
export function removeFromBlacklist(blacklistedUserId: string | number) {
    return request<boolean>({
        url: '/blacklist/remove',
        method: 'DELETE',
        params: {
            blacklistedUserId
        }
    });
}
export function getBlacklistDetailList() {
    return request<any[]>({
        url: '/blacklist/list/detail',
        method: 'GET'
    });
}
export function getBlacklistList() {
    return request<string[]>({
        url: '/blacklist/list',
        method: 'GET'
    });
}
export function checkBlacklist(blacklistedUserId: string | number) {
    return request<boolean>({
        url: '/blacklist/check',
        method: 'GET',
        params: {
            blacklistedUserId
        }
    });
}
export type ReportTargetType = 'song' | 'mv' | 'album' | 'playlist' | 'comment' | 'post' | 'marketplace_item' | 'user';
export type ReportType = 'inappropriate' | 'copyright' | 'spam' | 'wrong_info' | 'illegal' | 'porn' | 'abuse' | 'fake' | 'other';
export interface ReportSubmitPayload {
    targetType: ReportTargetType;
    targetId: string | number;
    reportType: ReportType;
    reason: string;
    description?: string;
    attachmentAssetIds?: Array<string | number>;
}
export interface ReportSubmitResult {
    reportId: string | number;
    status: 'pending';
    message: string;
}
export function reportUser(data: ReportSubmitPayload) {
    return request<ReportSubmitResult>({
        url: '/report/submit',
        method: 'POST',
        data
    });
}
export const blacklistApi = {
    addToBlacklist: (blacklistedUserId: string | number, reason?: string) => {
        return request<boolean>({
            url: '/blacklist/add',
            method: 'POST',
            params: {
                blacklistedUserId,
                reason
            }
        });
    },
    removeFromBlacklist: (blacklistedUserId: string | number) => {
        return request<boolean>({
            url: '/blacklist/remove',
            method: 'DELETE',
            params: {
                blacklistedUserId
            }
        });
    },
    getBlacklistList: () => {
        return request<string[]>({
            url: '/blacklist/list',
            method: 'GET'
        });
    },
    checkBlacklist: (blacklistedUserId: string | number) => {
        return request<boolean>({
            url: '/blacklist/check',
            method: 'GET',
            params: {
                blacklistedUserId
            }
        });
    },
    reportUser
};
