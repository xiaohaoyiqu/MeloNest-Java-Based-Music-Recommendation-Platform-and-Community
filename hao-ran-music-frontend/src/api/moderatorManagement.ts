import { request } from '@/utils/request';
type EntityId = string | number;
export function setAsModerator(userId: EntityId, data?: {
    moderatorNote?: string;
    dailyQuota?: number;
}) {
    return request<void>({
        url: `/moderator/admin/set/${userId}`,
        method: 'POST',
        params: data
    });
}
export function removeModerator(userId: EntityId) {
    return request<void>({
        url: `/moderator/admin/remove/${userId}`,
        method: 'POST'
    });
}
export function updateModeratorStatus(userId: EntityId, status: string) {
    return request<void>({
        url: `/moderator/admin/status/${userId}`,
        method: 'POST',
        params: { status }
    });
}
export function updateModeratorInfo(userId: EntityId, data?: {
    moderatorNote?: string;
    dailyQuota?: number;
}) {
    return request<void>({
        url: `/moderator/admin/update/${userId}`,
        method: 'POST',
        params: data
    });
}
export function getAllModerators(params?: {
    status?: string;
}) {
    return request<ModeratorVO[]>({
        url: '/moderator/admin/list',
        method: 'GET',
        params
    });
}
export function getModeratorPage(params: {
    status?: string;
    current?: number;
    size?: number;
}) {
    return request<{
        records: ModeratorVO[];
        total: number;
        current: number;
        pages: number;
        size: number;
    }>({
        url: '/moderator/admin/page',
        method: 'GET',
        params
    });
}
export function getModeratorDetail(userId: EntityId) {
    return request<ModeratorVO>({
        url: `/moderator/admin/detail/${userId}`,
        method: 'GET'
    });
}
export function resetModeratorQuota(userId: EntityId) {
    return request<void>({
        url: `/moderator/admin/reset-quota/${userId}`,
        method: 'POST'
    });
}
export function batchSetModerators(userIds: EntityId[]) {
    return request<{
        total: number;
        success: number;
    }>({
        url: '/moderator/admin/batch-set',
        method: 'POST',
        data: { userIds }
    });
}
export interface ModeratorVO {
    userId: EntityId;
    nickname: string;
    avatar: string;
    moderatorNote?: string;
    status: string;
    dailyQuota: number;
    todayProcessed: number;
    totalProcessed: number;
    accuracy: number;
    becomeModeratorTime: string;
}
