import { request } from '@/utils/request';
export interface AppealCreateDTO {
    appealType: string;
    appealReason: string;
    relatedId?: string | number;
    evidenceUrls?: string;
    evidenceAssetIds?: string[];
}
export interface AppealQueryDTO {
    page: number;
    size: number;
    userId?: string | number;
    appealType?: string;
    appealStatus?: string;
    keyword?: string;
}
export interface AppealVO {
    id: string;
    userId: string;
    username: string;
    nickname: string;
    avatar: string;
    appealType: string;
    appealTypeName: string;
    appealReason: string;
    appealStatus: string;
    appealStatusName: string;
    relatedId: string;
    evidenceUrls: string;
    evidenceAssetIds?: string[];
    reviewerId: string;
    reviewerName: string;
    reviewResult: string;
    createTime: string;
    reviewTime: string;
}
export function createAppeal(data: AppealCreateDTO) {
    return request({
        url: '/appeal/create',
        method: 'POST',
        data
    });
}
export function getMyAppeals(data: AppealQueryDTO) {
    return request({
        url: '/appeal/my',
        method: 'POST',
        data
    });
}
export function getAppealDetail(appealId: string | number) {
    return request({
        url: `/appeal/detail/${appealId}`,
        method: 'GET'
    });
}
export function cancelAppeal(appealId: string | number) {
    return request({
        url: `/appeal/cancel/${appealId}`,
        method: 'POST'
    });
}
export function getAppealListAdmin(data: AppealQueryDTO) {
    return request({
        url: '/admin/appeal/list',
        method: 'POST',
        data
    });
}
export function getAppealDetailAdmin(appealId: string | number) {
    return request({
        url: `/admin/appeal/detail/${appealId}`,
        method: 'GET'
    });
}
export function reviewAppeal(data: {
    appealId: string | number;
    reviewResult: string;
    reviewRemark?: string;
}) {
    return request({
        url: '/admin/appeal/review',
        method: 'POST',
        data
    });
}
export function batchReviewAppeal(appealIds: Array<string | number>, reviewResult: string) {
    return request({
        url: '/admin/appeal/batch/review',
        method: 'POST',
        data: appealIds,
        params: { reviewResult }
    });
}
export function getAppealStats() {
    return request({
        url: '/admin/appeal/stats',
        method: 'GET'
    });
}
export function getAppealRecords(appealId: string | number, page = 1, size = 20) {
    return request({
        url: `/admin/appeal/records/${appealId}`,
        method: 'GET',
        params: { page, size }
    });
}
